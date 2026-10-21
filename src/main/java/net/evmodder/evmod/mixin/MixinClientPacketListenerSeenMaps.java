package net.evmodder.evmod.mixin;

import net.evmodder.EvLib.util.Command;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.Main;
import net.evmodder.evmod.apis.MapGroupUtils;
import net.evmodder.evmod.apis.MiscUtils;
import net.evmodder.evmod.config.OptionUnlockedMapHandling;
import net.evmodder.evmod.commands.CommandMapArtGroup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
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

@Mixin(ClientPacketListener.class)
abstract class MixinClientPacketListenerSeenMaps{

	// Rest of this file: Seen map db
	private final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
	private final String DIR = "seen/";
	private final HashMap<String, HashSet<UUID>> mapsSaved = new HashMap<>(0);

	private final HashSet<UUID> loadMapsForServer(String address){
		final byte[] data = CommandMapArtGroup.loadGroupFile(DIR+address);
		if(data == null) return new HashSet<>(0);
		assert data.length % 16 == 0;
		final ByteBuffer bb = ByteBuffer.wrap(data);
		final int numIds = data.length/16;
		final HashSet<UUID> colorIds = new HashSet<>(numIds);
		for(int i=0; i<numIds; ++i) colorIds.add(new UUID(bb.getLong(), bb.getLong()));
		return colorIds;
	}

	private final boolean saveMapsForServer(String address, HashSet<UUID> colorIds){
		Main.LOGGER.info("MapDB: saveMapsForServer()");
		final ByteBuffer bb = ByteBuffer.allocate(colorIds.size()*16);
		for(UUID uuid : colorIds) bb.putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits());
//		colorIds.forEach(uuid -> bb.putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits()));
		return CommandMapArtGroup.saveGroupFile(DIR+address, bb.array());
	}

	private boolean pendingFileSave = false;
	private final void scheduleSaveMapsForServer(String addr, HashSet<UUID> seenForServer){
		synchronized(mapsSaved){
			if(pendingFileSave) return;
			pendingFileSave = true;
		}
		final Timer timer = new Timer();
		timer.schedule(new TimerTask(){@Override public void run(){
			synchronized(mapsSaved){
				try{saveMapsForServer(addr, seenForServer);}
				finally{pendingFileSave = false; timer.cancel();}
			}
		}}, 5_000l);  // 5s
	}

	@Inject(method="handleMapItemData", at=@At("TAIL"), order=1100)
	private final void updateSeenMaps(final ClientboundMapItemDataPacket packet, final CallbackInfo _ci){
		if(!Configs.Database.SAVE_MAPART.getBooleanValue()) return;
		final MapItemSavedData state = Minecraft.getInstance().level.getMapData(packet.mapId());
		assert state != null && state.colors.length == 128*128;
		final int id = packet.mapId().id();

		if(!state.locked && Configs.Generic.MAPART_GROUP_UNLOCKED_HANDLING.getOptionListValue() == OptionUnlockedMapHandling.SKIP) return;

		final String address = MiscUtils.getServerAddress();
		final String filename = IS_WINDOWS ? address.replace(':', '#') : address;
		final UUID oldColorsId = MapGroupUtils.getCachedIdForMapStateOrNull(state);
		final UUID colorsId = MapGroupUtils.getIdForMapState(state, /*evict=*/true);
		final HashSet<UUID> seenForServer;
		synchronized(mapsSaved){
			seenForServer = mapsSaved.computeIfAbsent(filename, this::loadMapsForServer);
			if(!seenForServer.add(colorsId)) return;  // Already seen
			if(oldColorsId != null && !oldColorsId.equals(colorsId)){
//				Main.LOGGER.info("MapDB: MapUpdateS2CPacket packet changed state.colors for id"+id+", colordsId "+oldColorsId+" -> "+colorsId);
				seenForServer.remove(oldColorsId);
			}
		}
		scheduleSaveMapsForServer(filename, seenForServer);

		if(!Configs.Database.SHARE_MAPART.getBooleanValue() || !state.locked) return;
		AccessorMain.getRemoteSender().sendBotMessage(Command.DB_MAPART_STORE, /*udp=*/false, /*timeout=*/15_000l, state.colors, reply->{
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