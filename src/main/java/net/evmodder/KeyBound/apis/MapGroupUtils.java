package net.evmodder.KeyBound.apis;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import java.util.UUID;
import net.evmodder.KeyBound.config.Configs;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.item.map.MapState;

public final class MapGroupUtils{
	public static int mapsInGroupHash; // TODO: private
	private static HashSet<UUID> currentMapGroup;
	private static boolean ENFORCE_MATCHES_LOCKEDNESS = true; // TODO: config setting

	private static final HashMap<MapState, UUID> stateToIdCache = new HashMap<MapState, UUID>(), unlockedStateToIdCache = new HashMap<MapState, UUID>();
	private static final Random rand = new Random();
	public static final UUID getIdForMapState(MapState state){
		UUID uuid = state.locked ? stateToIdCache.get(state) : unlockedStateToIdCache.get(state);
		if(uuid != null && (state.locked || rand.nextFloat() < 0.99)) return uuid; // 1% chance of cache eviction for unlocked states

		// Normalize all CLEAR/transparent colors
		for(int i=0; i<state.colors.length; ++i) if(state.colors[i] == 1 || state.colors[i] == 2) state.colors[i] = 0;

		uuid = UUID.nameUUIDFromBytes(state.colors);
		// set 1st bit = state.locked
		uuid = new UUID((uuid.getMostSignificantBits() & ~1l) | (state.locked ? 1l : 0l), uuid.getLeastSignificantBits());
		if(state.locked) stateToIdCache.put(state, uuid);
		else unlockedStateToIdCache.put(state, uuid);
		return uuid;
	}

	private static final int MAX_MAPS_IN_INV_AND_ECHEST = 64*27*(36+27); // 108864
	public static final HashSet<UUID> getLoadedMaps(final ClientWorld world){
		final HashSet<UUID> loadedMaps = new HashSet<UUID>();
		MapState state;
		final boolean INCLUDE_UNLOCKED = Configs.Generic.MAPART_GROUP_INCLUDE_UNLOCKED.getBooleanValue();
		for(int i=0; (state=world.getMapState(new MapIdComponent(i))) != null || i < MAX_MAPS_IN_INV_AND_ECHEST; ++i){
			if(state != null && (INCLUDE_UNLOCKED || state.locked)) loadedMaps.add(getIdForMapState(state));
		}
		return loadedMaps;
	}
	public static final void setCurrentGroup(HashSet<UUID> newGroup){
		currentMapGroup = newGroup;
		mapsInGroupHash = newGroup == null ? 0 : newGroup.hashCode();
	}
	public static final boolean isMapNotInCurrentGroup(final UUID colorsUUID){
		return currentMapGroup != null && !currentMapGroup.contains(colorsUUID);
	}
	public static final boolean shouldHighlightNotInCurrentGroup(final MapState state){
		if(currentMapGroup == null) return false;
		if(!Configs.Generic.MAPART_GROUP_INCLUDE_UNLOCKED.getBooleanValue() && !state.locked) return false;

		UUID uuid = getIdForMapState(state);
		if(currentMapGroup.contains(uuid)) return false;
		if(ENFORCE_MATCHES_LOCKEDNESS) return true;
		// toggle 1st bit on/off
		uuid = new UUID(uuid.getMostSignificantBits() ^ 1l, uuid.getLeastSignificantBits());
		return !currentMapGroup.contains(uuid);
	}
}