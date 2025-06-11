package net.evmodder.KeyBound.Keybinds;

import java.util.ArrayDeque;
import net.evmodder.KeyBound.Main;
import net.evmodder.KeyBound.Keybinds.ClickUtils.ClickEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.collection.DefaultedList;

//TODO: Maybe preserve relative position of maps (eg., in a 3x3, keep them in a 3x3 in result GUI)?

public final class KeybindMapArtBundleStow{

	private boolean ongoingBundleOp;
	private long lastBundleOp = 0;
	private final long bundleOpCooldown = 250l;
	private final void moveMapArtToFromBundle(){
		if(ongoingBundleOp){Main.LOGGER.warn("MapBundleOp cancelled: Already ongoing"); return;}
		//
		MinecraftClient client = MinecraftClient.getInstance();
		if(!(client.currentScreen instanceof InventoryScreen is)) return;
		//
		final long ts = System.currentTimeMillis();
		if(ts - lastBundleOp < bundleOpCooldown) return;
		lastBundleOp = ts;
		//
		final DefaultedList<Slot> slots = is.getScreenHandler().slots;
		ArrayDeque<ClickEvent> clicks = new ArrayDeque<>();

		//Main.LOGGER.info("MapMove: STARTED");
		ongoingBundleOp = true;
		Main.inventoryUtils.executeClicks(clicks, _0->true, ()->{Main.LOGGER.info("MapBundleOp: DONE!"); ongoingBundleOp = false;});
	}

	public KeybindMapArtBundleStow(){
		new Keybind("mapart_bundle", this::moveMapArtToFromBundle, InventoryScreen.class::isInstance);
	}
}