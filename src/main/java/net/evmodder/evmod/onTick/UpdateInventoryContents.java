package net.evmodder.evmod.onTick;

import static net.evmodder.evmod.compat.MinecraftCompat.bundleItems;

import java.util.HashSet;
import java.util.Objects;
import java.util.UUID;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.Main;
import net.evmodder.evmod.apis.InvUtils;
import net.evmodder.evmod.apis.MapGroupUtils;
import net.evmodder.evmod.apis.TickListener;
import net.evmodder.evmod.commands.CommandExportMapNames;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
//? >=26.1 {
import net.minecraft.world.item.ItemInstance;
//?}
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public final class UpdateInventoryContents implements TickListener{
	private static HashSet<UUID> inventoryMapGroup = new HashSet<>(), nestedInventoryMapGroup = new HashSet<>();
	private static volatile ItemStack currentlyBeingPlacedIntoItemFrame;
	private static int slotUsedForCurrentlyBeingPlacedItem, placementWaitTicks;
	private static int mapsInInvHash;
	private static boolean warnedNullMapId, sawNullMapIdThisTick;
//	private static int itemsInInvHash;

	public static final int getMapsInInvHash(){return mapsInInvHash;}
	public static final boolean isInInventory(final UUID colorsUUID){return inventoryMapGroup.contains(colorsUUID);}
	public static final boolean isNestedInInventory(final UUID colorsUUID){return nestedInventoryMapGroup.contains(colorsUUID);}

	public static final boolean hasCurrentlyBeingPlacedMapArt(){return currentlyBeingPlacedIntoItemFrame != null;}
	public static final void setCurrentlyBeingPlacedMapArt(final ItemStack stack, final int slot){ // Accessor: MapHangListener
		if(!ItemStack.matches(Minecraft.getInstance().player.getInventory().getItem(slot), stack)){
			assert false;
			return;
		}
		currentlyBeingPlacedIntoItemFrame = stack.copy();
		slotUsedForCurrentlyBeingPlacedItem = slot;
		placementWaitTicks = 0;
	}

	//? >=26.1 {
	private static final void addMapName(final ItemInstance stack){
	//?} else {
	/*private static final void addMapName(final ItemStack stack){*/
	//?}
		if(stack.is(Items.FILLED_MAP)){
			final Component name = stack.get(DataComponents.CUSTOM_NAME);
			if(name != null) CommandExportMapNames.addMapName(name.getString());
		}
		else{
			final BundleContents contents = stack.get(DataComponents.BUNDLE_CONTENTS);
			if(contents != null) InvUtils.getAllNestedItemViews(bundleItems(contents)).forEach(UpdateInventoryContents::addMapName);
		}
	}
	private static final boolean addMapStateIds(final ItemStack stack, final Level world){
		if(stack.isEmpty()) return false;
		if(stack.getItem() == Items.FILLED_MAP){
			addMapName(stack);
			final MapId mapId = stack.get(DataComponents.MAP_ID);
			if(mapId == null){
				sawNullMapIdThisTick = true;
				if(!warnedNullMapId){
					Main.LOGGER.warn("UpdateInv: mapId is null! stack="+stack.getHoverName().getString());
					warnedNullMapId = true;
				}
				return false;
			}
			final MapItemSavedData state = world.getMapData(mapId);
			if(state != null){
				MapGroupUtils.nullMapIds.remove(mapId.id());
				return inventoryMapGroup.add(MapGroupUtils.getIdForMapState(state));
			}
			else{
				MapGroupUtils.nullMapIds.add(mapId.id());
				return false;
			}
		}
		//else
		return nestedInventoryMapGroup.addAll(
				(Configs.Visuals.MAP_HIGHLIGHT_IN_INV_INCLUDE_BUNDLES.getBooleanValue()
						? InvUtils.getAllNestedItemViews(stack)
						: InvUtils.getAllNestedItemViewsExcludingBundles(stack))
				.peek(UpdateInventoryContents::addMapName)
				.map(s -> MapItem.getSavedData(s.get(DataComponents.MAP_ID), world)).filter(Objects::nonNull)
				.map(MapGroupUtils::getIdForMapState).toList());
	}
	@Override public final void onTickStart(final Minecraft client){
		final Player player = client.player;
		if(player == null || player.level() == null || !player.isAlive()){
			currentlyBeingPlacedIntoItemFrame = null;
			return;
		}

		{
			// Constantly force-refresh mapstate-colorsId cache for held unlocked maps
			// Might deserve its own onTick listener tbh
			final MapItemSavedData state = MapItem.getSavedData(player.getMainHandItem(), player.level());
			if(state != null && !state.locked) MapGroupUtils.getIdForMapState(state, /*evict*/true);
		}
		{
			// Slot change confirms placement; expire unconfirmed attempts so a rejected click cannot block forever.
			if(currentlyBeingPlacedIntoItemFrame != null && 
					(++placementWaitTicks > 60 || !ItemStack.matches(player.getInventory().getItem(slotUsedForCurrentlyBeingPlacedItem), currentlyBeingPlacedIntoItemFrame))){
//				MapState state = FilledMapItem.getMapState(currentlyBeingPlacedIntoItemFrame, player.getWorld());
//				UUID colorsId = MapGroupUtils.getIdForMapState(state);
//				if(UpdateItemFrameHighlights.isInItemFrame(colorsId)){
//					Main.LOGGER.info("UpdateInv.onTickStart: Map appeared in iFrame before disappearing from inv");
//				}
				currentlyBeingPlacedIntoItemFrame = null;
			}
		}

		inventoryMapGroup.clear();
		nestedInventoryMapGroup.clear();
		final AbstractContainerMenu sh = player.containerMenu;
//		boolean anyNewMap = false;
		sawNullMapIdThisTick = false;
		for(int i=0; i<41; ++i) /*anyNewMap |=*/ addMapStateIds(player.getInventory().getItem(i), player.level());
		if(sh != null) /*anyNewMap |=*/ addMapStateIds(sh.getCarried(), player.level());
		if(!sawNullMapIdThisTick) warnedNullMapId = false;

		final int syncId = sh == null ? 0 : sh.containerId;
		mapsInInvHash = syncId + inventoryMapGroup.hashCode() + nestedInventoryMapGroup.hashCode();// * (mapPlaceStillOngoing ? 7 : 1);
	}
}