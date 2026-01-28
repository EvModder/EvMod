package net.evmodder.evmod.listeners;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import com.nimbusds.oauth2.sdk.util.StringUtils;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.Main;
import net.evmodder.evmod.apis.MapRelationUtils;
import net.evmodder.evmod.apis.ClickUtils;
import net.evmodder.evmod.apis.ClickUtils.ActionType;
import net.evmodder.evmod.apis.ClickUtils.InvAction;
import net.evmodder.evmod.apis.MapRelationUtils.RelatedMapsData;
import net.evmodder.evmod.onTick.AutoPlaceMapArt;
import net.evmodder.evmod.onTick.AutoRemoveMapArt;
import net.evmodder.evmod.onTick.UpdateInventoryHighlights;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapState;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

public final class MapHandRestock{
	private final boolean JUST_PICK_A_MAP = true;
	private final PosData2D POS_DATA_404 = new PosData2D(false, null, null);

	private final String getCustomNameOrNull(ItemStack stack){
		final Text text = stack.getCustomName();
		return text == null ? null : text.getString();
	}

	//Friend: AutoPlaceMapArt
	private final record PosData2D(boolean isSideways, String minPos2, String maxPos2){}
	private final record Pos2DPair(String posA1, String posA2, String posB1, String posB2){}
	private final HashMap<String, PosData2D> posData2dForName = new HashMap<>(0);

	// For 2D maps, figure out the largest A2/B2 (2nd pos) in the available collection
	private final PosData2D getPosData2D(final List<String> posStrs, final boolean isSideways){
		assert posStrs.size() >= 2 : "PosData2D requires at least 2 related maps";
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
//		if(infoLogs) Main.LOGGER.info("MapRestock: confidence=0. A:"+posA+", B:"+posB);
		return 0;
	}

