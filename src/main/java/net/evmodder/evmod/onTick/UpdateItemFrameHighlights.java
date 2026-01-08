package net.evmodder.evmod.onTick;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.apis.MapGroupUtils;
import net.evmodder.evmod.apis.MiscUtils;
import net.evmodder.evmod.apis.NewMapNotifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.map.MapState;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

public class UpdateItemFrameHighlights{
	private record XYZD(int x, int y, int z, int d){}
	private static final HashMap<XYZD, UUID> hangLocsReverse = new HashMap<>(); // XYZD -> colorsId
	private static final HashMap<UUID, HashSet<XYZD>> iFrameMapGroup = new HashMap<>(); // colorsId -> {HangLocs}
	private static int numLoadedIfes;

	public static final HashMap<ItemFrameEntity, Boolean> hasLabelCache = new HashMap<>(); // TODO: remove? private?
	public static final HashMap<ItemFrameEntity, Text> displayNameCache = new HashMap<>(); // TODO: remove? private?
	public static Vec3d clientRotationNormalized; // TODO: remove? private?
	public static long lastIFrameMapGroupUpdateTs; // Only accessor: CommandExportMapImg::getNearbyMapNames

	public enum Highlight{INV_OR_NESTED_INV, NOT_IN_CURR_GROUP, MULTI_HUNG, UNLOCKED_OR_UNNAMED};
	private static final HashMap<Integer, Highlight> highlightedIFrames = new HashMap<>();

	public static final boolean isHungMultiplePlaces(UUID colorsId){
		final var l = iFrameMapGroup.get(colorsId);
//		if(l != null && Configs.Generic.MAX_IFRAME_TRACKING_DIST_SQ > 0){
//			l.removeIf(xyzd -> MinecraftClient.getInstance().player.squaredDistanceTo(xyzd.x, xyzd.y, xyzd.z) > Configs.Generic.MAX_IFRAME_TRACKING_DIST_SQ);
//			if(l.size() == 0) iFrameMapGroup.remove(colorsId);
//		}
		return l != null && l.size() > 1;
	}

	public static final boolean isInItemFrame(final UUID colorsId){
		final var l = iFrameMapGroup.get(colorsId);
//		if(l != null && Configs.Generic.MAX_IFRAME_TRACKING_DIST_SQ > 0){
//			l.removeIf(xyzd -> MinecraftClient.getInstance().player.squaredDistanceTo(xyzd.x, xyzd.y, xyzd.z) > Configs.Generic.MAX_IFRAME_TRACKING_DIST_SQ);
//			if(l.size() == 0) iFrameMapGroup.remove(colorsId);
//		}
		return l != null && l.size() > 0;
	}

	public static final Highlight iFrameGetHighlight(int entityId){
		return highlightedIFrames.get(entityId);
	}

