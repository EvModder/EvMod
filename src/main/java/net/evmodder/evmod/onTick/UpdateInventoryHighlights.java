package net.evmodder.evmod.onTick;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.apis.MapGroupUtils;
import net.evmodder.evmod.apis.MapRelationUtils;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.map.MapState;
import net.minecraft.world.World;

public class UpdateInventoryHighlights{
//	private static int invHash;
	private static HashSet<UUID> inventoryMapGroup = new HashSet<>(), nestedInventoryMapGroup = new HashSet<>();
	private static ItemStack currentlyBeingPlacedIntoItemFrame;
	public static int mapsInInvHash;

	public static final boolean isInInventory(/*final int id, */final UUID colorsUUID){
		return inventoryMapGroup.contains(colorsUUID);
	}
	public static final boolean isNestedInInventory(final UUID colorsUUID){
		return nestedInventoryMapGroup.contains(colorsUUID);
	}

	public static final boolean setCurrentlyBeingPlacedMapArt(PlayerEntity player, ItemStack stack){
		final MapState state = FilledMapItem.getMapState(stack, player.getEntityWorld());
		if(state != null && stack.getCount() == 1 &&
				IntStream.range(0, 41).noneMatch(i -> i != player.getInventory().getSelectedSlot() &&
				FilledMapItem.getMapState(player.getInventory().getStack(i), player.getEntityWorld()) == state))
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
							? MapRelationUtils.getAllNestedItems(Stream.of(stack))
							: MapRelationUtils.getAllNestedItemsExcludingBundles(Stream.of(stack)))
					.map(s -> FilledMapItem.getMapState(s, world)).filter(Objects::nonNull)
					.map(MapGroupUtils::getIdForMapState).toList();
			if(!colorIds.isEmpty()){
				nestedInventoryMapGroup.addAll(colorIds);
//				newInvHash += colorIds.hashCode();
			}
			return false;
		}
		//if(i == currentlyBeingPlacedIntoItemFrameSlot &&
		if(currentlyBeingPlacedIntoItemFrame != null && ItemStack.areEqual(stack, currentlyBeingPlacedIntoItemFrame)){
//			mapPlaceStillOngoing = true; continue;
			return true;
		}
		inventoryMapGroup.add(MapGroupUtils.getIdForMapState(state));
//		newInvHash += colorsId.hashCode();
		return false;
	}
	public static final void onTickStart(PlayerEntity player){
		if(player == null || player.getEntityWorld() == null || !player.isAlive()) return;

		//TODO: this might make more sense in its own onTick() listener
		MapState state = FilledMapItem.getMapState(player.getMainHandStack(), player.getEntityWorld());
		if(state != null && !state.locked) MapGroupUtils.getIdForMapState(state, /*evictUnlocked*/true);

//		int newInvHash = inventoryMapGroup.size() * nestedInventoryMapGroup.size();
		inventoryMapGroup.clear();
		nestedInventoryMapGroup.clear();
		boolean mapPlaceStillOngoing = false;
		for(int i=0; i<41; ++i) mapPlaceStillOngoing |= addMapStateIds(player.getInventory().getStack(i), player.getEntityWorld());
		if(player.currentScreenHandler != null){
			mapPlaceStillOngoing |= addMapStateIds(player.currentScreenHandler.getCursorStack(), player.getEntityWorld());
		}

		if(!mapPlaceStillOngoing){
			currentlyBeingPlacedIntoItemFrame = null;
//			currentlyBeingPlacedIntoItemFrameSlot = -1;
		}
//		else if(ItemFrameHighlightUpdater.isInItemFrame(currentlyBeingPlacedIntoItemFrame)){
//			Main.LOGGER.info("MapGroupUtils: Aha, yes, map is placed in itemframe and yet still in inventory. Thanks Minecraft");
//		}
//		if(newInvHash != invHash){
//			invHash = newInvHash;
//			ItemFrameHighlightUpdater.highlightedIFrames.clear();
//		}
		final int syncId = player.currentScreenHandler != null ? player.currentScreenHandler.syncId : 0;
		mapsInInvHash = syncId + inventoryMapGroup.hashCode() + nestedInventoryMapGroup.hashCode();// * (mapPlaceStillOngoing ? 7 : 1);
	}
}