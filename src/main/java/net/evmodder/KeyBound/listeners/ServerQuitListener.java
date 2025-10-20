package net.evmodder.KeyBound.listeners;

import net.evmodder.KeyBound.Configs;
import net.evmodder.KeyBound.Main;
import net.evmodder.KeyBound.apis.MapStateInventoryCacher;
import net.evmodder.KeyBound.apis.MiscUtils;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class ServerQuitListener{
	public ServerQuitListener(){
		//ClientLoginNetworkHandler handler/_0
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client)->{
			if(Configs.Generic.LOG_COORDS_ON_SERVER_QUIT.getBooleanValue())
				Main.LOGGER.info(client.player.getName().getString()+" logged out at: "+client.player.getBlockPos().toShortString());

			if(MiscUtils.getServerAddressHashCode(handler.getServerInfo()) != Main.HASHCODE_2B2T) return;
			if(Configs.Generic.MAP_STATE_CACHE.getBooleanValue()) MapStateInventoryCacher.saveMapStatesOnQuit();
		});
	}
}