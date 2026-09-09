package net.evmodder.evmod.apis;

import static net.evmodder.evmod.compat.MinecraftCompat.sendOverlay;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import net.evmodder.evmod.Main;
import net.evmodder.evmod.apis.ClickUtils.ActionType;
import net.evmodder.evmod.apis.ClickUtils.InvAction;
import net.evmodder.evmod.apis.MapRelationUtils.RelatedMapsData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public abstract class MapClickMoveNeighbors{
	private static boolean ongoingClickMove;

	// Only called by MixinScreenHandler
	public static final void moveNeighbors(final Player player, final int destSlot, final ItemStack mapMoved){
		if(mapMoved.getItem() != Items.FILLED_MAP) return;

		if(ongoingClickMove){Main.LOGGER.warn("MapMoveClick: Already ongoing"); return;}
		Main.LOGGER.info("MapMoveClick: moveNeighbors() called");

		final ItemStack[] slots = player.containerMenu.slots.stream().map(Slot::getItem).toArray(ItemStack[]::new);
		final MapItemSavedData state = MapItem.getSavedData(mapMoved, player.level());
		RelatedMapsData data;
		final boolean moveHalf;
		{
			final List<ItemStack> slotList = Arrays.asList(slots);
			final String name = mapMoved.getHoverName().getString();
			final int count = mapMoved.getCount();
			final Boolean locked = state == null ? null : state.locked;
			data = MapRelationUtils.getRelatedMapsByName(slotList, name, count, locked, player.level());
			if(data.prefixLen() == -1){
				data = MapRelationUtils.getRelatedMapsByName(slotList, name, count*2, locked, player.level());
				if(data.prefixLen() == -1) data = MapRelationUtils.getRelatedMapsByName(slotList, name, count*2-1, locked, player.level());
				if(data.prefixLen() == -1){
					Main.LOGGER.info("MapMoveClick: related-name maps not found");
					return;
				}
				else moveHalf = true;
			}
			else moveHalf = false;
		}
		data.slots().removeIf(i -> {
			if(i == destSlot) return true;
			if(ItemStack.isSameItemSameComponents(slots[i], mapMoved)){
				Main.LOGGER.warn("MapMoveClick: multiple copies of same map not yet supported (i:"+i+",dest"+destSlot);
				return true;
			}
			return false;
		});
		if(data.slots().isEmpty()){
			Main.LOGGER.info("MapMoveClick: no related moveable maps found");
			return;
		}

		HashSet<Integer> slotsInvolved = new HashSet<>();
		slotsInvolved.addAll(data.slots());

		int tl = slotsInvolved.stream().mapToInt(i->i.intValue()).min().getAsInt();
		if(tl%9 != 0 && slotsInvolved.contains(tl+8)) --tl;
		int br = slotsInvolved.stream().mapToInt(i->i.intValue()).max().getAsInt();
		if(br%9 != 8 && slotsInvolved.contains(br-8)) ++br;
		int h = (br/9)-(tl/9)+1;
		int w = (br%9)-(tl%9)+1;

		if((h == 1 || w == 1) && w*h == data.slots().size()){
			if(state == null){++w; br += 1;}
			else{
				final byte[] colors = state.colors;
				final byte[] tlColors = MapItem.getSavedData(slots[tl], player.level()).colors;
				final byte[] brColors = MapItem.getSavedData(slots[br], player.level()).colors;
				int scoreLeft = -2, scoreRight = -2, scoreTop = -2, scoreBottom = -2;
				if(h == 1){
					scoreLeft = (tl%9==0||destSlot%9+w>8) ? -2 : MapRelationUtils.adjacentEdgeScore(colors, tlColors, true);
					scoreRight = (br%9==8||destSlot%9-w<0) ? -2 : MapRelationUtils.adjacentEdgeScore(brColors, colors, true);
				}
				if(w == 1){
					scoreTop = (tl-9<0||destSlot+9*h>=slots.length) ? -2 : MapRelationUtils.adjacentEdgeScore(colors, tlColors, false);
					scoreBottom = (br+9>=slots.length||destSlot-9*h<0) ? -2 : MapRelationUtils.adjacentEdgeScore(brColors, colors, false);
				}
				if(Math.max(scoreLeft, scoreRight) >= Math.max(scoreTop, scoreBottom)){
					++w;
					if(scoreLeft > scoreRight) tl -= 1; else br += 1;
					Main.LOGGER.info("MapMoveClick: extending width of 1-tall map, scoreLeft:"+scoreLeft+", scoreRight:"+scoreRight);
				}
				else{
					++h;
					if(scoreTop > scoreBottom) tl -= 9; else br += 9;
					Main.LOGGER.info("MapMoveClick: extending height of 1-wide map, scoreTop:"+scoreTop+", scoreBottom:"+scoreBottom);
				}
			}//state != null
		}

		//Main.LOGGER.info("MapMoveClick: tl="+tl+",br="+br+" | h="+h+",w="+w+" | x>"+destSlot);
		if(h*w != data.slots().size()+1){Main.LOGGER.info("MapMoveClick: H*W not found (#maps:"+(data.slots().size()+1)+")");return;}

//		HashSet<Integer> unaccounted = new HashSet<>(); unaccounted.addAll(slotsInvolved);
		int fromSlot = -1;
		for(int i=0; i<h; ++i) for(int j=0; j<w; ++j){
			int s = tl + i*9 + j;
//			unaccounted.remove(s);
			if(slotsInvolved.contains(s)) continue;
			if(fromSlot != -1){Main.LOGGER.info("MapMoveClick: Maps not in a rectangle"); return;}
			ItemStack stack = slots[i];
			if(!stack.isEmpty() && stack.getItem() != Items.FILLED_MAP) Main.LOGGER.warn("MapMoveClick: moveFrom slot:"+s+" contains junk item: "+stack.getItem());
			fromSlot = s;
		}
		assert fromSlot != -1;
		if(fromSlot == destSlot) return;
//		if(!unaccounted.isEmpty()){Main.LOGGER.info("MapMoveClick: Maps not in a rectangle (B)"); return;}

		final int tlDest = tl + destSlot - fromSlot;
		final int brDest = br + destSlot - fromSlot;
//		final int brDest = tlDest + (br-tl);//equivalent: destSlot+(br-fromSlot);//destSlot-(fromSlot-br);
		if(tlDest < 0 || brDest >= slots.length){Main.LOGGER.info("MapMoveClick: Destination is outside inv window"); return;}

		Main.LOGGER.info("MapMoveClick: "+w+"x"+h+" [tl="+tl+",br="+br+"] -> [tl="+tlDest+",br="+brDest+"], "+fromSlot+"->"+destSlot);
		//player.sendMessage(Text.literal("MapMoveClick: tl="+tl+",br="+br+" | h="+h+",w="+w+" | "+fromSlot+"->"+destSlot), false);////

		for(int i=0; i<h; ++i) for(int j=0; j<w; ++j){
			int d = tlDest + i*9 + j;
			if(d == destSlot) continue;
			if(!slots[d].isEmpty() && !slotsInvolved.contains(d)){
				Main.LOGGER.info("MapMoveClick: Destination is not empty (dTL="+tlDest+",cur="+d+")");
				return;
			}
			slotsInvolved.add(d);
		}

//		final boolean moveHalf = mapMoved.getCount() > 1 && (tl > brDest || br < tlDest) && !Screen.hasShiftDown();;

		//if(PREFER_HOTBAR_SWAPS){
		int hotbarButton = 40;
		if(!moveHalf){
			final boolean isPlayerInv = player.containerMenu instanceof InventoryMenu;
			final int hbStart = slots.length-(isPlayerInv ? 10 : 9); // extra slot at end to account for offhand
			final boolean fromHotbar = br >= hbStart, toHotbar = brDest >= hbStart;
			//Main.LOGGER.warn("MapMoveClick: fromHotbar:"+fromHotbar+", toHotbar:"+toHotbar+", brDest:"+brDest+", last  hotbar if to: "+(brDest-hbStart));
			for(int i=0; i<9; ++i){
				if((fromHotbar || toHotbar) && slotsInvolved.contains(hbStart+i)) continue; // Avoid slots the map is moving from or into
				if(player.getInventory().getItem(i).isEmpty()){hotbarButton = i; break;}
			}
			if(hotbarButton == 40) Main.LOGGER.warn("MapMoveClick: Using offhand for swaps");
		}
		int tempSlot = -1;

//		final MinecraftClient client = MinecraftClient.getInstance();
		final ArrayDeque<InvAction> clicks = new ArrayDeque<>();
		boolean temporaryEmpty = player.getInventory().getItem(hotbarButton).isEmpty();
		boolean usesTemporary = false;

		final int mult, sStart, dStart;
		if(tl > tlDest){mult = 1; sStart = tl; dStart = tlDest;}
		else{mult = -1; sStart = br; dStart = brDest;}
		Main.LOGGER.info("MapMoveClick: Moving all, starting from "+(tl > tlDest ? "TL" : "BR"));
		for(int i=0; i<h; ++i) for(int j=0; j<w; ++j){
			int s = sStart + (i*9 + j)*mult, d = dStart + (i*9 + j)*mult;
//			int s = tl + i*9 + j, d = tlDest + i*9 + j;
//			int s = br - i*9 - j, d = brDest - i*9 - j;
			if(d == destSlot) continue;
			//Main.LOGGER.warn("MapMoveClick: adding 2 clicks: "+s+"->"+d+", hb:"+hotbarButton);
			if(moveHalf){
				clicks.add(new InvAction(s, 1, ActionType.CLICK));
				clicks.add(new InvAction(d, 0, ActionType.CLICK));
			}
			else{
				final Slot source = player.containerMenu.getSlot(s), destination = player.containerMenu.getSlot(d);
				final int sourceButton = source.container == player.getInventory()
						&& (Inventory.isHotbarSlot(source.getContainerSlot()) || source.getContainerSlot() == 40) ? source.getContainerSlot() : -1;
				final int destinationButton = destination.container == player.getInventory()
						&& (Inventory.isHotbarSlot(destination.getContainerSlot()) || destination.getContainerSlot() == 40) ? destination.getContainerSlot() : -1;
				if(sourceButton < 0 && destinationButton < 0 && !usesTemporary){
					if(hotbarButton == 40 && slotsInvolved.stream().map(player.containerMenu::getSlot)
							.anyMatch(slot -> slot.container == player.getInventory() && slot.getContainerSlot() == 40)){
						Main.LOGGER.warn("MapMoveClick: Offhand is part of the move and cannot serve as temporary storage");
						return;
					}
					if(!temporaryEmpty){
						final ItemStack temporaryStack = player.getInventory().getItem(hotbarButton);
						for(int k=0; k<slots.length; ++k) if(slots[k].isEmpty() && !slotsInvolved.contains(k)
								&& player.containerMenu.getSlot(k).mayPlace(temporaryStack)
								&& player.containerMenu.getSlot(k).getMaxStackSize(temporaryStack) >= temporaryStack.getCount()){
							tempSlot = k; break;
						}
						temporaryEmpty = tempSlot != -1;
						if(tempSlot == -1) Main.LOGGER.warn("MapMoveClick: No available slot with which to free up offhand");
					}
					usesTemporary = true;
				}
				InventoryTransferPlanner.swapStacks(clicks, s, d, sourceButton, destinationButton,
						hotbarButton, !temporaryEmpty || !slots[d].isEmpty());
				final ItemStack displaced = slots[d];
				slots[d] = slots[s];
				slots[s] = displaced;
			}
//			client.interactionManager.clickSlot(syncId, s, hotbarButton, ClickAction.HOTBAR_SWAP, player);
//			client.interactionManager.clickSlot(syncId, d, hotbarButton, ClickAction.HOTBAR_SWAP, player);
		}
		if(tempSlot != -1){
			clicks.addFirst(new InvAction(tempSlot, hotbarButton, ActionType.HOTBAR_SWAP));
			clicks.add(new InvAction(tempSlot, hotbarButton, ActionType.HOTBAR_SWAP));
		}

		final int numClicks = clicks.size();
		ongoingClickMove = true;
		ClickUtils.executeClicks(/*canProceed=*/_->true, ()->{
			ongoingClickMove = false;
			Main.LOGGER.info("MapMoveClick: DONE (clicks:"+numClicks+")");
			sendOverlay(player, Component.literal("MapMoveClick: DONE (clicks:"+numClicks+")"));
		}, clicks);
//		if(Main.inventoryUtils.addClick(null) >= Main.inventoryUtils.MAX_CLICKS){
//			Main.LOGGER.warn("Not enough clicks available to execute MapMoveNeighbors :(");
//			return;
//		}
//		final MinecraftClient client = MinecraftClient.getInstance();
//		for(ClickEvent c : clicks) client.interactionManager.clickSlot(syncId, c.slotId(), c.button(), c.actionType(), player);
	}
}