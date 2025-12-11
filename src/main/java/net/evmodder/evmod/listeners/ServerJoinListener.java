package net.evmodder.evmod.listeners;

import java.util.Timer;
import java.util.TimerTask;
import net.evmodder.EvLib.util.Command;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.Main;
import net.evmodder.evmod.apis.MapStateCacher;
import net.evmodder.evmod.apis.MiscUtils;
import net.evmodder.evmod.config.OptionMapStateCache;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;

public class ServerJoinListener{
	private final long JOIN_DELAY = 2500l;
	private long loadedAt/*, joinedAt*/;
	private double loadedAtX, loadedAtZ;
	private Timer joinMsgTimer, invLoadTimer;
	private final boolean WAIT_FOR_MOVEMENT = true; // TODO: put these into config

	public static long lastJoinTs; // TODO: remove horrible public static eww

	private void loadMapStateCaches(MinecraftClient client){
		if(Configs.Generic.MAP_CACHE_BY_ID.getBooleanValue()) MapStateCacher.loadMapStatesById();
		if(Configs.Generic.MAP_CACHE_BY_INV_POS.getBooleanValue())
			MapStateCacher.loadMapStatesByPos(client.player.getInventory().main, MapStateCacher.Cache.BY_PLAYER_INV);
	}

	public ServerJoinListener(){
		ClientPlayConnectionEvents.JOIN.register(
				//ServerPlayNetworkHandler handler, PacketSender sender, MinecraftServer server
				(handler, _1, _2) ->
		{
			lastJoinTs = System.currentTimeMillis();

			final int currServerHashCode = MiscUtils.getServerAddressHashCode(handler.getServerInfo());
			assert currServerHashCode == MiscUtils.getCurrentServerAddressHashCode();

			MinecraftClient client = MinecraftClient.getInstance();

			if(Configs.Generic.MAP_CACHE.getDefaultOptionListValue() != OptionMapStateCache.OFF){
				if(invLoadTimer != null){invLoadTimer.cancel(); invLoadTimer = null;}
				if(!client.player.getInventory().isEmpty()) loadMapStateCaches(client);
				else{// Inventory not loaded (can also mean still in queue)
					invLoadTimer = new Timer();
					invLoadTimer.schedule(new TimerTask(){@Override public void run(){
						if(client.player == null){cancel(); invLoadTimer.cancel(); invLoadTimer = null;}
						else if(!client.player.getInventory().isEmpty()){
							cancel(); invLoadTimer.cancel(); invLoadTimer = null;
							loadMapStateCaches(client);
						}
					}}, 1l, 50l); // check 20 times per second
				}
			}
			if(Configs.Database.SHARE_JOIN_QUIT.getBooleanValue() && Main.remoteSender != null){
				final String sessionName = client.getSession().getUsername(), playerName = client.player.getGameProfile().getName();
				if(!sessionName.equals(playerName)); // TODO: separate type of packet? Proxy-joined EvDoc->EvModder
				else Main.remoteSender.sendBotMessage(Command.DB_PLAYER_STORE_JOIN_TS,
						/*udp=*/true, 5000, MiscUtils.getCurrentServerAndPlayerData(), /*recv=*/null);
			}

//			if(currServerHashCode != Main.HASHCODE_2B2T) return;

			if(joinMsgTimer != null) joinMsgTimer.cancel(); // Restart timer

			//joinedAt = System.currentTimeMillis();
			joinMsgTimer = new Timer();
			joinMsgTimer.schedule(new TimerTask(){@Override public void run(){
				//final long now = System.currentTimeMillis();
				//if(now - joinedAt > GIVE_UP_AFTER_MS) cancel();

				//Main.LOGGER.info("Server join detected, checking if stuff is loaded");
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

				Main.LOGGER.info("JOIN_DELAY reached, triggering commands... ("+Configs.Generic.SEND_ON_SERVER_JOIN.getStrings().size()+")");
				//client.player.sendMessage(Text.literal("Sending "+messages.length+" msgs/cmds"), false);
				ClientPlayNetworkHandler handler = client.getNetworkHandler();
				for(String msg : Configs.Generic.SEND_ON_SERVER_JOIN.getStrings()){
					msg = msg.trim();
					//client.player.sendMessage(Text.literal("Sending "+(msg.startsWith("/")?"cmd":"msg")+": "+msg), false);
					if(msg.startsWith("/")) handler.sendChatCommand(msg.substring(1));
					else handler.sendChatMessage(msg);
				}
				loadedAt = 0;
				cancel();
				joinMsgTimer.cancel();
				if(invLoadTimer != null){invLoadTimer.cancel(); invLoadTimer = null;}
			}}, 0l, 100l);
		});
	}
}