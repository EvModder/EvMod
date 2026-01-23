package net.evmodder.evmod.onTick;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.apis.InvUtils;
import net.evmodder.evmod.apis.MapGroupUtils;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.map.MapState;
import net.minecraft.world.World;

public class UpdateInventoryHighlights{
	private static HashSet<UUID> inventoryMapGroup = new HashSet<>(), nestedInventoryMapGroup = new HashSet<>();
	private static ItemStack currentlyBeingPlacedIntoItemFrame;
//	private static int itemsInInvHash;
	private static int mapsInInvHash;

	public static final int getMapInInvHash(){return mapsInInvHash;}

	public static final boolean isInInventory(/*final int id, */final UUID colorsUUID){
		return inventoryMapGroup.contains(colorsUUID);
	}
	public static final boolean isNestedInInventory(final UUID colorsUUID){
		return nestedInventoryMapGroup.contains(colorsUUID);
	}

	public static final boolean setCurrentlyBeingPlacedMapArt(PlayerEntity player, ItemStack stack){
		final MapState state = FilledMapItem.getMapState(stack, player.getWorld());
		if(state != null && stack.getCount() == 1 &&
				IntStream.range(0, 41).noneMatch(i -> i != player.getInventory().selectedSlot &&
				FilledMapItem.getMapState(player.getInventory().getStack(i), player.getWorld()) == state))
		{
//			currentlyBeingPlacedIntoItemFrameSlot = player.getInventory().selectedSlot;
			currentlyBeingPlacedIntoItemFrame = stack.copy();
			onTickStart(player);
			return true;
		}
		return false;
	}
	public static final boolean hasCurrentlyBeingPlaceMapArt(){return currentlyBeingPlacedIntoItemFrame != null;}

	private static final boolean addMapStateIds(final ItemStack stack, final World world){
		if(stack.isEmpty()) return false;
		final MapState state = FilledMapItem.getMapState(stack, world);
		if(state == null){
			List<UUID> colorIds = 
					(Configs.Visuals.MAP_HIGHLIGHT_IN_INV_INCLUDE_BUNDLES.getBooleanValue()
							? InvUtils.getAllNestedItems(Stream.of(stack))
							: InvUtils.getAllNestedItemsExcludingBundles(Stream.of(stack)))
					.map(s -> FilledMapItem.getMapState(s, world)).filter(Objects::nonNull)
					.map(MapGroupUtils::getIdForMapState).toList();
			if(!colorIds.isEmpty()){
				nestedInventoryMapGroup.addAll(colorIds);
			}
			return false;
		}
		//if(i == currentlyBeingPlacedIntoItemFrameSlot &&
		if(currentlyBeingPlacedIntoItemFrame != null && ItemStack.areEqual(stack, currentlyBeingPlacedIntoItemFrame)){
//			mapPlaceStillOngoing = true; continue;
			return true;
		}
		inventoryMapGroup.add(MapGroupUtils.getIdForMapState(state));
		return false;
	}
	public static final void onTickStart(PlayerEntity player){
		if(player == null || player.getWorld() == null || !player.isAlive()) return;

		{
			// Constantly force-refresh mapstate-colorsId cache for held unlocked maps
			// Might deserve its own onTick listener tbh
			MapState state = FilledMapItem.getMapState(player.getMainHandStack(), player.getWorld());
			if(state != null && !state.locked) MapGroupUtils.getIdForMapState(state, /*evict*/true);
		}

		inventoryMapGroup.clear();
		nestedInventoryMapGroup.clear();
		boolean mapPlaceStillOngoing = false;
		for(int i=0; i<41; ++i) mapPlaceStillOngoing |= addMapStateIds(player.getInventory().getStack(i), player.getWorld());
		if(player.currentScreenHandler != null){
			mapPlaceStillOngoing |= addMapStateIds(player.currentScreenHandler.getCursorStack(), player.getWorld());
		}

		if(!mapPlaceStillOngoing){
			currentlyBeingPlacedIntoItemFrame = null;
//			currentlyBeingPlacedIntoItemFrameSlot = -1;
		}
//		else if(UpdateItemFrameHighlights.isInItemFrame(currentlyBeingPlacedIntoItemFrame)){
//			Main.LOGGER.info("MapGroupUtils: Ah yes, map is placed in itemframe and yet still in inventory. Thanks Minecraft");
//		}
//		if(newInvHash != invHash){
//			invHash = newInvHash;
//			ItemFrameHighlightUpdater.highlightedIFrames.clear(); // Push vs pull?
//		}
		final int syncId = player.currentScreenHandler != null ? player.currentScreenHandler.syncId : 0;
		mapsInInvHash = syncId + inventoryMapGroup.hashCode() + nestedInventoryMapGroup.hashCode();// * (mapPlaceStillOngoing ? 7 : 1);
	}
}