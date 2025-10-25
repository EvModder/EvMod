package net.evmodder.KeyBound.apis;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import net.evmodder.KeyBound.Main;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapState;
import net.minecraft.world.World;

public abstract class MapRelationUtils{
	public static final Stream<ItemStack> getAllNestedItems(Stream<ItemStack> items){//TODO: Move to a generic MapUtils.class
		return items.flatMap(s -> {
			BundleContentsComponent contents = s.get(DataComponentTypes.BUNDLE_CONTENTS);
			if(contents != null) return getAllNestedItems(contents.stream().sequential());
			ContainerComponent container = s.get(DataComponentTypes.CONTAINER);
			if(container != null) return getAllNestedItems(container.streamNonEmpty().sequential());
			return Stream.of(s);
		});
	}
	public static final Stream<ItemStack> getAllNestedItemsExcludingBundles(Stream<ItemStack> items){//TODO: Move to a generic MapUtils.class
		return items.flatMap(s -> {
			ContainerComponent container = s.get(DataComponentTypes.CONTAINER);
			if(container != null) return getAllNestedItemsExcludingBundles(container.streamNonEmpty());
			return Stream.of(s);
		});
	}

	public record RelatedMapsData(int prefixLen, int suffixLen, List<Integer> slots){}

	public static final int commonPrefixLen(String a, String b){
		int i=0; while(i<a.length() && i<b.length() && a.codePointAt(i) == b.codePointAt(i)) ++i; return i;
	}
	public static final int commonSuffixLen(String a, String b){
		int i=0; while(a.length()-i > 0 && b.length()-i > 0 && a.codePointAt(a.length()-i-1) == b.codePointAt(b.length()-i-1)) ++i; return i;
	}

	public static final String simplifyPosStr(String pos){
		pos = Normalizer.normalize(pos, Normalizer.Form.NFKD).toUpperCase();
		pos = pos.replace("\u250c", "TL").replace("\u2510", "TR").replace("\u2514", "BL").replace("\u2518", "BR");
		pos = pos.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", " ").trim().replaceAll("\\s+", " ");
		pos = pos.replace("TOP", "T").replace("BOTTOM", "B").replace("LEFT", "L").replace("RIGHT", "R").replace("MIDDLE", "M");
		pos = pos.replace("UP", "T").replace("DOWN", "B");
		pos = pos.replace("FIRST", "0").replace("SECOND", "1").replace("ONE", "0").replace("TWO", "1");
		pos = pos.replace("[^a-zA-Z0-9](?:by|By|BY) [a-zA-Z0-9_]+[^a-zA-Z0-9 ]?", "").trim(); // Author attribution
		while(pos.matches(".*[^0-9 ][^0-9 ].*")) pos = pos.replaceAll("([^0-9 ])([^0-9 ])", "$1 $2");
		return pos;
	}
	public static final boolean isValidPosStr(String posStr){
		return !posStr.isBlank() && posStr.split(" ").length <= 2;
	}

	/*public static final boolean areMapNamesRelated(final String name1, final String name2){
		if(name1 == null && name2 == null) return true;
		if(name1 == null || name2 == null) return false;
		if(name1.equals(name2)) return true;
		final int a = AdjacentMapUtils.commonPrefixLen(name1, name2);
		final int b = AdjacentMapUtils.commonSuffixLen(name1, name2);
		final boolean name1ValidPos = isValidPosStr(simplifyPosStr(name1.substring(a, name1.length()-b)));
		final boolean name2ValidPos = isValidPosStr(simplifyPosStr(name2.substring(a, name2.length()-b)));
		return name1ValidPos && name2ValidPos;
	}*/

	public static final int adjacentEdgeScore(final byte[] tl, final byte[] br, boolean lr_tb){
		if(tl == null || br == null || tl.length != br.length || tl.length != 16384){
			Main.LOGGER.error("AdjacentMapUtils: input byte[] arrays are invalid! Expected non-null, length=128x128");
			return -1;
		}
		int score = 0;
		boolean lastAcross = true, lastUp = true, lastDown = true;
		final int incr = lr_tb ? 128 : 1, tlStart = lr_tb ? 127 : tl.length-128;
		final int tlEnd = tl.length - tlStart;
		byte lastBrColor = -1;
		for(int i=0; i<tlEnd; i+=incr){
			// Score of [0,3] per pixel
			final boolean sameAcross = tl[i+tlStart] == br[i];
			final boolean sameUp = i > 0 && tl[i+tlStart] == br[i-incr];
			final boolean sameDown = i+incr < tl.length && tl[i+tlStart] == br[i+incr];
			if(sameAcross || sameUp || sameDown){
				score += 1;
				if(br[i] != lastBrColor && ((sameAcross && lastAcross) || (sameUp && lastUp) || (sameDown && lastDown))) score += 2;
			}
			lastBrColor = br[i];
			lastAcross = sameAcross; lastUp = sameUp; lastDown = sameDown;
		}
		return score; // Maximum score = 3*128 = 384
	}

