package net.evmodder.KeyBound.EventListeners;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.IntStream;
import net.evmodder.KeyBound.MapRelationUtils;
import net.evmodder.KeyBound.MapRelationUtils.RelatedMapsData;
import net.evmodder.KeyBound.Main;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapState;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

public final class MapHandRestock{
	final boolean USE_NAME, USE_IMG, JUST_PICK_A_MAP = true;

	private final HashMap<String, Boolean> isSideways2d;
	//TODO: passing metadata, particularly NxM if known.
	// Higher number = closer match
	// 5 = definitely next (no doubt)
	// 4 = likely next (but not 100%)
	// 3 = maybe next (line wrapping? hex?)
	// 1,2 = not impossibly next
	private final int checkComesAfter(String posA, String posB, boolean hintSideways, boolean infoLogs){
		if(posA.isBlank() || posB.isBlank() || posA.equals(posB)) return 1; // "Map"->"Map p2", "Map start"->"Map"

		if(posA.equals("T R") && posB.equals("M L")) return 5;
		if(posA.equals("M R") && posB.equals("B L")) return 5;
		if(posA.equals("T R") && posB.equals("B L")) return 4;
		if(posA.equals("L") && posB.equals("M")) return 5;
		if(posA.equals("M") && posB.equals("R")) return 4;//4 not 5, because m->n->l->o.. vs m->r
		if(posA.equals("L") && posB.equals("R")) return 4;
		if(posA.equals("9") && posB.equals("A")) return 3;//hex?

		final boolean sameLen = posA.length() == posB.length();
		if(sameLen && posA.regionMatches(0, posB, 0, posA.length()-1) && (
				posA.codePointAt(posA.length()-1)+1 == posB.codePointAt(posA.length()-1)) ||
				(posA.codePointAt(posA.length()-1) == '9' && posB.codePointAt(posA.length()-1) == 'A' && posA.length() > 1)//49->4a
		){
			if(infoLogs) Main.LOGGER.info("MapRestock: c->c+1");
			return 5; // 4->5, E->F
		}
		if((sameLen || posA.length()+1 == posB.length()) && posA.matches("\\d{1,3}") && (""+(Integer.parseInt(posA)+1)).equals(posB)){
			if(infoLogs) Main.LOGGER.info("MapRestock: i->i+1");
			return 4; // 4->5, 9->10
		}

		int cutA, cutB, cutSpaceA, cutSpaceB;
		if(sameLen && posA.length() == 2){cutA = cutB = 1; cutSpaceA = cutSpaceB = 0;}
		else{cutA = posA.indexOf(' '); cutB = posB.indexOf(' '); cutSpaceA = cutSpaceB = 1;}
		//assert (cutA==-1) == (cutB==-1);
		if(cutA != -1 || cutB != -1){
			if((cutA == -1) != (cutB == -1)){
				if(cutA != -1 && posA.length() == posB.length()+1){cutB = cutA; cutSpaceB = 0;}
				else if(cutB != -1 && posB.length() == posA.length()+1){cutA = cutB; cutSpaceA = 0;}
				else{
					//Main.LOGGER.info("MapRestock: mismatched-2D");
					return 0;
				}
			}
			//Main.LOGGER.info("MapRestock: 2D pos not yet fully supported. A:"+posA+", B:"+posB);
			final String posA1t = posA.substring(0, cutA), posA2t = posA.substring(cutA+cutSpaceA);
			final String posB1t = posB.substring(0, cutB), posB2t = posB.substring(cutB+cutSpaceB);
			final String posA1, posA2, posB1, posB2;

			if(!hintSideways){posA1=posA1t; posA2=posA2t; posB1=posB1t; posB2=posB2t;}
			else{posA1=posA2t; posA2=posA1t; posB1=posB2t; posB2=posB1t;}

			if(posA1.equals(posB1)){
				if(infoLogs) Main.LOGGER.info("MapRestock: 2D, A1==B1"+(hintSideways?" (SIDEWAYS)":""));
				return checkComesAfter(posA2, posB2, false, infoLogs);
			}
			//if(posA2.equals(posB2)) return checkComesAfter(posA1, posB1)-2;// TODO: nerf with -2 (until impl edge matching), col should not be before row..
			if(posB2.matches("[A0]") && !posA2.equals(posB2)){
				if(infoLogs) Main.LOGGER.info("MapRestock: 2D, B2==[A0], recur checkComesAfter(A1, B1)"+(hintSideways?" (SIDEWAYS)":""));
				//Main.LOGGER.info("MapRestock: 2D pos: B2=0");
				return Math.max(checkComesAfter(posA1, posB1, false, infoLogs)-1, 0);
			}
			if(posB2.equals("1") && !posA2.matches("[01]")){
				if(infoLogs) Main.LOGGER.info("MapRestock: 2D, B2==[1], recur checkComesAfter(A1, B1)"+(hintSideways?" (SIDEWAYS)":""));
				return Math.max(checkComesAfter(posA1, posB1, false, infoLogs)-2, 0);
			}
			if(infoLogs) Main.LOGGER.info("MapRestock: 2D, checkComesAfter=0. A:"+posA+", B:"+posB+(hintSideways?" (SIDEWAYS)":""));
			return 0;
		}

		//Main.LOGGER.info("MapRestock: checkComesAfter=1. A:"+posA+", B:"+posB);
		return 0;
	}
	public final boolean simpleCanComeAfter(final String name1, final String name2){
		if(name1 == null && name2 == null) return true;
		if(name1 == null || name2 == null) return false;
		if(name1.equals(name2)) return true;
		final int a = MapRelationUtils.commonPrefixLen(name1, name2);
		final int b = MapRelationUtils.commonSuffixLen(name1, name2);
		final String posA = MapRelationUtils.simplifyPosStr(name1.substring(a, name1.length()-b));
		final String posB = MapRelationUtils.simplifyPosStr(name2.substring(a, name2.length()-b));
		final boolean name1ValidPos = MapRelationUtils.isValidPosStr(posA);
		final boolean name2ValidPos = MapRelationUtils.isValidPosStr(posB);
		if(!name1ValidPos && !name2ValidPos) return true;
		if(!name1ValidPos || !name2ValidPos) return false;
		return checkComesAfter(posA, posB, /*hintSideways=*/false, true) > 0 || checkComesAfter(posA, posB, /*hintSideways=*/true, true) > 0;
	}
	private int getNextSlotByName(final ItemStack[] slots, final RelatedMapsData data,
			final int prevSlot, final String prevName, final String prevPosStr, final boolean isSideways, final boolean infoLogs){
		int bestSlot = -1, bestConfidence=1;//bestConfidence = -1;
		//String bestName = prevName;
		for(int i : data.slots()){
			if(i == prevSlot) continue;
			if(slots[i].getCustomName() == null) continue;
			final String name = slots[i].getCustomName().getLiteralString();
			if(name == null) continue;
			final String posStr = data.prefixLen() == -1 ? name : MapRelationUtils.simplifyPosStr(name.substring(data.prefixLen(), name.length()-data.suffixLen()));
			//if(infoLogs) Main.LOGGER.info("MapRestock: checkComesAfter for name: "+name);
			final int confidence = checkComesAfter(prevPosStr, posStr, isSideways, infoLogs);
			if(confidence > bestConfidence/* || (confidence==bestConfidence && name.compareTo(bestName) < 0)*/){
				if(infoLogs) Main.LOGGER.info("MapRestock: new best confidence for "+prevPosStr+"->"+posStr+": "+confidence+" (slot"+i+")");
				bestConfidence = confidence; bestSlot = i;// bestName = name;
			}
		}
		if(infoLogs){
			if(bestSlot != -1) Main.LOGGER.info("MapRestock: findByName() succeeded, slot="+bestSlot);
			else Main.LOGGER.info("MapRestock: findByName() failed");
		}
		if(bestConfidence == 0) Main.LOGGER.warn("MapRestock: Likely skipping a map");
		return bestSlot;
	}
	private int getTrailLength(final ItemStack[] slots, final RelatedMapsData data,
			int prevSlot, final String prevName, final String prevPosStr, final boolean isSideways){
		int trailLength = 0;
		final RelatedMapsData copiedData = new RelatedMapsData(data.prefixLen(), data.suffixLen(), new ArrayList<>(data.slots()));
		while(prevSlot != -1){
			++trailLength;
			final int i = getNextSlotByName(slots, copiedData, prevSlot, prevName, prevPosStr, isSideways, /*infoLogs=*/false);
			copiedData.slots().remove(Integer.valueOf(prevSlot));
			prevSlot = i;
		}
		return trailLength;
	}
	private final int getNextSlotByName(final ItemStack[] slots, final int prevSlot, final World world){
		final String prevName = slots[prevSlot].getCustomName().getLiteralString();
		final int prevCount = slots[prevSlot].getCount();
		final boolean locked = FilledMapItem.getMapState(slots[prevSlot], world).locked;
		final RelatedMapsData data = MapRelationUtils.getRelatedMapsByName(slots, prevName, prevCount, locked, world);
		if(data.slots().isEmpty()) return -1;

		// Offhand, hotbar ascending, inv ascending
		data.slots().sort((i, j) -> i==45 ? -999 : (i - j) - (i>=36 ? 99 : 0));

		assert (data.prefixLen() == -1) == (data.suffixLen() == -1);
		assert data.prefixLen() < prevName.length() && data.suffixLen() < prevName.length();

		final String prevPosStr = data.prefixLen() == -1 ? prevName
				: MapRelationUtils.simplifyPosStr(prevName.substring(data.prefixLen(), prevName.length()-data.suffixLen()));

		final String nameWithoutPos = data.prefixLen() == -1 ? prevName
				: prevName.substring(0, data.prefixLen()) + prevName.substring(prevName.length()-data.suffixLen());
		Boolean isSideways = isSideways2d.get(nameWithoutPos);
		Main.LOGGER.info("MapRestock: findByName() called, hbSlot="+(prevSlot-36)
				+", prevPos="+prevPosStr+", prevCount="+prevCount+", numRelated="+data.slots().size());
		if(isSideways == null){
			final int sidewaysLen = getTrailLength(slots, data, prevSlot, prevName, prevPosStr, true);
			final int regularLen = getTrailLength(slots, data, prevSlot, prevName, prevPosStr, false);
			isSideways2d.put(nameWithoutPos, isSideways=(sidewaysLen > regularLen));
			Main.LOGGER.info("MapRestock: findByName() sideways2d="+(isSideways?"TRUE":"FALSE")+" (trail len "+sidewaysLen+" vs "+regularLen+")");
		}
		return getNextSlotByName(slots, data, prevSlot, prevName, prevPosStr, isSideways, /*infoLogs=*/false);//TODO: set this to true for debugging
	}

