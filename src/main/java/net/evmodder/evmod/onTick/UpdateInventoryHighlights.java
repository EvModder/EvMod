package net.evmodder.evmod.onTick;

import java.util.HashSet;
import java.util.Objects;
import java.util.UUID;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.Main;
import net.evmodder.evmod.apis.InvUtils;
import net.evmodder.evmod.apis.MapGroupUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapState;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.world.World;

public class UpdateInventoryHighlights{
	private static HashSet<UUID> inventoryMapGroup = new HashSet<>(), nestedInventoryMapGroup = new HashSet<>();
	private static ItemStack currentlyBeingPlacedIntoItemFrame;
	private static int slotUsedForCurrentlyBeingPlacedItem;
//	private static int itemsInInvHash;
	private static int mapsInInvHash;

	public static final int getMapInInvHash(){return mapsInInvHash;}

	public static final boolean isInInventory(/*final int id, */final UUID colorsUUID){
		return inventoryMapGroup.contains(colorsUUID);
	}
	public static final boolean isNestedInInventory(final UUID colorsUUID){
		return nestedInventoryMapGroup.contains(colorsUUID);
	}

	public static final void setCurrentlyBeingPlacedMapArt(ItemStack stack, int slot){ // Accessor: MapHandRestock
		assert ItemStack.areEqual(MinecraftClient.getInstance().player.getInventory().getStack(slot), stack);
		currentlyBeingPlacedIntoItemFrame = stack.copy();
		slotUsedForCurrentlyBeingPlacedItem = slot;
	}
	public static final boolean hasCurrentlyBeingPlacedMapArt(){return currentlyBeingPlacedIntoItemFrame != null;}

	private static final boolean addMapStateIds(final ItemStack stack, final World world){
		if(stack.isEmpty()) return false;
		if(stack.getItem() == Items.FILLED_MAP){
			final MapIdComponent mapId = stack.get(DataComponentTypes.MAP_ID);
			if(mapId == null){
				Main.LOGGER.warn("UpdateInv: mapId is null! stack="+stack.getName().getString());
				return false;
			}
			final MapState state = world.getMapState(mapId);
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
						? InvUtils.getAllNestedItems(stack)
						: InvUtils.getAllNestedItemsExcludingBundles(stack))
				.map(s -> FilledMapItem.getMapState(s, world)).filter(Objects::nonNull)
				.map(MapGroupUtils::getIdForMapState).toList());
	}
	public static final void onTickStart(PlayerEntity player){
		if(player == null || player.getWorld() == null || !player.isAlive()) return;

		{
			// Constantly force-refresh mapstate-colorsId cache for held unlocked maps
			// Might deserve its own onTick listener tbh
			MapState state = FilledMapItem.getMapState(player.getMainHandStack(), player.getWorld());
			if(state != null && !state.locked) MapGroupUtils.getIdForMapState(state, /*evict*/true);
		}
		{
			// Check if the currentlyBeingPlacedIntoItemFrame slot has changed value (indicates it's done being placed)
			if(currentlyBeingPlacedIntoItemFrame != null && 
					!ItemStack.areEqual(player.getInventory().getStack(slotUsedForCurrentlyBeingPlacedItem), currentlyBeingPlacedIntoItemFrame)){
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
		final ScreenHandler sh = player.currentScreenHandler;
//		boolean anyNewMap = false;
		for(int i=0; i<41; ++i) /*anyNewMap |=*/ addMapStateIds(player.getInventory().getStack(i), player.getWorld());
		if(sh != null) /*anyNewMap |=*/ addMapStateIds(sh.getCursorStack(), player.getWorld());

		final int syncId = sh == null ? 0 : sh.syncId;
		mapsInInvHash = syncId + inventoryMapGroup.hashCode() + nestedInventoryMapGroup.hashCode();// * (mapPlaceStillOngoing ? 7 : 1);
	}
}