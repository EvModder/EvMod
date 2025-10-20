package net.evmodder.KeyBound.listeners;

import java.util.ArrayList;
import java.util.List;
import net.evmodder.KeyBound.Configs;
import net.evmodder.KeyBound.KeyCallbacks;
import net.evmodder.KeyBound.Main;
import net.evmodder.KeyBound.keybinds.KeybindInventoryOrganize;
import net.minecraft.client.MinecraftClient;
import net.minecraft.screen.ScreenHandler;

public final class ContainerOpenListener{
	List<KeybindInventoryOrganize> mustMatchLayouts;

	private final void organizeInvThenRestock(int i){
		if(mustMatchLayouts == null || i >= mustMatchLayouts.size()) Main.kbInvRestock.doRestock(mustMatchLayouts);
		else mustMatchLayouts.get(i).organizeInventory(/*RESTOCK_ONLY=*/true, ()->organizeInvThenRestock(i+1));
	}

	private static int syncId;
	public final void onUpdateTick(MinecraftClient client){
		if(client.player == null || !Configs.Generic.INV_RESTOCK_AUTO.getBooleanValue()) return;
		ScreenHandler sh = client.player.currentScreenHandler;
		final int newSyncId = sh == null ? 0 : sh.syncId;
		if(newSyncId == 0 || newSyncId == syncId) return;
		syncId = newSyncId;
		organizeInvThenRestock(0);
	}

	public final void refreshLayouts(List<String> layouts){
		mustMatchLayouts = new ArrayList<>();
//		List<String> organizationLayouts = Configs.Generic.INV_RESTOCK_AUTO_FOR_INV_ORGS.getStrings();
		if(layouts.contains("1")) mustMatchLayouts.add(KeyCallbacks.kbInvOrg1);
		if(layouts.contains("2")) mustMatchLayouts.add(KeyCallbacks.kbInvOrg2);
		if(layouts.contains("3")) mustMatchLayouts.add(KeyCallbacks.kbInvOrg3);
	}

	public ContainerOpenListener(){
		refreshLayouts(Configs.Generic.INV_RESTOCK_AUTO_FOR_INV_ORGS.getStrings());
	}
}