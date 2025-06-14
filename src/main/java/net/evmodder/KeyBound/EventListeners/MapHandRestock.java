package net.evmodder.KeyBound.EventListeners;

import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.IntStream;
import net.evmodder.KeyBound.AdjacentMapUtils;
import net.evmodder.KeyBound.AdjacentMapUtils.RelatedMapsData;
import net.evmodder.KeyBound.Main;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapState;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

public final class MapHandRestock{
	final boolean USE_NAME, USE_IMG, JUST_PICK_A_MAP = true;

	private final int getNextSlotByImage(final PlayerEntity player, final byte[] colors, final int count, final String prevName){
		List<Slot> slots = player.playerScreenHandler.slots;
		int bestSlot = -1, bestScore = 50;//TODO: magic number
		for(int i=0; i<slots.size(); ++i){
			ItemStack stack = slots.get(i).getStack();
			if(stack.getItem() != Items.FILLED_MAP) continue;
			MapIdComponent mapId = stack.get(DataComponentTypes.MAP_ID);
			if(mapId == null) continue;
			MapState state = player.getWorld().getMapState(mapId);
			if(state == null) continue;
			final String name = stack.getCustomName() == null ? null : stack.getCustomName().getLiteralString();
			if(!AdjacentMapUtils.areMapNamesRelated(prevName, name)) continue;
			final int score = AdjacentMapUtils.adjacentEdgeScore(colors, state.colors, /*leftRight=*/true);
			if(score > bestScore){bestScore = score; bestSlot = i;}
		}
		if(bestSlot != -1) Main.LOGGER.info("MapRestock: findByImage() succeeded, confidence score: "+bestScore);
		else Main.LOGGER.info("MapRestock: findByImage() failed");
		return bestSlot;
	}

	private final HashMap<String, Boolean> isSideways2d;
	//TODO: passing metadata, particularly NxM if known.
	// Higher number = closer match
	// 5,4 = definitely next (no doubt)
	// 3 = likely next (but not 100%)
	// 2 = maybe next (line wrapping?)
	// 1 = not impossibly next
	private final int checkComesAfter(String posA, String posB, Boolean hintSideways){
		if(posA.isBlank() || posB.isBlank() || posA.equals(posB)) return 1; // "Map"->"Map p2", "Map start"->"Map"

		if(posA.equals("T R") && posB.equals("M L")) return 4;
		if(posA.equals("M R") && posB.equals("B L")) return 4;
		if(posA.equals("T R") && posB.equals("B L")) return 3;
		if(posA.equals("L") && posB.equals("M")) return 4;
		if(posA.equals("M") && posB.equals("R")) return 3;//3 not 4, because m->n->l->o.. vs m->r
		if(posA.equals("L") && posB.equals("R")) return 3;
		if(posA.equals("9") && posB.equals("A")) return 2;//hex?

		final boolean sameLen = posA.length() == posB.length();
		if(sameLen && posA.regionMatches(0, posB, 0, posA.length()-1) && (
				posA.codePointAt(posA.length()-1)+1 == posB.codePointAt(posA.length()-1)) ||
				(posA.codePointAt(posA.length()-1) == '9' && posB.codePointAt(posA.length()-1) == 'A' && posA.length() > 1)//49->4a
		){
			Main.LOGGER.info("MapRestock: c->c+1");
			return 5; // 4->5, E->F
		}
		if((sameLen || posA.length()+1 == posB.length()) && posA.matches("\\d{1,3}") && (""+(Integer.parseInt(posA)+1)).equals(posB)){
			Main.LOGGER.info("MapRestock: i->i+1");
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
				Main.LOGGER.info("MapRestock: 2D, A1==B1"+(hintSideways?" (SIDEWAYS)":""));
				return checkComesAfter(posA2, posB2, null)+1;
			}
			//if(posA2.equals(posB2)) return checkComesAfter(posA1, posB1)-2;// TODO: nerf with -2 (until impl edge matching), col should not be before row..
			if(posB2.matches("[A0]") && !posA2.equals(posB2)){
				Main.LOGGER.info("MapRestock: 2D, B2==[A0], recur checkComesAfter(A1, B1)"+(hintSideways?" (SIDEWAYS)":""));
				//Main.LOGGER.info("MapRestock: 2D pos: B2=0");
				return checkComesAfter(posA1, posB1, null)-1;
			}
			if(posB2.equals("1") && !posA2.matches("[01]")){
				Main.LOGGER.info("MapRestock: 2D, B2==[1], recur checkComesAfter(A1, B1)"+(hintSideways?" (SIDEWAYS)":""));
				return checkComesAfter(posA1, posB1, null)-2;
			}
			Main.LOGGER.info("MapRestock: 2D, checkComesAfter=1. A:"+posA+", B:"+posB+(hintSideways?" (SIDEWAYS)":""));
			return 1;
		}

