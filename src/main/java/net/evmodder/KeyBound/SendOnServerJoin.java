package net.evmodder.KeyBound;

import java.util.Timer;
import java.util.TimerTask;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.text.Text;

public class SendOnServerJoin{
	private final long JOIN_DELAY = 2000l;
	private boolean isJoining;
	private long joinedAt;

	SendOnServerJoin(String[] messages){
		ClientPlayConnectionEvents.JOIN.register(
				//ServerPlayNetworkHandler handler, PacketSender sender, MinecraftServer server
				(_0, _1, _2) ->
		{
			if(isJoining) return;
			isJoining = true;
			new Timer().schedule(new TimerTask(){@Override public void run(){
				Main.LOGGER.info("Server join detected, JOIN_DELAY is over, checking if stuff is loaded");
				MinecraftClient client  = MinecraftClient.getInstance();

				// Wait for stuff to load properly
				if(!client.isFinishedLoading() || client.player == null || client.world == null || client.getNetworkHandler() == null || !client.player.isAlive()
					|| !client.player.isLoaded() || client.player.isSpectator() || client.player.isRegionUnloaded() || client.player.isInvisible()) return;
				final long now = System.currentTimeMillis();
				if(joinedAt == 0){joinedAt = now; return;}
				else if(now - joinedAt < JOIN_DELAY) return;

				Main.LOGGER.info("Server join detected, stuff seems loaded, triggering commands...");
				ClientPlayNetworkHandler handler = client.getNetworkHandler();
				for(String msg : messages){
					msg = msg.trim();
					client.player.sendMessage(Text.of("Sending "+(msg.startsWith("/")?"cmd":"msg")+": "+msg), false);
					if(msg.startsWith("/")) handler.sendChatCommand(msg.substring(1));
					else handler.sendChatMessage(msg);
				}
				joinedAt = 0;
				isJoining = false;
				cancel();
			}}, 0l, 100l);
		});
	}
}