package net.evmodder.KeyBound.EventListeners;

import net.evmodder.KeyBound.Keybinds.KeybindInventoryRestock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.screen.ScreenHandler;

public final class ContainerOpenListener{
	KeybindInventoryRestock inventoryRestock;
	
	private static int syncId;
	public final void onUpdateTick(MinecraftClient client){
		if(client.player == null) return;
		ScreenHandler sh = client.player.currentScreenHandler;
		final int newSyncId = sh == null ? 0 : sh.syncId;
		if(newSyncId != 0 && newSyncId != syncId) inventoryRestock.doRestock();
		syncId = newSyncId;
	}

	public ContainerOpenListener(KeybindInventoryRestock inventoryRestock){
		this.inventoryRestock = inventoryRestock;
	}
}