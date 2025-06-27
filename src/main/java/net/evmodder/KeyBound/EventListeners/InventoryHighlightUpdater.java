package net.evmodder.KeyBound.EventListeners;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.evmodder.KeyBound.MapGroupUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.map.MapState;

public class InventoryHighlightUpdater{
	//private static HashSet<Integer> inventoryMapGroupRawIds;
	private static int invHash;
	private static HashSet<UUID> inventoryMapGroup = new HashSet<>(), nestedInventoryMapGroup = new HashSet<>();
	public static UUID currentlyBeingPlacedIntoItemFrame;
//	public static int currentlyBeingPlacedIntoItemFrameSlot = -1;

	public static final boolean isInInventory(/*final int id, */final UUID colorsUUID){
		//return inventoryMapGroup != null && (inventoryMapGroupRawIds.contains(id) || inventoryMapGroup.contains(colorsUUID));
		return inventoryMapGroup.contains(colorsUUID);
//		return
//				(id != -1 && IntStream.range(0, 41)
//				.mapToObj(i -> player.getInventory().getStack(i)).filter(s -> s.getItem() == Items.FILLED_MAP)
//				.anyMatch(s-> s.get(DataComponentTypes.MAP_ID).id() == id))
//				||
//				(colorsUUID != null && IntStream.range(0, 41)
//				.mapToObj(i -> player.getInventory().getStack(i)).filter(s -> s.getItem() == Items.FILLED_MAP)
//				.map(s -> FilledMapItem.getMapState(s, player.getWorld())).filter(s -> s != null)
//				.anyMatch(s -> colorsUUID.equals(getIdForMapState(s))));
	}
	public static final boolean isNestedInInventory(final UUID colorsUUID){
		return nestedInventoryMapGroup.contains(colorsUUID);
	}

	//private static int tickIdx = 0;
	public static final void onUpdateTick(MinecraftClient client){
		if(client.player == null || client.world == null || !client.player.isAlive()) return;
		int newInvHash = inventoryMapGroup.size() * nestedInventoryMapGroup.size();
		inventoryMapGroup.clear();
		nestedInventoryMapGroup.clear();
		boolean mapPlaceStillOngoing = false;
		for(int i=0; i<41; ++i){
			ContainerComponent container = client.player.getInventory().getStack(i).get(DataComponentTypes.CONTAINER);
			if(container != null){
				List<UUID> colorIds = container.streamNonEmpty()
						.map(s -> FilledMapItem.getMapState(s, client.world)).filter(s -> s != null)
						.map(s -> MapGroupUtils.getIdForMapState(s)).toList();
				nestedInventoryMapGroup.addAll(colorIds);
				newInvHash += colorIds.hashCode();
				continue;
			}
			final MapState state = FilledMapItem.getMapState(client.player.getInventory().getStack(i), client.world);
			if(state == null) continue;
			final UUID colorsId = MapGroupUtils.getIdForMapState(state);
			if(/*i == currentlyBeingPlacedIntoItemFrameSlot && */colorsId.equals(currentlyBeingPlacedIntoItemFrame)){mapPlaceStillOngoing = true; continue;}
			inventoryMapGroup.add(colorsId);
			newInvHash += colorsId.hashCode();
		}
		if(!mapPlaceStillOngoing){
			currentlyBeingPlacedIntoItemFrame = null;
//			currentlyBeingPlacedIntoItemFrameSlot = -1;
		}
//		else if(ItemFrameHighlightUpdater.isInItemFrame(currentlyBeingPlacedIntoItemFrame)){
//			Main.LOGGER.info("MapGroupUtils: Aha, yes, map is placed in itemframe and yet still in inventory. Thanks Minecraft");
//		}
		if(newInvHash != invHash){
			invHash = newInvHash;
			ItemFrameHighlightUpdater.skipIFrameHasLabel.clear();
		}
	}
}
