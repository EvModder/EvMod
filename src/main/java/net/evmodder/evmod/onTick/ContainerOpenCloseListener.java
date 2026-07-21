package net.evmodder.evmod.onTick;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.apis.MapStateCacher;
import net.evmodder.evmod.apis.TickListener;
import net.evmodder.evmod.config.OptionMapStateCache;
import net.evmodder.evmod.keybinds.KeybindInventoryRestock;
import net.evmodder.evmod.listeners.BlockClickListener;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public final class ContainerOpenCloseListener implements TickListener{
	private final KeybindInventoryRestock kbInvRestock;
	public ContainerOpenCloseListener(final KeybindInventoryRestock kbInvRestock){
		this.kbInvRestock = kbInvRestock;
		new BlockClickListener();
	}

	private int syncId;
	private boolean waitingForEcToLoad, currentlyViewingEchest, currentlyViewingContainer;
	private List<Slot> slots;

	public static boolean echestCacheLoaded; // TODO: remove horrible public static vars
	public static HashSet<UUID> containerCachesLoaded = new HashSet<>();

	@Override public final void onTickEnd(final Minecraft client){
		if(client.player == null) return;
		final AbstractContainerMenu sh = client.player.containerMenu;
		final int newSyncId = sh == null ? 0 : sh.containerId;
		if(newSyncId == syncId){
			if(Configs.Generic.MAP_CACHE.getOptionListValue() != OptionMapStateCache.OFF){
				if(currentlyViewingContainer){
					slots = sh.slots;
					if(waitingForEcToLoad && IntStream.range(0, 27).anyMatch(i -> !slots.get(i).getItem().isEmpty())){
						waitingForEcToLoad = false;
						if(!echestCacheLoaded) MapStateCacher.loadMapStatesByPos(sh.getItems(), MapStateCacher.BY_PLAYER_EC);
						echestCacheLoaded = true;
					}
				}
			}
			return;
		}
		syncId = newSyncId;
		if(newSyncId != 0){
//			Main.LOGGER.info("ContainerOpenCloseListener: container opened, syncId="+newSyncId+", name="+client.currentScreen.getTitle().toString());
			if(Configs.Generic.INV_RESTOCK_AUTO.getBooleanValue()) kbInvRestock.organizeThenRestock();

			if(Configs.Generic.MAP_CACHE.getOptionListValue() != OptionMapStateCache.OFF){
				waitingForEcToLoad = false;
				currentlyViewingContainer = true;
				// Don't reload from echest-cache unless player leaves and rejoins server
				if(Configs.Generic.MAP_CACHE_BY_EC_POS.getBooleanValue() && (
					currentlyViewingEchest=client.gui.screen().getTitle().contains(Component.translatable("container.enderchest")))
				){
					waitingForEcToLoad = true;
				}
				else{
					if(Configs.Generic.MAP_CACHE_BY_CONTAINER_POS.getBooleanValue() && containerCachesLoaded.add(BlockClickListener.lastClickedBlockHash)){
						MapStateCacher.loadMapStatesByPos(sh.getItems(), MapStateCacher.BY_CONTAINER);
					}
					if(Configs.Generic.MAP_CACHE_BY_NAME.getBooleanValue()) sh.getItems().stream()
						.filter(s -> s.getItem() == Items.FILLED_MAP && s.getCustomName() != null)
						.forEach(s -> {
							MapItemSavedData state = MapItem.getSavedData(s, client.level);
							if(state == null) MapStateCacher.loadMapStateByName(s, client.level);
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
						if(!waitingForEcToLoad) MapStateCacher.saveMapStatesByPos(slots.stream().map(Slot::getItem), MapStateCacher.BY_PLAYER_EC);
					}
					else{
						if(Configs.Generic.MAP_CACHE_BY_CONTAINER_POS.getBooleanValue()){
							MapStateCacher.saveMapStatesByPos(slots.stream().map(Slot::getItem), MapStateCacher.BY_CONTAINER);
						}
						if(Configs.Generic.MAP_CACHE_BY_NAME.getBooleanValue()) slots.stream().map(Slot::getItem)
							.filter(s -> s.getItem() == Items.FILLED_MAP && s.getCustomName() != null)
							.forEach(s -> {
								MapItemSavedData state = MapItem.getSavedData(s, client.level);
								if(state != null) MapStateCacher.addMapStateByName(s, state);
//								else MapStateCacher.loadMapStateByName(s, client.world);
							});
					}
				}
			}
		}
	}
}