	// Negative number implies end of row -> beginning of next row (terrible hack)
	private final int checkComesAfter2d(final Pos2DPair posStrs, final PosData2D posData2d, boolean infoLogs){
		final String posA1, posA2, posB1, posB2;
		if(!posData2d.isSideways){posA1=posStrs.posA1; posA2=posStrs.posA2; posB1=posStrs.posB1; posB2=posStrs.posB2;}
		else{posA1=posStrs.posA2; posA2=posStrs.posA1; posB1=posStrs.posB2; posB2=posStrs.posB1;}

		if(posA1.equals(posB1)){
			if(infoLogs) Main.LOGGER.info("MapRestock: 2D, A2:"+posA2+", B2:"+posB2+", A1==B1:"+posA1/*+(isSideways?" (SIDEWAYS)":"")*/);
			return checkComesAfter1d(posA2, posB2, infoLogs);
		}
		if(posData2d.minPos2 != null){
			if(posB2.equals(posData2d.minPos2) && posA2.equals(posData2d.maxPos2)){
				if(infoLogs) Main.LOGGER.info("MapRestock: 2D, A1:"+posA1+", B1:"+posB1+", A2==^ && B2==$, check(A1, B1)"/*+(isSideways?" (SIDEWAYS)":"")*/);
				return -checkComesAfter1d(posA1, posB1, infoLogs);
			}
		}
		else{
			if(posB2.matches("[A0]") && posData2d.maxPos2 == null ? !posA2.equals(posB2) : posA2.equals(posData2d.maxPos2)){
				if(infoLogs) Main.LOGGER.info("MapRestock: 2D, A1:"+posA1+", B1:"+posB1+", B2==[A0], check(A1, B1)"/*+(isSideways?" (SIDEWAYS)":"")*/);
				return -Math.max(checkComesAfter1d(posA1, posB1, infoLogs)-1, 0);
			}
			if(posB2.equals("1") && posData2d.maxPos2 == null ? !posA2.matches("[01]") : posA2.equals(posData2d.maxPos2)){
				if(infoLogs) Main.LOGGER.info("MapRestock: 2D, A1:"+posA1+", B1:"+posB1+", B2==[1], check(A1, B1)"/*+(isSideways?" (SIDEWAYS)":"")*/);
				return -Math.max(checkComesAfter1d(posA1, posB1, infoLogs)-2, 0);
			}
		}
		if(infoLogs) Main.LOGGER.info("MapRestock: 2D, A:"+posA1+" "+posA2+", B:"+posB1+" "+posB2+", confidence=0"/*+(isSideways?" (SIDEWAYS)":"")*/);
		return 0;
	}
	private final Pos2DPair get2dPosStrs(String posA, String posB, boolean infoLogs){
		int cutA, cutB, cutSpaceA, cutSpaceB;
		if(posA.length() == posB.length() && posA.length() == 2){cutA = cutB = 1; cutSpaceA = cutSpaceB = 0;}
		else{cutA = posA.indexOf(' '); cutB = posB.indexOf(' '); cutSpaceA = cutSpaceB = 1;}
		//assert (cutA==-1) == (cutB==-1);
		if(cutA == -1 && cutB == -1) return null;
		if((cutA == -1) != (cutB == -1)){
			if(cutA != -1 && posA.length() == posB.length()+1){cutB = cutA; cutSpaceB = 0;}
			else if(cutB != -1 && posB.length() == posA.length()+1){cutA = cutB; cutSpaceA = 0;}
			else{
				if(infoLogs) Main.LOGGER.info("MapRestock: confidence=0. mismatched-2D");
				return null;
			}
		}
		//Main.LOGGER.info("MapRestock: 2D pos not yet fully supported. A:"+posA+", B:"+posB);
		final String posA1 = posA.substring(0, cutA), posA2 = posA.substring(cutA+cutSpaceA);
		final String posB1 = posB.substring(0, cutB), posB2 = posB.substring(cutB+cutSpaceB);

		return new Pos2DPair(posA1, posA2, posB1, posB2);
	}
	private final int checkComesAfterStrict(String posA, String posB, PosData2D posData2d, boolean infoLogs){
		if(posA.isBlank() || posB.isBlank() || posA.equals(posB)) return 1; // "Map"->"Map p2", "Map start"->"Map"

		if(posA.equals("T R") && posB.equals("M L")) return 5;
		if(posA.equals("M R") && posB.equals("B L")) return 5;
		if(posA.equals("T R") && posB.equals("B L")) return 4;
		if(posA.matches("[A-Z]9") && posB.matches("[A-Z]10") && posA.charAt(0) == posB.charAt(0)) return 5;

		if(posData2d == null) return checkComesAfter1d(posA, posB, infoLogs);

		if(posData2d.maxPos2 == null){
			int check1d = checkComesAfter1d(posA, posB, infoLogs);
			if(check1d != 0) return check1d;
		}

		Pos2DPair posStrs2d = get2dPosStrs(posA, posB, infoLogs);
		return posStrs2d == null ? checkComesAfter1d(posA, posB, infoLogs) : checkComesAfter2d(posStrs2d, posData2d, infoLogs);
	}
	private final int checkComesAfterAnyOrder(String posA, String posB, PosData2D posData2d, boolean infoLogs){
		int a = checkComesAfterStrict(posA, posB, posData2d, infoLogs);
		int b = checkComesAfterStrict(posB, posA, posData2d, /*infoLogs*/false);
		return Math.abs(a) > Math.abs(b) ? a : b;
//		return Math.max(checkComesAfterStrict(posA, posB, posData2d, infoLogs), checkComesAfterStrict(posB, posA, posData2d, /*infoLogs*/false));
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
		final PosData2D regular2dData = getPosData2D(List.of(posA, posB), /*isSideways=*/false);
		final PosData2D rotated2dData = getPosData2D(List.of(posA, posB), /*isSideways=*/true);
		//TODO: set final boolean param to true for debugging
		return checkComesAfterAnyOrder(posA, posB, regular2dData, /*infoLogs=*/false) != 0
			|| checkComesAfterAnyOrder(posA, posB, rotated2dData, /*infoLogs=*/false) != 0;
	}
	private final String getPosStrFromName(String name, final RelatedMapsData data){
		if(data.prefixLen() == -1) return name;
		name = MapRelationUtils.removeByArtist(name);
		return MapRelationUtils.simplifyPosStr(name.substring(data.prefixLen(), name.length()-data.suffixLen()));
	}
	private final int getNextSlotByNameUsingPosData2d(final List<ItemStack> slots, final RelatedMapsData data,
			final String prevPosStr, final PosData2D posData2d, final boolean infoLogs){
		assert !data.slots().isEmpty() : "getNextSlotByNameUsingPosData2d() must be called with non-empty slot list!";
		int bestSlot = data.slots().getFirst(), bestConfidence=0;
		//String bestName = prevName;
		for(int i : data.slots()){
			final String posStr = getPosStrFromName(getCustomNameOrNull(slots.get(i)), data);
			//if(infoLogs) Main.LOGGER.info("MapRestock: checkComesAfter for name: "+name);
			final int confidence = checkComesAfterAnyOrder(prevPosStr, posStr, posData2d, infoLogs);
			if(Math.abs(confidence) > Math.abs(bestConfidence)/* || (confidence==bestConfidence && name.compareTo(bestName) < 0)*/){
				if(infoLogs) Main.LOGGER.info("MapRestock: new best confidence for "+prevPosStr+"->"+posStr+": "+confidence+" (slot"+i+")");
				bestConfidence = confidence; bestSlot = i;// bestName = name;
			}
		}
		if(bestConfidence == 0 && infoLogs) Main.LOGGER.warn("MapRestock: bestConfidence==0! Likely skipping a map");
		return bestSlot * (bestConfidence < 0 ? -1 : 1);//TODO: remove horrible hack
	}
	private final record TrailLenAndScore(int len, long score){}
	private final TrailLenAndScore getTrailLengthAndScore(final List<ItemStack> slots, final RelatedMapsData data, ItemStack prevMap, int prevSlot,
			final PosData2D posData2d, final World world){
		int trailLength = 0;
		long scoreSum = 0;
		final RelatedMapsData copiedData = new RelatedMapsData(data.prefixLen(), data.suffixLen(), new ArrayList<>(data.slots()));
		while(!copiedData.slots().isEmpty()){
			final String prevName = getCustomNameOrNull(prevMap);
			final String prevPosStr = getPosStrFromName(prevName, data);
			final int i = getNextSlotByNameUsingPosData2d(slots, copiedData, prevPosStr, posData2d, /*infoLogs=*/false);
			final int currSlot = Math.abs(i);
			MapState prevState = FilledMapItem.getMapState(prevMap, world);
			MapState currState = FilledMapItem.getMapState(prevMap=slots.get(currSlot), world);
			if(prevState != null && currState != null && i > 0){
				//TODO: for up/down, need to look further back in the trail (last leftmost map)
				//TODO: might as well check up/down for every map in inv once we have the arrangement finder
				scoreSum += MapRelationUtils.adjacentEdgeScore(prevState.colors, currState.colors, /*leftRight=*/true);
			}
			copiedData.slots().remove(Integer.valueOf(prevSlot));
			prevSlot = currSlot;
			++trailLength;
			if(copiedData.slots().isEmpty()) Main.LOGGER.info("MapRestock: Trail ended on pos: "+prevPosStr);
		}
		return new TrailLenAndScore(trailLength, scoreSum);
	}
	private final int getNextSlotByName(final List<ItemStack> slots, final ItemStack prevMap, final int prevSlot, final World world){
		final String prevName = getCustomNameOrNull(prevMap);
		final MapState state = FilledMapItem.getMapState(prevMap, world);
		final Boolean locked = state == null ? null : state.locked;
		final RelatedMapsData data = MapRelationUtils.getRelatedMapsByName(slots, prevName, prevMap.getCount(), locked, world);
		data.slots().remove(Integer.valueOf(prevSlot));
		if(data.slots().isEmpty()){
			Main.LOGGER.info("MapRestock: getNextSlotByName() found no related named maps ");
			return -1;
		}
		if(data.prefixLen() == -1) return -1; // Indicates all related map slots have identical item names

//		Main.LOGGER.info("prevSlot: "+prevSlot+", related slots: "+data.slots());

		// Offhand, hotbar ascending, inv ascending
		data.slots().sort((i, j) -> i==45 ? -99 : (i - j) - (i>=36 ? 99 : 0));

//		assert (data.prefixLen() == -1) == (data.suffixLen() == -1); // Unreachable. But yes, should always be true
		assert data.prefixLen() < prevName.length() && data.suffixLen() < prevName.length();

		final String nameWoArtist = MapRelationUtils.removeByArtist(prevName);
		final String prevPosStr = MapRelationUtils.simplifyPosStr(nameWoArtist.substring(data.prefixLen(), nameWoArtist.length()-data.suffixLen()));

		PosData2D posData2d = posData2dForName.getOrDefault(prevName, POS_DATA_404);
		if(posData2d == POS_DATA_404){
			final List<String> mapNames = Stream.concat(Stream.of(prevName),
					data.slots().stream().map(i -> getCustomNameOrNull(slots.get(i)))).toList();
			final String prefixStr = nameWoArtist.substring(0, data.prefixLen());
			final String suffixStr = nameWoArtist.substring(nameWoArtist.length()-data.suffixLen());
			{
				String nonPosName = prefixStr + "[XY]" + suffixStr;
				if(prevName.startsWith(nameWoArtist)) nonPosName += prevName.substring(nameWoArtist.length());
				else if(prevName.endsWith(nameWoArtist)) nonPosName = nonPosName + prevName.substring(0, prevName.length()-nameWoArtist.length());
//				else Main.LOGGER.info("MapRestock: trouble re-attaching artist name (not at start or end)");
				Main.LOGGER.info("MapRestock: finding posData2d for map '"+nonPosName+"', related names: "+mapNames.size());
			}
			final boolean hasSizeInName = suffixStr.matches("\\s*(of|/)\\s*\\d+.*");
			if(hasSizeInName){
				posData2d = null;
				Main.LOGGER.info("MapRestock: Detected 'X/SIZE' posStr format, treating map as 1d");
			}
			else{
				final List<String> mapPosStrs = mapNames.stream().map(name -> getPosStrFromName(name, data)).toList();
				final PosData2D sidewaysPos2dData = getPosData2D(mapPosStrs, true);
				final PosData2D regularPos2dData = getPosData2D(mapPosStrs, false);
				final TrailLenAndScore sidewaysTrail = getTrailLengthAndScore(slots, data, prevMap, prevSlot, sidewaysPos2dData, world);
				final TrailLenAndScore regularTrail = getTrailLengthAndScore(slots, data, prevMap, prevSlot, regularPos2dData, world);
				final boolean isSideways = sidewaysTrail.len > regularTrail.len || (sidewaysTrail.len == regularTrail.len && sidewaysTrail.score > regularTrail.score);
				posData2d = isSideways ? sidewaysPos2dData : regularPos2dData;
				//TODO: if sidewaysLen == regularLen, determine which has better ImgEdgeStitching sum
				Main.LOGGER.info("MapRestock: Determined sideways="+posData2d.isSideways+" (trail len "+sidewaysTrail.len+" vs "+regularTrail.len+")");
			}
			for(String name : mapNames) posData2dForName.put(name, posData2d);
		}
		Main.LOGGER.info("MapRestock: getNextSlotByName() called, hb="+(prevSlot-36)+", prevPos="+prevPosStr+", numMaps="+data.slots().size()
				+(posData2d == null ? ", posData2d=null" : ", minPos2="+posData2d.minPos2+", maxPos2="+posData2d.maxPos2+", sideways="+posData2d.isSideways)
				+", name: "+prevName);

		final int i = getNextSlotByNameUsingPosData2d(slots, data, prevPosStr, posData2d, /*infoLogs=*/true);//TODO: set to true for debugging
		if(i == -999){
			Main.LOGGER.info("MapRestock: getNextSlotByName() failed");
			return -1;
		}
		Main.LOGGER.info("MapRestock: getNextSlotByName() succeeded, slot="+i+", name="+getCustomNameOrNull(slots.get(Math.abs(i))));
		return Math.abs(i);//i != -999 ? i : getNextSlotAny(slots, prevSlot, world);
	}

