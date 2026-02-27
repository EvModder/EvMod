package net.evmodder.evmod.listeners;

import net.evmodder.EvLib.util.Command;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.Main;
import net.evmodder.evmod.apis.MapStateCacher;
import net.evmodder.evmod.apis.MiscUtils;
import net.evmodder.evmod.apis.RemoteServerSender;
import net.evmodder.evmod.config.OptionMapStateCache;
import net.evmodder.evmod.onTick.ContainerOpenCloseListener;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class ServerQuitListener{
	public ServerQuitListener(RemoteServerSender rms){
		//ClientLoginNetworkHandler handler/_0
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client)->{
			if(Configs.Generic.LOG_COORDS_ON_SERVER_QUIT.getBooleanValue() && client.player != null){
				Main.LOGGER.info(client.player.getName().getString()+" logged out at: "+client.player.getBlockPos().toShortString());
			}

			if(Configs.Database.SHARE_JOIN_QUIT.getBooleanValue() && rms != null){
				rms.sendBotMessage(Command.DB_PLAYER_STORE_QUIT_TS, /*udp=*/true, 5000, MiscUtils.getEncodedPlayerIds(client), /*recv=*/null);
			}

			if(Configs.Generic.MAP_CACHE.getOptionListValue() != OptionMapStateCache.OFF){
				if(Configs.Generic.MAP_CACHE.getOptionListValue() == OptionMapStateCache.MEMORY_AND_DISK){
					if(Configs.Generic.MAP_CACHE_BY_ID.getBooleanValue()) MapStateCacher.saveMapStatesByIdToFile();
					if(Configs.Generic.MAP_CACHE_BY_NAME.getBooleanValue()) MapStateCacher.saveMapStatesByNameToFile();
				}
				MapStateCacher.saveMapStatesByPos(client.player.getInventory().main.stream(), MapStateCacher.BY_PLAYER_INV);
				ContainerOpenCloseListener.echestCacheLoaded = false; // TODO: ewww
				ContainerOpenCloseListener.containerCachesLoaded.clear(); // TODO: ewww
			}
		});
	}
}