package net.evmodder.evmod.apis;

import java.io.File;
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import net.evmodder.EvLib.util.FileIO;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.Main;
import net.evmodder.evmod.config.OptionMapStateCache;
import net.evmodder.evmod.listeners.ContainerClickListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.entity.Entity;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapDecoration;
import net.minecraft.item.map.MapDecorationTypes;
import net.minecraft.item.map.MapState;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

public class MapStateCacher{
	// Server address -> cache
	private static HashMap<String, HashMap<Integer, MapStateSerializable>> byId;
	private static HashMap<String, HashMap<String, MapStateSerializable>> byName;
	private static HashMap<String, HashSet<String>> unusableNames;
	private static HashMap<String, HashMap</*hash=*/UUID, List<MapStateSerializable>>> bySlot;
	private static boolean namesCacheUpdated, idCacheUpdated;

	private static final String DIR = "map_cache/";

	public static final String BY_ID = "map_ids",
			BY_NAME = "item_names",
			BY_PLAYER_INV = "player_invs",
			BY_PLAYER_EC = "player_ecs",
			BY_CONTAINER = "containers";

//	public static final byte CACHED_MARKER_SCALE = (byte)128;
	private static final MapDecoration CACHED_MARKER_DECORATION
		= new MapDecoration(MapDecorationTypes.BLUE_MARKER, /*x=*/(byte)-1, /*z=*/(byte)-1, /*rot=*/(byte)-1, java.util.Optional.empty());

	public static final boolean hasCacheMarker(MapState state){
		Iterator<MapDecoration> iter = state.getDecorations().iterator();
		return iter.hasNext() && iter.next() == CACHED_MARKER_DECORATION && !iter.hasNext();
	}

	private static final record MapStateSerializable(byte scale, boolean locked, String dimRegistry, String dimValue, byte[] colors) implements Serializable{
		private static final long serialVersionUID = 2713495820097984925L;

		public static MapStateSerializable fromMapState(MapState ms){
			return ms == null/* || ms.colors == null*/ ? null :
				new MapStateSerializable(ms.scale, ms.locked, ms.dimension.getRegistry().toString(), ms.dimension.getValue().toString(), ms.colors);
		}
		public MapState toMapState(){
			Identifier registryId = Identifier.of(dimRegistry), valueId = Identifier.of(dimValue);
			RegistryKey<World> dimension = RegistryKey.of(RegistryKey.ofRegistry(registryId), valueId);
			MapState ms = MapState.of(scale, locked, dimension);
			ms.colors = colors;
			ms.replaceDecorations(List.of(CACHED_MARKER_DECORATION));
			return ms;
		}

		@Override public boolean equals(Object o){
			return o != null && o instanceof MapStateSerializable mss && mss.scale == scale && mss.locked == locked
					&& (mss.dimRegistry == null ? dimRegistry == null : mss.dimRegistry.equals(dimRegistry))
					&& Arrays.equals(mss.colors, colors);
		}
	}

	private static final UUID getIdForPlayer(boolean invOrEc){
		MinecraftClient client = MinecraftClient.getInstance();
		if(client.player == null) return null;
		Entity e = MinecraftClient.getInstance().player;
		if(e == null) return null;
		UUID uuid = e.getUuid();
		// set 1st bit, 0= is inv, 1= is ec
		return new UUID((uuid.getMostSignificantBits() & ~1l) | (invOrEc ? 0l : 1l), uuid.getLeastSignificantBits());
	}

	private static final HashMap<?, ?> getInMemCachePerServer(String server, String cache){
		switch(cache){
			case BY_ID: return byId == null ? null : byId.get(server);
			case BY_NAME: return byName == null ? null : byName.get(server);
			case BY_PLAYER_INV:
			case BY_PLAYER_EC:
			case BY_CONTAINER:
				return bySlot == null ? null : bySlot.get(server);
			default:
				throw new RuntimeException("MapStateCacher: Unknown cache type in getInMemCachePerServer()! "+cache);
		}
	}