	private final int getNextSlotByImage(final ItemStack[] slots, final int prevSlot, final World world){
		final String prevName = slots[prevSlot].getCustomName() == null ? null : slots[prevSlot].getCustomName().getLiteralString();
		final int prevCount = slots[prevSlot].getCount();
		final MapState prevState = FilledMapItem.getMapState(slots[prevSlot], world);
		assert prevState != null;

		int bestSlot = -1, bestScore = 50;//TODO: magic number
		for(int i=0; i<slots.length; ++i){
			if(!MapRelationUtils.isMapArtWithCount(slots[i], prevCount) || i == prevSlot) continue;
			final MapState state = FilledMapItem.getMapState(slots[i], world);
			if(state == null) continue;
			final String name = slots[i].getCustomName() == null ? null : slots[i].getCustomName().getLiteralString();
			if(!simpleCanComeAfter(prevName, name)) continue;

			//TODO: up/down & sideways hint
			final int score = Math.max(MapRelationUtils.adjacentEdgeScore(prevState.colors, state.colors, /*leftRight=*/true),
										(int)(0.8*MapRelationUtils.adjacentEdgeScore(prevState.colors, state.colors, /*leftRight=*/false)));
			//Main.LOGGER.info("MapRestock: findByImage() score for "+name+": "+score);
			if(score > bestScore){
				Main.LOGGER.info("MapRestock: findByImage() new best score for "+name+": "+bestScore+" (slot"+i+")");
				bestScore = score; bestSlot = i;
			}
		}
		if(bestSlot != -1) Main.LOGGER.info("MapRestock: findByImage() succeeded, confidence score: "+bestScore);
		else Main.LOGGER.info("MapRestock: findByImage() failed");
		return bestSlot;
	}

