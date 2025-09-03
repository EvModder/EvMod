package net.evmodder.KeyBound;

import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.item.map.MapState;

public final class MapGroupUtils{
	private static HashSet<UUID> currentMapGroup;
	static boolean INCLUDE_UNLOCKED;
	private static boolean ENFORCE_MATCHES_LOCKEDNESS = true; // TODO: config setting

	private static final HashMap<MapState, UUID> stateToIdCache = new HashMap<MapState, UUID>();
	public static final UUID getIdForMapState(MapState state){
		UUID uuid;
		if(state.locked && (uuid=stateToIdCache.get(state)) != null) return uuid;

		// Normalize all CLEAR/transparent colors
		for(int i=0; i<state.colors.length; ++i) if(state.colors[i] == 1 || state.colors[i] == 2) state.colors[i] = 0; 

		uuid = UUID.nameUUIDFromBytes(state.colors);
		// set 1st bit = state.locked
		uuid = new UUID((uuid.getMostSignificantBits() & ~1l) | (state.locked ? 1l : 0l), uuid.getLeastSignificantBits());
		stateToIdCache.put(state, uuid);
		return uuid;
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
//		ItemFrameHighlightUpdater.highlightedIFrames.clear();
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
}