package net.evmodder.evmod.listeners;

import java.util.Timer;
import java.util.TimerTask;
import net.evmodder.EvLib.util.Command;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.Setting;
import net.evmodder.evmod.Main;
import net.evmodder.evmod.Settings;
import net.evmodder.evmod.apis.MapStateCacher;
import net.evmodder.evmod.apis.MiscUtils;
import net.evmodder.evmod.apis.RemoteServerSender;
import net.evmodder.evmod.config.OptionMapStateCache;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

public final class ServerJoinListener{
	private static final long JOIN_DELAY = 2500L;
	private static final boolean WAIT_FOR_MOVEMENT = true;  // TODO: put these into config
	private Timer joinPollTimer;
	private boolean waitingForInventory, waitingForJoinCommands;
	private long loadedAt/*, joinedAt*/;
	private double loadedAtX, loadedAtZ;
	public static long lastJoinTs;  // TODO: remove horrible public static eww

	private void loadMapStateCaches(Minecraft client){
		if(Configs.Database.MAP_CACHE_BY_ID.getBooleanValue()) MapStateCacher.loadMapStatesById();
		if(Configs.Database.MAP_CACHE_BY_INV_POS.getBooleanValue())
			MapStateCacher.loadMapStatesByPos(client.player.getInventory().getNonEquipmentItems(), MapStateCacher.BY_PLAYER_INV);
		waitingForInventory = false;
	}

	private void sendJoinCommands(Minecraft client){
		//final long now = System.currentTimeMillis();
		//if(now - joinedAt > GIVE_UP_AFTER_MS) return;
		//Main.LOGGER.info("Server join detected, checking if stuff is loaded");
		if(!waitingForJoinCommands || !client.isGameLoadFinished() || client.player == null || client.level == null
				|| client.getConnection() == null || !client.player.isAlive() || client.player.isSpectator()
				|| client.player.touchingUnloadedChunk() || client.player.isInvisible()) return;
		if(loadedAt == 0){
			loadedAt = System.currentTimeMillis();
			if(WAIT_FOR_MOVEMENT){loadedAtX = client.player.getX(); loadedAtZ = client.player.getZ();}
			return;
		}
		//Main.LOGGER.info("Stuff seems loaded, waiting for player movement");
		if(WAIT_FOR_MOVEMENT){
			final double diffX = client.player.getX()-loadedAtX, diffZ = client.player.getZ()-loadedAtZ;
			if(Math.abs(diffX) <= 1 && Math.abs(diffZ) <= 1) return;  // Didn't move (enough)
			if(Math.abs(diffX) > 70 || Math.abs(diffZ) > 70){loadedAtX += diffX; loadedAtZ += diffZ; return;}  // Teleported
			//client.player.sendMessage(Text.literal(
			//	"Movement: "+loadedAtX+","+loadedAtZ+"->"+client.player.getX()+","+client.player.getZ()), false);
		}

		//Main.LOGGER.info("Player movement detected, checking JOIN_DELAY");
		if(System.currentTimeMillis()-loadedAt < JOIN_DELAY) return;
		Main.LOGGER.info("JOIN_DELAY reached, triggering commands... ("+Configs.Generic.SEND_ON_SERVER_JOIN.getStrings().size()+")");
		//client.player.sendMessage(Text.literal("Sending "+messages.length+" msgs/cmds"), false);
		final ClientPacketListener handler = client.getConnection();
		for(String msg : Configs.Generic.SEND_ON_SERVER_JOIN.getStrings()){
			msg = msg.trim();
			//client.player.sendMessage(Text.literal("Sending "+(msg.startsWith("/")?"cmd":"msg")+": "+msg), false);
			if(msg.startsWith("/")) handler.sendCommand(msg.substring(1));
			else handler.sendChat(msg);
		}
		waitingForJoinCommands = false;
	}

	public ServerJoinListener(final RemoteServerSender rms){
		final Settings settings = Settings.get();
		if(settings.enabled(Setting.CMD_TIMEONLINE)) ClientPlayConnectionEvents.JOIN.register((_, _, _) -> lastJoinTs = System.currentTimeMillis());
		if(rms != null) ClientPlayConnectionEvents.JOIN.register((_, _, client) -> {
			rms.minecraftServerConnected();
//			assert MiscUtils.getServerAddressHashCode(handler.getServerInfo()) == MiscUtils.getServerAddressHashCode();
			if(Configs.Database.SHARE_JOIN_QUIT.getBooleanValue())
				rms.sendBotMessage(Command.DB_PLAYER_STORE_JOIN_TS, /*udp=*/false, 5000, MiscUtils.getEncodedPlayerIds(client), /*recv=*/null);
		});
		if(settings.any(Setting.MAP_CACHE, Setting.CHAT_ACTIONS)){
			ClientPlayConnectionEvents.JOIN.register((_, _, client) -> {
				if(joinPollTimer != null){joinPollTimer.cancel(); joinPollTimer = null;}
//				if(currServerHashCode != Main.HASHCODE_2B2T) return;
				//joinedAt = System.currentTimeMillis();
				loadedAt = 0;
				waitingForJoinCommands = settings.enabled(Setting.CHAT_ACTIONS) && !Configs.Generic.SEND_ON_SERVER_JOIN.getStrings().isEmpty();
				waitingForInventory = settings.enabled(Setting.MAP_CACHE) && Configs.Database.MAP_CACHE.getOptionListValue() != OptionMapStateCache.OFF;
				if(waitingForInventory && !client.player.getInventory().isEmpty()) loadMapStateCaches(client);
				if(!waitingForInventory && !waitingForJoinCommands) return;
				final Timer timer = joinPollTimer = new Timer(/*isDaemon=*/true);
				timer.schedule(new TimerTask(){@Override public void run(){
					client.submit(() -> {
						if(joinPollTimer != timer) return;  // A disconnect/rejoin can precede an already queued poll.
						if(waitingForInventory && client.player != null && !client.player.getInventory().isEmpty()) loadMapStateCaches(client);
						if(waitingForJoinCommands) sendJoinCommands(client);
						if(!waitingForInventory && !waitingForJoinCommands){timer.cancel(); joinPollTimer = null;}
					}).join();  // Do not queue another poll while the client is busy.
				}}, 1L, 50L);  // Only while waiting; game state is read on the client thread.
			});
			ClientPlayConnectionEvents.DISCONNECT.register((_, _) -> {
				if(joinPollTimer != null){joinPollTimer.cancel(); joinPollTimer = null;}
				waitingForInventory = waitingForJoinCommands = false;
			});
		}
	}
}