package net.evmodder.evmod.listeners;

import net.evmodder.EvLib.util.Command;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.Setting;
import net.evmodder.evmod.Main;
import net.evmodder.evmod.Settings;
import net.evmodder.evmod.apis.MapStateCacher;
import net.evmodder.evmod.apis.MiscUtils;
import net.evmodder.evmod.apis.RemoteServerSender;
import net.evmodder.evmod.commands.CommandExportMapNames;
import net.evmodder.evmod.config.OptionMapStateCache;
import net.evmodder.evmod.onTick.ContainerOpenCloseListener;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public final class ServerQuitListener{
	public ServerQuitListener(final RemoteServerSender rms){
		final Settings settings = Settings.get();
		if(settings.enabled(Setting.CMD_EXPORT_MAPNAMES)) ClientPlayConnectionEvents.DISCONNECT.register((_, _) -> CommandExportMapNames.clearMapNames());
		if(settings.enabled(Setting.CHAT_ACTIONS)) ClientPlayConnectionEvents.DISCONNECT.register((_, client) -> {
			if(Configs.Generic.LOG_COORDS_ON_SERVER_QUIT.getBooleanValue() && client.player != null)
				Main.LOGGER.info(client.player.getName().getString()+" logged out at: "+client.player.blockPosition().toShortString());
		});
		if(rms != null) ClientPlayConnectionEvents.DISCONNECT.register((_, client) -> {
			rms.minecraftServerDisconnecting();
			if(Configs.Database.SHARE_JOIN_QUIT.getBooleanValue())
				rms.sendBotMessage(Command.DB_PLAYER_STORE_QUIT_TS, /*udp=*/false, 5000, MiscUtils.getEncodedPlayerIds(client), /*recv=*/null);
			rms.minecraftServerDisconnected();
		});
		if(settings.enabled(Setting.MAP_CACHE)) ClientPlayConnectionEvents.DISCONNECT.register((_, client) -> {
			if(Configs.Database.MAP_CACHE.getOptionListValue() != OptionMapStateCache.OFF){
				if(Configs.Database.MAP_CACHE.getOptionListValue() == OptionMapStateCache.MEMORY_AND_DISK){
					if(Configs.Database.MAP_CACHE_BY_ID.getBooleanValue()) MapStateCacher.saveMapStatesByIdToFile();
					if(Configs.Database.MAP_CACHE_BY_NAME.getBooleanValue()) MapStateCacher.saveMapStatesByNameToFile();
				}
				MapStateCacher.saveMapStatesByPos(client.player.getInventory().getNonEquipmentItems().stream(), MapStateCacher.BY_PLAYER_INV);
				ContainerOpenCloseListener.echestCacheLoaded = false;  // TODO: ewww
				ContainerOpenCloseListener.containerCachesLoaded.clear();  // TODO: ewww
			}
		});
	}
}