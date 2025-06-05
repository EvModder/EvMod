package net.evmodder.KeyBound;

import java.util.HashSet;
import java.util.UUID;
import net.minecraft.item.map.MapState;

public final class MapGroupUtils{
	public static HashSet<UUID> mapsInGroup;
	public static boolean INCLUDE_UNLOCKED;
	public static boolean ENFORCE_MATCHES_LOCKEDNESS = true; // TODO: config setting

	public static final UUID getIdForMapState(MapState state){
		UUID uuid = UUID.nameUUIDFromBytes(state.colors);
		// set 1st bit = state.locked
		return new UUID((uuid.getMostSignificantBits() & ~1l) | (state.locked ? 1l : 0l), uuid.getLeastSignificantBits());
	}
	public static final boolean isMapNotInCurrentGroup(MapState state){
		if(mapsInGroup == null) return false;
		if(!INCLUDE_UNLOCKED && !state.locked) return false;

		UUID uuid = getIdForMapState(state);
		if(mapsInGroup.contains(uuid)) return false;
		if(ENFORCE_MATCHES_LOCKEDNESS) return true;
		// toggle 1st bit on/off
		uuid = new UUID(uuid.getMostSignificantBits() ^ 1l, uuid.getLeastSignificantBits());
		return !mapsInGroup.contains(uuid);
	}
}
