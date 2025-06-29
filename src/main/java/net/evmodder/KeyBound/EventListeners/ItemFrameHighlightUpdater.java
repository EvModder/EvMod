package net.evmodder.KeyBound.EventListeners;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.evmodder.KeyBound.Main;
import net.evmodder.KeyBound.MapGroupUtils;
import net.evmodder.KeyBound.MapRelationUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.map.MapState;

public class ItemFrameHighlightUpdater{
	private record XYZD(int x, int y, int z, int d){}
	private static final HashMap<XYZD, UUID> hangLocsReverse = new HashMap<>();
//	private static final HashMap<XYZD, Long> lastUpdated = new HashMap<>();
	private static final HashMap<UUID, HashSet<XYZD>> iFrameMapGroup = new HashMap<>();

	//TODO: Figure out something nicer than this shared global var
	public static final HashSet<Integer> skipIFrameHasLabel = new HashSet<>();

//	private static final boolean wasRecentlyUpdated(final XYZD xyzd, final long ts){return ts - lastUpdated.getOrDefault(xyzd, 0l) < 5000;}

	private static final void updateItemFrameEntity(final MinecraftClient client, final ItemFrameEntity ife/*, final long ts*/){
		//==================== Compute some stuff ====================//
		final ItemStack stack = ife.getHeldItemStack();
		final MapState state = stack == null || stack.isEmpty() ? null : FilledMapItem.getMapState(stack, ife.getWorld());
		final UUID colorsId = state == null ? null : MapGroupUtils.getIdForMapState(state);
		final XYZD xyzd = new XYZD(ife.getBlockX(), ife.getBlockY(), ife.getBlockZ(), ife.getFacing().ordinal());
		final UUID oldColorsIdForXYZ = colorsId != null ? hangLocsReverse.put(xyzd, colorsId) : hangLocsReverse.remove(xyzd);
		if(oldColorsIdForXYZ == null){
//			if(colorsId != null){InventoryHighlightUpdater.onUpdateTick(client);}
			//if(wasRecentlyUpdated(xyzd, ts)) Main.LOGGER.info("IFHU: Added map at xyzd");
		}
		else if(!oldColorsIdForXYZ.equals(colorsId)){
//			InventoryHighlightUpdater.onUpdateTick(client);
//			if(wasRecentlyUpdated(xyzd, ts)) Main.LOGGER.info("IFHU: "+(colorsId == null ? "Removed" : "Replaced")+" map xyzd");
			final HashSet<XYZD> oldLocs = iFrameMapGroup.get(oldColorsIdForXYZ);
			if(oldLocs != null && oldLocs.remove(xyzd) && oldLocs.isEmpty()) iFrameMapGroup.remove(oldColorsIdForXYZ);
		}
//		lastUpdated.put(xyzd, ts);
		if(colorsId == null) return; // Equivalent to if(state==null) return

		if(skipIFrameHasLabel.contains(ife.getId())) return; // Probably save to comment this out

		final HashSet<XYZD> locs = iFrameMapGroup.get(colorsId);
		final boolean isMultiHung;
		if(locs == null){iFrameMapGroup.put(colorsId, new HashSet<>(List.of(xyzd))); isMultiHung = false;}
		else{locs.add(xyzd); isMultiHung = locs.size() > 1 && !MapRelationUtils.isFillerMap(state);}

		//==================== Mark iFrame as skippable in renderer ====================//
		final boolean isInInv = InventoryHighlightUpdater.isInInventory(colorsId) || InventoryHighlightUpdater.isNestedInInventory(colorsId);
		final boolean isNotInCurrGroup = MapGroupUtils.shouldHighlightNotInCurrentGroup(state);
		if(!isMultiHung && !isInInv && !isNotInCurrGroup && state.locked && stack.getCustomName() != null){
			skipIFrameHasLabel.add(ife.getId());
			return;
		}
	}
//	private static long ts;
	public static final void onUpdateTick(MinecraftClient client){
		//ts = System.currentTimeMillis();
		if(client.world != null) client.world.getEntities().forEach(e -> {if(e instanceof ItemFrameEntity ife) updateItemFrameEntity(client, ife/*, ts*/);});
	}

	public static final boolean isHungMultiplePlaces(UUID colorsId){
		final var l = iFrameMapGroup.get(colorsId);
		if(l != null && Main.MAX_IFRAME_TRACKING_DIST_SQ > 0){
			l.removeIf(xyzd -> MinecraftClient.getInstance().player.squaredDistanceTo(xyzd.x, xyzd.y, xyzd.z) > Main.MAX_IFRAME_TRACKING_DIST_SQ);
			if(l.size() == 0) iFrameMapGroup.remove(colorsId);
		}
		return l != null && l.size() > 1;
	}

	public static final boolean isInItemFrame(final UUID colorsId){
		final var l = iFrameMapGroup.get(colorsId);
		if(l != null && Main.MAX_IFRAME_TRACKING_DIST_SQ > 0){
			l.removeIf(xyzd -> MinecraftClient.getInstance().player.squaredDistanceTo(xyzd.x, xyzd.y, xyzd.z) > Main.MAX_IFRAME_TRACKING_DIST_SQ);
			if(l.size() == 0) iFrameMapGroup.remove(colorsId);
		}
		return l != null && l.size() > 0;
	}
}
