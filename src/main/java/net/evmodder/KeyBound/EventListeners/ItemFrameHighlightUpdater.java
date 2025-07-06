package net.evmodder.KeyBound.EventListeners;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.evmodder.KeyBound.Main;
import net.evmodder.KeyBound.MapGroupUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.map.MapState;

public class ItemFrameHighlightUpdater{
	private record XYZD(int x, int y, int z, int d){}
	private static final HashMap<XYZD, UUID> hangLocsReverse = new HashMap<>();
	private static final HashMap<UUID, HashSet<XYZD>> iFrameMapGroup = new HashMap<>();

	public enum Highlight{INV_OR_NESTED_INV, NOT_IN_CURR_GROUP, MULTI_HUNG, UNLOCKED_OR_UNNAMED};
	private static final HashMap<Integer, Highlight> highlightedIFrames = new HashMap<>();

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

	public static final Highlight iFrameGetHighlight(int entityId){
		return highlightedIFrames.get(entityId);
	}

	private static final void updateItemFrameEntity(final MinecraftClient client, final ItemFrameEntity ife){
		//==================== Compute some stuff ====================//
		final ItemStack stack = ife.getHeldItemStack();
		final MapState state = stack == null || stack.isEmpty() ? null : FilledMapItem.getMapState(stack, ife.getWorld());
		final UUID colorsId = state == null ? null : MapGroupUtils.getIdForMapState(state);
		final XYZD xyzd = new XYZD(ife.getBlockX(), ife.getBlockY(), ife.getBlockZ(), ife.getFacing().ordinal());
		final UUID oldColorsIdForXYZ = colorsId != null ? hangLocsReverse.put(xyzd, colorsId) : hangLocsReverse.remove(xyzd);
		if(oldColorsIdForXYZ == null){
//			Main.LOGGER.info("IFHU: Added map at xyzd");
		}
		else if(oldColorsIdForXYZ != null && !oldColorsIdForXYZ.equals(colorsId)){
//			Main.LOGGER.info("IFHU: "+(colorsId == null ? "Removed" : "Replaced")+" map xyzd");
			final HashSet<XYZD> oldLocs = iFrameMapGroup.get(oldColorsIdForXYZ);
			if(oldLocs != null && oldLocs.remove(xyzd) && oldLocs.isEmpty()) iFrameMapGroup.remove(oldColorsIdForXYZ);
		}
		if(colorsId == null) return; // Equivalent to state==null

//		if(!highlightedIFrames.contains(ife.getId())) return; // Short-circuit that relied on InvHL and GroupHL clearing highlightedIFrames for us

		final HashSet<XYZD> locs = iFrameMapGroup.get(colorsId);
		final boolean isMultiHung;
		if(locs == null){iFrameMapGroup.put(colorsId, new HashSet<>(List.of(xyzd))); isMultiHung = false;}
		else{locs.add(xyzd); isMultiHung = locs.size() > 1;}

		if(InventoryHighlightUpdater.isInInventory(colorsId) || InventoryHighlightUpdater.isNestedInInventory(colorsId))
			highlightedIFrames.put(ife.getId(), Highlight.INV_OR_NESTED_INV);
		else if(MapGroupUtils.shouldHighlightNotInCurrentGroup(state))
			highlightedIFrames.put(ife.getId(), Highlight.NOT_IN_CURR_GROUP);
		else if(isMultiHung)
			highlightedIFrames.put(ife.getId(), Highlight.MULTI_HUNG);
		else if(!state.locked || stack.getCustomName() == null)
			highlightedIFrames.put(ife.getId(), Highlight.UNLOCKED_OR_UNNAMED);
		else
			highlightedIFrames.remove(ife.getId());
	}
	public static final void onUpdateTick(MinecraftClient client){
		if(client.world != null) client.world.getEntities().forEach(e -> {if(e instanceof ItemFrameEntity ife) updateItemFrameEntity(client, ife);});
	}
}
