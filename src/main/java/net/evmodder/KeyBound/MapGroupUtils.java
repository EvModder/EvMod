package net.evmodder.KeyBound;

import java.util.HashSet;
import java.util.UUID;
import java.util.stream.IntStream;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapState;

public final class MapGroupUtils{
	private static HashSet<UUID> currentMapGroup;
	static boolean INCLUDE_UNLOCKED;
	private static boolean ENFORCE_MATCHES_LOCKEDNESS = true; // TODO: config setting


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
	}
	public static final boolean isMapNotInCurrentGroup(MapState state){
		if(currentMapGroup == null) return false;
		if(!INCLUDE_UNLOCKED && !state.locked) return false;

		UUID uuid = getIdForMapState(state);
		if(currentMapGroup.contains(uuid)) return false;
		if(ENFORCE_MATCHES_LOCKEDNESS) return true;
		// toggle 1st bit on/off
		uuid = new UUID(uuid.getMostSignificantBits() ^ 1l, uuid.getLeastSignificantBits());
		return !currentMapGroup.contains(uuid);
	}
	public static final boolean isInInventory(PlayerEntity player, final int id, final MapState state){
		final UUID invMapUUID = getIdForMapState(state);
		return
				(id != -1 && IntStream.range(0, 41)
				.mapToObj(i -> player.getInventory().getStack(i)).filter(s -> s.getItem() == Items.FILLED_MAP)
				.anyMatch(s-> s.get(DataComponentTypes.MAP_ID).id() == id))
				||
				(state != null && IntStream.range(0, 41)
				.mapToObj(i -> player.getInventory().getStack(i)).filter(s -> s.getItem() == Items.FILLED_MAP)
				.map(s -> FilledMapItem.getMapState(s, player.getWorld())).filter(s -> s != null)
				.anyMatch(s -> invMapUUID.equals(getIdForMapState(s))));
	}
}