		//Main.LOGGER.info("MapRestock: checkComesAfter=1. A:"+posA+", B:"+posB);
		return 1;
	}
	private int getLongestNameTrail(final PlayerEntity player, String prevName, int prevCount, final boolean locked){
		int trailLength = 0;
		while(true){
			int slot = getNextSlotByName(player, prevName, prevCount, locked);
			if(slot == -1) return trailLength;
			ItemStack stack = player.playerScreenHandler.slots.get(slot).getStack();
			prevName = stack.getCustomName() == null ? null : stack.getCustomName().getLiteralString();
			if(prevName == null) return trailLength;
			prevCount = stack.getCount();
			++trailLength;
			stack.setCount(0); // Avoid looping back to the same slot
			player.getInventory().markDirty();
		}
	}
	private final int getNextSlotByName(final PlayerEntity player, final String prevName, final int count, final boolean locked){
		final PlayerScreenHandler psh = player.playerScreenHandler;
		final RelatedMapsData data = AdjacentMapUtils.getRelatedMapsByName(psh.slots, prevName, count, locked, player.getWorld());
		if(data.slots().isEmpty()) return -1;

		// Offhand, hotbar ascending, inv ascending
		data.slots().sort((i, j) -> i==45 ? -999 : (i - j) - (i>=36 ? 99 : 0));

		final String prevPosStr = data.prefixLen() == -1 ? prevName
				: AdjacentMapUtils.simplifyPosStr(prevName.substring(data.prefixLen(), prevName.length()-data.suffixLen()));

		final String nameWithoutPos = prevName.substring(0, data.prefixLen()) + prevName.substring(prevName.length()-data.suffixLen());
		Boolean isSideways = isSideways2d.get(nameWithoutPos);
		if(isSideways == null){
			isSideways2d.put(nameWithoutPos, true); // Try it
			int withSideways = getLongestNameTrail(player, prevName, count, locked);
			isSideways2d.put(nameWithoutPos, isSideways=false);
			int withoutSideways = getLongestNameTrail(player, prevName, count, locked);
			if(withSideways > withoutSideways){
				Main.LOGGER.info("MapRestock: findByName() detected likely sideways 2d map (trail len "+withSideways+" vs "+withoutSideways+")");
				isSideways2d.put(nameWithoutPos, isSideways=true);
			}
		}

		int bestSlot = -1, bestConfidence=1;//bestConfidence = -1;
		String bestName = prevName;
		for(int slot : data.slots()){
			final ItemStack stack = psh.slots.get(slot).getStack();
			if(stack.getCustomName() == null) continue;
			final String name = stack.getCustomName() == null ? null : stack.getCustomName().getLiteralString();
			if(name == null) continue;
			if(name.equals(prevName) && slot-36 == player.getInventory().selectedSlot) continue;
			final String posStr = data.prefixLen() == -1 ? name :
					AdjacentMapUtils.simplifyPosStr(name.substring(data.prefixLen(), name.length()-data.suffixLen()));
			//Main.LOGGER.info("MapRestock: checkComesAfter for name: "+name);
			final int confidence = checkComesAfter(prevPosStr, posStr, isSideways);
			if(confidence > bestConfidence || (confidence==bestConfidence && name.compareTo(bestName) < 0)){
				Main.LOGGER.info("MapRestock: new best confidence for "+name+": "+confidence);
				bestConfidence = confidence; bestSlot = slot; bestName = name;
			}
		}
		if(bestSlot != -1) Main.LOGGER.info("MapRestock: findByName() succeeded");
		else Main.LOGGER.info("MapRestock: findByName() failed");
		if(bestConfidence == 0) Main.LOGGER.warn("MapRestock: Likely skipping a map");
		return bestSlot;
	}
	//1=map with same count
	//2=map with same count & locked state
	//2=map with same count & locked state, has name
	//3=map with same count & locked state, is multi-map group
	//4=map with same count & locked state, is multi-map group, start index
	private final int getNextSlotAny(PlayerEntity player, Hand hand, final int count, final boolean locked){
		int bestSlot = -1, bestScore = 0;
		String bestPosStr = null;
		final int[] slots =
		IntStream.concat(
			IntStream.concat(
				IntStream.range(PlayerScreenHandler.HOTBAR_START, PlayerScreenHandler.HOTBAR_END),
				IntStream.range(PlayerScreenHandler.INVENTORY_START, PlayerScreenHandler.INVENTORY_END)
			),
			IntStream.of(PlayerScreenHandler.OFFHAND_ID)
		).toArray();
		for(int i : slots){
			final ItemStack stack = player.playerScreenHandler.getSlot(i).getStack();
			if(!AdjacentMapUtils.isMapArtWithCount(stack, count)) continue;
			if(bestScore < 1){bestScore = 1; bestSlot = i;} // It's a map with the same count
			final MapIdComponent mapId = stack.get(DataComponentTypes.MAP_ID);
			final MapState state = player.getWorld().getMapState(mapId);
			if(state == null || state.locked != locked) continue;
			if(bestScore < 2){bestScore = 2; bestSlot = i;} // It's a map with the same locked state
			if(stack.getCustomName() == null) continue;
			final String name = stack.getCustomName().getLiteralString();
			if(name == null) continue;
			if(bestScore < 3){bestScore = 3; bestSlot = i;} // It's a named map
			final RelatedMapsData data = AdjacentMapUtils.getRelatedMapsByName(player.playerScreenHandler.slots, name, count, locked, player.getWorld());
			if(data.slots().size() < 2) continue;
			String posStr = data.prefixLen() == -1 ? name : AdjacentMapUtils.simplifyPosStr(name.substring(data.prefixLen(), name.length()-data.suffixLen()));
			if(bestPosStr == null || posStr.compareTo(bestPosStr) < 0){bestPosStr = posStr; bestScore=4; bestSlot = i;} // In a map group, possible starter
		}
		if(bestScore == 4) Main.LOGGER.info("MapRestock: findAny() found potential TL named map");
		if(bestScore == 3) Main.LOGGER.info("MapRestock: findAny() found same count/locked named map");
		if(bestScore == 2) Main.LOGGER.info("MapRestock: findAny() found same count/locked");
		if(bestScore == 1) Main.LOGGER.info("MapRestock: findAny() found same count");
		return bestSlot;
	}

	private final void tryToStockNextMap(PlayerEntity player, Hand hand, ItemStack prevMap){
//		if(!player.getStackInHand(hand).isEmpty()){
//			Main.LOGGER.info("MapRestock: hand still not empty after right-clicking item frame"); return;
//		}
		Main.LOGGER.info("Single mapart placed, hand:"+hand.ordinal()+", looking for restock map...");
		int restockFromSlot = -1;
		final int prevCount = prevMap.getCount();
		final String prevName = prevMap.getCustomName() == null ? null : prevMap.getCustomName().getLiteralString();
		final MapIdComponent mapId = prevMap.get(DataComponentTypes.MAP_ID);
		final MapState state = mapId == null ? null : player.getWorld().getMapState(mapId);
		if(USE_NAME && restockFromSlot == -1){
			if(prevName != null){
				Main.LOGGER.info("MapRestock: finding next map by name: "+prevName);
				restockFromSlot = getNextSlotByName(player, prevName, prevCount, state.locked);
			}
		}
		if(USE_IMG && restockFromSlot == -1){
			if(state != null){
				Main.LOGGER.info("MapRestock: finding next map by img-edge");
				restockFromSlot = getNextSlotByImage(player, state.colors, prevCount, prevName);
			}
		}
		if(JUST_PICK_A_MAP && restockFromSlot == -1){
			Main.LOGGER.info("MapRestock: finding any single map");
			restockFromSlot = getNextSlotAny(player, hand, prevCount, state.locked);
		}
		if(restockFromSlot == -1){Main.LOGGER.info("MapRestock: unable to find next map"); return;}

		//PlayerScreenHandler.HOTBAR_START=36
		MinecraftClient client = MinecraftClient.getInstance();
		if(restockFromSlot >= 36 && restockFromSlot < 45){
			player.getInventory().selectedSlot = restockFromSlot - 36;
			client.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(player.getInventory().selectedSlot));
			Main.LOGGER.info("MapRestock: Changed selected hotbar slot to nextMap: hb="+player.getInventory().selectedSlot);
		}
		else if(prevCount > 2){
			Main.LOGGER.warn("MapRestock: Won't swap with inventory since prevMap count > 2");
		}
		else{
			client.interactionManager.clickSlot(0, restockFromSlot, player.getInventory().selectedSlot, SlotActionType.SWAP, player);
			Main.LOGGER.info("MapRestock: Swapped inv.selectedSlot to nextMap: s="+restockFromSlot);
		}
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
			final ItemStack stack = player.getStackInHand(hand).copy();
			new Timer().schedule(new TimerTask(){@Override public void run(){tryToStockNextMap(player, hand, stack);}}, 50l);
			return ActionResult.PASS;
		});
	}
}