package net.evmodder.evmod;

import java.util.HashMap;
import net.evmodder.EvLib.util.FileIO;

class Settings{
	private final String internalSettingsFile = Main.mapArtFeaturesOnly ? "startup_settings_for_mapart_ver.txt" : "startup_settings.txt";

	final boolean database, epearlOwners;
	final boolean inventoryRestockAuto, placementHelperIframe, placementHelperMapArt, placementHelperMapArtAutoPlace, placementHelperMapArtAutoRemove, broadcaster;
	final boolean serverJoinListener, serverQuitListener, gameMessageListener, gameMessageFilter, containerOpenCloseListener;
	final boolean cmdAssignPearl, cmdExportMapImg, cmdMapArtGroup, cmdSeen, cmdSendAs, cmdTimeOnline;
	final boolean mapHighlights, mapHighlightsInGUIs, tooltipMapHighlights, tooltipMapMetadata, tooltipRepairCost;

	private HashMap<String, Boolean> loadSettings(){
		HashMap<String, Boolean> config = new HashMap<>();
		final String configContents = FileIO.loadFile("startup_settings.txt", getClass().getResourceAsStream("/assets/"+Main.MOD_ID+"/"+internalSettingsFile));
		for(String line : configContents.split("\\r?\\n")){
			final int sep = line.indexOf(':');
			if(sep == -1) continue;
			final String key = line.substring(0, sep).trim();
			final String value = line.substring(sep+1).trim();
			if(key.isEmpty() || value.isEmpty()) continue;
			if(key.equals("mapart_features_only")) config.put(key, !value.equalsIgnoreCase("false")); // Prefer true when ambiguous
			else config.put(key, value.equalsIgnoreCase("true")); // Prefer false when ambiguous
		}
		return config;
	}

	private boolean extractConfigValue(HashMap<String, Boolean> config, String key){
		Boolean value = config.remove(key);
		return value != null ? value : false;
	}

	Settings(){
		final HashMap<String, Boolean> settings = loadSettings();

		Main.mapArtFeaturesOnly = settings.getOrDefault("mapart_features_only", true); // Note: true instead of false
		settings.remove("mapart_features_only");

		database = extractConfigValue(settings, "database");
		epearlOwners = extractConfigValue(settings, "epearl_owners");
		broadcaster = extractConfigValue(settings, "broadcaster");
		placementHelperIframe = extractConfigValue(settings, "placement_helper.iframe");
		placementHelperMapArt = extractConfigValue(settings, "placement_helper.mapart");
		placementHelperMapArtAutoPlace = placementHelperMapArt && extractConfigValue(settings, "placement_helper.mapart.auto");
		placementHelperMapArtAutoRemove = placementHelperMapArt && extractConfigValue(settings, "placement_helper.mapart.autoremove");
		serverJoinListener = extractConfigValue(settings, "listener.server_join");
		serverQuitListener = extractConfigValue(settings, "listener.server_quit");
		gameMessageListener = extractConfigValue(settings, "listener.game_message.read");
		gameMessageFilter = extractConfigValue(settings, "listener.game_message.filter");
		containerOpenCloseListener = extractConfigValue(settings, "listener.container_open");
		mapHighlights = extractConfigValue(settings, "map_highlights");
		mapHighlightsInGUIs = extractConfigValue(settings, "map_highlights.in_gui");
		tooltipMapHighlights = mapHighlights && extractConfigValue(settings, "tooltip.map_highlights");
		tooltipMapMetadata = extractConfigValue(settings, "tooltip.map_metadata");
		tooltipRepairCost = extractConfigValue(settings, "tooltip.repair_cost");
		inventoryRestockAuto = containerOpenCloseListener && extractConfigValue(settings, "inventory_restock.auto");

		cmdAssignPearl = epearlOwners && extractConfigValue(settings, "command.assignpearl");
		cmdExportMapImg = extractConfigValue(settings, "command.exportmapimg");
		cmdMapArtGroup = extractConfigValue(settings, "command.mapartgroup");
		cmdSeen = database && extractConfigValue(settings, "command.seen");
		cmdSendAs = database && extractConfigValue(settings, "command.sendas");
		cmdTimeOnline = database && extractConfigValue(settings, "command.timeonline");

		if(!settings.isEmpty()) Main.LOGGER.error("Unrecognized config setting(s)!: "+settings);
	}
}