package net.evmodder.KeyBound.events;

import net.evmodder.KeyBound.keybinds.KeybindInventoryOrganize;
import net.evmodder.KeyBound.keybinds.KeybindInventoryRestock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.screen.ScreenHandler;

public final class ContainerOpenListener{
	KeybindInventoryRestock invRestock;
	KeybindInventoryOrganize[] mustMatchLayouts;

	private static int syncId;
	public final void onUpdateTick(MinecraftClient client){
		if(client.player == null) return;
		ScreenHandler sh = client.player.currentScreenHandler;
		final int newSyncId = sh == null ? 0 : sh.syncId;
		if(newSyncId != 0 && newSyncId != syncId) invRestock.doRestock(mustMatchLayouts);
		syncId = newSyncId;
	}

	public ContainerOpenListener(KeybindInventoryRestock invRestock, KeybindInventoryOrganize[] mustMatchLayouts){
		this.invRestock = invRestock;
		this.mustMatchLayouts = mustMatchLayouts;
	}
}