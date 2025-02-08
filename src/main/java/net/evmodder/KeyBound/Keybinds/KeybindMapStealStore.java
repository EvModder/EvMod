package net.evmodder.KeyBound.Keybinds;

import java.util.ArrayDeque;
import net.evmodder.KeyBound.Main;
import net.evmodder.KeyBound.Keybinds.InventoryUtils.ClickEvent;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

public final class KeybindMapStealStore{
	private final boolean isMapArt(ItemStack stack){
		if(stack == null || stack.isEmpty()) return false;
		return Registries.ITEM.getId(stack.getItem()).getPath().equals("filled_map");
	}

	private boolean ongoingStealStore;
	private long lastStealStore;
	private final long stealStoreCooldown = 500L;
	private final void loadMapArtFromShulker(final int MILLIS_BETWEEN_CLICKS){
		if(ongoingStealStore){Main.LOGGER.warn("Map SS cancelled: Already ongoing"); return;}
		//
		final long ts = System.currentTimeMillis();
		if(ts - lastStealStore < stealStoreCooldown){Main.LOGGER.warn("Map SS cancelled: Cooldown"); return;}
		lastStealStore = ts;
		//
		MinecraftClient client = MinecraftClient.getInstance();
		if(!(client.currentScreen instanceof ShulkerBoxScreen sbs)){Main.LOGGER.warn("Map SS cancelled: Not in ShulkerBoxScreen"); return;}
		//
		ShulkerBoxScreenHandler sh = sbs.getScreenHandler();
		int numInShulk = 0, emptySlotsShulk = 0;
		for(int i=0; i<27; ++i){
			ItemStack stack = sh.getSlot(i).getStack();
			if(stack == null || stack.isEmpty()) ++emptySlotsShulk;
			else if(isMapArt(stack)) ++numInShulk;
		}
		int numInInv = 0, emptySlotsInv = 0;
		for(int i=0; i<36; ++i){
			ItemStack stack = client.player.getInventory().getStack(i);
			if(stack == null || stack.isEmpty()) ++emptySlotsInv;
			else if(isMapArt(stack)) ++numInInv;
		}
		if(numInShulk == 0 && numInInv == 0){Main.LOGGER.warn("Map SS cancelled: No mapart found"); return;}
		if(numInShulk != 0 && numInInv != 0){Main.LOGGER.warn("Map SS cancelled: Mapart found in both inventory AND shulker"); return;}
		if(numInShulk != 0 && numInShulk > emptySlotsInv){Main.LOGGER.warn("Map SS cancelled: Not enough empty slots in inventory"); return;}
		if(numInInv != 0 && numInInv > emptySlotsShulk){Main.LOGGER.warn("Map SS cancelled: Not enough empty slots in shulker"); return;}
		//
		ongoingStealStore = true;
		Main.LOGGER.info("Map SS: length of sb: "+sh.slots.size());
		ArrayDeque<ClickEvent> clicks = new ArrayDeque<>();
		if(numInShulk > 0) for(int i=27; i>=0; --i){
			if(isMapArt(sh.getSlot(i).getStack())) clicks.add(new ClickEvent(sh.syncId, i, 0, SlotActionType.QUICK_MOVE));
		}
		else for(int i=27; i<63; ++i){
			if(isMapArt(sh.getSlot(i).getStack())) clicks.add(new ClickEvent(sh.syncId, i, 0, SlotActionType.QUICK_MOVE));
		}

		InventoryUtils.executeClicks(client, clicks, MILLIS_BETWEEN_CLICKS, /*MAX_CLICKS_PER_SECOND=*/27,
				_->true,
				()->{
					Main.LOGGER.info("Map SS: DONE!");
					ongoingStealStore = false;
				});
	}

	public KeybindMapStealStore(int MILLIS_BETWEEN_CLICKS){
		KeyBindingHelper.registerKeyBinding(new EvKeybind("mapart_take_place", ()->loadMapArtFromShulker(MILLIS_BETWEEN_CLICKS), s->s instanceof ShulkerBoxScreen));
	}
}