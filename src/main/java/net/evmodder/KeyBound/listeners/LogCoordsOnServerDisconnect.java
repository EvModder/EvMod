package net.evmodder.KeyBound.listeners;

import net.evmodder.KeyBound.Main;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class LogCoordsOnServerDisconnect{
	long joinedServerTimestamp;

	public LogCoordsOnServerDisconnect(){
		ClientPlayConnectionEvents.DISCONNECT.register((_0, client)->
			Main.LOGGER.info(client.player.getName().getString()+" logged out at: "+client.player.getBlockPos().toShortString())
		);
	}
}