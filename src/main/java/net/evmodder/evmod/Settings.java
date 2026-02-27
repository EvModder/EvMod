package net.evmodder.evmod;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import net.evmodder.EvLib.util.FileIO;
import net.fabricmc.loader.api.FabricLoader;

final class Settings{
	private final String internalSettingsFile = Main.mapArtFeaturesOnly ? "settings_for_mapart_ver.txt" : "settings.txt";

	final boolean showNicheConfigs;
	final boolean storeDataInInstanceFolder, database, epearlOwners;
	final boolean placementHelperIframeAutoPlace, placementHelperMapArt, placementHelperMapArtAutoPlace, placementHelperMapArtAutoRemove;
	final boolean serverJoinListener, serverQuitListener, gameMessageListener, gameMessageFilter, blockClickListener;
	final boolean onTickInventory, onTickContainer, onTickIframes, containerOpenCloseListener, mapLoaderBot, broadcaster;
	final boolean tooltipMapHighlights, tooltipMapMetadata, tooltipRepairCost;
	final boolean cmdAssignPearl, cmdDeletedMapsNearby, cmdExportMapImg, cmdMapArtGroup, cmdMapHashCode, cmdSeen, cmdSendAs, cmdTimeOnline;


	private final HashMap<String, Boolean> loadSettings(){
		{//==================================================
			// TODO: remove these legacy-patches in a future version
			File oldSettings = new File(FileIO.DIR+"enabled_features.txt"); 
			if(oldSettings.exists()){
				oldSettings.renameTo(new File(FileIO.DIR+"settings.txt"));
				FileIO.deleteFile("evmod.json");
				Main.LOGGER.info("EvModConfig: Migrating configs from v1.x -> v2.0 (some settings may get reset)");
			}
		}//==================================================

		final HashMap<String, Boolean> config = new HashMap<>();
		String configContents = FileIO.loadFile("settings.txt", getClass().getResourceAsStream("/assets/"+Main.MOD_ID+"/"+internalSettingsFile));
		{//==================================================
			// TODO: remove these legacy-patches in a future version
			if(configContents.contains("map_highlights:")){
				Main.LOGGER.info("EvModConfig: Deleting unusable old settings.txt file (due to name changes; resetting to defaults)");
				FileIO.deleteFile("settings.txt");
				configContents = FileIO.loadFile("settings.txt", getClass().getResourceAsStream("/assets/"+Main.MOD_ID+"/"+internalSettingsFile));
			}
		}
		for(final String line : configContents.split("\\r?\\n")){
			final int sep = line.indexOf(':');
			if(sep == -1) continue;
			final String key = line.substring(0, sep).trim();
			final String value = line.substring(sep+1).trim();
			if(key.isEmpty() || value.isEmpty()) continue;
			config.put(key, value.equalsIgnoreCase("true")); // Prefer false when ambiguous
		}
		return config;
	}

	private final boolean extractConfigValue(final HashMap<String, Boolean> config, final String key){
		final Boolean value = config.remove(key);
		return value != null && value;
	}

	Settings(){
		final HashMap<String, Boolean> settings = loadSettings();

		Main.mapArtFeaturesOnly = !extractConfigValue(settings, "enable_non_mapart_features");
		showNicheConfigs = extractConfigValue(settings, "show_niche_config_settings");
		storeDataInInstanceFolder = extractConfigValue(settings, "store_data_in_instance_folder");

		database = extractConfigValue(settings, "database");
		epearlOwners = extractConfigValue(settings, "epearl_owners");
		broadcaster = extractConfigValue(settings, "broadcaster");
		placementHelperIframeAutoPlace = extractConfigValue(settings, "placement_helper.iframe.autoplace");
		onTickInventory = extractConfigValue(settings, "on_tick.inventory");
		onTickIframes = extractConfigValue(settings, "on_tick.iframes");
		onTickContainer = extractConfigValue(settings, "on_tick.container");
		containerOpenCloseListener = extractConfigValue(settings, "listener.container_open");
		placementHelperMapArt = onTickInventory && extractConfigValue(settings, "placement_helper.mapart");
		placementHelperMapArtAutoPlace = placementHelperMapArt && extractConfigValue(settings, "placement_helper.mapart.autoplace");
		placementHelperMapArtAutoRemove = placementHelperMapArt && extractConfigValue(settings, "placement_helper.mapart.autoremove");
		mapLoaderBot = extractConfigValue(settings, "map_bot.loader");
		serverJoinListener = extractConfigValue(settings, "listener.server_join");
		serverQuitListener = extractConfigValue(settings, "listener.server_quit");
		blockClickListener = extractConfigValue(settings, "listener.block_click");
		gameMessageListener = extractConfigValue(settings, "listener.game_message.read");
		gameMessageFilter = extractConfigValue(settings, "listener.game_message.filter");
		tooltipMapHighlights = (onTickInventory || onTickIframes) && extractConfigValue(settings, "tooltip.map_highlights");
		tooltipMapMetadata = extractConfigValue(settings, "tooltip.map_metadata");
		tooltipRepairCost = extractConfigValue(settings, "tooltip.repair_cost");

		cmdAssignPearl = epearlOwners && extractConfigValue(settings, "command.assignpearl");
		cmdDeletedMapsNearby = extractConfigValue(settings, "command.deletedmapsnearby");
		cmdExportMapImg = extractConfigValue(settings, "command.exportmapimg");
		cmdMapArtGroup = extractConfigValue(settings, "command.mapartgroup");
		cmdMapHashCode = extractConfigValue(settings, "command.maphashcode");
		cmdSeen = database && extractConfigValue(settings, "command.seen");
		cmdSendAs = database && extractConfigValue(settings, "command.sendas");
		cmdTimeOnline = database && extractConfigValue(settings, "command.timeonline");

		if(!settings.isEmpty()) Main.LOGGER.error("Unrecognized config setting(s)!: "+settings);

		{//==================================================
			// TODO: remove these legacy-patches in a future version
			final String mapGroupDir = (storeDataInInstanceFolder ? FabricLoader.getInstance().getGameDir()+"/"+Main.MOD_ID
					: FabricLoader.getInstance().getConfigDir())+"/mapart_groups/";
			try{
				List<Path> paths = Files.walk(Paths.get(mapGroupDir))
						.filter(Files::isRegularFile)
						.filter(p -> 
							(p.getFileName().toString().indexOf('.') == -1 || (
								p.getNameCount() > 1 && p.getName(p.getNameCount()-2).toString().equals("seen")
								&& !p.getFileName().toString().startsWith(".")
							)) && !p.getFileName().toString().endsWith(".group"))
						.toList();
				if(!paths.isEmpty()){
					paths.stream().forEach(p -> p.toFile().renameTo(p.resolveSibling(p.getFileName().toString()+".group").toFile()));
					Main.LOGGER.info("[MIGRATION]: added '.group' file_ext to "+paths.size()+" group files");
				}
			}
			catch(IOException e){e.printStackTrace();}
		}//==================================================
	}
}