	public static final boolean isMapArtWithCount(final ItemStack stack, final int count){
		return stack.getCount() == count && stack.getItem() == Items.FILLED_MAP;
	}
	private static final boolean differentLockedState(final Boolean locked, final ItemStack item, final World world){
		if(locked == null) return false;
		final MapIdComponent mapId = item.get(DataComponentTypes.MAP_ID);
		if(mapId == null) return false;
		final MapState state = world.getMapState(mapId);
		return state != null && state.locked != locked;
	}
	// Output inclues input map
	public static final RelatedMapsData getRelatedMapsByName(final List<ItemStack> slots, final String sourceName,
			final int count, final Boolean locked, final World world){
		List<Integer> relatedMapSlots = new ArrayList<>();
		if(sourceName == null) return new RelatedMapsData(-1, -1, relatedMapSlots);
//		sourceName = removeByArtist(sourceName);//TODO

		int prefixLen = -1, suffixLen = -1;
//		Main.LOGGER.info("MapAdjUtil: getRelatedMapsByName() called for name: "+sourceName);
//		for(int f=0; f<=(count==1 ? 36 : 9); ++f){
//			final int i = (f+27)%37 + 9; // Hotbar+Offhand [36->45], then Inv [9->35]
		Iterator<ItemStack> it = slots.iterator();
		for(int i=0; i<slots.size(); ++i){
			final ItemStack item = it.next();
			if(item.getCustomName() == null || !isMapArtWithCount(item, count)) continue;
			if(differentLockedState(locked, item, world)) continue;

			final String name = item.getCustomName().getLiteralString();
			if(name == null) continue;
//			name = removeByArtist(name);//TODO
			if(name.equals(sourceName)){relatedMapSlots.add(i); continue;}

			//if(item.equals(prevMap)) continue;
			int a = commonPrefixLen(sourceName, name), b = commonSuffixLen(sourceName, name);
			int o = a-(Math.min(name.length(), sourceName.length())-b);
			if(o>0){a-=o; b-=o;}//Handle special case: "a 11/x"+"a 111/x", a=len(a 11)=4,b=len(11/x)=4,o=2 => a=len(a ),b=len(/x)
			//if(a == 0 && b == 0) continue; // No shared prefix/suffix
			//Main.LOGGER.info("MapRestock: map"+i+" prefixLen|suffixLen: "+a+"|"+b);
			if(prefixLen == a && suffixLen == b) continue;// No change to prefix/suffix
			//Main.LOGGER.info("MapRestock: map"+i+" prefixLen|suffixLen: "+a+"|"+b);
			final String posStr = simplifyPosStr(name.substring(a, name.length()-b));
			final String sourcePosStr = simplifyPosStr(sourceName.substring(a, sourceName.length()-b));
			if(posStr.isBlank()) Main.LOGGER.info("Empty posStr for name in slot "+i+": "+name+", prefix/suffix: "+a+"/"+b);
			final boolean validMatchingPosStrs = isValidPosStr(posStr) && isValidPosStr(sourcePosStr) && 
					(posStr.indexOf(' ') != -1) == (sourcePosStr.indexOf(' ') != -1);
//			Main.LOGGER.info("slot: "+i+ ", posStr: "+posStr+", sourcePosStr: "+sourcePosStr+", bothValid: "+validMatchingPosStrs);
			if(prefixLen == -1 && suffixLen == -1){ // Prefix/suffix not yet determined
				if(validMatchingPosStrs){prefixLen = a; suffixLen = b;}
				else if(a != 0 || b != 0)
					Main.LOGGER.debug("MapAdjUtil: found matching prefix/suffix ("+a+"/"+b+"), but invalid PosStr: "+name.substring(a, name.length()-b));
				continue;
			}
			final boolean oldContainsNew = prefixLen >= a && suffixLen >= b && (prefixLen > a || suffixLen > b);
			//final boolean newContainsOld = a >= prefixLen && b >= suffixLen;
			if(oldContainsNew && validMatchingPosStrs){
				Main.LOGGER.info("MapAdjUtil: decreasing prefix/suffix from "+prefixLen+"/"+suffixLen+" to "+a+"/"+b+" for name: "+name);
				prefixLen = a; suffixLen = b; // Expand posStr
			}
			if(a+b > prefixLen+suffixLen && !isValidPosStr(simplifyPosStr(name.substring(Math.min(a, prefixLen), name.length()-Math.min(b, suffixLen))))){
				Main.LOGGER.info("MapAdjUtil: increasing prefix/suffix from "+prefixLen+"/"+suffixLen+" to "+a+"/"+b+" for name: "+name);
				prefixLen = a; suffixLen = b; // Shrink posStr
			}
		}
		if(prefixLen == -1){
			// error:
			if(relatedMapSlots.isEmpty()) Main.LOGGER.warn("MapAdjUtil: no shared prefix/suffix named maps found for name: "+sourceName);
			// 1x1:
//			if(relatedMapSlots.size() == 1) Main.LOGGER.warn("MapAdjUtil: only one shared prefix/suffix named maps found for name: "+sourceName);
			return new RelatedMapsData(prefixLen, suffixLen, relatedMapSlots);
		}
		//Main.LOGGER.info("MapAdjUtil: prefixLen="+prefixLen+", suffixLen="+suffixLen);
		final String sourcePosStr = simplifyPosStr(sourceName.substring(prefixLen, sourceName.length()-suffixLen));
		final boolean sourcePosIs2d = sourcePosStr.indexOf(' ') != -1;
		//Main.LOGGER.info("AdjacentMapUtils:sourcePosStr: "+sourcePosStr);
//		for(int f=0; f<=(count==1 ? 36 : 9); ++f){
//			final int i = (f+27)%37 + 9; // Hotbar+Offhand [36->45], then Inv [9->35]
		it = slots.iterator();
		for(int i=0; i<slots.size(); ++i){
			ItemStack item = it.next();
			if(!isMapArtWithCount(item, count) || item.getCustomName() == null) continue;
			if(differentLockedState(locked, item, world)) continue;

			final String name = item.getCustomName().getString();
			if(name == null) continue;
			if(name.length() < prefixLen+suffixLen+1 || name.equals(sourceName)) continue;
			if(!sourceName.regionMatches(0, name, 0, prefixLen) || !sourceName.regionMatches(
					sourceName.length()-suffixLen, name, name.length()-suffixLen, suffixLen)){
				//Main.LOGGER.info("MapAdjUtil: name does not match: "+name);
				continue;
			}
			final String posStr = simplifyPosStr(name.substring(prefixLen, name.length()-suffixLen));
			if(!isValidPosStr(posStr)){
				//Main.LOGGER.info("MapAdjUtil: unrecognized pos data: '"+posStr+"' for name:'"+name+"'");
				continue;
			}
			final boolean pos2d = posStr.indexOf(' ') != -1;
			//TODO: finding next map by name: "Flag 2/8" -> mismatched pos data: "Flag 4&8/8"
			if(pos2d != sourcePosIs2d){Main.LOGGER.warn("MapAdjUtil: mismatched pos data: "+name); return new RelatedMapsData(-1, -1, new ArrayList<>());}
			relatedMapSlots.add(i);
		}
		return new RelatedMapsData(prefixLen, suffixLen, relatedMapSlots);
	}

	public static final RelatedMapsData getRelatedMapsByName0(final List<ItemStack> slots, final World world){
		assert slots != null && !slots.isEmpty();
		final String name = slots.getFirst().getCustomName() == null ? null : slots.getFirst().getCustomName().getString();
		if(name == null) return new RelatedMapsData(-1, -1, new ArrayList<>());
		final MapState state = FilledMapItem.getMapState(slots.getFirst(), world);
		final Boolean locked = state == null ? null : state.locked;
		return getRelatedMapsByName(slots, name, slots.getFirst().getCount(), locked, world);
	}
/*
	record PosData2D(boolean isSideways, String minPos2, String maxPos2){}
	record Pos2DPair(String posA1, String posA2, String posB1, String posB2){}

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
	}*/

	public static final RelatedMapsData orderRelatedMaps(RelatedMapsData data){
		return data;
	}
}