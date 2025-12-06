package net.evmodder.evmod.mixin;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.stream.Collectors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.evmodder.EvLib.util.Command;
import net.evmodder.EvLib.util.FileIO_New;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.Main;
import net.evmodder.evmod.apis.MapStateCacher;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.item.map.MapState;

@Mixin(ClientWorld.class)
abstract class MixinClientWorld{
	private static final MinecraftClient client = MinecraftClient.getInstance();
	private static final String DB_FILENAME = "seen_maps";
	private static final long MAPART_STORE_TIMEOUT = 15*1000;
	//private static final boolean preloadMapStates = true; // requires saving full byte[] 128x128 and not just the hash (UUID)

	private static HashMap<String, HashSet<UUID>> mapsSaved;
	private static HashSet<UUID> mapsInTransit, mapsToSave;
	private static String saveAddr;

	private static void initMapDb(){
		mapsSaved = new HashMap<>();
		mapsInTransit = new HashSet<>();
		mapsToSave = new HashSet<>();
		final String data = FileIO_New.loadFile(DB_FILENAME, (String)null);
		if(data != null) Arrays.stream(data.split("\\r?\\n")).forEach(s -> {
			String[] parts = s.split(":");
			if(parts.length != 2) return;
			String[] uuidStrs = parts[1].split(",");
			HashSet<UUID> uuids = new HashSet<>();
			Arrays.stream(uuidStrs).map(UUID::fromString).forEach(uuids::add);
			mapsSaved.put(parts[0], uuids);
		});
	}

	@Inject(method="putClientsideMapState", at=@At("HEAD"))
	private void e(MapIdComponent id, MapState state, CallbackInfo ci){
		if(MapStateCacher.hasCacheMarker(state)) return;
		if(Configs.Generic.MAP_CACHE_BY_ID.getBooleanValue()) MapStateCacher.saveMapStateById(id.id(), state);
//		if(Configs.Generic.MAP_CACHE_BY_NAME.getBooleanValue()) MapStateCacher.saveMapStateByName(name, state);

		if(!Configs.Database.SHARE_MAPART.getBooleanValue() && Main.remoteSender == null) return;
		if(!state.locked) return; // TODO: supporting unlocked maps could waste a LOT of disk with 1-pixel changes while building
		final String addr = client != null && client.getCurrentServerEntry() != null ? client.getCurrentServerEntry().address : null;
		if(addr == null) return;
		if(saveAddr != null && !saveAddr.equals(addr)) return;
		if(mapsSaved == null) initMapDb();
		final UUID uuid = UUID.nameUUIDFromBytes(state.colors);
		synchronized(mapsSaved){
			HashSet<UUID> savedPerServer = mapsSaved.get(addr);
			if(savedPerServer != null && savedPerServer.contains(uuid)) return; // Map has already been sent to the remoteDB
			if(mapsToSave.contains(uuid)) return; // Map was sent, got reply, but not yet added to `mapsSaved`
			if(!mapsInTransit.add(uuid)) return; // Map is currently being sent to the remoteDB (awaiting reply)
		}
		saveAddr = addr;

		Main.remoteSender.sendBotMessage(Command.DB_MAPART_STORE, /*udp=*/false, MAPART_STORE_TIMEOUT, state.colors, msg->{
			synchronized(mapsSaved){
				mapsInTransit.remove(uuid);
				if(msg == null){
					Main.LOGGER.info("MapDB: No response from server");
					if(mapsInTransit.isEmpty() && mapsToSave.isEmpty()) saveAddr = null;
					return;
				}
				if(msg.length != 1){
					Main.LOGGER.warn("MapDB: Got unexpected response from server: "+new String(msg));
					if(mapsInTransit.isEmpty() && mapsToSave.isEmpty()) saveAddr = null;
					return;
				}
				else{
					if(msg[0] == 0) Main.LOGGER.info("MapDB: Server already contained map"+id.id());
					else Main.LOGGER.info("MapDB: Server indicated map"+id.id()+" is a new addition");
				}

				if(!mapsToSave.add(uuid) || mapsToSave.size() > 1) return;
			}
			// Only reached if mapsToSave.size()==1
			new Timer().schedule(new TimerTask(){@Override public void run(){
				synchronized(mapsSaved){
					if(!mapsSaved.containsKey(saveAddr)) mapsSaved.put(saveAddr, new HashSet<UUID>());
					mapsSaved.get(saveAddr).addAll(mapsToSave);
					final String str = mapsSaved.entrySet().stream()
							.map(e -> e.getKey()+":"+e.getValue().stream()
							.map(UUID::toString).collect(Collectors.joining(",")))
							.collect(Collectors.joining("\n"));
					if(!FileIO_New.saveFile(DB_FILENAME, str)){
						mapsSaved.get(saveAddr).removeAll(mapsToSave);
						Main.LOGGER.info("MapDB: Failed to save to maps file!");
					}
					else Main.LOGGER.info("MapDB: Added "+(mapsToSave.size() == 1 ? "a map" : mapsToSave.size()+" maps")+" to the DB!");
					mapsToSave.clear();
					saveAddr = null;
				}
			}}, 5_000l); // Wait 5s
		});
		//if(MapHandRestock.isEnabled) MapHandRestock.onProcessRightClickPre(player, hand);
	}
}