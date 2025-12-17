package net.evmodder.evmod.listeners;

import java.util.List;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.apis.MapStateCacher;
import net.evmodder.evmod.apis.TickListener;
import net.evmodder.evmod.config.OptionMapStateCache;
import net.evmodder.evmod.keybinds.KeybindInventoryRestock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapState;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

public final class ContainerOpenCloseListener implements TickListener{
	private final KeybindInventoryRestock kbInvRestock;
	public ContainerOpenCloseListener(KeybindInventoryRestock kbInvRestock){this.kbInvRestock = kbInvRestock;}

	private int syncId;
	private boolean currentlyViewingEchest, currentlyViewingContainer;
//	private List<ItemStack> contents; // null until echest is opened
	List<Slot> slots;

	public static boolean echestCacheLoaded; // TODO: remove horrible public static var

	@Override public final void onTickEnd(MinecraftClient client){
		if(client.player == null) return;
		ScreenHandler sh = client.player.currentScreenHandler;
		final int newSyncId = sh == null ? 0 : sh.syncId;
		if(newSyncId == syncId){
			if(Configs.Generic.MAP_CACHE.getOptionListValue() != OptionMapStateCache.OFF){
				if(currentlyViewingContainer) slots = sh.slots;
			}
			return;
		}
		syncId = newSyncId;
		if(newSyncId != 0){
//			Main.LOGGER.info("ContainerOpenCloseListener: container opened, syncId="+newSyncId+", name="+client.currentScreen.getTitle().toString());
			if(Configs.Generic.INV_RESTOCK_AUTO.getBooleanValue()) kbInvRestock.organizeThenRestock();

			if(Configs.Generic.MAP_CACHE.getOptionListValue() != OptionMapStateCache.OFF){
				currentlyViewingContainer = true;
				// Don't reload from echest-cache unless player leaves and rejoins server
				if(Configs.Generic.MAP_CACHE_BY_EC_POS.getBooleanValue() && !echestCacheLoaded && (
					currentlyViewingEchest=client.currentScreen.getTitle().contains(Text.translatable("container.enderchest")))
				){
					MapStateCacher.loadMapStatesByPos(sh.getStacks(), MapStateCacher.Cache.BY_PLAYER_EC);
					echestCacheLoaded = true;
				}
				else{
//					if(Configs.Generic.MAP_CACHE_BY_CONTAINER_POS.getBooleanValue()){
//						MapStateCacher.loadMapStatesByPos(contents=sh.getStacks(), MapStateCacher.HolderType.CONTAINER);//TODO: holder-identifying data? xyz?
//						holderXYZCacheLoaded = true;
//					}
					if(Configs.Generic.MAP_CACHE_BY_NAME.getBooleanValue()) sh.getStacks().stream()
						.filter(s -> s.getItem() == Items.FILLED_MAP && s.getCustomName() != null && s.getCustomName().getLiteralString() != null)
						.forEach(s -> {
							MapState state = FilledMapItem.getMapState(s, client.world);
							if(state == null) MapStateCacher.loadMapStateByName(s, client.world);
//							else MapStateCacher.addMapStateByName(s, state);
						});
				}
			}
		}
		else{
//			Main.LOGGER.info("ContainerOpenCloseListener: container closed, wasViewingEchest="+currentlyViewingEchest);
			if(Configs.Generic.MAP_CACHE.getOptionListValue() != OptionMapStateCache.OFF){
				if(currentlyViewingContainer){
					currentlyViewingContainer = false;
					if(currentlyViewingEchest){
						currentlyViewingEchest = false;
						MapStateCacher.saveMapStatesByPos(slots.stream().map(Slot::getStack).toList(), MapStateCacher.Cache.BY_PLAYER_EC);
					}
					else{
//						if(Configs.Generic.MAP_CACHE_BY_CONTAINER_POS.getBooleanValue()){
//							MapStateCacher.saveMapStatesByPos(contents, MapStateCacher.HolderType.CONTAINER);//TODO: holder-identifying data? xyz?
//						}
						if(Configs.Generic.MAP_CACHE_BY_NAME.getBooleanValue()) slots.stream().map(Slot::getStack)
							.filter(s -> s.getItem() == Items.FILLED_MAP && s.getCustomName() != null && s.getCustomName().getLiteralString() != null)
							.forEach(s -> {
								MapState state = FilledMapItem.getMapState(s, client.world);
								if(state != null) MapStateCacher.addMapStateByName(s, state);
//								else MapStateCacher.loadMapStateByName(s, client.world);
							});
					}
				}
			}
		}
	}
}