	private static final Object getInMemCacheSpecific(String server, String cache){
		HashMap<UUID, List<MapStateSerializable>> subCache;
		switch(cache){
			case BY_ID: return byId == null ? null : byId.get(server); // Same as getInMemCacheServer()
			case BY_NAME: return byName == null ? null : byName.get(server); // Same as getInMemCacheServer()
			case BY_PLAYER_INV:
				return bySlot == null ? null : (subCache=bySlot.get(server)) == null ? null : subCache.get(getIdForPlayer(true));
			case BY_PLAYER_EC:
				return bySlot == null ? null : (subCache=bySlot.get(server)) == null ? null : subCache.get(getIdForPlayer(false));
			case BY_CONTAINER:
				return bySlot == null ? null : (subCache=bySlot.get(server)) == null ? null : subCache.get(ContainerClickListener.lastClickedBlockHash);
			default:
				throw new RuntimeException("MapStateCacher: Unknown cache type in getInMemCacheSpecific()! "+cache);
		}
	}

	private static final boolean saveCacheFile(String server, String cache){
		final String filename = DIR+server+"/"+cache+".cache";
		final HashMap<?, ?> perServerCache = getInMemCachePerServer(server, cache);
		if(perServerCache == null || perServerCache.isEmpty()) return new File(filename).delete();

		File dir = new File(FileIO.DIR+DIR);
		if(!dir.isDirectory()){Main.LOGGER.info("MapStateCacher: Creating dir '"+dir.getName()+"'"); dir.mkdir();}
		dir = new File(FileIO.DIR+DIR+server);
		if(!dir.isDirectory()){Main.LOGGER.info("MapStateCacher: Creating dir '"+dir.getName()+"'"); dir.mkdir();}
		FileIO.writeObject(filename, perServerCache);
		return true;
	}
	@SuppressWarnings("unchecked")
	private static final HashMap<?, ?> createInMemCacheFromFile(String server, String cache){
		final String filename = DIR+server+"/"+cache+".cache";
		HashMap<?, ?> loadedCache = (HashMap<?, ?>)FileIO.readObject(filename);
//		if(loadedCache == null) return null;
		switch(cache){
			case BY_ID:
				if(byId == null) byId = new HashMap<>();
				if(loadedCache == null) loadedCache = new HashMap<Integer, MapStateSerializable>();
				byId.put(server, (HashMap<Integer, MapStateSerializable>)loadedCache);
				return loadedCache;
			case BY_NAME:
				if(byName == null) byName = new HashMap<>();
				if(loadedCache == null) loadedCache = new HashMap<String, MapStateSerializable>();
				byName.put(server, (HashMap<String, MapStateSerializable>)loadedCache);
				return loadedCache;
			case BY_PLAYER_INV:
			case BY_PLAYER_EC:
			case BY_CONTAINER:
				if(bySlot == null) bySlot = new HashMap<>();
				if(loadedCache == null) loadedCache = new HashMap<UUID, List<MapStateSerializable>>();
				bySlot.put(server, (HashMap<UUID, List<MapStateSerializable>>)loadedCache);
//				bySlot.putIfAbsent(server, new HashMap<>());
//				bySlot.get(server).putAll((HashMap<UUID, List<MapStateSerializable>>)loadedCache);
				return loadedCache;
			default:
				throw new RuntimeException("MapStateCacher: Unknown cache type in loadCacheFile()! "+cache);
		}
	}

