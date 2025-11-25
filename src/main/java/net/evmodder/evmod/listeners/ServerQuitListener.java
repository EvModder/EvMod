package net.evmodder.evmod.listeners;

import net.evmodder.EvLib.util.Command;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.Main;
import net.evmodder.evmod.apis.MapStateCacher;
import net.evmodder.evmod.apis.MiscUtils;
import net.evmodder.evmod.config.OptionMapStateCache;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class ServerQuitListener{
	public ServerQuitListener(){
		//ClientLoginNetworkHandler handler/_0
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client)->{
			if(Configs.Generic.LOG_COORDS_ON_SERVER_QUIT.getBooleanValue()){
				Main.LOGGER.info(client.player.getName().getString()+" logged out at: "+client.player.getBlockPos().toShortString());
			}

			if(Configs.Database.SHARE_JOIN_QUIT.getBooleanValue() && Main.remoteSender != null){
				final String sessionName = client.getSession().getUsername(), playerName = client.player.getGameProfile().getName();
				if(!sessionName.equals(playerName)); // TODO: separate type of packet? Proxy-joined EvDoc->EvModder
				else Main.remoteSender.sendBotMessage(Command.DB_PLAYER_STORE_QUIT_TS,
						/*udp=*/true, 5000, MiscUtils.getCurrentServerAndPlayerData(), /*recv=*/null);
			}

			if(Configs.Generic.MAP_STATE_CACHE.getDefaultOptionListValue() != OptionMapStateCache.OFF){
				MapStateCacher.saveMapStates(client.player.getInventory().main, MapStateCacher.HolderType.PLAYER_INV);
				ContainerOpenCloseListener.echestCacheLoaded = false; // TODO: eww
			}
		});
	}
}