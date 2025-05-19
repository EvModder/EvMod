package net.evmodder.KeyBound;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;

public abstract class AdjacentMapUtils{
	private static final int commonPrefixLen(String a, String b){
		int i=0; while(i<a.length() && i<b.length() && a.charAt(i) == b.charAt(i)) ++i; return i;
	}
	private static final int commonSuffixLen(String a, String b){
		int i=0; while(a.length()-i > 0 && b.length()-i > 0 && a.charAt(a.length()-i-1) == b.charAt(b.length()-i-1)) ++i; return i;
	}

	static final String simplifyPosStr(String rawPos){
		String pos = Normalizer.normalize(
				rawPos.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", " ").trim(),
				Normalizer.Form.NFD).toUpperCase()
				.replaceAll("\\s+", " ");
		while(pos.matches(".*[^0-9 ][^0-9 ].*")) pos = pos.replaceAll("([^0-9 ])([^0-9 ])", "$1 $2");
		return pos;
	}
	private static final boolean isValidPosStr(String posStr){
		return !posStr.isBlank() && posStr.split(" ").length <= 2;
	}

	public static final int adjacentEdgeScore(final byte[] tl, final byte[] br, boolean lr_tb){
		if(tl == null || br == null || tl.length != br.length || tl.length != 16384){
			Main.LOGGER.error("AdjacentMapUtils: input byte[] arrays are invalid! Expected non-null, length=128x128");
			return -1;
		}
		int score = 0;
		boolean lastAcross = true, lastUp = true, lastDown = true;
		final int incr = lr_tb ? 128 : 1, tlStart = lr_tb ? 127 : tl.length-128;
		final int tlEnd = tl.length - tlStart;
		for(int i=0; i<tlEnd; i+=incr){
			// Score of [0,3] per pixel
			final boolean sameAcross = tl[i+tlStart] == br[i];
			final boolean sameUp = i > 0 && tl[i+tlStart] == br[i-incr];
			final boolean sameDown = i+incr < tl.length && tl[i+tlStart] == br[i+incr];
			if(!sameAcross && !sameUp && !sameDown) continue;
			if(sameAcross){
				score += 2;
				if(sameAcross == lastAcross) score += 1;
			}
			else if(sameUp || sameDown){
				score += 1;
				if((sameUp && sameUp == lastUp) || (sameDown && sameDown == lastDown)) score += 1;
			}
			lastAcross = sameAcross; lastUp = sameUp; lastDown = sameDown;
		}
		return score; // Maximum score = 3*128 = 384
	}

	public static final boolean isMapArtWithCount(final ItemStack stack, final int count){
		return stack.getCount() == count && stack.getItem() == Items.FILLED_MAP;
	}
	public static final RelatedMapsData getRelatedMapsByName(List<Slot> slots, String sourceName, final int count){
		List<Integer> relatedMapSlots = new ArrayList<>();
		int prefixLen = -1, suffixLen = -1;
//		for(int f=0; f<=(count==1 ? 36 : 9); ++f){
//			final int i = (f+27)%37 + 9; // Hotbar+Offhand [36->45], then Inv [9->35]
		for(int i=0; i<slots.size(); ++i){
			final ItemStack item = slots.get(i).getStack();
			if(item.getCustomName() == null || !isMapArtWithCount(item, count)) continue;

			final String name = item.getCustomName().getLiteralString();
			if(name == null) continue;
			if(name.equals(sourceName)){relatedMapSlots.add(i); continue;}

			//if(item.equals(prevMap)) continue;
			int a = commonPrefixLen(sourceName, name), b = commonSuffixLen(sourceName, name);
			int o = a-(name.length()-b);
			if(o>0){a-=o; b-=o;}//Handle special case: "a 11/x"+"a 111/x", a=len(a 11)=4,b=len(11/x)=4,o=2 => a=len(a ),b=len(/x)
			//if(a == 0 && b == 0) continue; // No shared prefix/suffix
			//Main.LOGGER.info("MapRestock: map"+i+" prefixLen|suffixLen: "+a+"|"+b);
			if(prefixLen == a && suffixLen == b) continue;// No change to prefix/suffix
			//Main.LOGGER.info("MapRestock: map"+i+" prefixLen|suffixLen: "+a+"|"+b);
			final boolean validPosStr = isValidPosStr(simplifyPosStr(name.substring(a, name.length()-b)));
			if(prefixLen == -1 && suffixLen == -1){ // Prefix/suffix not yet determined
				if(validPosStr){prefixLen = a; suffixLen = b;}
				continue;
			}
			final boolean oldContainsNew = prefixLen >= a && suffixLen >= b;
			//final boolean newContainsOld = a >= prefixLen && b >= suffixLen;
			if(oldContainsNew && validPosStr){
				Main.LOGGER.info("MapAdjUtil: decreasing prefix/suffix len (expanding posStr) to "+a+"/"+b+" for name: "+name);
				prefixLen = a; suffixLen = b;
			}
			if(a+b > prefixLen+suffixLen && !isValidPosStr(simplifyPosStr(name.substring(Math.min(a, prefixLen), name.length()-Math.min(b, suffixLen))))){
				Main.LOGGER.info("MapAdjUtil: increasing prefix/suffix len (shrinking posStr) to "+a+"/"+b+" for name: "+name);
				prefixLen = a; suffixLen = b;
			}
		}
		if(prefixLen == -1){
			if(relatedMapSlots.isEmpty()) Main.LOGGER.info("MapAdjUtil: no shared prefix/suffix named maps found for name: "+sourceName);
			return new RelatedMapsData(prefixLen, suffixLen, relatedMapSlots);
		}
		//Main.LOGGER.info("MapAdjUtil: prefixLen="+prefixLen+", suffixLen="+suffixLen);
		final String sourcePosStr = simplifyPosStr(sourceName.substring(prefixLen, sourceName.length()-suffixLen));
		final boolean sourcePosIs2d = sourcePosStr.indexOf(' ') != -1;
//		for(int f=0; f<=(count==1 ? 36 : 9); ++f){
//			final int i = (f+27)%37 + 9; // Hotbar+Offhand [36->45], then Inv [9->35]
		for(int i=0; i<slots.size(); ++i){
			ItemStack item = slots.get(i).getStack();
			if(!isMapArtWithCount(item, count) || item.getCustomName() == null) continue;
			final String name = item.getCustomName().getLiteralString();
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
			if(pos2d != sourcePosIs2d){Main.LOGGER.warn("MapAdjUtil: mismatched pos data: "+name); return new RelatedMapsData(-1, -1, List.of());}
			relatedMapSlots.add(i);
		}
		return new RelatedMapsData(prefixLen, suffixLen, relatedMapSlots);
	}
}