	private final int getNextSlotByImage(final List<ItemStack> slots, final ItemStack prevMap, final int prevSlot, final World world){
		final String prevName = getCustomNameOrNull(prevMap);
		final int prevCount = prevMap.getCount();
		final MapState prevState = FilledMapItem.getMapState(prevMap, world);
		assert prevState != null;

		final List<Integer> relatedSlots = MapRelationUtils.getRelatedMapsByName(slots, prevName, prevCount, prevState.locked, world).slots();
		//List<Integer> usedSlots = !data.slots().isEmpty() ? data.slots() : IntStream.range(0, slots.length).boxed().toList();

		int bestSlot = -1, bestScore = 50;//TODO: magic number
		//for(int i : usedSlots){
		for(int i=0; i<slots.size(); ++i){
			if(!MapRelationUtils.isMapArtWithCount(slots.get(i), prevCount) || i == prevSlot) continue;
			final MapState state = FilledMapItem.getMapState(slots.get(i), world);
			if(state == null) continue;
			final String name = getCustomNameOrNull(slots.get(i));
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
//		else Main.LOGGER.info("MapRestock: findByImage() failed");
		return bestSlot;
	}

	//1=map with same count
	//2=map with same count & locked state
	//2=map with same count & locked state, has name
	//3=map with same count & locked state, has name, matches multi-map group
	//4=map with same count & locked state, has name, matches multi-map group, is start index
	private final int getNextSlotFirstMap(final List<ItemStack> slots, final ItemStack prevMap, final int prevSlot, final World world){
		final int prevCount = prevMap.getCount();
//		assert prevState != null; // Only possible if map IDs get corrupted (or a player in creative spawns an id that doesn't exist yet)
		final Boolean prevLocked;
		{
			final MapState prevState = FilledMapItem.getMapState(prevMap, world);
			prevLocked = prevState == null ? null : prevState.locked;
		}
		final String prevName = getCustomNameOrNull(prevMap);
		final boolean prevWas1x1 = MapRelationUtils.getRelatedMapsByName(slots, prevName, prevCount, prevLocked, world).slots().size() < 2;

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
			if(!MapRelationUtils.isMapArtWithCount(slots.get(i), prevCount) || i == prevSlot) continue;
			if(bestScore < 1){bestScore = 1; bestSlot = i;} // It's a map with the same count
			final MapState state = FilledMapItem.getMapState(slots.get(i), world);
//			assert state != null;
			if(state == null) continue; // Only possible if map IDs get corrupted (or a player in creative spawns an id that doesn't exist yet)
			if(prevLocked != null && state.locked != prevLocked) continue;
			if(bestScore < 2){bestScore = 2; bestSlot = i;} // It's a map with the same locked state
			final String name = getCustomNameOrNull(slots.get(i));
			if(name == null) continue;
			if(bestScore < 3){bestScore = 3; bestSlot = i;} // It's a named map
			final RelatedMapsData data = MapRelationUtils.getRelatedMapsByName(slots, name, prevCount, prevLocked, world);
			if(data.slots().size() < 2 != prevWas1x1) continue;
			if(bestScore < 4){bestScore = 4; bestSlot = i;} // It's a named map with matching 1x1 status
			final String posStr = getPosStrFromName(name, data);
			if(bestPosStr == null || posStr.compareTo(bestPosStr) < 0){bestPosStr = posStr; bestScore=5; bestSlot = i;} // In a map group, possible starter
		}
		if(bestScore == 5) Main.LOGGER.info("MapRestock: findAny() found same count/locked/hasName/is1x1, potential TL map");
		if(bestScore == 4) Main.LOGGER.info("MapRestock: findAny() found same count/locked/hasName/is1x1");
		if(bestScore == 3) Main.LOGGER.info("MapRestock: findAny() found same count/locked/hasName");
		if(bestScore == 2) Main.LOGGER.info("MapRestock: findAny() found same count/locked");
		if(bestScore == 1) Main.LOGGER.info("MapRestock: findAny() found same count");
		return bestSlot;
	}