	//1=map with same count
	//2=map with same count & locked state
	//2=map with same count & locked state, has name
	//3=map with same count & locked state, is multi-map group
	//4=map with same count & locked state, is multi-map group, start index
	private final int getNextSlotAny(final ItemStack[] slots, final int prevSlot, final World world){
		final int prevCount = slots[prevSlot].getCount();
		final MapState prevState = FilledMapItem.getMapState(slots[prevSlot], world);
		assert prevState != null;
		final boolean prevLocked = prevState.locked;

		int bestSlot = -1, bestScore = 0;
		String bestPosStr = null;
		//final ItemStack[] slots = player.playerScreenHandler.slots.stream().map(Slot::getStack).toArray(ItemStack[]::new);
		final int[] slotScanOrder =
		IntStream.concat(
			IntStream.concat(
				IntStream.range(PlayerScreenHandler.HOTBAR_START, PlayerScreenHandler.HOTBAR_END),
				IntStream.range(PlayerScreenHandler.INVENTORY_START, PlayerScreenHandler.INVENTORY_END)
			),
			IntStream.of(PlayerScreenHandler.OFFHAND_ID)
		).toArray();
		for(int i : slotScanOrder){
			if(!MapRelationUtils.isMapArtWithCount(slots[i], prevCount) || i == prevSlot) continue;
			if(bestScore < 1){bestScore = 1; bestSlot = i;} // It's a map with the same count
			final MapState state = FilledMapItem.getMapState(slots[i], world);
			assert state != null;
			if(state.locked != prevLocked) continue;
			if(bestScore < 2){bestScore = 2; bestSlot = i;} // It's a map with the same locked state
			if(slots[i].getCustomName() == null) continue;
			final String name = slots[i].getCustomName().getLiteralString();
			if(name == null) continue;
			if(bestScore < 3){bestScore = 3; bestSlot = i;} // It's a named map
			final RelatedMapsData data = MapRelationUtils.getRelatedMapsByName(slots, name, prevCount, prevLocked, world);
			if(data.slots().size() < 2) continue;
			String posStr = data.prefixLen() == -1 ? name : MapRelationUtils.simplifyPosStr(name.substring(data.prefixLen(), name.length()-data.suffixLen()));
			if(bestPosStr == null || posStr.compareTo(bestPosStr) < 0){bestPosStr = posStr; bestScore=4; bestSlot = i;} // In a map group, possible starter
		}
		if(bestScore == 4) Main.LOGGER.info("MapRestock: findAny() found potential TL named map");
		if(bestScore == 3) Main.LOGGER.info("MapRestock: findAny() found same count/locked named map");
		if(bestScore == 2) Main.LOGGER.info("MapRestock: findAny() found same count/locked");
		if(bestScore == 1) Main.LOGGER.info("MapRestock: findAny() found same count");
		return bestSlot;
	}

