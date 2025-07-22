package net.evmodder.KeyBound.EventListeners;

import net.evmodder.KeyBound.Keybinds.KeybindInventoryRestock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.screen.ScreenHandler;

public final class ContainerOpenListener{
	private static int syncId;
	public static final void onUpdateTick(MinecraftClient client){
		if(client.player == null) return;
		ScreenHandler sh = client.player.currentScreenHandler;
		final int newSyncId = sh == null ? 0 : sh.syncId;
		if(newSyncId != 0 && newSyncId != syncId) KeybindInventoryRestock.doRestock();
		syncId = newSyncId;
	}
}