	private final boolean isInNearbyItemFrame(final ItemStack stack, final PlayerEntity player, final int dist){
		return !player.getWorld().getEntitiesByType(
				TypeFilter.instanceOf(ItemFrameEntity.class),
				Box.of(player.getPos(), dist, dist, dist),
				e -> ItemStack.areEqual(e.getHeldItemStack(), stack)).isEmpty();
	}

	private final List<ItemStack> getSlotsWithBundleSub(List<ItemStack> slots, PlayerEntity player, String prevName){
		if(!Configs.Generic.PLACEMENT_HELPER_MAPART_FROM_BUNDLE.getBooleanValue()) return slots;
		if(!slots.stream().map(s -> s.get(DataComponentTypes.BUNDLE_CONTENTS))
				.anyMatch(b -> b != null && !b.isEmpty() && b.stream().allMatch(s -> s.getItem() == Items.FILLED_MAP))) return slots;
		ArrayList<ItemStack> slotsWithBundleSub = new ArrayList<>(slots);
		for(int i=0; i<slots.size(); ++i){
			BundleContentsComponent contents = slots.get(i).get(DataComponentTypes.BUNDLE_CONTENTS);
			if(contents == null || contents.isEmpty()) continue;
			int topBundleSlot = Configs.Generic.BUNDLES_ARE_REVERSED.getBooleanValue() ? contents.size()-1 : 0;
			ItemStack stack = contents.get(topBundleSlot);
			if(stack.getItem() != Items.FILLED_MAP) continue;
			if(slots.stream().anyMatch(s -> ItemStack.areItemsAndComponentsEqual(s, stack))) continue; // If map is also present unbundled in inv
			if(stack.getCount() == 1 && isInNearbyItemFrame(stack, player, 20)) continue;

			final String name = getCustomNameOrNull(stack);
			if((prevName == null) != (name == null) || (prevName != null && prevName.equals(name))) continue;

			Main.LOGGER.info("MapRestock: available from bundle slot="+i+",name="+name);
			slotsWithBundleSub.set(i, stack);
		}
		return slotsWithBundleSub;
	}

