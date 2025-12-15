package net.evmodder.evmod.apis;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.evmodder.EvLib.util.FileIO_New;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.Main;
import net.evmodder.evmod.config.OptionMapStateCache;
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
	private static HashMap<String, HashMap<UUID, List<MapStateSerializable>>> bySlot;

//	public static abstract class Cache{
//		final String filename;
//		private Cache(String n){filename=n;}
//	}
//	private static final class CacheById extends Cache{CacheById(){super("ids");}}
//	private static final class CacheByName extends Cache{CacheByName(){super("names");}}
//	private static final class CacheBySlot extends Cache{UUID uuid; CacheBySlot(UUID u){super("containers"); uuid = u;}}
//
//	public static final RegistryKey<Registry<Cache>> REGISTRY_KEY = RegistryKey.ofRegistry(Identifier.of(Main.MOD_ID, "map_state_cache"));
//	DefaultedRegistry<Cache> registry = FabricRegistryBuilder.createDefaulted(
//			REGISTRY_KEY, Identifier.of(Main.MOD_ID, "cache_by_slot")
//			).buildAndRegister();
//
//	public static final CacheById BY_ID = new CacheById();
//	public static final CacheByName BY_NAME = new CacheByName();
//	public static final CacheBySlot BY_PLAYER_INV = new CacheBySlot(new UUID(445878968, -696921926));
//	public static final CacheBySlot BY_PLAYER_EC = new CacheBySlot(new UUID(744859711, -1296040053));
	public static enum Cache{
		BY_ID("ids", null), BY_NAME("names", null),
		BY_PLAYER_INV("containers", new UUID(445878968, -696921926)),
		BY_PLAYER_EC("containers", new UUID(744859711, -1296040053));
//		BY_SLOT("containers", ...);
		String filename;
		private UUID uuid;
		Cache(String n, UUID u){filename=n; uuid=u;}

		private final UUID getContainerId(){
			switch(this){
				case BY_PLAYER_INV: return getPlayerInvKey(uuid);
				case BY_PLAYER_EC: return getPlayerInvKey(uuid);
				default: return uuid;
			}
		}
	}

	private static final UUID getPlayerInvKey(UUID cacheKey){
		Entity e = MinecraftClient.getInstance().player;
		if(e == null) return cacheKey;
		ByteBuffer bb = ByteBuffer.wrap(new byte[32]);
		bb.putLong(e.getUuid().getMostSignificantBits());
		bb.putLong(e.getUuid().getLeastSignificantBits());
		bb.putLong(cacheKey.getMostSignificantBits());
		bb.putLong(cacheKey.getLeastSignificantBits());
		return UUID.nameUUIDFromBytes(bb.array());
	}

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

	private static final String getServerIp(MinecraftClient client){
		if(client == null) return "null0";
		if(client.getCurrentServerEntry() == null){
			return client.getServer() == null ? "null1" : client.getServer().getSaveProperties().getLevelName();
		}
		return client.getCurrentServerEntry().address;
	}

	private static final HashMap<?, ?> getInMemCachePerServer(String server, Cache type){
		switch(type){
			case BY_ID: return byId == null ? null : byId.get(server);
			case BY_NAME: return byName == null ? null : byName.get(server);
//			case BY_PLAYER_INV: return bySlot == null ? null : bySlot.get(server);
//			case BY_PLAYER_EC: return bySlot == null ? null : bySlot.get(server);
			default:
//				if(type instanceof CacheBySlot == false) Main.LOGGER.error("MapStateCacher: Unknown cache type!");
//				assert type instanceof CacheBySlot;
				return bySlot == null ? null : bySlot.get(server);
		}
	}

	private static final Object getInMemCacheSpecific(String server, Cache type){
		switch(type){
			case BY_ID: return byId == null ? null : byId.get(server); // Same as getInMemCacheServer()
			case BY_NAME: return byName == null ? null : byName.get(server); // Same as getInMemCacheServer()
//			case BY_PLAYER_INV: return bySlot == null ? null : bySlot.get(server).get(getPlayerInvKey(Cache.BY_PLAYER_INV.uuid));
//			case BY_PLAYER_EC: return bySlot == null ? null : bySlot.get(server).get(getPlayerInvKey(Cache.BY_PLAYER_EC.uuid));
			default:
//				if(type instanceof CacheBySlot == false) Main.LOGGER.error("MapStateCacher: Unknown cache type!");
//				assert type instanceof CacheBySlot;
//				return bySlot == null ? null : bySlot.get(server).get(getSlotKey((CacheBySlot)type));
				return bySlot == null ? null : bySlot.get(server).get(type.getContainerId());
		}
	}

	private static final Object readFile(String filename){
		try(FileInputStream fis = new FileInputStream(filename); ObjectInputStream ois = new ObjectInputStream(fis)){
			return ois.readObject();
		}
		catch(FileNotFoundException e){return null;}
		catch(EOFException e){} // Hasn't been cached yet
		catch(IOException | ClassNotFoundException e){e.printStackTrace();}
		return null;
	}
	private static final void writeFile(String filename, Object obj){
		try(FileOutputStream fos = new FileOutputStream(filename); ObjectOutputStream oos = new ObjectOutputStream(fos)){
			oos.writeObject(obj);
		}
		catch(IOException e){e.printStackTrace();}
	}
	private static final boolean saveCacheFile(String server, Cache type){
		if(getInMemCacheSpecific(server, type) == null) return false;
		String filename = FileIO_New.DIR+"map_cache/"+server+"/"+type.filename+".cache";
		HashMap<?, ?> perServerCache = getInMemCachePerServer(server, type);

		File dir = new File(FileIO_New.DIR+"map_cache/");
		if(!dir.isDirectory()){Main.LOGGER.info("MapStateCacher: Creating dir '"+dir.getName()+"'"); dir.mkdir();}
		dir = new File(FileIO_New.DIR+"map_cache/"+server);
		if(!dir.isDirectory()){Main.LOGGER.info("MapStateCacher: Creating dir '"+dir.getName()+"'"); dir.mkdir();}
		writeFile(filename, perServerCache);
		return true;
	}
	@SuppressWarnings("unchecked")
	private static final boolean loadCacheFile(String server, Cache type){
//		if(getInMemCacheSpecific(client, type) != null) return false;
		String filename = FileIO_New.DIR+"map_cache/"+server+"/"+type.filename+".cache";
		HashMap<?, ?> cache = (HashMap<?, ?>)readFile(filename);
		if(cache == null) return false;
//		HashMap<?, ?> oldCache = getInMemCacheServer(client, type);
//		if(oldCache != null && oldCache.equals(cache)) return false; // Shouldn't happen
		switch(type){
			case BY_ID:
				if(byId == null) byId = new HashMap<>();
				byId.put(server, (HashMap<Integer, MapStateSerializable>)cache);
				return true;
			case BY_NAME:
				if(byName == null) byName = new HashMap<>();
				byName.put(server, (HashMap<String, MapStateSerializable>)cache);
				return true;
//			case Cache.BY_PLAYER_INV:
//				containerId = getPlayerInvKey(Cache.BY_PLAYER_INV.uuid);
//				break;
//			case Cache.BY_PLAYER_EC:
//				containerId = getPlayerInvKey(Cache.BY_PLAYER_EC.uuid);
//				break;
			default:
//				if(type instanceof CacheBySlot == false) Main.LOGGER.error("MapStateCacher: Unknown cache type!");
				UUID containerId = type.getContainerId();
				List<MapStateSerializable> containerCache = (List<MapStateSerializable>)cache.get(containerId);
				if(containerCache == null) return false;

				if(bySlot == null) bySlot = new HashMap<>();
				HashMap<UUID, List<MapStateSerializable>> bySlotSub = bySlot.get(server);
				if(bySlotSub == null){
					bySlot.put(server, (HashMap<UUID, List<MapStateSerializable>>)cache);
					return true;
				}
//				List<MapStateSerializable> oldSlotCache = bySlotSub.get(containerId);
//				if(oldSlotCache != null && oldSlotCache.equals(containerCache)) return false; // Shouldn't happen
				bySlotSub.put(containerId, (List<MapStateSerializable>)cache.get(containerId));
				return true;
		}
	}

	private static final boolean keepOldCache(List<MapStateSerializable> oldCache, List<MapStateSerializable> newCache){
		if(newCache.size() != oldCache.size()) return false; // Potentially different maps!
		for(int i=0; i<oldCache.size(); ++i){
			if(newCache.get(i) != null && (oldCache.get(i) == null || !oldCache.get(i).equals(newCache.get(i)))) return false; // Different maps!
			if(oldCache.get(i) != null && newCache.get(i) == null){
				Main.LOGGER.error("MapStateCacher: Cached mapstate index "+i+" never got loaded (?!)");
			}
		}
		return true;
	}
	public static final boolean saveMapStatesByPos(List<ItemStack> items, Cache type){
		MinecraftClient client = MinecraftClient.getInstance();
		final List<MapStateSerializable> mapStates = MapRelationUtils.getAllNestedItems(items.stream().sequential())
				.sequential()
				.filter(s -> s.getItem() == Items.FILLED_MAP)
				.map(s -> FilledMapItem.getMapState(s, client.world))
				.map(MapStateSerializable::fromMapState).toList();
		if(mapStates.isEmpty() || mapStates.stream().allMatch(Objects::isNull)){
			Main.LOGGER.info("MapStateCacher: "+type.filename+" has nothing to save");
			return false;
		}
		// Load old cache values (actually loads all slot-based container caches for the current server)
		final String server = getServerIp(client);
		@SuppressWarnings("unchecked")
		final List<MapStateSerializable> oldCache = (List<MapStateSerializable>)getInMemCacheSpecific(server, type);
		if(oldCache == null){
			if(Configs.Generic.MAP_CACHE.getOptionListValue() == OptionMapStateCache.MEMORY_AND_DISK) loadCacheFile(server, type);
			if(bySlot == null) bySlot = new HashMap<>();
			if(!bySlot.containsKey(server)) bySlot.put(server, new HashMap<>());
		}
		else if(keepOldCache(oldCache, mapStates)) return false; // No cache update needed
		bySlot.get(server).put(type.getContainerId(), mapStates);
		if(Configs.Generic.MAP_CACHE.getOptionListValue() == OptionMapStateCache.MEMORY_AND_DISK) saveCacheFile(server, type);

		Main.LOGGER.info("MapStateCacher: "+type.name()+" saved "+mapStates.size()+" mapstates");
		return true;
	}

	private static final Object commonCacheLoad(Cache type){
		MinecraftClient client = MinecraftClient.getInstance();
		if(client == null || client.player == null || client.world == null) return null;
		final String server = getServerIp(client);
		Object cache = getInMemCacheSpecific(server, type);
		if(cache == null){
			if(Configs.Generic.MAP_CACHE.getOptionListValue() == OptionMapStateCache.MEMORY_AND_DISK){
				if(loadCacheFile(server, type)) cache = getInMemCacheSpecific(server, type);
			}
			if(cache == null){
				Main.LOGGER.info("MapStateCacher: no mapstates found for the target cache type: "+type);
			}
		}
		return cache;
	}

	public static final boolean loadMapStatesByPos(List<ItemStack> items, Cache type){
		@SuppressWarnings("unchecked")
		List<MapStateSerializable> cache = (List<MapStateSerializable>) commonCacheLoad(type);
		if(cache == null) return false;
		List<ItemStack> mapItems = MapRelationUtils.getAllNestedItems(items.stream()).filter(s -> s.getItem() == Items.FILLED_MAP).toList();
		if(mapItems.size() != cache.size()){
			Main.LOGGER.warn("MapStateCacher: "+type.name()+" mapItems.size:"+mapItems.size()+" != cache.size:"+cache.size());
			return false;
		}
//		Main.LOGGER.info("MapStateCacher: "+type.name()+" loading cached map states (size="+mapItems.size()+")");
		int statesLoaded = 0, statesCached = 0;
		ClientWorld world = MinecraftClient.getInstance().world;
		for(int i=0; i<mapItems.size(); ++i){
			if(cache.get(i) == null) continue; // Loaded state wasn't cached
			++statesCached;
			MapIdComponent mapIdComponent = mapItems.get(i).get(DataComponentTypes.MAP_ID);
			assert mapIdComponent != null : "Unable to load from cache when even the mapId is missing!";
			if(world.getMapState(mapIdComponent) != null) continue; // Already loaded
//			world.putMapState(mapIdComponent, cachedMapStates.get(i));
			world.putClientsideMapState(mapIdComponent, cache.get(i).toMapState());
//			getMapRenderer().update(mapIdComponent, cachedMapStates.get(i), null);
			++statesLoaded;
		}
		Main.LOGGER.info("MapStateCacher: "+type.name()+" loaded cached map states (loaded="+statesLoaded+",nonnull="+statesCached+",cached="+mapItems.size()+")");
		return statesLoaded > 0;
	}

	/*private static final boolean addMapState(Object key, MapState state, HashMap<String, HashMap<? extends Object, MapStateSerializable>> parent){
		if(state == null || state.colors == null) return false;
		@SuppressWarnings("unchecked")
		HashMap<? extends Object, MapStateSerializable> cache = (HashMap<? extends Object, MapStateSerializable>) commonCacheLoad(Cache.BY_ID);
		if(cache == null){
			if(parent == null) parent = new HashMap<>();
			final String server = getServerIp(MinecraftClient.getInstance());
			if(!parent.containsKey(server)) parent.put(server, cache=new HashMap<>());
		}
		MapStateSerializable mss = MapStateSerializable.fromMapState(state);
		MapStateSerializable oldMss = cache.put(key, mss);
		if(mss.equals(oldMss)) return false; // No update
		if(oldMss != null && oldMss.locked){
			// error
		}
		return true;
	}*/
	public static final boolean addMapStateById(int id, MapState state){
		if(state == null || state.colors == null) return false;
		@SuppressWarnings("unchecked")
		HashMap<Integer, MapStateSerializable> cache = (HashMap<Integer, MapStateSerializable>) commonCacheLoad(Cache.BY_ID);
		if(cache == null){
			if(byId == null) byId = new HashMap<>();
			final String server = getServerIp(MinecraftClient.getInstance());
			if(!byId.containsKey(server)) byId.put(server, cache=new HashMap<>());
		}
		MapStateSerializable mss = MapStateSerializable.fromMapState(state);
		MapStateSerializable oldMss = cache.put(id, mss);
		if(mss.equals(oldMss)) return false; // No update
		if(oldMss != null && oldMss.locked){
			Main.LOGGER.error("MapStateCacher: Different MapStates found for same ID! Unable to use cache-by-id on current server, disabling it");
			Configs.Generic.MAP_CACHE_BY_ID.setBooleanValue(false);
			return false;
		}
		return true;
	}
	public static final boolean loadMapStatesById(){
		@SuppressWarnings("unchecked")
		HashMap<Integer, MapStateSerializable> cache = (HashMap<Integer, MapStateSerializable>) commonCacheLoad(Cache.BY_ID);
		if(cache == null) return false;

//		Main.LOGGER.info("MapStateCacher: "+type.name()+" loading cached map states (size="+mapItems.size()+")");
		ClientWorld world = MinecraftClient.getInstance().world;
		for(var e : cache.entrySet()) world.putClientsideMapState(new MapIdComponent(e.getKey()), e.getValue().toMapState());
		Main.LOGGER.info("MapStateCacher: loaded "+cache.size()+" maps cached by id");
		return true;
	}
	public static final boolean saveMapStatesByIdToFile(){
		assert Configs.Generic.MAP_CACHE.getOptionListValue() == OptionMapStateCache.MEMORY_AND_DISK;
//		if(byId == null) return false;
		final String server = getServerIp(MinecraftClient.getInstance());
//		HashMap<Integer, MapStateSerializable> cache = byId.get(server);
//		if(cache == null/* || cache.isEmpty()*/) return false;
		boolean ret = saveCacheFile(server, Cache.BY_ID); // All of the above checks are already handled by saveCacheFile()
		if(!ret) return false;
		Main.LOGGER.info("MapStateCacher: saved "+getInMemCachePerServer(server, Cache.BY_ID).size()+" by id");
		return true;
	}

	public static final boolean addMapStateByName(ItemStack stack, MapState state){
//		if(state == null || state.colors == null) return false;
		assert stack != null && stack.getItem() == Items.FILLED_MAP;
		assert state != null && state.colors != null;
		assert FilledMapItem.getMapState(stack, MinecraftClient.getInstance().world) == state;
//		final String name = stack.getCustomName() == null ? null : stack.getCustomName().getLiteralString();
		final String name = stack.getCustomName().getLiteralString();
		assert name != null;
		final String server = getServerIp(MinecraftClient.getInstance());
		HashSet<String> unusable = unusableNames == null ? null : unusableNames.get(server);
		if(unusable != null && unusable.contains(name)) return false;

		@SuppressWarnings("unchecked")
		HashMap<String, MapStateSerializable> cache = (HashMap<String, MapStateSerializable>) commonCacheLoad(Cache.BY_NAME);
		if(cache == null){
			if(byName == null) byName = new HashMap<>();
			if(!byName.containsKey(server)) byName.put(server, cache=new HashMap<>());
		}
		MapStateSerializable mss = MapStateSerializable.fromMapState(state);
		MapStateSerializable oldMss = cache.put(name, mss);
		if(mss.equals(oldMss)) return false; // No update
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
//		if(stack == null || stack.getItem() != Items.FILLED_MAP) return false;
		assert stack != null && stack.getItem() == Items.FILLED_MAP;
//		MapIdComponent mapIdComponent = stack.get(DataComponentTypes.MAP_ID);
//		if(world.getMapState(mapIdComponent) != null) return false; // Already loaded
		assert world.getMapState(stack.get(DataComponentTypes.MAP_ID)) == null; // Already loaded

		final String name = stack.getCustomName().getLiteralString();
		assert name != null;
//		if(name == null) return false;

		@SuppressWarnings("unchecked")
		HashMap<String, MapStateSerializable> cache = (HashMap<String, MapStateSerializable>) commonCacheLoad(Cache.BY_NAME);
		if(cache == null) return false;

		final String server = getServerIp(MinecraftClient.getInstance());
		HashSet<String> unusable = unusableNames == null ? null : unusableNames.get(server);
		if(unusable != null && unusable.contains(name)) return false;

		MapStateSerializable mss = cache.get(name);
		if(mss == null) return false;
		world.putClientsideMapState(stack.get(DataComponentTypes.MAP_ID), mss.toMapState());
		return true;
	}
	public static final boolean saveMapStatesByNameToFile(){
		assert Configs.Generic.MAP_CACHE.getOptionListValue() == OptionMapStateCache.MEMORY_AND_DISK;
		final String server = getServerIp(MinecraftClient.getInstance());
		boolean ret = saveCacheFile(server, Cache.BY_NAME);
		if(!ret) return false;
		Main.LOGGER.info("MapStateCacher: saved "+getInMemCachePerServer(server, Cache.BY_NAME).size()+" by name");
		return true;
	}
}