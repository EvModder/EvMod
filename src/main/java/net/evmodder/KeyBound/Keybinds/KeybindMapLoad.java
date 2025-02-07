package net.evmodder.KeyBound.Keybinds;

import java.util.Timer;
import java.util.TimerTask;
import net.evmodder.KeyBound.Main;
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
	final int CLICKS_PER_GT;

	private boolean isUnloadedMapArt(World world, ItemStack stack){
		if(stack == null || stack.isEmpty()) return false;
		if(!Registries.ITEM.getId(stack.getItem()).getPath().equals("filled_map")) return false;
		return FilledMapItem.getMapState(stack, world) == null;
	}

	private boolean ongoingLoad;
	private long lastLoad;
	private final long loadCooldown = 500L;
	private final void loadMapArtFromShulker(int fromSlot, final int clicks_left){
		if(ongoingLoad){Main.LOGGER.warn("MapLoad cancelled: Already ongoing"); return;}
		//
		final long ts = System.currentTimeMillis();
		if(ts - lastLoad < loadCooldown){Main.LOGGER.warn("MapLoad cancelled: Cooldown"); return;}
		lastLoad = ts;
		//
//		if(slot >= 27){Main.LOGGER.warn("MapLoad cancelled: slot>27"); return;} // should be unreachable
		MinecraftClient client = MinecraftClient.getInstance();
		if(!(client.currentScreen instanceof ShulkerBoxScreen sbs)){Main.LOGGER.warn("MapLoad cancelled: not in ShulkerBoxScreen"); return;}
		//
		ShulkerBoxScreenHandler sh = sbs.getScreenHandler();
		int numMapArtsToLoad = 0;
		for(int i=fromSlot; i<27; ++i) if(isUnloadedMapArt(client.player.clientWorld, sh.getSlot(i).getStack())) ++numMapArtsToLoad;
		if(numMapArtsToLoad == 0){Main.LOGGER.warn("MapLoad cancelled: none to load"); return;}
		//
		ongoingLoad = true;
		int[] slots = new int[Math.min(9, clicks_left)];
		int nextHotbarSlot = 0;
		//Main.LOGGER.info("MapLoad starting slot: "+fromSlot);
		for(; fromSlot<27; ++fromSlot){
			ItemStack stack = sh.getSlot(fromSlot).getStack();
			if(!isUnloadedMapArt(client.player.clientWorld, stack)) continue;
			if(nextHotbarSlot == slots.length) break;
			slots[nextHotbarSlot] = fromSlot;
			client.interactionManager.clickSlot(sh.syncId, fromSlot, nextHotbarSlot, SlotActionType.SWAP, client.player);
			++nextHotbarSlot; // delay breaking till we encounter next map
		}
		//
		sh.updateToClient();
		sh.sendContentUpdates();
		sh.syncState();
		client.player.getInventory().markDirty();
		//
		final int lastHotbarSlot = nextHotbarSlot;
		final int fromSlotFinal = fromSlot;
		//Main.LOGGER.info("lastHotbarSlot: "+lastHotbarSlot+", fromSlotFinal: "+fromSlotFinal);
		new Timer().schedule(new TimerTask(){@Override public void run(){
			for(int i=0; i<lastHotbarSlot; ++i){
				client.interactionManager.clickSlot(sh.syncId, slots[i], i, SlotActionType.SWAP, client.player);
			}
//			sh.updateToClient();
//			sh.sendContentUpdates();
//			sh.syncState();
//			client.player.getInventory().markDirty();
			if(fromSlotFinal == 27){
				//Main.LOGGER.info("MapLoad: DONE!");
				ongoingLoad = false;
			}
			else{
				//Main.LOGGER.info("MapLoad: recursive call");
				if(clicks_left - 9 >= 9){
					ongoingLoad=false;lastLoad=0;
					loadMapArtFromShulker(fromSlotFinal, clicks_left-9);
				}
				else new Timer().schedule(new TimerTask(){@Override public void run(){
					ongoingLoad=false;lastLoad=0; loadMapArtFromShulker(fromSlotFinal, CLICKS_PER_GT);
				}}, 51L);
			}
		}}, 50L);
	}

//	private static long takeMapArtFromShulker(int fromSlot, final int clicks_left, final int clicks_per_gt){
//		
//	}

	public KeybindMapLoad(int clicks_per_gt){
		CLICKS_PER_GT = clicks_per_gt;
		if(CLICKS_PER_GT < 2){
			Main.LOGGER.error("clicks_per_gt value is set too low, disabling MapArtLoad keybind");
			return;
		}
		KeyBindingHelper.registerKeyBinding(new EvKeybind("mapart_load_container", ()->loadMapArtFromShulker(0, CLICKS_PER_GT), true));
	}
}