	private boolean waitingForRestock;
	private final void tryToStockNextMap(ItemStack prevMap, Hand hand){
		assert prevMap != null && prevMap.getItem() == Items.FILLED_MAP;

		final MinecraftClient client = MinecraftClient.getInstance();
		final PlayerEntity player = client.player;
		final int prevSlot = hand == Hand.MAIN_HAND ? player.getInventory().selectedSlot+36 : PlayerScreenHandler.OFFHAND_ID;
		final List<ItemStack> slots = player.playerScreenHandler.slots.stream().map(Slot::getStack).toList();
		final String prevName = getCustomNameOrNull(prevMap);

		final List<ItemStack> slotsWithBundleSub = getSlotsWithBundleSub(slots, player, prevName);

		int restockFromSlot = -1;
		if(Configs.Generic.PLACEMENT_HELPER_MAPART_USE_NAMES.getBooleanValue() && restockFromSlot == -1){
			if(prevName != null){
				Main.LOGGER.info("MapRestock: finding next map by name: "+prevName);
				restockFromSlot = getNextSlotByName(slotsWithBundleSub, prevMap, prevSlot, player.getWorld());
			}
		}
		if(Configs.Generic.PLACEMENT_HELPER_MAPART_USE_IMAGE.getBooleanValue() && restockFromSlot == -1 && !posData2dForName.containsKey(prevName)){
			final MapState state = FilledMapItem.getMapState(prevMap, player.getWorld());
			if(state != null){
				Main.LOGGER.info("MapRestock: finding next map by img-edge");
				restockFromSlot = getNextSlotByImage(/*slotsWithBundleSub*/slots, prevMap, prevSlot, player.getWorld());
			}
		}
		if(JUST_PICK_A_MAP && restockFromSlot == -1){
			Main.LOGGER.info("MapRestock: finding next map by ANY (count->locked->named->related)");
			restockFromSlot = getNextSlotFirstMap(/*slotsWithBundleSub*/slots, prevMap, prevSlot, player.getWorld());
		}
		if(restockFromSlot == -1){Main.LOGGER.info("MapRestock: unable to find next map"); return;}

		//PlayerScreenHandler.HOTBAR_START=36
		final boolean isHotbarSlot = restockFromSlot >= 36 && restockFromSlot < 45;
		if(prevMap.getCount() > 2 && !isHotbarSlot){
			Main.LOGGER.warn("MapRestock: Won't swap with inventory since prevMap count > 2");
			return;
		}

		// Wait for hand to be free
		final int restockFromSlotFinal = restockFromSlot;
		waitingForRestock = true;
		new Thread(){@Override public void run(){
//			Main.LOGGER.info("MapRestock: waiting for currently placed map to load");
			while(player != null && !player.isInCreativeMode() && UpdateInventoryHighlights.hasCurrentlyBeingPlacedMapArt()) Thread.yield();
			if(player == null){waitingForRestock = false; return;}

//			Main.LOGGER.info("MapRestock: ok, sync client execution");
			client.executeSync(()->{
//				try{sleep(50l);}catch(InterruptedException e){e.printStackTrace();waitingForRestock=false;} // 50ms = 1tick
//				Main.LOGGER.info("MapRestock: ok, doing restock click(s)");

				if(slots.get(restockFromSlotFinal).get(DataComponentTypes.BUNDLE_CONTENTS) != null){
					ArrayDeque<InvAction> clicks = new ArrayDeque<>();
//					clicks.add(new ClickEvent(restockFromSlotFinal, 0, SlotActionType.PICKUP)); // Pickup bundle
//					clicks.add(new ClickEvent(36+player.getInventory().selectedSlot, 1, SlotActionType.PICKUP)); // Place in active hb slot
//					clicks.add(new ClickEvent(restockFromSlotFinal, 0, SlotActionType.PICKUP)); // Putback bundle
					clicks.add(new InvAction(restockFromSlotFinal, 1, ActionType.CLICK)); // Take last from bundle
					clicks.add(new InvAction(36+player.getInventory().selectedSlot, 0, ActionType.CLICK)); // Place in active hb slot
					ClickUtils.executeClicks(_0->true, ()->Main.LOGGER.info("HandRestockFromBundle: DONE"), clicks);
					Main.LOGGER.info("MapRestock: Extracted from bundle: s="+restockFromSlotFinal+" -> hb="+player.getInventory().selectedSlot);
				}
				else if(isHotbarSlot){
//					player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(restockFromSlotFinal - 36));
//					player.getInventory().selectedSlot = restockFromSlotFinal - 36;
					player.getInventory().setSelectedSlot(restockFromSlotFinal - 36);
					Main.LOGGER.info("MapRestock: Changed selected hotbar slot to nextMap: hb="+player.getInventory().selectedSlot);
				}
				else{
					client.interactionManager.clickSlot(0, restockFromSlotFinal, player.getInventory().selectedSlot, SlotActionType.SWAP, player);
					Main.LOGGER.info("MapRestock: Swapped inv.selectedSlot to nextMap: s="+restockFromSlotFinal);
				}
				waitingForRestock = false;
			});
		}}.start();
	}