	//====================================================================================================
	@SuppressWarnings("unchecked")
	public static final boolean saveMapStatesByPos(Stream<ItemStack> items, String cache){
		MinecraftClient client = MinecraftClient.getInstance();
		Stream<MapState> states = InvUtils.getAllNestedItems(items/*.sequential()*/)
				.sequential()
				.filter(s -> s.getItem() == Items.FILLED_MAP)
				.map(s -> FilledMapItem.getMapState(s, client.world));
		if(!Configs.Generic.MAP_CACHE_UNLOCKED.getBooleanValue()) states = states.map(s -> s == null || s.locked ? s : null);
		final List<MapStateSerializable> serialStates = states.map(MapStateSerializable::fromMapState).toList();

		final boolean deleteCache = serialStates.isEmpty() || serialStates.stream().allMatch(Objects::isNull);

		// Load old cache values
		final String server = MiscUtils.getServerAddress();
		HashMap<UUID, List<MapStateSerializable>> bySlotPerServer = (HashMap<UUID, List<MapStateSerializable>>)getInMemCachePerServer(server, cache);
		final List<MapStateSerializable> oldCache = (List<MapStateSerializable>)getInMemCacheSpecific(server, cache);
		if(oldCache == null){
			if(deleteCache) return false; // Already deleted
			if(bySlotPerServer == null){
				if(bySlot == null) bySlot = new HashMap<>();
				if(Configs.Generic.MAP_CACHE.getOptionListValue() == OptionMapStateCache.MEMORY_AND_DISK){
					bySlotPerServer = (HashMap<UUID, List<MapStateSerializable>>)createInMemCacheFromFile(server, cache);
				}
				else bySlot.put(server, bySlotPerServer=new HashMap<>());
			}
		}
		else if(oldCache.equals(serialStates)) return false; // No cache update
//		else if(keepOldCache(oldCache, mapStates)) return false; // No cache update needed
		final UUID key;
		switch(cache){
			case BY_PLAYER_INV: key = getIdForPlayer(true); break;
			case BY_PLAYER_EC: key = getIdForPlayer(false); break;
			case BY_CONTAINER: key = ContainerClickListener.lastClickedBlockHash; break;
			default: throw new RuntimeException("MapStateCacher: Unknown cache type in saveMapStatesByPos()! "+cache);
		}
		if(!deleteCache) bySlotPerServer.put(key, serialStates);
		else if(bySlotPerServer != null) bySlotPerServer.remove(key);

		boolean success = true;
		if(Configs.Generic.MAP_CACHE.getOptionListValue() == OptionMapStateCache.MEMORY_AND_DISK) success &= saveCacheFile(server, cache);
		Main.LOGGER.info("MapStateCacher: type="+cache+",stored="+serialStates.size());
		return success;
	}

	private static final Object commonCacheLoad(String cache){
		MinecraftClient client = MinecraftClient.getInstance();
		if(client == null || client.player == null || client.world == null) return null;
		final String server = MiscUtils.getServerAddress();
		final Object specificCache = getInMemCacheSpecific(server, cache);
		if(specificCache == null && getInMemCachePerServer(server, cache) == null){
			if(Configs.Generic.MAP_CACHE.getOptionListValue() == OptionMapStateCache.MEMORY_AND_DISK && !createInMemCacheFromFile(server, cache).isEmpty()){
				return getInMemCacheSpecific(server, cache);
			}
			switch(cache){
				case BY_ID:
					if(byId == null) byId = new HashMap<>();
					byId.put(server, new HashMap<Integer, MapStateSerializable>());
					return byId.get(server);
				case BY_NAME:
					if(byName == null) byName = new HashMap<>();
					byName.put(server, new HashMap<String, MapStateSerializable>());
					return byName.get(server);
				case BY_PLAYER_INV:
				case BY_PLAYER_EC:
				case BY_CONTAINER:
					if(bySlot == null) bySlot = new HashMap<>();
					bySlot.put(server, new HashMap<UUID, List<MapStateSerializable>>());
					final UUID key;
					switch(cache){
						case BY_PLAYER_INV: key = getIdForPlayer(true); break;
						case BY_PLAYER_EC: key = getIdForPlayer(false); break;
						case BY_CONTAINER: key = ContainerClickListener.lastClickedBlockHash; break;
						default: throw new RuntimeException("MapStateCacher: Unreachable in commonCacheLoad()!");
					}
					return bySlot.get(server).get(key);
				default:
					throw new RuntimeException("MapStateCacher: Unknown cache type in commonCacheLoad()! "+cache);
			}
		}
		return specificCache;
	}

