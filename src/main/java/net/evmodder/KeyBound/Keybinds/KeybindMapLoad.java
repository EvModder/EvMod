package net.evmodder.KeyBound.Keybinds;

import java.util.ArrayDeque;
import net.evmodder.KeyBound.Main;
import net.evmodder.KeyBound.Keybinds.InventoryUtils.ClickEvent;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.world.World;

public final class KeybindMapLoad{
	private boolean isUnloadedMapArt(World world, ItemStack stack){
		if(stack == null || stack.isEmpty()) return false;
		if(!Registries.ITEM.getId(stack.getItem()).getPath().equals("filled_map")) return false;
		return FilledMapItem.getMapState(stack, world) == null;
	}

	private boolean isShulkerBox(ItemStack stack){
		return !stack.isEmpty() && Registries.ITEM.getId(stack.getItem()).getPath().endsWith("shulker_box");
	}

	private int getNextUsableHotbarButton(MinecraftClient client, int hb){
		while(++hb < 9 && client.currentScreen instanceof ShulkerBoxScreen && isShulkerBox(client.player.getInventory().getStack(hb)));
		return hb;
	}

	//TODO: Consider shift-clicks instead of hotbar swaps (basically, MapMove but only for unloaded maps, and keep track of which)
	private boolean ongoingLoad;
	private long lastLoad;
	private final long loadCooldown = 500L;
	private final void loadMapArtFromContainer(){
		if(ongoingLoad){Main.LOGGER.warn("MapLoad cancelled: Already ongoing"); return;}
		//
		MinecraftClient client = MinecraftClient.getInstance();
		if(!(client.currentScreen instanceof HandledScreen hs)){Main.LOGGER.warn("MapLoad cancelled: not in HandledScreen"); return;}
		//
		final long ts = System.currentTimeMillis();
		if(ts - lastLoad < loadCooldown){Main.LOGGER.warn("MapLoad cancelled: Cooldown"); return;}
		lastLoad = ts;
		//
		ScreenHandler sh = hs.getScreenHandler();
		int numToLoad = 0;
		for(int i=0; i<sh.slots.size(); ++i) if(isUnloadedMapArt(client.player.clientWorld, sh.getSlot(i).getStack())) ++numToLoad;
		if(numToLoad == 0){Main.LOGGER.warn("MapLoad cancelled: none to load"); return;}
		//
		int hotbarButton = getNextUsableHotbarButton(client, -1);
		if(hotbarButton == 9){Main.LOGGER.warn("MapLoad cancelled: in shulker, and hotbar is full of shulkers"); return;}
		//
		int[] putBackSlots = new int[9];
		for(int i=0; i<putBackSlots.length; ++i) putBackSlots[i] = -1;

		ArrayDeque<ClickEvent> clicks = new ArrayDeque<>();
		for(int i=0; i<sh.slots.size() && numToLoad > 0; ++i){
			if(!isUnloadedMapArt(client.player.clientWorld, sh.getSlot(i).getStack())) continue;
			clicks.add(new ClickEvent(sh.syncId, i, hotbarButton, SlotActionType.SWAP));
			putBackSlots[hotbarButton] = i;
			--numToLoad;

			hotbarButton = getNextUsableHotbarButton(client, hotbarButton);
			if(hotbarButton == 9 || numToLoad == 0){
				for(int j=0; j<hotbarButton; ++j) if(putBackSlots[j] != -1) clicks.add(new ClickEvent(sh.syncId, putBackSlots[j], j, SlotActionType.SWAP));
				hotbarButton = getNextUsableHotbarButton(client, -1);
			}
		}
		ongoingLoad = true;
		Main.inventoryUtils.executeClicks(client, clicks,
				c->!isUnloadedMapArt(client.player.clientWorld, client.player.getInventory().getStack(c.button())),
				()->{
					Main.LOGGER.info("MapLoad: DONE!");
					ongoingLoad = false;
				});
	}

	public KeybindMapLoad(){
		KeyBindingHelper.registerKeyBinding(new EvKeybind("mapart_load_data", ()->loadMapArtFromContainer(),
				s->s instanceof InventoryScreen == false));
	}
}