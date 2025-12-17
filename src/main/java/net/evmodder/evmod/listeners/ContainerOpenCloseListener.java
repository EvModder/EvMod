package net.evmodder.evmod.listeners;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
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
	private boolean waitingForEcToLoad, currentlyViewingEchest, currentlyViewingContainer;
//	private List<ItemStack> contents; // null until echest is opened
	List<Slot> slots;

	public static boolean echestCacheLoaded; // TODO: remove horrible public static vars
	public static HashSet<UUID> containerCachesLoaded = new HashSet<>();

	@Override public final void onTickEnd(MinecraftClient client){
		if(client.player == null) return;
		ScreenHandler sh = client.player.currentScreenHandler;
		final int newSyncId = sh == null ? 0 : sh.syncId;
		if(newSyncId == syncId){
			if(Configs.Generic.MAP_CACHE.getOptionListValue() != OptionMapStateCache.OFF){
				if(currentlyViewingContainer){
					slots = sh.slots;
					if(waitingForEcToLoad && IntStream.range(0, 27).anyMatch(i -> !slots.get(i).getStack().isEmpty())){
						waitingForEcToLoad = false;
						if(!echestCacheLoaded) MapStateCacher.loadMapStatesByPos(sh.getStacks(), MapStateCacher.BY_PLAYER_EC);
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
					currentlyViewingEchest=client.currentScreen.getTitle().contains(Text.translatable("container.enderchest")))
				){
					waitingForEcToLoad = true;
				}
				else{
					if(Configs.Generic.MAP_CACHE_BY_CONTAINER_POS.getBooleanValue() && containerCachesLoaded.add(ContainerClickListener.lastClickedBlockHash)){
						MapStateCacher.loadMapStatesByPos(sh.getStacks(), MapStateCacher.BY_CONTAINER);
					}
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
						if(!waitingForEcToLoad) MapStateCacher.saveMapStatesByPos(slots.stream().map(Slot::getStack).toList(), MapStateCacher.BY_PLAYER_EC);
					}
					else{
						if(Configs.Generic.MAP_CACHE_BY_CONTAINER_POS.getBooleanValue()){
							MapStateCacher.saveMapStatesByPos(slots.stream().map(Slot::getStack).toList(), MapStateCacher.BY_CONTAINER);
						}
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