	private boolean hasAutoPlaceableMapInInv;
	public MapHandRestock(final boolean allowAutoPlacer, final boolean allowAutoRemover){
		final AutoPlaceMapArt autoPlacer = allowAutoPlacer ? new AutoPlaceMapArt(
				stack->tryToStockNextMap(stack, Hand.MAIN_HAND)) : null;
		final AutoRemoveMapArt autoRemover = allowAutoRemover ? new AutoRemoveMapArt() : null;
		if(allowAutoPlacer || allowAutoRemover){
			AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
				if(allowAutoPlacer && autoPlacer.hasKnownLayout()){
					BlockPos placement;
					if(entity instanceof ItemFrameEntity ife && ife.getHeldItemStack().getItem() == Items.FILLED_MAP && autoPlacer.ifePosFilter(ife)
							&& (placement=autoPlacer.getPlacement(ife.getHeldItemStack())) != null && !placement.equals(ife.getBlockPos()))
					{
						Main.LOGGER.info("MapRestock: Player manually removed an incorrectly placed map (during AutoPlaceMapArt)");
					}
					else{
						autoPlacer.disableAndReset();
						Main.LOGGER.info("MapRestock: Disabling AutoPlaceMapArt due to EntityAttackEvent");
					}
				}
				else if(allowAutoRemover && entity instanceof ItemFrameEntity ife && ife.getHeldItemStack().getItem() == Items.FILLED_MAP
						&& autoRemover.mapRemoved(ife))
				{
					Main.LOGGER.info("MapRestock: AutoRemoveMapArt is active");
				}
				return ActionResult.PASS;
			});
		}

		UseEntityCallback.EVENT.register((player, _0, hand, entity, _1) -> {
			if(!(entity instanceof ItemFrameEntity ife)) return ActionResult.PASS;
			//Main.LOGGER.info("clicked item frame");
			if(allowAutoRemover && autoRemover.isActivelyRemoving()){
				autoRemover.disableAndReset();
				Main.LOGGER.info("MapRestock: Disabling AutoRemoveMapArt due to EntityInteractEvent");
			}
			if(hand != Hand.MAIN_HAND){
				Main.LOGGER.info("MapHandRestock: not main hand: "+hand.name());
//				return ActionResult.FAIL;
			}
			//Main.LOGGER.info("placed item from mainhand");
			if(!ife.getHeldItemStack().isEmpty()){
				if(allowAutoPlacer && Configs.Generic.MAPART_AUTOPLACE_ANTI_ROTATE.getBooleanValue()
						&& autoPlacer.hasKnownLayout() && hasAutoPlaceableMapInInv
						&& ife.getHeldItemStack().getItem() == Items.FILLED_MAP){
					Main.LOGGER.warn("AutoPlaceMapArt: Discarding a (likely accidental) map-rotation click");
					return ActionResult.FAIL;
				}
				return ActionResult.PASS;
			}
			//Main.LOGGER.info("item frame is empty");

			final ItemStack stack = player.getStackInHand(hand);
			if(waitingForRestock && (stack.isEmpty() || stack.getItem() == Items.FILLED_MAP)){
				// Little safety net to keep player from placing offhand item into iFrame if right-clicking faster than hand restock can handle
				Main.LOGGER.warn("MapRestock: Player right-clicking iFrame before previous tryToStockNextMap() has finished!");
				player.sendMessage(Text.literal("Warn: right-clicking iFrame before AutoHandRestock has finished"), true);
				return ActionResult.FAIL;
			}
			if(stack.getItem() != Items.FILLED_MAP) return ActionResult.PASS;
			if(stack.getCount() > 2) return ActionResult.PASS;
			//Main.LOGGER.info("item in hand is filled_map [1or2]");

			final int shSlot = hand == Hand.MAIN_HAND ? player.getInventory().selectedSlot : 40;
//			assert ItemStack.areEqual(player.getStackInHand(hand), player.getInventory().getStack(player.getInventory().selectedSlot));
			UpdateInventoryHighlights.setCurrentlyBeingPlacedMapArt(stack, shSlot);

			if(allowAutoPlacer && autoPlacer.recalcLayout(player, ife, stack) && (hasAutoPlaceableMapInInv=
					(autoPlacer.getNearestMapPlacement(player, /*allowOutsideReach=*/true, /*allowMapInHand=*/false) != null))
			){
				Main.LOGGER.info("MapRestock: AutoPlaceMapArt is active");
			}
			else if(Configs.Generic.PLACEMENT_HELPER_MAPART.getBooleanValue()){
				Main.LOGGER.info("MapRestock: doing best-guess hand restock");
				final int prevSlot = hand == Hand.MAIN_HAND ? player.getInventory().selectedSlot+36 : PlayerScreenHandler.OFFHAND_ID;
				final ItemStack mapInHand = player.getStackInHand(hand);
				assert mapInHand == player.playerScreenHandler.slots.get(prevSlot).getStack();
				tryToStockNextMap(mapInHand, hand);
			}
			return ActionResult.PASS;
		});
	}
}