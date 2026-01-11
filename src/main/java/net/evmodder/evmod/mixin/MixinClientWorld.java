package net.evmodder.evmod.mixin;

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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.evmodder.EvLib.util.Command;
import net.evmodder.EvLib.util.FileIO;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.Main;
import net.evmodder.evmod.apis.MapGroupUtils;
import net.evmodder.evmod.apis.MapStateCacher;
import net.evmodder.evmod.apis.MiscUtils;
import net.evmodder.evmod.commands.CommandMapArtGroup;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.item.map.MapState;

@Mixin(ClientWorld.class)
abstract class MixinClientWorld{
	private static final String DIR = CommandMapArtGroup.DIR + "seen/";

	private static HashMap<String, HashSet<UUID>> mapsSaved = new HashMap<>(0);

	private HashSet<UUID> loadMapsForServer(String address){
		final byte[] data = FileIO.loadFileBytes(DIR+address);
		if(data == null) return new HashSet<>(0);
		assert data.length % 16 == 0;
		final ByteBuffer bb = ByteBuffer.wrap(data);
		final int numIds = data.length/16;
		final HashSet<UUID> colorIds = new HashSet<>(numIds);
		for(int i=0; i<numIds; ++i) colorIds.add(new UUID(bb.getLong(), bb.getLong()));
		return colorIds;
	}

	private void saveMapsForServer(String address, HashSet<UUID> colorIds){
		final ByteBuffer bb = ByteBuffer.allocate(colorIds.size()*16);
		for(UUID uuid : colorIds) bb.putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits());
		File dir = new File(FileIO.DIR+DIR);
		if(!dir.exists()){
			File parent = new File(FileIO.DIR+CommandMapArtGroup.DIR);
			if(!parent.exists()) parent.mkdir();
			dir.mkdir();
		}
		FileIO.saveFileBytes(DIR+address, bb.array());
	}

	private static boolean pendingFileSave = false;
	private void scheduleSaveMapsForServer(String addr, HashSet<UUID> seenForServer){
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

	@Inject(method="putClientsideMapState", at=@At("HEAD"))
	private void e(MapIdComponent id, MapState state, CallbackInfo ci){
		if(MapStateCacher.hasCacheMarker(state)) return;
		if(Configs.Generic.MAP_CACHE_BY_ID.getBooleanValue()) MapStateCacher.addMapStateById(id.id(), state);

		if(!Configs.Database.SAVE_MAPART.getBooleanValue()) return;
		if(!state.locked) return; // Supporting unlocked maps would waste a LOT of disk with constant 1-pixel changes while building

		final String addr = MiscUtils.getServerAddress();
		final UUID colorsId = MapGroupUtils.getIdForMapState(state);
		final HashSet<UUID> seenForServer;
		synchronized(mapsSaved){
			seenForServer = mapsSaved.computeIfAbsent(addr, this::loadMapsForServer);
			if(!seenForServer.add(colorsId)) return; // Already seen
		}
		scheduleSaveMapsForServer(addr, seenForServer);

		if(!Configs.Database.SHARE_MAPART.getBooleanValue()) return;
		Main.mixinAccess().remoteSender.sendBotMessage(Command.DB_MAPART_STORE, /*udp=*/false, /*timeout=*/15_000l, state.colors, reply->{
			if(reply == null){
				Main.LOGGER.info("MapDB: Null response from server");
			}
			else if(reply.length != 1){
				Main.LOGGER.warn("MapDB: Got unexpected response from server: "+new String(reply));
			}
			else{
				if(reply[0] == 0) Main.LOGGER.info("MapDB: Server already contained map"+id.id());
				else Main.LOGGER.info("MapDB: Server indicated map"+id.id()+" is a new addition");
			}
		});
	}
}