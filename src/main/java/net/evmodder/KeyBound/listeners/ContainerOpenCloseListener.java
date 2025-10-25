package net.evmodder.KeyBound.listeners;

import java.util.ArrayList;
import java.util.List;
import net.evmodder.KeyBound.Main;
import net.evmodder.KeyBound.apis.MapStateCacher;
import net.evmodder.KeyBound.config.Configs;
import net.evmodder.KeyBound.config.KeyCallbacks;
import net.evmodder.KeyBound.config.MapStateCacheOption;
import net.evmodder.KeyBound.keybinds.KeybindInventoryOrganize;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;

public final class ContainerOpenCloseListener{
	private List<KeybindInventoryOrganize> mustMatchLayouts;

	public final void refreshLayouts(List<String> layouts){
		mustMatchLayouts = new ArrayList<>();
//		List<String> organizationLayouts = Configs.Generic.INV_RESTOCK_AUTO_FOR_INV_ORGS.getStrings();
		if(layouts.contains("1")) mustMatchLayouts.add(KeyCallbacks.kbInvOrg1);
		if(layouts.contains("2")) mustMatchLayouts.add(KeyCallbacks.kbInvOrg2);
		if(layouts.contains("3")) mustMatchLayouts.add(KeyCallbacks.kbInvOrg3);
	}

	public ContainerOpenCloseListener(){
		refreshLayouts(Configs.Generic.INV_RESTOCK_AUTO_FOR_INV_ORGS.getStrings());
	}

	private final void organizeInvThenRestock(int i){
		if(mustMatchLayouts == null || i >= mustMatchLayouts.size()) Main.kbInvRestock.doRestock(mustMatchLayouts);
		else mustMatchLayouts.get(i).organizeInventory(/*RESTOCK_ONLY=*/true, ()->organizeInvThenRestock(i+1));
	}

	private int syncId;
	private boolean currentlyViewingEchest;
	private List<ItemStack> echestContents; // null until echest is opened
	public static boolean echestCacheLoaded; // TODO: remove horrible public static var

	public final void onUpdateTick(MinecraftClient client){
		if(client.player == null) return;
		ScreenHandler sh = client.player.currentScreenHandler;
		final int newSyncId = sh == null ? 0 : sh.syncId;
		if(newSyncId == syncId){
			if(Configs.Generic.MAP_STATE_CACHE.getOptionListValue() != MapStateCacheOption.OFF && currentlyViewingEchest) echestContents = sh.getStacks();
			return;
		}
		syncId = newSyncId;
		if(newSyncId != 0){
//			Main.LOGGER.info("ContainerOpenCloseListener: container opened, syncId="+newSyncId+", name="+client.currentScreen.getTitle().toString());
			if(Configs.Generic.INV_RESTOCK_AUTO.getBooleanValue()) organizeInvThenRestock(0);

			if(Configs.Generic.MAP_STATE_CACHE.getOptionListValue() != MapStateCacheOption.OFF
					&& (currentlyViewingEchest=client.currentScreen.getTitle().contains(Text.translatable("container.enderchest")))
					&& !echestCacheLoaded // Don't reload from cache unless player leaves and rejoins server
			){
				MapStateCacher.loadMapStates(echestContents=sh.getStacks(), MapStateCacher.HolderType.ENDER_CHEST);
				echestCacheLoaded = true;
			}
		}
		else{
//			Main.LOGGER.info("ContainerOpenCloseListener: container closed, wasViewingEchest="+currentlyViewingEchest);
			if(Configs.Generic.MAP_STATE_CACHE.getOptionListValue() != MapStateCacheOption.OFF && currentlyViewingEchest){
				currentlyViewingEchest = false;
				MapStateCacher.saveMapStates(echestContents, MapStateCacher.HolderType.ENDER_CHEST);
			}
		}
	}
}