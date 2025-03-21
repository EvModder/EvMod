package net.evmodder.KeyBound;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;

public class SendOnServerJoin{
	SendOnServerJoin(String[] messages){
		ServerPlayConnectionEvents.JOIN.register(
				//ServerPlayNetworkHandler handler, PacketSender sender, MinecraftServer server
				(_, _, _) ->
		{
			ClientPlayNetworkHandler handler = MinecraftClient.getInstance().getNetworkHandler();
			for(String msg : messages){
				if(msg.startsWith("/")) handler.sendChatCommand(msg.substring(1));
				else handler.sendChatCommand(msg);
			}
		});
	}
}