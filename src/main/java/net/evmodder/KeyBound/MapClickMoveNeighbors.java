package net.evmodder.KeyBound;

import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

public abstract class MapClickMoveNeighbors{
	public static void moveNeighbors(PlayerEntity player, int destSlot, ItemStack mapMoved){
		Main.LOGGER.info("MapMoveClick: moveNeighbors() called");
		final List<Slot> slots = player.currentScreenHandler.slots;
		final String movedName = mapMoved.getCustomName().getLiteralString();
		final RelatedMapsData data =  AdjacentMapUtils.getRelatedMapsByName(slots, movedName, mapMoved.getCount());
		data.slots().removeIf(i -> {
			if(i == destSlot) return true;
			if(ItemStack.areItemsAndComponentsEqual(slots.get(i).getStack(), mapMoved)){
				Main.LOGGER.info("MapMoveClick: TODO:support multiple copies (i:"+i+",dest"+destSlot);
				return true;
			}
			return false;
		});
		if(data.slots().isEmpty()){
			Main.LOGGER.info("MapMoveClick: no connected moveable maps found");
			return;
		}

		int tl = data.slots().stream().mapToInt(i->i.intValue()).min().getAsInt();
		if(tl%9 != 0 && data.slots().contains(tl+8)) --tl;
		int br = data.slots().stream().mapToInt(i->i.intValue()).max().getAsInt();
		if(br%9 != 8 && data.slots().contains(br-8)) ++br;
		int h = (br/9)-(tl/9)+1;
		int w = (br%9)-(tl%9)+1;

		if(h == 1){if(w != data.slots().size()+1){++w; --tl;}} // Assume missing map is leftmost (TODO: edge detect, it could be on the right)
		else if(w == 1){if(h != data.slots().size()+1){++h; tl-=9;}} // Assume missing map is topmost (TODO: edge detect, it could be on the bottom)

		Main.LOGGER.info("MapMoveClick: tl="+tl+",br="+br+" : h="+h+",w="+w);

		if(h*w != data.slots().size()+1){Main.LOGGER.info("MapMoveClick: H*W not found (expected:"+data.slots().size()+")");return;}

		int fromSlot = -1;
		for(int i=0; i<h; ++i) for(int j=0; j<w; ++j){
			int s = tl + i*9 + j;
			if(!data.slots().contains(s)){
				if(fromSlot != -1){Main.LOGGER.info("MapMoveClick: Maps not in a rectangle");return;}
				fromSlot = s;
			}
		}

		final int tlDest = destSlot-(fromSlot-tl);
		for(int i=0; i<h; ++i) for(int j=0; j<w; ++j){
			int d = tlDest + i*9 + j;
			if(d == destSlot) continue;
			if(!slots.get(d).getStack().isEmpty() && !data.slots().contains(d)){
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