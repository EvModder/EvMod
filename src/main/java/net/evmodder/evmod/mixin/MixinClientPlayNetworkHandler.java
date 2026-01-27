package net.evmodder.evmod.mixin;

import net.evmodder.EvLib.util.Command;
import net.evmodder.EvLib.util.FileIO;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.Main;
import net.evmodder.evmod.apis.MapGroupUtils;
import net.evmodder.evmod.apis.MapStateCacher;
import net.evmodder.evmod.apis.MiscUtils;
import net.evmodder.evmod.commands.CommandMapArtGroup;
import net.evmodder.evmod.config.OptionMapStateCache;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.item.map.MapState;
import net.minecraft.network.packet.s2c.play.MapUpdateS2CPacket;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
abstract class MixinClientPlayNetworkHandler{

	// Saw this in https://github.com/red-stoned/client_maps/, and realized it's probably good to incorporate
	@Redirect(method="onMapUpdate", at=@At(value="INVOKE",
			target="Lnet/minecraft/client/world/ClientWorld;getMapState(Lnet/minecraft/component/type/MapIdComponent;)Lnet/minecraft/item/map/MapState;"))
	private final MapState replaceIfClientMaps(ClientWorld instance, MapIdComponent id){
		final MapState s = instance.getMapState(id);
		if(Configs.Generic.MAP_CACHE.getOptionListValue() != OptionMapStateCache.MEMORY_AND_DISK) return s;
		return s == null || MapStateCacher.hasCacheMarker(s) ? null : s;
	}


	// Rest of this file: Seen map db
	private final String DIR = CommandMapArtGroup.DIR + "seen/";
	private final HashMap<String, HashSet<UUID>> mapsSaved = new HashMap<>(0);

	private final HashSet<UUID> loadMapsForServer(String address){
		final byte[] data = FileIO.loadFileBytes(DIR+address);
		if(data == null) return new HashSet<>(0);
		assert data.length % 16 == 0;
		final ByteBuffer bb = ByteBuffer.wrap(data);
		final int numIds = data.length/16;
		final HashSet<UUID> colorIds = new HashSet<>(numIds);
		for(int i=0; i<numIds; ++i) colorIds.add(new UUID(bb.getLong(), bb.getLong()));
		return colorIds;
	}

	private final void saveMapsForServer(String address, HashSet<UUID> colorIds){
		Main.LOGGER.info("MapDB: saveMapsForServer()");
		final ByteBuffer bb = ByteBuffer.allocate(colorIds.size()*16);
		for(UUID uuid : colorIds) bb.putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits());
//		colorIds.forEach(uuid -> bb.putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits()));
		File dir = new File(FileIO.DIR+DIR);
		if(!dir.exists()){
			File parent = new File(FileIO.DIR+CommandMapArtGroup.DIR);
			if(!parent.exists()) parent.mkdir();
			dir.mkdir();
		}
		FileIO.saveFileBytes(DIR+address, bb.array());
	}

	private boolean pendingFileSave = false;
	private final void scheduleSaveMapsForServer(String addr, HashSet<UUID> seenForServer){
		synchronized(mapsSaved){
			if(pendingFileSave) return;
			pendingFileSave = true;
		}
		new Timer().schedule(new TimerTask(){@Override public void run(){
			synchronized(mapsSaved){
				saveMapsForServer(addr, seenForServer);
				pendingFileSave = false;
			}
		}}, 5_000l); // 5s
	}

	@Inject(method="onMapUpdate", at=@At("TAIL"))
	private final void updateSeenMaps(MapUpdateS2CPacket packet, CallbackInfo _ci){
		final MapState state = MinecraftClient.getInstance().world.getMapState(packet.mapId());
		final int id = packet.mapId().id();
		assert !MapStateCacher.hasCacheMarker(state);
		if(Configs.Generic.MAP_CACHE_BY_ID.getBooleanValue()) MapStateCacher.addMapStateById(id, state);

		if(!Configs.Database.SAVE_MAPART.getBooleanValue()) return;
		if(!Configs.Generic.MAPART_GROUP_INCLUDE_UNLOCKED.getBooleanValue() && !state.locked) return;

		final String addr = MiscUtils.getServerAddress();
		final UUID oldColorsId = MapGroupUtils.getCachedIdForMapStateOrNull(state);
		final UUID colorsId = MapGroupUtils.getIdForMapState(state, /*evict=*/true);
		final HashSet<UUID> seenForServer;
		synchronized(mapsSaved){
			seenForServer = mapsSaved.computeIfAbsent(addr, this::loadMapsForServer);
			if(!seenForServer.add(colorsId)) return; // Already seen
			if(oldColorsId != null && !oldColorsId.equals(colorsId)){
//				Main.LOGGER.info("MapDB: MapUpdateS2CPacket packet changed state.colors for id"+id+", colordsId "+oldColorsId+" -> "+colorsId);
				seenForServer.remove(oldColorsId);
			}
		}
		scheduleSaveMapsForServer(addr, seenForServer);

		if(!Configs.Database.SHARE_MAPART.getBooleanValue() || !state.locked) return;
		AccessorMain.getInstance().remoteSender.sendBotMessage(Command.DB_MAPART_STORE, /*udp=*/false, /*timeout=*/15_000l, state.colors, reply->{
			if(reply == null){
				Main.LOGGER.info("MapDB: Null response from server");
			}
			else if(reply.length != 1){
				Main.LOGGER.warn("MapDB: Got unexpected response from server: "+new String(reply));
			}
			else{
				if(reply[0] == 0) Main.LOGGER.info("MapDB: Server already contained map"+id);
				else Main.LOGGER.info("MapDB: Server indicated map"+id+" is a new addition");
			}
		});
	}
}