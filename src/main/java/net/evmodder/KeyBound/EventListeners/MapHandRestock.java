package net.evmodder.KeyBound.EventListeners;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.IntStream;
import com.nimbusds.oauth2.sdk.util.StringUtils;
import net.evmodder.KeyBound.MapRelationUtils;
import net.evmodder.KeyBound.MapRelationUtils.RelatedMapsData;
import net.evmodder.EvLib.Pair;
import net.evmodder.KeyBound.Main;
import net.evmodder.KeyBound.MapGroupUtils;
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

	record PosData2D(boolean isSideways, String minPos2, String maxPos2){}
	private final HashMap<String, PosData2D> posData2dForName;

	// For 2D maps, figure out the largest A2/B2 (2nd pos) in the available collection
	private PosData2D getPosData2D(final List<String> posStrs, final boolean isSideways){
		assert posStrs.size() >= 2;
		final boolean hasSpace = posStrs.stream().anyMatch(n -> n.indexOf(' ') != -1);
		final boolean cutMid = !hasSpace && posStrs.stream().allMatch(n -> n.length() == 2); // TODO: A9->A10 support
		final boolean someSpace = hasSpace && posStrs.stream().anyMatch(n -> n.indexOf(' ') == -1);
		final List<String> pos2s;
		if(cutMid){
			if(isSideways) pos2s = posStrs.stream().map(n -> n.substring(0, 1)).toList();
			else pos2s = posStrs.stream().map(n -> n.substring(1)).toList();
		}
		else if(someSpace){
			final int spaceIdx = !someSpace ? -1 : posStrs.stream().filter(n -> n.indexOf(' ') != -1).findAny().get().indexOf(' ');
			if(posStrs.stream().map(n -> n.indexOf(' ')).anyMatch(i -> i != -1 && i != spaceIdx)){
				Main.LOGGER.warn("MapRestock: getMaxPos2() detected mismatched pos2d spacing");
			}
			if(isSideways) pos2s = posStrs.stream().map(n -> n.substring(0, spaceIdx)).toList();
			else pos2s = posStrs.stream().map(n -> n.substring(spaceIdx + (n.indexOf(' ') == spaceIdx ? 1 : 0))).toList();
		}
		else if(hasSpace){
			if(isSideways) pos2s = posStrs.stream().map(n -> n.substring(0, n.indexOf(' '))).toList();
			else pos2s = posStrs.stream().map(n -> n.substring(n.indexOf(' ')+1)).toList();
		}
		else{
			//Main.LOGGER.warn("MapRestock: getMaxPos2() does not recognize pos '"+posStrs.getFirst()+"' as 2D");
			//pos2s = posStrs.stream();
			return new PosData2D(isSideways, null, null);
		}
		Comparator<String> c = (a, b) -> StringUtils.isNumeric(a) && StringUtils.isNumeric(b) ? Integer.parseInt(a)-Integer.parseInt(b) : a.compareTo(b);
		String min = pos2s.stream().min(c).get();
		String max = pos2s.stream().max(c).get();
		if(min.length() == 1 && !min.matches("[A01TL]")) min = null;
		return new PosData2D(isSideways, min, max);
	}

	//TODO: passing metadata, particularly NxM if known.
	// Higher number = closer match
	// 5 = definitely next (no doubt)
	// 4 = likely next (but not 100%)
	// 3 = maybe next (line wrapping? hex?)
	// 1,2 = not impossibly next
	private final int checkComesAfter1d(final String posA, final String posB, final boolean infoLogs){
		if(posA.equals("L") && posB.equals("M")) return 5;
		if(posA.equals("M") && posB.equals("R")) return 4;//4 not 5, because m->n->l->o.. vs m->r
		if(posA.equals("L") && posB.equals("R")) return 4;
		if(posA.equals("9") && posB.equals("A")) return 3;//hex?

		final boolean sameLen = posA.length() == posB.length();
		if(sameLen && posA.regionMatches(0, posB, 0, posA.length()-1) && (
				posA.codePointAt(posA.length()-1)+1 == posB.codePointAt(posB.length()-1)) ||
				(posA.codePointAt(posA.length()-1) == '9' && posB.codePointAt(posB.length()-1) == 'A' && posA.length() > 1)//49->4a
		){
			if(infoLogs) Main.LOGGER.info("MapRestock: confidence=5. c->c+1");
			return 5; // 4->5, E->F
		}
		if((sameLen || posA.length()+1 == posB.length()) && posA.matches("\\d{1,3}") && (""+(Integer.parseInt(posA)+1)).equals(posB)){
			if(infoLogs) Main.LOGGER.info("MapRestock: confidence=4. i->i+1");
			return 4; // 4->5, 9->10
		}
		if(infoLogs) Main.LOGGER.info("MapRestock: confidence=0. A:"+posA+", B:"+posB);
		return 0;
	}
	private final int checkComesAfter(String posA, String posB, PosData2D posData2d, boolean infoLogs){
		if(posA.isBlank() || posB.isBlank() || posA.equals(posB)) return 1; // "Map"->"Map p2", "Map start"->"Map"

		if(posA.equals("T R") && posB.equals("M L")) return 5;
		if(posA.equals("M R") && posB.equals("B L")) return 5;
		if(posA.equals("T R") && posB.equals("B L")) return 4;
		if(posA.matches("[A-Z]9") && posB.matches("[A-Z]10") && posA.charAt(0) == posB.charAt(0)) return 5;

		if(posData2d.maxPos2 == null){
			int check1d = checkComesAfter1d(posA, posB, infoLogs);
			if(check1d != 0) return check1d;
		}

		int cutA, cutB, cutSpaceA, cutSpaceB;
		if(posA.length() == posB.length() && posA.length() == 2){cutA = cutB = 1; cutSpaceA = cutSpaceB = 0;}
		else{cutA = posA.indexOf(' '); cutB = posB.indexOf(' '); cutSpaceA = cutSpaceB = 1;}
		//assert (cutA==-1) == (cutB==-1);
		if(cutA != -1 || cutB != -1){
			if((cutA == -1) != (cutB == -1)){
				if(cutA != -1 && posA.length() == posB.length()+1){cutB = cutA; cutSpaceB = 0;}
				else if(cutB != -1 && posB.length() == posA.length()+1){cutA = cutB; cutSpaceA = 0;}
				else{
					if(infoLogs) Main.LOGGER.info("MapRestock: confidence=0. mismatched-2D");
					return 0;
				}
			}
			//Main.LOGGER.info("MapRestock: 2D pos not yet fully supported. A:"+posA+", B:"+posB);
			final String posA1t = posA.substring(0, cutA), posA2t = posA.substring(cutA+cutSpaceA);
			final String posB1t = posB.substring(0, cutB), posB2t = posB.substring(cutB+cutSpaceB);
			final String posA1, posA2, posB1, posB2;

			if(!posData2d.isSideways){posA1=posA1t; posA2=posA2t; posB1=posB1t; posB2=posB2t;}
			else{posA1=posA2t; posA2=posA1t; posB1=posB2t; posB2=posB1t;}

			if(posA1.equals(posB1)){
				if(infoLogs) Main.LOGGER.info("MapRestock: 2D, A:"+posA+", B:"+posB+", A1==B1"+(posData2d.isSideways?" (SIDEWAYS)":"")+"");
				return checkComesAfter1d(posA2, posB2, infoLogs);
			}
			if(posData2d.minPos2 != null){
				if(posB2.equals(posData2d.minPos2) && posA2.equals(posData2d.maxPos2)){
					if(infoLogs) Main.LOGGER.info("MapRestock: 2D, A:"+posA+", B:"+posB+", A2==^ && B2==$, check(A1, B1)"+(posData2d.isSideways?" (SIDEWAYS)":""));
					return checkComesAfter1d(posA1, posB1, infoLogs);
				}
			}
			else{
				if(posB2.matches("[A0]") && posData2d.maxPos2 == null ? !posA2.equals(posB2) : posA2.equals(posData2d.maxPos2)){
					if(infoLogs) Main.LOGGER.info("MapRestock: 2D, A:"+posA+", B:"+posB+", B2==[A0], check(A1, B1)"+(posData2d.isSideways?" (SIDEWAYS)":""));
					return Math.max(checkComesAfter1d(posA1, posB1, infoLogs)-1, 0);
				}
				if(posB2.equals("1") && posData2d.maxPos2 == null ? !posA2.matches("[01]") : posA2.equals(posData2d.maxPos2)){
					if(infoLogs) Main.LOGGER.info("MapRestock: 2D, A:"+posA+", B:"+posB+", B2==[1], check(A1, B1)"+(posData2d.isSideways?" (SIDEWAYS)":""));
					return Math.max(checkComesAfter1d(posA1, posB1, infoLogs)-2, 0);
				}
			}
			if(infoLogs) Main.LOGGER.info("MapRestock: 2D, A:"+posA+", B:"+posB+", confidence=0"+(posData2d.isSideways?" (SIDEWAYS)":""));
			return 0;
		}
		return checkComesAfter1d(posA, posB, infoLogs);
	}
	public final boolean simpleCanComeAfter(final String name1, final String name2){
		if(name1 == null && name2 == null) return true;
		if(name1 == null || name2 == null) return false;
		if(name1.equals(name2)) return true;
		int a = MapRelationUtils.commonPrefixLen(name1, name2);
		int b = MapRelationUtils.commonSuffixLen(name1, name2);
		final int o = a-(Math.min(name1.length(), name2.length())-b);
		if(o>0){a-=o; b-=o;}//Handle special case: "a 11/x"+"a 111/x", a=len(a 11)=4,b=len(11/x)=4,o=2 => a=len(a ),b=len(/x)
		final String posA = MapRelationUtils.simplifyPosStr(name1.substring(a, name1.length()-b));
		final String posB = MapRelationUtils.simplifyPosStr(name2.substring(a, name2.length()-b));
		final boolean name1ValidPos = MapRelationUtils.isValidPosStr(posA);
		final boolean name2ValidPos = MapRelationUtils.isValidPosStr(posB);
		if(!name1ValidPos && !name2ValidPos) return true;
		if(!name1ValidPos || !name2ValidPos) return false;
		if((posA.indexOf(' ') == -1) != (posB.indexOf(' ') == -1)){
			Main.LOGGER.warn("simpleCanComeAfter: mismatched pos data: "+posA+", "+posB);
			return true; // TODO: or return false?
		}
		final PosData2D regular2dData = getPosData2D(List.of(posA, posB), false);
		final PosData2D sideways2dData = getPosData2D(List.of(posA, posB), true);
		//TODO: set final boolean param to true for debugging
		return checkComesAfter(posA, posB, regular2dData, /*infoLogs=*/false) > 0 || checkComesAfter(posA, posB, sideways2dData, /*infoLogs=*/false) > 0;
	}
	private String getPosStrFromName(final String name, final RelatedMapsData data){
		return data.prefixLen() == -1 ? name : MapRelationUtils.simplifyPosStr(name.substring(data.prefixLen(), name.length()-data.suffixLen()));
	}
	private int getNextSlotByName(final ItemStack[] slots, final RelatedMapsData data,
			final String prevPosStr, final PosData2D posData2d, final boolean infoLogs){
		int bestSlot = -1, bestConfidence=1;//bestConfidence = -1;
		//String bestName = prevName;
		for(int i : data.slots()){
			final String posStr = getPosStrFromName(slots[i].getCustomName().getLiteralString(), data);
			//if(infoLogs) Main.LOGGER.info("MapRestock: checkComesAfter for name: "+name);
			final int confidence = checkComesAfter(prevPosStr, posStr, posData2d, infoLogs);
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
	private Pair<Integer, Long> getTrailLengthAndScore(final ItemStack[] slots, final RelatedMapsData data, int prevSlot,
			final PosData2D posData2d, final World world){
		int trailLength = 0;
		long scoreSum = 0;
		final RelatedMapsData copiedData = new RelatedMapsData(data.prefixLen(), data.suffixLen(), new ArrayList<>(data.slots()));
		while(/*prevSlot != -1*/true){
			final String prevName = slots[prevSlot].getCustomName().getLiteralString();
			final String prevPosStr = getPosStrFromName(prevName, data);
			final int i = getNextSlotByName(slots, copiedData, prevPosStr, posData2d, /*infoLogs=*/false);
			if(i == -1){
				Main.LOGGER.info("MapRestock: Trail ended on pos: "+prevPosStr);
				return new Pair<>(trailLength, scoreSum);
			}
			final byte[] prevColors = FilledMapItem.getMapState(slots[prevSlot], world).colors;
			final byte[] colors = FilledMapItem.getMapState(slots[i], world).colors;
			scoreSum += MapRelationUtils.adjacentEdgeScore(prevColors, colors, /*leftRight=*/true);//TODO: detect when up/down vs left/right
			copiedData.slots().remove(Integer.valueOf(prevSlot));
			prevSlot = i;
			++trailLength;
		}
		//return new Pair<>(trailLength, scoreSum);
	}
	private final int getNextSlotByName(final ItemStack[] slots, final int prevSlot, final World world){
		final String prevName = slots[prevSlot].getCustomName().getLiteralString();
		final int prevCount = slots[prevSlot].getCount();
		final MapState state = FilledMapItem.getMapState(slots[prevSlot], world);
		final Boolean locked = state == null ? null : state.locked;
		final RelatedMapsData data = MapRelationUtils.getRelatedMapsByName(slots, prevName, prevCount, locked, world);
		data.slots().remove(Integer.valueOf(prevSlot));
		if(data.slots().isEmpty()) return -1;

		// Offhand, hotbar ascending, inv ascending
		data.slots().sort((i, j) -> i==45 ? -999 : (i - j) - (i>=36 ? 99 : 0));

		assert (data.prefixLen() == -1) == (data.suffixLen() == -1);
		assert data.prefixLen() < prevName.length() && data.suffixLen() < prevName.length();

		final String prevPosStr = getPosStrFromName(prevName, data);

		PosData2D posData2d = posData2dForName.get(prevName);
		Main.LOGGER.info("MapRestock: findByName() called, hb="+(prevSlot-36)+", prevPos="+prevPosStr+", numMaps="+data.slots().size());
		if(posData2d == null){
			final List<String> mapNames = data.slots().stream().map(i -> slots[i].getCustomName().getLiteralString()).toList();
			final List<String> mapPosStrs = mapNames.stream().map(name -> getPosStrFromName(name, data)).toList();
			final PosData2D sidewaysPos2dData = getPosData2D(mapPosStrs, true);
			final PosData2D regularPos2dData = getPosData2D(mapPosStrs, false);
			final Pair<Integer, Long> sidewaysTrail = getTrailLengthAndScore(slots, data, prevSlot, sidewaysPos2dData, world);
			final Pair<Integer, Long> regularTrail = getTrailLengthAndScore(slots, data, prevSlot, regularPos2dData, world);
			final boolean isSideways = sidewaysTrail.a > regularTrail.a || (sidewaysTrail.a == regularTrail.a && sidewaysTrail.b > regularTrail.b);
			posData2d = isSideways ? sidewaysPos2dData : regularPos2dData;
			//TODO: if sidewaysLen == regularLen, determine which has better ImgEdgeStitching sum
			for(String name : mapNames) posData2dForName.put(name, posData2d);
			Main.LOGGER.info("MapRestock: Determined sideways="+posData2d.isSideways+" (trail len "+sidewaysTrail.a+" vs "+regularTrail.a+")");
		}
		Main.LOGGER.info("MapRestock: findByName() minPos2="+posData2d.minPos2+", maxPos2="+posData2d.maxPos2+", sideways="+posData2d.isSideways);

		final int i = getNextSlotByName(slots, data, prevPosStr, posData2d, /*infoLogs=*/true);//TODO: set to true for debugging
		return i != -1 ? i : getNextSlotAny(slots, prevSlot, world);
	}

	private final int getNextSlotByImage(final ItemStack[] slots, final int prevSlot, final World world){
		final String prevName = slots[prevSlot].getCustomName() == null ? null : slots[prevSlot].getCustomName().getLiteralString();
		final int prevCount = slots[prevSlot].getCount();
		final MapState prevState = FilledMapItem.getMapState(slots[prevSlot], world);
		assert prevState != null;

		final List<Integer> relatedSlots = MapRelationUtils.getRelatedMapsByName(slots, prevName, prevCount, prevState.locked, world).slots();
		//List<Integer> usedSlots = !data.slots().isEmpty() ? data.slots() : IntStream.range(0, slots.length).boxed().toList();

		int bestSlot = -1, bestScore = 50;//TODO: magic number
		//for(int i : usedSlots){
		for(int i=0; i<slots.length; ++i){
			if(!MapRelationUtils.isMapArtWithCount(slots[i], prevCount) || i == prevSlot) continue;
			final MapState state = FilledMapItem.getMapState(slots[i], world);
			if(state == null) continue;
			final String name = slots[i].getCustomName() == null ? null : slots[i].getCustomName().getLiteralString();
			if(!simpleCanComeAfter(prevName, name)) continue;

			//TODO: up/down & sideways hint
			final int score = Math.max(MapRelationUtils.adjacentEdgeScore(prevState.colors, state.colors, /*leftRight=*/true),
										(int)(0.8*MapRelationUtils.adjacentEdgeScore(prevState.colors, state.colors, /*leftRight=*/false)))
					* (relatedSlots.contains(i) ? 2 : 1);
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

	private final void tryToStockNextMap(PlayerEntity player){
		int restockFromSlot = -1;
		final ItemStack[] slots = player.playerScreenHandler.slots.stream().map(Slot::getStack).toArray(ItemStack[]::new);
		final int prevSlot = player.getInventory().selectedSlot+36;
		final ItemStack mapInHand = player.getMainHandStack();
		assert slots[prevSlot] == mapInHand;

		final MapState state = FilledMapItem.getMapState(mapInHand, player.getWorld());
		MinecraftClient client = MinecraftClient.getInstance();
		if(state != null && mapInHand.getCount() == 1 &&
				IntStream.range(0, 41).noneMatch(i -> i != player.getInventory().selectedSlot &&
				FilledMapItem.getMapState(player.getInventory().getStack(i), player.getWorld()) == state)){
//			InventoryHighlightUpdater.currentlyBeingPlacedIntoItemFrameSlot = player.getInventory().selectedSlot;
			InventoryHighlightUpdater.currentlyBeingPlacedIntoItemFrame = MapGroupUtils.getIdForMapState(state);
			InventoryHighlightUpdater.onUpdateTick(client);
		}

		if(USE_NAME && restockFromSlot == -1){
			final String prevName = mapInHand.getCustomName() == null ? null : mapInHand.getCustomName().getLiteralString();
			if(prevName != null){
				Main.LOGGER.info("MapRestock: finding next map by name: "+prevName);
				restockFromSlot = getNextSlotByName(slots, prevSlot, player.getWorld());
			}
		}
		if(USE_IMG && restockFromSlot == -1){
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
		final boolean isHotbarSlot = restockFromSlot >= 36 && restockFromSlot < 45;
		if(mapInHand.getCount() > 2 && !isHotbarSlot){
			Main.LOGGER.warn("MapRestock: Won't swap with inventory since prevMap count > 2");
			return;
		}

		final int restockFromSlotFinal = restockFromSlot;
		new Timer().schedule(new TimerTask(){@Override public void run(){
			if(isHotbarSlot){
				player.getInventory().selectedSlot = restockFromSlotFinal - 36;
				client.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(player.getInventory().selectedSlot));
				Main.LOGGER.info("MapRestock: Changed selected hotbar slot to nextMap: hb="+player.getInventory().selectedSlot);
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
		posData2dForName = USE_NAME ? new HashMap<>() : null;
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
			if(player.getMainHandStack().getItem() != Items.FILLED_MAP) return ActionResult.PASS;
			if(player.getMainHandStack().getCount() > 2) return ActionResult.PASS;
			//Main.LOGGER.info("item in hand is filled_map [1or2]");
			Main.LOGGER.info("Single mapart placed, looking for restock map...");
			tryToStockNextMap(player);
			return ActionResult.PASS;
		});
	}
}