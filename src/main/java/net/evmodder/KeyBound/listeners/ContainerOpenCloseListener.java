package net.evmodder.KeyBound.listeners;

import java.util.List;
import net.evmodder.KeyBound.Configs;
import net.evmodder.KeyBound.KeyCallbacks;
import net.evmodder.KeyBound.apis.MapStateCacher;
import net.evmodder.KeyBound.config.OptionMapStateCache;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;

public final class ContainerOpenCloseListener{

	private int syncId;
	private boolean currentlyViewingEchest;
	private List<ItemStack> echestContents; // null until echest is opened
	public static boolean echestCacheLoaded; // TODO: remove horrible public static var

	public final void onUpdateTick(MinecraftClient client){
		if(client.player == null) return;
		ScreenHandler sh = client.player.currentScreenHandler;
		final int newSyncId = sh == null ? 0 : sh.syncId;
		if(newSyncId == syncId){
			if(Configs.Generic.MAP_STATE_CACHE.getOptionListValue() != OptionMapStateCache.OFF && currentlyViewingEchest) echestContents = sh.getStacks();
			return;
		}
		syncId = newSyncId;
		if(newSyncId != 0){
//			Main.LOGGER.info("ContainerOpenCloseListener: container opened, syncId="+newSyncId+", name="+client.currentScreen.getTitle().toString());
			if(Configs.Generic.INV_RESTOCK_AUTO.getBooleanValue()) KeyCallbacks.kbInvRestock.organizeThenRestock();

			if(Configs.Generic.MAP_STATE_CACHE.getOptionListValue() != OptionMapStateCache.OFF
					&& (currentlyViewingEchest=client.currentScreen.getTitle().contains(Text.translatable("container.enderchest")))
					&& !echestCacheLoaded // Don't reload from cache unless player leaves and rejoins server
			){
				MapStateCacher.loadMapStates(echestContents=sh.getStacks(), MapStateCacher.HolderType.ENDER_CHEST);
				echestCacheLoaded = true;
			}
		}
		else{
//			Main.LOGGER.info("ContainerOpenCloseListener: container closed, wasViewingEchest="+currentlyViewingEchest);
			if(Configs.Generic.MAP_STATE_CACHE.getOptionListValue() != OptionMapStateCache.OFF && currentlyViewingEchest){
				currentlyViewingEchest = false;
				MapStateCacher.saveMapStates(echestContents, MapStateCacher.HolderType.ENDER_CHEST);
			}
		}
	}
}