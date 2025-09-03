package net.evmodder.KeyBound.events;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import net.evmodder.KeyBound.MapGroupUtils;
import net.evmodder.KeyBound.MapRelationUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.map.MapState;

public class InventoryHighlightUpdater{
//	private static int invHash;
	private static HashSet<UUID> inventoryMapGroup = new HashSet<>(), nestedInventoryMapGroup = new HashSet<>();
	public static UUID currentlyBeingPlacedIntoItemFrame;
	public static int mapsInInvHash;

	public static final boolean isInInventory(/*final int id, */final UUID colorsUUID){
		return inventoryMapGroup.contains(colorsUUID);
	}
	public static final boolean isNestedInInventory(final UUID colorsUUID){
		return nestedInventoryMapGroup.contains(colorsUUID);
	}

	public static final void onUpdateTick(MinecraftClient client){
		if(client.player == null || client.world == null || !client.player.isAlive()) return;
//		int newInvHash = inventoryMapGroup.size() * nestedInventoryMapGroup.size();
		inventoryMapGroup.clear();
		nestedInventoryMapGroup.clear();
		boolean mapPlaceStillOngoing = false;
		for(int i=0; i<41; ++i){
			ItemStack stack = client.player.getInventory().getStack(i);
			if(stack.isEmpty()) continue;
			final MapState state = FilledMapItem.getMapState(stack, client.world);
			if(state == null){
				List<UUID> colorIds = MapRelationUtils.getAllNestedItems(Stream.of(stack))
						.map(s -> FilledMapItem.getMapState(s, client.world)).filter(Objects::nonNull)
						.map(MapGroupUtils::getIdForMapState).toList();
				if(!colorIds.isEmpty()){
					nestedInventoryMapGroup.addAll(colorIds);
//					newInvHash += colorIds.hashCode();
				}
				continue;
			}
			final UUID colorsId = MapGroupUtils.getIdForMapState(state);
			if(/*i == currentlyBeingPlacedIntoItemFrameSlot && */colorsId.equals(currentlyBeingPlacedIntoItemFrame)){mapPlaceStillOngoing = true; continue;}
			inventoryMapGroup.add(colorsId);
//			newInvHash += colorsId.hashCode();
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
		mapsInInvHash = inventoryMapGroup.hashCode() + nestedInventoryMapGroup.hashCode();
	}
}