	public static final boolean loadMapStatesByPos(List<ItemStack> items, String type){
		List<ItemStack> mapItems = InvUtils.getAllNestedItems(items.stream()).filter(s -> s.getItem() == Items.FILLED_MAP).toList();
		ClientWorld world = MinecraftClient.getInstance().world;
		if(mapItems.stream().allMatch(s -> FilledMapItem.getMapState(s, world) != null)) return false; // All states already loaded

		@SuppressWarnings("unchecked")
		List<MapStateSerializable> loadedCache = (List<MapStateSerializable>) commonCacheLoad(type);
		if(loadedCache == null) return false;
		if(mapItems.size() != loadedCache.size()){
			Main.LOGGER.warn("MapStateCacher: type="+type+", mapItems.size ("+mapItems.size()+") != cache.size ("+loadedCache.size()+"), raw items.size="+items.size());
			return false;
		}
//		Main.LOGGER.info("MapStateCacher: "+type.name()+" loading cached map states (size="+mapItems.size()+")");
		int statesLoaded = 0, statesCached = 0;
		for(int i=0; i<mapItems.size(); ++i){
			if(loadedCache.get(i) == null) continue; // Loaded state wasn't cached
			++statesCached;
			MapIdComponent mapIdComponent = mapItems.get(i).get(DataComponentTypes.MAP_ID);
			assert mapIdComponent != null : "Unable to load from cache when even the mapId is missing!";
			if(world.getMapState(mapIdComponent) != null) continue; // Already loaded
//			world.putMapState(mapIdComponent, cachedMapStates.get(i));
			world.putClientsideMapState(mapIdComponent, loadedCache.get(i).toMapState());
//			getMapRenderer().update(mapIdComponent, cachedMapStates.get(i), null);
			++statesLoaded;
		}
		Main.LOGGER.info("MapStateCacher: type="+type+",loaded="+statesLoaded+",nonnull="+statesCached+",cached="+mapItems.size()+")");
		return statesLoaded > 0;
	}
	//====================================================================================================

	public static final boolean addMapStateById(int id, MapState state){
		if(state == null || state.colors == null) return false;
		if(!state.locked && !Configs.Generic.MAP_CACHE_UNLOCKED.getBooleanValue()) return false;
		@SuppressWarnings("unchecked")
		HashMap<Integer, MapStateSerializable> cache = (HashMap<Integer, MapStateSerializable>) commonCacheLoad(BY_ID);
		assert cache != null; // commonCacheLoad creates a server-level cache if one does not already exist
//		if(cache == null){
//			if(byId == null) byId = new HashMap<>();
//			final String server = getServerIp(MinecraftClient.getInstance());
//			if(!byId.containsKey(server)) byId.put(server, cache=new HashMap<>());
//		}
		MapStateSerializable mss = MapStateSerializable.fromMapState(state);
		MapStateSerializable oldMss = cache.put(id, mss);
		if(mss.equals(oldMss)) return false; // No update
		if(oldMss != null && oldMss.locked && !MapColorUtils.sameMapExceptForMissingPixels(mss.colors, oldMss.colors)){
			Main.LOGGER.error("MapStateCacher: Different MapStates found for same ID! Unable to use cache-by-id on current server, disabling it");
			Configs.Generic.MAP_CACHE_BY_ID.setBooleanValue(false);
			return false;
		}
		idCacheUpdated = true;
		return true;
	}
	public static final boolean loadMapStatesById(){
		@SuppressWarnings("unchecked")
		HashMap<Integer, MapStateSerializable> cache = (HashMap<Integer, MapStateSerializable>) commonCacheLoad(BY_ID);
//		if(cache == null) return false; // created by commonCacheLoad

		ClientWorld world = MinecraftClient.getInstance().world;
		for(var e : cache.entrySet()) world.putClientsideMapState(new MapIdComponent(e.getKey()), e.getValue().toMapState());
		Main.LOGGER.info("MapStateCacher: type="+BY_ID+",loaded="+cache.size());
		return true;
	}
	public static final boolean saveMapStatesByIdToFile(){
		if(!idCacheUpdated) return false;
		assert Configs.Generic.MAP_CACHE.getOptionListValue() == OptionMapStateCache.MEMORY_AND_DISK;
		final String server = MiscUtils.getServerAddress();
		boolean ret = saveCacheFile(server, BY_ID); // All of the above checks are already handled by saveCacheFile()
		if(!ret) return false;
		Main.LOGGER.info("MapStateCacher: type="+BY_ID+",stored="+getInMemCachePerServer(server, BY_ID).size());
		return true;
	}
	//====================================================================================================

