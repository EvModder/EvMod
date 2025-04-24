package net.evmodder.KeyBound;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

public abstract class MapClickMoveNeighbors{
	private static boolean isMapArt(ItemStack stack){
		return stack != null && !stack.isEmpty() && Registries.ITEM.getId(stack.getItem()).getPath().equals("filled_map");
	}

	private static int commonPrefixLen(String a, String b){
		int i=0; while(i<a.length() && i<b.length() && a.charAt(i) == b.charAt(i)) ++i; return i;
	}
	private static int commonSuffixLen(String a, String b){
		int i=0; while(a.length()-i > 0 && b.length()-i > 0 && a.charAt(a.length()-i-1) == b.charAt(b.length()-i-1)) ++i; return i;
	}

	private static String simplifyPosStr(String rawPos){
		String pos = Normalizer.normalize(
				rawPos.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", " ").trim(),
				Normalizer.Form.NFD).toUpperCase();
		while(pos.matches(".*[A-Z][A-Z].*")) pos = pos.replaceAll("([A-Z])([A-Z])", "$1 $2");
		return pos;
	}
	private static boolean isValidWhenSimplifiedPosStr(String posStr){
		posStr = simplifyPosStr(posStr);
		return !posStr.isBlank() && posStr.split(" ").length <= 2;
	}


