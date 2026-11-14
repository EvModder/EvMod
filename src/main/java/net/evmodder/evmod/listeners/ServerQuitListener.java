package net.evmodder.evmod.listeners;

import net.evmodder.EvLib.util.Command;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.Setting;
import net.evmodder.evmod.Main;
import net.evmodder.evmod.Settings;
import net.evmodder.evmod.apis.MapStateCacher;
import net.evmodder.evmod.apis.MiscUtils;
import net.evmodder.evmod.apis.PlayerUnload;
import net.evmodder.evmod.apis.RemoteServerSender;
import net.evmodder.evmod.commands.CommandExportMapNames;
import net.evmodder.evmod.config.OptionMapStateCache;
import net.evmodder.evmod.onTick.ContainerOpenCloseListener;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

// Player and level state is handled in PlayerUnload: Fabric's DISCONNECT usually fires later, on the Netty thread.
public final class ServerQuitListener{
	public ServerQuitListener(final RemoteServerSender rms){
		final Settings settings = Settings.get();
		if(settings.enabled(Setting.CMD_EXPORT_MAPNAMES)) PlayerUnload.register(player -> {
			if(PlayerUnload.isDisconnect(player)) CommandExportMapNames.clearMapNames();
		});
		if(settings.enabled(Setting.CHAT_ACTIONS)) PlayerUnload.register(player -> {
			if(Configs.Generic.LOG_COORDS_ON_SERVER_QUIT.getBooleanValue() && PlayerUnload.isDisconnect(player))
				Main.LOGGER.info(player.getName().getString()+" logged out at: "+player.blockPosition().toShortString());
		});
		if(rms != null) ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			rms.minecraftServerDisconnecting();
			if(Configs.Database.SHARE_JOIN_QUIT.getBooleanValue())
				rms.sendBotMessage(Command.DB_PLAYER_STORE_QUIT_TS, /*udp=*/false, 5000, MiscUtils.getEncodedPlayerIds(client, handler), /*recv=*/null);
			rms.minecraftServerDisconnected();
		});
		if(settings.enabled(Setting.MAP_CACHE)){
			PlayerUnload.register(player -> {
				if(Configs.Database.MAP_CACHE.getOptionListValue() == OptionMapStateCache.OFF) return;
				MapStateCacher.saveMapStatesByPos(player.getInventory().getNonEquipmentItems().stream(), MapStateCacher.BY_PLAYER_INV);
				ContainerOpenCloseListener.echestCacheLoaded = false;  // TODO: ewww
				ContainerOpenCloseListener.containerCachesLoaded.clear();  // TODO: ewww
			});
			// The larger cache files are written off the client thread.
			ClientPlayConnectionEvents.DISCONNECT.register((_, _) -> {
				if(Configs.Database.MAP_CACHE.getOptionListValue() == OptionMapStateCache.MEMORY_AND_DISK){
					if(Configs.Database.MAP_CACHE_BY_ID.getBooleanValue()) MapStateCacher.saveMapStatesByIdToFile();
					if(Configs.Database.MAP_CACHE_BY_NAME.getBooleanValue()) MapStateCacher.saveMapStatesByNameToFile();
				}
			});
		}
	}
}