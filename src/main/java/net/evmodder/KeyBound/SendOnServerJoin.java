package net.evmodder.KeyBound;

import java.util.Timer;
import java.util.TimerTask;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;

public class SendOnServerJoin{
	SendOnServerJoin(String[] messages){
		Main.LOGGER.info("waiting for world to load...");
		ServerPlayConnectionEvents.JOIN.register(
				//ServerPlayNetworkHandler handler, PacketSender sender, MinecraftServer server
				(_, _, _) ->
		{
			new Timer().schedule(new TimerTask(){@Override public void run(){
				MinecraftClient client  = MinecraftClient.getInstance();
				if(client.player == null || client.world == null || client.getNetworkHandler() == null
						|| client.player.isSpectator()) return; // Wait for stuff to load properly

				ClientPlayNetworkHandler handler = client.getNetworkHandler();
				for(String msg : messages){
					if(msg.startsWith("/")) handler.sendChatCommand(msg.substring(1));
					else handler.sendChatCommand(msg);
				}
				cancel();
			}}, 1000l, 1000l);
		});
	}
}