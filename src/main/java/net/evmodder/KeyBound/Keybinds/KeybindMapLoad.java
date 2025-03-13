package net.evmodder.KeyBound.Keybinds;

import java.util.ArrayDeque;
import net.evmodder.KeyBound.Main;
import net.evmodder.KeyBound.Keybinds.InventoryUtils.ClickEvent;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.world.World;

public final class KeybindMapLoad{

	private boolean isUnloadedMapArt(World world, ItemStack stack){
		if(stack == null || stack.isEmpty()) return false;
		if(!Registries.ITEM.getId(stack.getItem()).getPath().equals("filled_map")) return false;
		return FilledMapItem.getMapState(stack, world) == null;
	}
//	private boolean isLoadedMapArt(World world, ItemStack stack){
//		if(stack == null || stack.isEmpty()) return false;
//		if(!Registries.ITEM.getId(stack.getItem()).getPath().equals("filled_map")) return false;
//		return FilledMapItem.getMapState(stack, world) != null;
//	}

	//TODO: use shift-clicks instead of hotbar swaps
	private boolean ongoingLoad;
	private long lastLoad;
	private final long loadCooldown = 500L;
	private final void loadMapArtFromShulker(final int MAX_CLICKS_PER_SECOND){
		if(ongoingLoad){Main.LOGGER.warn("MapLoad cancelled: Already ongoing"); return;}
		//
		final long ts = System.currentTimeMillis();
		if(ts - lastLoad < loadCooldown){Main.LOGGER.warn("MapLoad cancelled: Cooldown"); return;}
		lastLoad = ts;
		//
		MinecraftClient client = MinecraftClient.getInstance();
		if(!(client.currentScreen instanceof ShulkerBoxScreen sbs)){Main.LOGGER.warn("MapLoad cancelled: not in ShulkerBoxScreen"); return;}
		//
		ShulkerBoxScreenHandler sh = sbs.getScreenHandler();
		int numToLoad = 0;
		for(int i=0; i<27; ++i) if(isUnloadedMapArt(client.player.clientWorld, sh.getSlot(i).getStack())) ++numToLoad;
		if(numToLoad == 0){Main.LOGGER.warn("MapLoad cancelled: none to load"); return;}
		//
		ongoingLoad = true;
		int hotbarButton = 0;
		int[] putBackSlots = new int[Math.min(9, numToLoad)];
		ArrayDeque<ClickEvent> clicks = new ArrayDeque<>();
		for(int i=0; i<27 && numToLoad > 0; ++i){
			ItemStack stack = sh.getSlot(i).getStack();
			if(!isUnloadedMapArt(client.player.clientWorld, stack)) continue;
			putBackSlots[hotbarButton] = i;
			clicks.add(new ClickEvent(sh.syncId, i, hotbarButton, SlotActionType.SWAP));
			++hotbarButton;
			if(hotbarButton == 9 || hotbarButton == numToLoad){
				for(int j=0; j<hotbarButton; ++j) clicks.add(new ClickEvent(sh.syncId, putBackSlots[j], j, SlotActionType.SWAP));
				numToLoad -= hotbarButton;
				hotbarButton = 0;
			}
		}
		InventoryUtils.executeClicks(client, clicks, /*MILLIS_BETWEEN_CLICKS=*/0, MAX_CLICKS_PER_SECOND,
				c->!isUnloadedMapArt(client.player.clientWorld, client.player.getInventory().getStack(c.button())),
				()->{
					Main.LOGGER.info("MapLoad: DONE!");
					ongoingLoad = false;
				});
	}

	public KeybindMapLoad(int MAX_CLICKS_PER_SECOND){
		if(MAX_CLICKS_PER_SECOND < 9){
			Main.LOGGER.error("max_clicks_per_second value is set too low, disabling MapArtLoad keybind");
			return;
		}
		KeyBindingHelper.registerKeyBinding(new EvKeybind("mapart_load_data", ()->loadMapArtFromShulker(MAX_CLICKS_PER_SECOND), s->s instanceof ShulkerBoxScreen));
	}
}