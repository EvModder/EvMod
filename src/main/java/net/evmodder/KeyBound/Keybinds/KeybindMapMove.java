package net.evmodder.KeyBound.Keybinds;

import java.util.ArrayDeque;
import java.util.HashSet;
import net.evmodder.KeyBound.Main;
import net.evmodder.KeyBound.Keybinds.InventoryUtils.ClickEvent;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

//TODO: Maybe preserve relative position of maps (eg., in a 3x3, keep them in a 3x3 in result GUI)?

public final class KeybindMapMove{
	private final boolean isMapArt(ItemStack stack){
		if(stack == null || stack.isEmpty()) return false;
		return Registries.ITEM.getId(stack.getItem()).getPath().equals("filled_map");
	}

//	private MapState getMapState(World world, ItemStack stack){
//		if(stack == null || stack.isEmpty()) return null;
//		if(!Registries.ITEM.getId(stack.getItem()).getPath().equals("filled_map")) return null;
//		return FilledMapItem.getMapState(stack, world);
//	}

	private boolean ongoingStealStore;
	private long lastStealStore;
	private final long stealStoreCooldown = 500L;
	private final void loadMapArtFromShulker(final int MILLIS_BETWEEN_CLICKS){
		if(ongoingStealStore){Main.LOGGER.warn("MapMove cancelled: Already ongoing"); return;}
		//
		final long ts = System.currentTimeMillis();
		if(ts - lastStealStore < stealStoreCooldown){Main.LOGGER.warn("MapMove cancelled: Cooldown"); return;}
		lastStealStore = ts;
		//
		MinecraftClient client = MinecraftClient.getInstance();
		if(!(client.currentScreen instanceof ShulkerBoxScreen sbs)){Main.LOGGER.warn("MapMove cancelled: Not in ShulkerBoxScreen"); return;}
		//
		ShulkerBoxScreenHandler sh = sbs.getScreenHandler();
		int numInInv = 0, emptySlotsInv = 0, sameCountInv = 0;
		HashSet<Integer> mapsInPlayerInv = new HashSet<>();
		for(int i=0; i<36; ++i){
			ItemStack stack = client.player.getInventory().getStack(i);
			if(stack == null || stack.isEmpty()) ++emptySlotsInv;
			else if(isMapArt(stack)){
				MapIdComponent id = stack.get(DataComponentTypes.MAP_ID);
				if(id == null){Main.LOGGER.error("MapMove cancelled: Unloaded map in player inventory (!!!)"); return;}
				mapsInPlayerInv.add(id.id());
				++numInInv;
				if(sameCountInv == 0) sameCountInv = stack.getCount();
				else if(sameCountInv != stack.getCount()) sameCountInv = -1; // Different map slots with different counts => -1
			}
		}
		boolean shulkerHasExtraMaps = false;
		int numInShulk = 0, emptySlotsShulk = 0, sameCountShulk = 0;
		for(int i=0; i<27; ++i){
			ItemStack stack = sh.getSlot(i).getStack();
			if(stack == null || stack.isEmpty()) ++emptySlotsShulk;
			else if(isMapArt(stack)){
				MapIdComponent id = stack.get(DataComponentTypes.MAP_ID);
				if(id == null || !mapsInPlayerInv.contains(id.id())) shulkerHasExtraMaps = true;
				++numInShulk;
				if(sameCountShulk == 0) sameCountShulk = stack.getCount();
				else if(sameCountShulk != stack.getCount()) sameCountShulk = -1; // Different map slots with different counts => -1
			}
		}
		boolean moveToShulk = true;
		if(numInShulk == 0 && numInInv == 0){Main.LOGGER.warn("MapMove cancelled: No mapart found"); return;}
		if(numInShulk != 0 && numInInv != 0 && (numInInv != numInShulk || shulkerHasExtraMaps
				|| sameCountShulk == -1 || sameCountInv == -1 || sameCountShulk+sameCountInv > 64)){
			Main.LOGGER.warn("MapMove cancelled: Mapart found in both inventory AND shulker");
			return;
		}
		if(numInInv == 0){
			moveToShulk = false;
			if(numInShulk > emptySlotsInv) Main.LOGGER.warn("MapMove cancelled: Not enough empty slots in inventory"); return;
		}
		else if(numInInv > emptySlotsShulk){Main.LOGGER.warn("MapMove cancelled: Not enough empty slots in shulker"); return;}
		//
		ongoingStealStore = true;
		ArrayDeque<ClickEvent> clicks = new ArrayDeque<>();
		if(moveToShulk) for(int i=27; i<63; ++i){
			if(!isMapArt(sh.getSlot(i).getStack())) continue;
			if(sameCountInv > 1){
				clicks.add(new ClickEvent(sh.syncId, 0, SlotActionType.PICKUP)); // left-click: pickup all
				clicks.add(new ClickEvent(sh.syncId, 1, SlotActionType.PICKUP)); // right-click: put back one
			}
			clicks.add(new ClickEvent(sh.syncId, i, 0, SlotActionType.QUICK_MOVE)); // shift-click: all in slot to shulker
			if(sameCountInv > 1){
				clicks.add(new ClickEvent(sh.syncId, 0, SlotActionType.PICKUP)); // left-click: put back one on cursor
			}
		}
		else for(int i=27; i>=0; --i){
			if(isMapArt(sh.getSlot(i).getStack())) clicks.add(new ClickEvent(sh.syncId, i, 0, SlotActionType.QUICK_MOVE));
		}

		InventoryUtils.executeClicks(client, clicks, MILLIS_BETWEEN_CLICKS, /*MAX_CLICKS_PER_SECOND=*/20,
				_->true,
				()->{
					Main.LOGGER.info("MapMove: DONE!");
					ongoingStealStore = false;
				});
	}

	public KeybindMapMove(int MILLIS_BETWEEN_CLICKS){
		KeyBindingHelper.registerKeyBinding(new EvKeybind("mapart_take_place", ()->loadMapArtFromShulker(MILLIS_BETWEEN_CLICKS), s->s instanceof ShulkerBoxScreen));
	}
}