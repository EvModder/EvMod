package net.evmodder.KeyBound;

import java.util.Timer;
import java.util.TimerTask;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;

public class SendOnServerJoin{
	private final long JOIN_DELAY = 2000l;
	private long loadedAt/*, joinedAt*/;
	private TimerTask timerTask;

	SendOnServerJoin(String[] messages){
		ClientPlayConnectionEvents.JOIN.register(
				//ServerPlayNetworkHandler handler, PacketSender sender, MinecraftServer server
				(_0, _1, _2) ->
		{
			if(timerTask != null) timerTask.cancel(); // Restart timer

			//joinedAt = System.currentTimeMillis();
			new Timer().schedule(timerTask = new TimerTask(){@Override public void run(){
				//final long now = System.currentTimeMillis();
				//if(now - joinedAt > GIVE_UP_AFTER_MS) cancel();

				//Main.LOGGER.info("Server join detected, checking if stuff is loaded");
				MinecraftClient client  = MinecraftClient.getInstance();
				if(!client.isFinishedLoading() || client.player == null || client.world == null || client.getNetworkHandler() == null || !client.player.isAlive()
					|| !client.player.isLoaded() || client.player.isSpectator() || client.player.isRegionUnloaded() || client.player.isInvisible()) return;

				//Main.LOGGER.info("Stuff seems loaded, checking JOIN_DELAY");
				final long now = System.currentTimeMillis();
				if(loadedAt == 0){loadedAt = now; return;}
				else if(now - loadedAt < JOIN_DELAY) return;

				//Main.LOGGER.info("JOIN_DELAY reached, triggering commands...");
				ClientPlayNetworkHandler handler = client.getNetworkHandler();
				for(String msg : messages){
					msg = msg.trim();
					//client.player.sendMessage(Text.of("Sending "+(msg.startsWith("/")?"cmd":"msg")+": "+msg), false);
					if(msg.startsWith("/")) handler.sendChatCommand(msg.substring(1));
					else handler.sendChatMessage(msg);
				}
				loadedAt = 0;
				cancel();
			}}, 0l, 100l);
		});
	}
}