	private final void tryToStockNextMap(PlayerEntity player, ItemStack mapInHand){
		int restockFromSlot = -1;
		final ItemStack[] slots = player.playerScreenHandler.slots.stream().map(Slot::getStack).toArray(ItemStack[]::new);
		final int prevSlot = player.getInventory().selectedSlot+36;
		assert slots[prevSlot] == mapInHand;

		if(USE_NAME && restockFromSlot == -1){
			final String prevName = mapInHand.getCustomName() == null ? null : mapInHand.getCustomName().getLiteralString();
			if(prevName != null){
				Main.LOGGER.info("MapRestock: finding next map by name: "+prevName);
				restockFromSlot = getNextSlotByName(slots, prevSlot, player.getWorld());
			}
		}
		if(USE_IMG && restockFromSlot == -1){
			final MapState state = FilledMapItem.getMapState(mapInHand, player.getWorld());
			if(state != null){
				Main.LOGGER.info("MapRestock: finding next map by img-edge");
				restockFromSlot = getNextSlotByImage(slots, prevSlot, player.getWorld());
			}
		}
		if(JUST_PICK_A_MAP && restockFromSlot == -1){
			Main.LOGGER.info("MapRestock: finding any single map");
			restockFromSlot = getNextSlotAny(slots, prevSlot, player.getWorld());
		}
		if(restockFromSlot == -1){Main.LOGGER.info("MapRestock: unable to find next map"); return;}

		//PlayerScreenHandler.HOTBAR_START=36
		MinecraftClient client = MinecraftClient.getInstance();
		final int restockFromSlotFinal = restockFromSlot;
		new Timer().schedule(new TimerTask(){@Override public void run(){
			if(restockFromSlotFinal >= 36 && restockFromSlotFinal < 45){
				player.getInventory().selectedSlot = restockFromSlotFinal - 36;
				client.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(player.getInventory().selectedSlot));
				Main.LOGGER.info("MapRestock: Changed selected hotbar slot to nextMap: hb="+player.getInventory().selectedSlot);
			}
			else if(slots[prevSlot].getCount() > 2){
				Main.LOGGER.warn("MapRestock: Won't swap with inventory since prevMap count > 2");
			}
			else{
				client.interactionManager.clickSlot(0, restockFromSlotFinal, player.getInventory().selectedSlot, SlotActionType.SWAP, player);
				Main.LOGGER.info("MapRestock: Swapped inv.selectedSlot to nextMap: s="+restockFromSlotFinal);
			}
		}}, 50l);
	}

	public MapHandRestock(boolean useName, boolean useImg){
		USE_NAME = useName;
		USE_IMG = useImg;
		isSideways2d = USE_NAME ? new HashMap<>() : null;
		UseEntityCallback.EVENT.register((player, _0, hand, entity, _1) -> {
			if(!(entity instanceof ItemFrameEntity itemFrame)) return ActionResult.PASS;
			//Main.LOGGER.info("clicked item frame");
			if(hand != Hand.MAIN_HAND){
				Main.LOGGER.info("not main hand");
				return ActionResult.FAIL;
			}
			//Main.LOGGER.info("placed item from offhand");
			if(!itemFrame.getHeldItemStack().isEmpty()) return ActionResult.PASS;
			//Main.LOGGER.info("item frame is empty");
			if(player.getStackInHand(hand).getItem() != Items.FILLED_MAP) return ActionResult.PASS;
			if(player.getStackInHand(hand).getCount() > 2) return ActionResult.PASS;
			//Main.LOGGER.info("item in hand is filled_map [1or2]");
			Main.LOGGER.info("Single mapart placed, hand:"+hand.ordinal()+", looking for restock map...");
			//new Timer().schedule(new TimerTask(){@Override public void run(){
				tryToStockNextMap(player, player.getStackInHand(hand));
			//}}, 50l);
			return ActionResult.PASS;
		});
	}
}