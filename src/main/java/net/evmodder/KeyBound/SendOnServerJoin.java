package net.evmodder.KeyBound;

import java.util.Timer;
import java.util.TimerTask;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;

public class SendOnServerJoin{
	private final long JOIN_DELAY = 2500l;
	private long loadedAt/*, joinedAt*/;
	private double loadedAtX, loadedAtZ;
	private TimerTask timerTask;
	private final boolean WAIT_FOR_MOVEMENT = true;
	public static final int HASHCODE_2B2T = -437714968;//"2b2t.org".hashCode()

	SendOnServerJoin(String[] messages){
		ClientPlayConnectionEvents.JOIN.register(
				//ServerPlayNetworkHandler handler, PacketSender sender, MinecraftServer server
				(_0, _1, _2) ->
		{
			if(_0.getServerInfo().address.hashCode() != HASHCODE_2B2T) return; // TODO: customize per-server
			if(timerTask != null) timerTask.cancel(); // Restart timer

			//joinedAt = System.currentTimeMillis();
			new Timer().schedule(timerTask = new TimerTask(){@Override public void run(){
				//final long now = System.currentTimeMillis();
				//if(now - joinedAt > GIVE_UP_AFTER_MS) cancel();

				//Main.LOGGER.info("Server join detected, checking if stuff is loaded");
				MinecraftClient client  = MinecraftClient.getInstance();
				if(!client.isFinishedLoading() || client.player == null || client.world == null || client.getNetworkHandler() == null || !client.player.isAlive()
					|| !client.player.isLoaded() || client.player.isSpectator() || client.player.isRegionUnloaded() || client.player.isInvisible()) return;
				if(loadedAt == 0){
					loadedAt = System.currentTimeMillis();
					if(WAIT_FOR_MOVEMENT){loadedAtX = client.player.getX(); loadedAtZ = client.player.getZ();}
					return;
				}

				//Main.LOGGER.info("Stuff seems loaded, waiting for player movement");
				if(WAIT_FOR_MOVEMENT){
					final double diffX = client.player.getX() - loadedAtX, diffZ = client.player.getZ() - loadedAtZ;
					if(Math.abs(diffX) <= 1d && Math.abs(diffZ) <= 1d) return; // Didn't move (enough)
					if(Math.abs(diffX) > 70d || Math.abs(diffZ) > 70d){loadedAtX += diffX; loadedAtZ += diffZ; return;} // Teleported
					//client.player.sendMessage(Text.literal(
					//	"Movement: "+loadedAtX+","+loadedAtZ+"->"+client.player.getX()+","+client.player.getZ()), false);
				}

				//Main.LOGGER.info("Player movement detected, checking JOIN_DELAY");
				if(System.currentTimeMillis() - loadedAt < JOIN_DELAY) return;

				//Main.LOGGER.info("JOIN_DELAY reached, triggering commands...");
				//client.player.sendMessage(Text.literal("Sending "+messages.length+" msgs/cmds"), false);
				ClientPlayNetworkHandler handler = client.getNetworkHandler();
				for(String msg : messages){
					msg = msg.trim();
					//client.player.sendMessage(Text.literal("Sending "+(msg.startsWith("/")?"cmd":"msg")+": "+msg), false);
					if(msg.startsWith("/")) handler.sendChatCommand(msg.substring(1));
					else handler.sendChatMessage(msg);
				}
				loadedAt = 0;
				cancel();
			}}, 0l, 100l);
		});
	}
}