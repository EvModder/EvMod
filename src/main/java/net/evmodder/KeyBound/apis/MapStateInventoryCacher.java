package net.evmodder.KeyBound.apis;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;
import net.evmodder.KeyBound.Main;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapState;
import net.minecraft.text.Text;

public class MapStateInventoryCacher{
	private static HashMap<Integer, List<MapState>> mapStatesInInv;

	public static final void saveMapStatesOnQuit(){
		MinecraftClient client = MinecraftClient.getInstance();
		if(mapStatesInInv == null) mapStatesInInv = new HashMap<>();
		mapStatesInInv.put(MiscUtils.getCurrentServerAddressHashCode(),
			MapRelationUtils.getAllNestedItems(
				Stream.concat(client.player.getInventory().main.stream(), client.player.getEnderChestInventory().heldStacks.stream())
			)
			.filter(s -> s.getItem() == Items.FILLED_MAP).map(s -> FilledMapItem.getMapState(s, client.world)).toList()
		);
	}

	public static final boolean loadMapStatesOnJoin(int currServerHashCode){
		if(mapStatesInInv == null) return false;
		List<MapState> cachedMapStates = mapStatesInInv.get(currServerHashCode);
		if(cachedMapStates == null || cachedMapStates.isEmpty()) return false;

		MinecraftClient client = MinecraftClient.getInstance();
		List<ItemStack> mapItems = MapRelationUtils.getAllNestedItems(
			Stream.concat(client.player.getInventory().main.stream(), client.player.getEnderChestInventory().heldStacks.stream())
		).filter(s -> s.getItem() == Items.FILLED_MAP).toList();

		if(mapItems.size() != cachedMapStates.size()){
			Main.LOGGER.warn("MapStateInventoryCacher: inventory on join does not match inventory when disconnected");
			return false;
		}
		Main.LOGGER.info("Loading cached map states (size="+mapItems.size()+")");
		int statesLoaded = 0;
		for(int i=0; i<mapItems.size(); ++i){
			if(FilledMapItem.getMapState(mapItems.get(i), client.world) != null) continue; // Already loaded
			if(cachedMapStates.get(i) == null) continue; // Loaded state wasn't cached
			MapIdComponent mapIdComponent = mapItems.get(i).get(DataComponentTypes.MAP_ID);
			client.world.putMapState(mapIdComponent, cachedMapStates.get(i));
			client.world.putClientsideMapState(mapIdComponent, cachedMapStates.get(i));
			++statesLoaded;
		}
		if(statesLoaded > 0) client.player.sendMessage(Text.literal("Loaded "+statesLoaded+" cached mapstates"), true);
		return statesLoaded > 0;
	}
}