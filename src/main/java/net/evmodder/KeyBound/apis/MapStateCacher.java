package net.evmodder.KeyBound.apis;

import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import net.evmodder.EvLib.util.FileIO_New;
import net.evmodder.KeyBound.Configs;
import net.evmodder.KeyBound.Main;
import net.evmodder.KeyBound.config.OptionMapStateCache;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.MapIdComponent;
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
	public enum HolderType {PLAYER_INV, ENDER_CHEST}

	private static HashMap<Integer, List<MapState>> inMemoryCache;
	public static List<ItemStack> enderChestContents;

//	public static final byte CACHED_MARKER_SCALE = (byte)128;
	private static final MapDecoration CACHED_MARKER_DECORATION
		= new MapDecoration(MapDecorationTypes.BLUE_MARKER, /*x=*/(byte)-1, /*z=*/(byte)-1, /*rot=*/(byte)-1, java.util.Optional.empty());

	public static final boolean hasCacheMarker(MapState state){
		Iterator<MapDecoration> iter = state.getDecorations().iterator();
		return iter.hasNext() && iter.next() == CACHED_MARKER_DECORATION && !iter.hasNext();
	}

	record MapStateSerializable(byte scale, boolean locked, String dimRegistry, String dimValue, byte[] colors)
	implements Serializable{
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
	}

	@SuppressWarnings("unchecked")
	public static final void readFromFile(){
		try(FileInputStream fis = new FileInputStream(FileIO_New.DIR+"cached_mapstates"); ObjectInputStream ois = new ObjectInputStream(fis)){
			HashMap<Integer, List<MapStateSerializable>> persistentMapStatesInInv = (HashMap<Integer, List<MapStateSerializable>>)ois.readObject();
			for(var entry : persistentMapStatesInInv.entrySet()){
				inMemoryCache.put(entry.getKey(), entry.getValue().stream().map(mss -> mss == null ? null : mss.toMapState()).toList());
			}
		}
		catch(EOFException e){} // Hasn't been cached yet
		catch(IOException | ClassNotFoundException e){e.printStackTrace();}
	}
	private static final void saveToFile(){
		try(FileOutputStream fos = new FileOutputStream(FileIO_New.DIR+"cached_mapstates"); ObjectOutputStream oos = new ObjectOutputStream(fos)){
			HashMap<Integer, List<MapStateSerializable>> persistentMapStatesInInv = new HashMap<>(inMemoryCache.size());
			for(var entry : inMemoryCache.entrySet()){
				persistentMapStatesInInv.put(entry.getKey(), entry.getValue().stream().map(MapStateSerializable::fromMapState).toList());
			}
			oos.writeObject(persistentMapStatesInInv);
		}
		catch(IOException e){e.printStackTrace();}
	}

	public static final boolean saveMapStates(List<ItemStack> items, HolderType type){
		MinecraftClient client = MinecraftClient.getInstance();
		final int key = client.player.getUuid().hashCode() + MiscUtils.getCurrentServerAddressHashCode() + type.ordinal();
		final List<MapState> mapStates = MapRelationUtils.getAllNestedItems(items.stream().sequential())
				.sequential()
				.filter(s -> s.getItem() == Items.FILLED_MAP).map(s -> FilledMapItem.getMapState(s, client.world)).toList();
		if(mapStates.isEmpty()){
			Main.LOGGER.info("MapStateCacher: "+type.name()+" nothing to save");
			return false;
		}
		if(inMemoryCache == null){
			inMemoryCache = new HashMap<>();
			if(Configs.Generic.MAP_STATE_CACHE.getOptionListValue() == OptionMapStateCache.MEMORY_AND_DISK) readFromFile();
		}
		inMemoryCache.put(key, mapStates);

		if(Configs.Generic.MAP_STATE_CACHE.getOptionListValue() == OptionMapStateCache.MEMORY_AND_DISK) saveToFile();
		Main.LOGGER.info("MapStateCacher: "+type.name()+" saved "+mapStates.size()+" mapstates");
		return true;
	}

	public static final boolean loadMapStates(List<ItemStack> items, HolderType type){
		if(inMemoryCache == null){
			if(Configs.Generic.MAP_STATE_CACHE.getOptionListValue() == OptionMapStateCache.MEMORY_AND_DISK){
				inMemoryCache = new HashMap<>();
				readFromFile();
			}
			else{
				Main.LOGGER.info("MapStateCacher: "+type.name()+" no saved mapstates");
				return false;
			}
		}
		MinecraftClient client = MinecraftClient.getInstance();
		final int key = client.player.getUuid().hashCode() + MiscUtils.getCurrentServerAddressHashCode() + type.ordinal();
		List<MapState> cachedMapStates = inMemoryCache.get(key);
		if(cachedMapStates == null || cachedMapStates.isEmpty()){
			Main.LOGGER.info("MapStateCacher: "+type.name()+" no saved mapstates for given key: "+key);
			return false;
		}
		List<ItemStack> mapItems = MapRelationUtils.getAllNestedItems(items.stream()).filter(s -> s.getItem() == Items.FILLED_MAP).toList();
		if(mapItems.size() != cachedMapStates.size()){
			Main.LOGGER.warn("MapStateCacher: "+type.name()+" mapItems.size:"+mapItems.size()+" != cachedMapStates.size:"+cachedMapStates.size());
			return false;
		}
//		Main.LOGGER.info("MapStateCacher: "+type.name()+" loading cached map states (size="+mapItems.size()+")");
		int statesLoaded = 0, statesCached = 0;
		for(int i=0; i<mapItems.size(); ++i){
			if(cachedMapStates.get(i) == null) continue; // Loaded state wasn't cached
			++statesCached;
			MapIdComponent mapIdComponent = mapItems.get(i).get(DataComponentTypes.MAP_ID);
			if(mapIdComponent == null){
				Main.LOGGER.warn("MapStateCacher: Item's mapIdComponent==null despite the cache indicating it is a map!");
				continue;
			}
			if(client.world.getMapState(mapIdComponent) != null) continue; // Already loaded
//			client.world.putMapState(mapIdComponent, cachedMapStates.get(i));
			client.world.putClientsideMapState(mapIdComponent, cachedMapStates.get(i));
//			client.getMapRenderer().update(mapIdComponent, cachedMapStates.get(i), null);
			++statesLoaded;
		}
		Main.LOGGER.info("MapStateCacher: "+type.name()+" loaded cached map states (loaded="+statesLoaded+",nonnull="+statesCached+",cached="+mapItems.size()+")");
		return statesLoaded > 0;
	}
}