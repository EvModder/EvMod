package net.evmodder.KeyBound.listeners;

import net.evmodder.KeyBound.keybinds.KeybindInventoryOrganize;
import net.evmodder.KeyBound.keybinds.KeybindInventoryRestock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.screen.ScreenHandler;

public final class ContainerOpenListener{
	KeybindInventoryRestock invRestock;
	KeybindInventoryOrganize[] mustMatchLayouts;

	private final void organizeInvThenRestock(int i){
		if(mustMatchLayouts == null || i == mustMatchLayouts.length) invRestock.doRestock(mustMatchLayouts);
		else mustMatchLayouts[i].organizeInventory(/*RESTOCK_ONLY=*/true, ()->organizeInvThenRestock(i+1));
	}

	private static int syncId;
	public final void onUpdateTick(MinecraftClient client){
		if(client.player == null) return;
		ScreenHandler sh = client.player.currentScreenHandler;
		final int newSyncId = sh == null ? 0 : sh.syncId;
		if(newSyncId == 0 || newSyncId == syncId) return;
		syncId = newSyncId;
		organizeInvThenRestock(0);
	}

	public ContainerOpenListener(KeybindInventoryRestock invRestock, KeybindInventoryOrganize[] mustMatchLayouts){
		this.invRestock = invRestock;
		this.mustMatchLayouts = mustMatchLayouts;
	}
}