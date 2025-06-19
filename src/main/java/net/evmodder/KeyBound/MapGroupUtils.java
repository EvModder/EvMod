package net.evmodder.KeyBound;

import java.util.HashSet;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.map.MapState;

public final class MapGroupUtils{
	//private static HashSet<Integer> inventoryMapGroupRawIds;
	private static HashSet<UUID> currentMapGroup, inventoryMapGroup = new HashSet<>();
	static boolean INCLUDE_UNLOCKED;
	private static boolean ENFORCE_MATCHES_LOCKEDNESS = true; // TODO: config setting

	//TODO: Figure out something nicer than this shared global var
	public static final HashSet<Integer> skipIFrameHasLabel = new HashSet<>();

	public static final UUID getIdForMapState(MapState state){
		UUID uuid = UUID.nameUUIDFromBytes(state.colors);
		// set 1st bit = state.locked
		return new UUID((uuid.getMostSignificantBits() & ~1l) | (state.locked ? 1l : 0l), uuid.getLeastSignificantBits());
	}

	private static final int MAX_MAPS_IN_INV_AND_ECHEST = 64*27*(36+27); // 108864
	public static final HashSet<UUID> getLoadedMaps(final ClientWorld world){
		final HashSet<UUID> loadedMaps = new HashSet<UUID>();
		MapState state;
		for(int i=0; (state=world.getMapState(new MapIdComponent(i))) != null || i < MAX_MAPS_IN_INV_AND_ECHEST; ++i){
			if(state != null && (INCLUDE_UNLOCKED || state.locked)) loadedMaps.add(getIdForMapState(state));
		}
		return loadedMaps;
	}
	public static final void setCurrentGroup(HashSet<UUID> newGroup){
		currentMapGroup = newGroup;
		skipIFrameHasLabel.clear();
	}
	public static final boolean isMapNotInCurrentGroup(final UUID colorsUUID){
		return currentMapGroup != null && !currentMapGroup.contains(colorsUUID);
	}
	public static final boolean shouldHighlightNotInCurrentGroup(final MapState state){
		if(currentMapGroup == null) return false;
		if(!INCLUDE_UNLOCKED && !state.locked) return false;

		UUID uuid = getIdForMapState(state);
		if(currentMapGroup.contains(uuid)) return false;
		if(ENFORCE_MATCHES_LOCKEDNESS) return true;
		// toggle 1st bit on/off
		uuid = new UUID(uuid.getMostSignificantBits() ^ 1l, uuid.getLeastSignificantBits());
		return !currentMapGroup.contains(uuid);
	}
	public static final boolean isInInventory(/*final int id, */final UUID colorsUUID){
		//return inventoryMapGroup != null && (inventoryMapGroupRawIds.contains(id) || inventoryMapGroup.contains(colorsUUID));
		return inventoryMapGroup != null && inventoryMapGroup.contains(colorsUUID);
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

	private static int tickIdx = 0;
	static final void updateInvMapGroup(MinecraftClient client){
		if(client.player == null || client.world == null || !client.player.isAlive()) return;
		inventoryMapGroup.clear();
		if(++tickIdx == 4) skipIFrameHasLabel.clear(); // Only run every 4 ticks; TODO: this can probably be run every tick safely
		for(int i=0; i<41; ++i){
			final MapState state = FilledMapItem.getMapState(client.player.getInventory().getStack(i), client.world);
			if(state != null) inventoryMapGroup.add(getIdForMapState(state));
		}
	}
}