	public static final boolean addMapStateByName(ItemStack stack, MapState state){
		assert stack != null && stack.getItem() == Items.FILLED_MAP;
		assert state != null && state.colors != null;
		assert FilledMapItem.getMapState(stack, MinecraftClient.getInstance().world) == state;
		assert stack.getCustomName() != null;

		if(!state.locked && !Configs.Generic.MAP_CACHE_UNLOCKED.getBooleanValue()) return false;
		final String name = stack.getCustomName().getString();
		final String server = MiscUtils.getServerAddress();
		HashSet<String> unusable = unusableNames == null ? null : unusableNames.get(server);
		if(unusable != null && unusable.contains(name)) return false;

		@SuppressWarnings("unchecked")
		HashMap<String, MapStateSerializable> cache = (HashMap<String, MapStateSerializable>) commonCacheLoad(BY_NAME);
		assert cache != null; // commonCacheLoad creates a server-level cache if one does not already exist
//		if(cache == null){
//			if(byName == null) byName = new HashMap<>();
//			if(!byName.containsKey(server)) byName.put(server, cache=new HashMap<>());
//		}
		MapStateSerializable mss = MapStateSerializable.fromMapState(state);
		MapStateSerializable oldMss = cache.put(name, mss);
		if(mss.equals(oldMss)) return false; // No update
		namesCacheUpdated = true;
		if(oldMss != null && oldMss.locked){
			Main.LOGGER.warn("MapStateCacher: Different MapStates found for same name! Unable to use cache-by-name for name: "+name);
			cache.remove(name);
			if(unusableNames == null) unusableNames = new HashMap<>();
			if(unusable == null) unusableNames.put(server, unusable=new HashSet<>());
			unusable.add(name);
			return false; // Maybe should return true, since technically removal is a cache update?
		}
		return true;
	}
	public static final boolean loadMapStateByName(ItemStack stack, ClientWorld world){
		assert stack != null && stack.getItem() == Items.FILLED_MAP;
		assert world.getMapState(stack.get(DataComponentTypes.MAP_ID)) == null; // Already loaded
		assert stack.getCustomName() != null;

		final String name = stack.getCustomName().getString();

		@SuppressWarnings("unchecked")
		HashMap<String, MapStateSerializable> cache = (HashMap<String, MapStateSerializable>) commonCacheLoad(BY_NAME);
//		if(cache == null) return false; // created by commonCacheLoad
		MapStateSerializable mss = cache.get(name);
		if(mss == null) return false;

		// Already handled (when name is added to `unusableNames`, it is also removed from `cache`)
//		final String server = getServerIp(MinecraftClient.getInstance());
//		HashSet<String> unusable = unusableNames == null ? null : unusableNames.get(server);
//		if(unusable != null && unusable.contains(name)) return false;

		world.putClientsideMapState(stack.get(DataComponentTypes.MAP_ID), mss.toMapState());
		return true;
	}
	public static final boolean saveMapStatesByNameToFile(){
		if(!namesCacheUpdated) return false;
		assert Configs.Generic.MAP_CACHE.getOptionListValue() == OptionMapStateCache.MEMORY_AND_DISK;
		final String server = MiscUtils.getServerAddress();
		boolean ret = saveCacheFile(server, BY_NAME);
		if(!ret) return false;
		Main.LOGGER.info("MapStateCacher: type="+BY_NAME+",stored="+getInMemCachePerServer(server, BY_NAME).size());
		return true;
	}
}