	private static final boolean scanIFrameContents(final List<ItemFrameEntity> ifes, final double trackingDistSq, final Vec3d centerPos){
		boolean anyMapGroupUpdate = false;
		for(ItemFrameEntity ife : ifes){
			final ItemStack stack = ife.getHeldItemStack();
			final MapState state = stack == null || stack.isEmpty() ? null : FilledMapItem.getMapState(stack, ife.getWorld());
			final UUID colorsId = state == null ? null : MapGroupUtils.getIdForMapState(state);
			final XYZD xyzd = new XYZD(ife.getBlockX(), ife.getBlockY(), ife.getBlockZ(), ife.getFacing().ordinal());
			final UUID oldColorsIdForXYZD = colorsId != null ? hangLocsReverse.put(xyzd, colorsId) : hangLocsReverse.remove(xyzd);
			if(colorsId != null){
				if(trackingDistSq == 0 || centerPos.squaredDistanceTo(xyzd.x, xyzd.y, xyzd.z) <= trackingDistSq){
					anyMapGroupUpdate |= iFrameMapGroup.computeIfAbsent(colorsId, _0 -> new HashSet<XYZD>()).add(xyzd);
				}
//				if(oldColorsIdForXYZ == null) Main.LOGGER.info("IFHU: Added map at xyzd");
			}
			if(oldColorsIdForXYZD != null && !oldColorsIdForXYZD.equals(colorsId)){
//				Main.LOGGER.info("IFHU: "+(colorsId == null ? "Removed" : "Replaced")+" map at xyzd");
				if(colorsId == null) highlightedIFrames.remove(ife.getId()); // IFHU: Removed map at xyzd
				anyMapGroupUpdate = true;
				final HashSet<XYZD> oldLocs = iFrameMapGroup.get(oldColorsIdForXYZD);
				if(oldLocs != null && oldLocs.remove(xyzd) && oldLocs.isEmpty()) iFrameMapGroup.remove(oldColorsIdForXYZD);
			}
		}
		return anyMapGroupUpdate;
	}
	private static final boolean updateIframeHighlights(final List<ItemFrameEntity> ifes){
		boolean anyHighlightUpdate = false;
		for(ItemFrameEntity ife : ifes){
			final XYZD xyzd = new XYZD(ife.getBlockX(), ife.getBlockY(), ife.getBlockZ(), ife.getFacing().ordinal());
			UUID colorsId = hangLocsReverse.get(xyzd);
			if(colorsId == null) continue;

			final ItemStack stack = ife.getHeldItemStack();
			final MapState state = FilledMapItem.getMapState(ife.getHeldItemStack(), ife.getWorld());
			if(state == null) continue; // Can happen in creative worlds!

			final Highlight highlight;
			if(UpdateInventoryHighlights.isInInventory(colorsId) || UpdateInventoryHighlights.isNestedInInventory(colorsId)) highlight = Highlight.INV_OR_NESTED_INV;
			else if(MapGroupUtils.shouldHighlightNotInCurrentGroup(state)){
				highlight = Highlight.NOT_IN_CURR_GROUP;
				if(Configs.Generic.NEW_MAP_NOTIFIER_IFRAME.getBooleanValue()) NewMapNotifier.call(ife, colorsId);
			}
			else if(isHungMultiplePlaces(colorsId)) highlight = Highlight.MULTI_HUNG;
			else if(!state.locked || stack.getCustomName() == null) highlight = Highlight.UNLOCKED_OR_UNNAMED;
			else{
				anyHighlightUpdate |= (highlightedIFrames.remove(ife.getId()) != null);
				continue;
			}
			anyHighlightUpdate |= (highlightedIFrames.put(ife.getId(), highlight) != highlight);
		}
		return anyHighlightUpdate;
	}
	private static int lastGroupInvHash;
	public static final void onTickStart(MinecraftClient client){
		if(client.world == null) return;
		Vec3d newClientRot = client.player.getRotationVec(1.0F).normalize();
		if(!newClientRot.equals(clientRotationNormalized) || MiscUtils.hasMoved(client.player)){
			clientRotationNormalized = newClientRot;
			hasLabelCache.clear(); // Depends on client looking direction
		}

//		final boolean anyHangLocUpdate = client.world.getEntitiesByClass(ItemFrameEntity.class,
//			client.player.getBoundingBox().expand(200, 200, 200), _0->true).stream().anyMatch(ife -> updateItemFrameEntity(client, ife));

//		client.world.getEntities().forEach(e -> {if(e instanceof ItemFrameEntity ife) updateItemFrameEntity(client, ife);});
//		client.world.getEntitiesByClass(ItemFrameEntity.class, client.player.getBoundingBox().expand(200, 200, 200), _0->true)
//					.forEach(ife -> updateItemFrameEntity(client, ife));

		List<ItemFrameEntity> ifes = client.world.getEntitiesByClass(ItemFrameEntity.class, client.player.getBoundingBox().expand(200, 200, 200), _0->true);

		final double TRACKING_DIST_SQ = Configs.Generic.MAX_IFRAME_TRACKING_DIST_SQ;
		final Vec3d playerPos = /*TRACKING_DIST_SQ == 0 ? null : */client.player.getPos();
		boolean anyMapGroupUpdate = false;
		if(TRACKING_DIST_SQ > 0 && (ifes.size() != numLoadedIfes || TRACKING_DIST_SQ < 32)){
			// Untrack maps which have gone out of range for iFrameMapGroup (for isInIFrame, isMultiHung)
//			iFrameMapGroup.entrySet().removeIf(e -> {
			for(var it = iFrameMapGroup.entrySet().iterator(); it.hasNext();) {
				var e = it.next();
				anyMapGroupUpdate |= e.getValue().removeIf(xyzd -> client.player.squaredDistanceTo(xyzd.x, xyzd.y, xyzd.z) > TRACKING_DIST_SQ);
				if(e.getValue().isEmpty()) it.remove();
			}
//			});
		}
		// Updates iFrameMapGroup, highlightedIFrames, hangLocsReverse, & anyTrackedIFrameUpdate
		anyMapGroupUpdate |= scanIFrameContents(ifes,TRACKING_DIST_SQ, playerPos);

		final int currGroupInvHash = MapGroupUtils.mapsInGroupHash + UpdateInventoryHighlights.mapsInInvHash;
		try{
			if(anyMapGroupUpdate) lastIFrameMapGroupUpdateTs = System.currentTimeMillis();
			else if(currGroupInvHash == lastGroupInvHash) return;
		}
		finally{
			lastGroupInvHash = currGroupInvHash;
			numLoadedIfes = ifes.size();
		}

//		Main.LOGGER.info("IframeHighlighter: Recomputing highlight cache, due to "+(anyHangLocUpdate?"hung iframe update":"inv update"));
		final boolean anyHighlightUpdate = updateIframeHighlights(ifes);
		if(!anyHighlightUpdate) return;

//		Main.LOGGER.info("IframeHighlighter: Recomputing hasLabel/getDisplayName cache");
		hasLabelCache.clear();
		displayNameCache.clear();
	}
}