	public static void moveNeighbors(PlayerEntity player, int destSlot, ItemStack mapMoved){
		Main.LOGGER.info("MapMoveClick: moveNeighbors() called");
		final String movedName = mapMoved.getCustomName().getLiteralString();
		HashSet<Integer> slotsInvolved = new HashSet<>();
		int prefixLen = -1, suffixLen = -1;
		List<Slot> slots = player.currentScreenHandler.slots;
//		int destSlot = -1;
		for(int i=0; i<slots.size(); ++i){
			if(i == destSlot) continue;
			ItemStack item = slots.get(i).getStack();
			if(!isMapArt(item) || item.getCustomName() == null/* || item.getCount() != mapMoved.getCount()*/) continue;
			if(ItemStack.areItemsAndComponentsEqual(item, mapMoved)){
//				if(destSlot == -1) destSlot = i; else{
				Main.LOGGER.info("MapMoveClick: TODO:support multiple copies (i:"+i+",dest"+destSlot);return;
			}
			final String name = item.getCustomName().getLiteralString();
			if(name.equals(movedName)){
				slotsInvolved.add(i);
				continue;
			}
			int a = commonPrefixLen(movedName, name), b = commonSuffixLen(movedName, name);
			int o = a-(name.length()-b);
			if(o>0){a-=o; b-=o;}//Handle special case: "a 11/x"+"a 111/x", a=len(a 11)=4,b=len(11/x)=4,o=2 => a=len(a ),b=len(/x)
			//Main.LOGGER.info("a:"+a+", b:"+b+", name:"+name);
			//if(a == 0 && b == 0) continue;
			if(prefixLen == a && suffixLen == b) continue;
			final boolean validPosStr = isValidWhenSimplifiedPosStr(name.substring(a, name.length()-b));
			if(prefixLen == -1){
				if(validPosStr){prefixLen = a; suffixLen = b;}
				continue;
			}
			final boolean oldContainsNew = prefixLen >= a && suffixLen >= b;
			if(oldContainsNew && validPosStr){
				Main.LOGGER.info("MapMoveClick: reducing prefix/suffix len for name: "+name);
				prefixLen = a; suffixLen = b;
			} // Reduce prefix/suffix len
			if(a+b > prefixLen+suffixLen && !isValidWhenSimplifiedPosStr(name.substring(Math.min(a, prefixLen), name.length()-Math.min(b, suffixLen)))){
				Main.LOGGER.info("MapMoveClick: expanding prefix/suffix len for name: "+name);
				prefixLen = a; suffixLen = b; // Expand prefix/suffix len
			}
			Main.LOGGER.info("a:"+a+", b:"+b+", name:"+name);
		}
//		if(destSlot == -1){Main.LOGGER.error("MapMoveClick: cannot find original moved map!");return;}
		if(prefixLen == -1 && suffixLen == -1 && slotsInvolved.isEmpty()){Main.LOGGER.info("MapMoveClick: no matching maps found");return;}

		for(int i=0; i<slots.size(); ++i){
			ItemStack item = slots.get(i).getStack();
			if(!isMapArt(item) || item.getCustomName() == null/* || item.getCount() != mapMoved.getCount()*/) continue;
			final String name = item.getCustomName().getLiteralString();
			if(name.length() < prefixLen+suffixLen+1 || name.equals(movedName)) continue;
			if(!movedName.regionMatches(0, name, 0, prefixLen) || !movedName.regionMatches(
					movedName.length()-suffixLen, name, name.length()-suffixLen, suffixLen)) continue;
			if(!isValidWhenSimplifiedPosStr(name.substring(prefixLen, name.length()-suffixLen))) continue;
			slotsInvolved.add(i);
		}
		int tl = slotsInvolved.stream().mapToInt(i->i.intValue()).min().getAsInt();
		if(tl%9 != 0 && slotsInvolved.contains(tl+8)) --tl;
		int br = slotsInvolved.stream().mapToInt(i->i.intValue()).max().getAsInt();
		if(br%9 != 8 && slotsInvolved.contains(br-8)) ++br;
		int h = (br/9)-(tl/9)+1;
		int w = (br%9)-(tl%9)+1;

		if(h == 1){if(w != slotsInvolved.size()+1){++w; --tl;}} // Assume missing map is leftmost (TODO: edge detect, it could be on the right)
		else if(w == 1){if(h != slotsInvolved.size()+1){++h; tl-=9;}} // Assume missing map is topmost (TODO: edge detect, it could be on the bottom)

		Main.LOGGER.info("MapMoveClick: tl="+tl+",br="+br+" : h="+h+",w="+w);

		if(h*w != slotsInvolved.size()+1){Main.LOGGER.info("MapMoveClick: H*W not found (expected:"+slotsInvolved.size()+")");return;}

		int fromSlot = -1;
		for(int i=0; i<h; ++i) for(int j=0; j<w; ++j){
			int s = tl + i*9 + j;
			if(!slotsInvolved.contains(s)){
				if(fromSlot != -1){Main.LOGGER.info("MapMoveClick: Maps not in a rectangle");return;}
				fromSlot = s;
			}
		}

		final int tlDest = destSlot-(fromSlot-tl);
		for(int i=0; i<h; ++i) for(int j=0; j<w; ++j){
			int d = tlDest + i*9 + j;
			if(d == destSlot) continue;
			if(!slots.get(d).getStack().isEmpty() && !slotsInvolved.contains(d)){
				Main.LOGGER.info("MapMoveClick: Destination is not empty (dTL="+tlDest+",cur="+d+")");
				return;
			}
		}
		final int brDest = destSlot+(br-fromSlot);

		final int lHotbar = tl%9, rHotbar = br%9, lHotbarDest = tlDest%9, rHotbarDest = brDest%9;
		//if(PREFER_HOTBAR_SWAPS){
		int hotbarButton = 40;
		for(int i=0; i<8; ++i){
			if(lHotbar <= i && i <= rHotbar) continue; 		   // Avoid hotbar slots the map might be moving from
			if(lHotbarDest <= i && i <= rHotbarDest) continue; // Avoid hotbar slots the map might be moving into
			if(player.getInventory().getStack(i).isEmpty()){hotbarButton = i; break;}
		}
		int tempSlot = -1;
		if(!player.getInventory().getStack(hotbarButton).isEmpty()){
			Main.LOGGER.warn("MapMoveClick: No available hotbar slot");
			if(tl > tlDest){
				for(int i=tlDest-1; i>=0; --i) if(slots.get(i).getStack().isEmpty()){tempSlot=i; break;}
				if(tempSlot==-1) for(int i=br; i<slots.size(); ++i) if(slots.get(i).getStack().isEmpty()){tempSlot=i; break;}
			}
			else{
				for(int i=tl; i>=0; --i) if(slots.get(i).getStack().isEmpty()){tempSlot=i; break;}
				if(tempSlot==-1) for(int i=brDest; i<slots.size(); ++i) if(slots.get(i).getStack().isEmpty()){tempSlot=i; break;}
			}
			if(tempSlot == -1) Main.LOGGER.warn("MapMoveClick: No available slot with which to free up offhand");
		}

		final MinecraftClient client = MinecraftClient.getInstance();
		final int syncId = player.currentScreenHandler.syncId;
		if(tempSlot != -1) client.interactionManager.clickSlot(syncId, tempSlot, hotbarButton, SlotActionType.SWAP, player);
		if(tl > tlDest){
			//Main.LOGGER.info("MapMoveClick: Moving all, starting from TL");
			for(int i=0; i<h; ++i) for(int j=0; j<w; ++j){
				int s = tl + i*9 + j, d = tlDest + i*9 + j;
				if(d == destSlot) continue;
				client.interactionManager.clickSlot(syncId, s, hotbarButton, SlotActionType.SWAP, player);
				client.interactionManager.clickSlot(syncId, d, hotbarButton, SlotActionType.SWAP, player);
			}
		}
		else{
			//Main.LOGGER.info("MapMoveClick: Moving all, starting from BR");
			for(int i=0; i<h; ++i) for(int j=0; j<w; ++j){
				int s = br - i*9 - j, d = brDest - i*9 - j;
				if(d == destSlot) continue;
				client.interactionManager.clickSlot(syncId, s, hotbarButton, SlotActionType.SWAP, player);
				client.interactionManager.clickSlot(syncId, d, hotbarButton, SlotActionType.SWAP, player);
			}
		}
		if(tempSlot != -1) client.interactionManager.clickSlot(syncId, tempSlot, hotbarButton, SlotActionType.SWAP, player);
	}
}