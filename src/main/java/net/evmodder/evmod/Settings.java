package net.evmodder.evmod;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

/** Immutable bootstrap settings, safe to read before Minecraft targets or normal mod configs are loaded. */
public final class Settings{
	private final EnumSet<Setting> enabled;
	final boolean database, epearlOwners;
	final boolean placementHelperIframeAutoPlace, placementHelperMapArt, placementHelperMapArtAutoPlace, placementHelperMapArtAutoRemove;
	final boolean serverJoinListener, serverQuitListener, gameMessageListener, gameMessageFilter, blockClickListener;
	final boolean onTickInventory, onTickContainer, onTickIframes, muteMusicOnMenuScreen, containerOpenCloseListener, playerMoveListener, broadcaster;
	final boolean tooltipMapHighlights, tooltipMapMetadata, tooltipRepairCost;
	final boolean cmdExportMapNames, cmdMapArtGroup, cmdTimeOnline;

	private static final Settings INSTANCE = load();
	public static Settings get(){return INSTANCE;}
	public boolean enabled(Setting setting){return enabled.contains(setting);}
	public boolean any(Setting... settings){
		for(Setting setting : settings) if(enabled(setting)) return true;
		return false;
	}
	public boolean usesClicks(){
		return any(Setting.CLICK_SAFETY, Setting.MAP_ACTIONS, Setting.MAP_PLACE_HAND_RESTOCK, Setting.MAP_AUTO_PLACE,
				Setting.MAP_AUTO_REMOVE, Setting.INVENTORY_TOOLS, Setting.CRAFTING, Setting.ARMOR_SWAP);
	}
	public boolean usesMapPackets(){
		return any(Setting.MAP_ACTIONS, Setting.MAP_CACHE, Setting.MAP_SCAN_INVENTORY, Setting.MAP_SCAN_CONTAINERS,
				Setting.MAP_SCAN_IFRAMES, Setting.MAP_PLACE_HAND_RESTOCK, Setting.MAP_AUTO_PLACE, Setting.MAP_AUTO_REMOVE,
				Setting.MAP_NAME_COLORS, Setting.MAP_TOOLTIPS, Setting.MAP_HUD, Setting.CMD_MAPARTGROUP,
				Setting.CMD_EXPORT_MAPNAMES, Setting.DATABASE_MAP_HISTORY);
	}
	public boolean usesRemoteCommandHotkeys(){return database && !enabled(Setting.MAPART_ONLY_DEFAULTS);}
	public boolean usesCustomWidgets(){return !enabled(Setting.MAPART_ONLY_DEFAULTS) || enabled(Setting.CHAT_ACTIONS);}

	private void require(Setting setting, Setting dependency){
		if(!enabled(dependency) && enabled.remove(setting)) System.getLogger("evmod").log(System.Logger.Level.WARNING,
				"Ignoring "+setting.key+"=true: requires "+dependency.key+"=true in settings.txt");
	}

	private Settings(Map<String, Boolean> values){
		enabled = EnumSet.noneOf(Setting.class);
		for(Setting setting : Setting.values()){
			if(values.getOrDefault(setting.key, setting.defaultEnabled)) enabled.add(setting);
		}
		// Dependent capabilities never create unusable registrations.
		require(Setting.MAP_AUTO_PLACE, Setting.MAP_PLACE_HAND_RESTOCK);
		require(Setting.MAP_AUTO_REMOVE, Setting.MAP_PLACE_HAND_RESTOCK);
		require(Setting.NOTIFY_NEW_MAPART_IFRAME, Setting.CMD_MAPARTGROUP);

		database = enabled(Setting.DATABASE_REMOTE);
		epearlOwners = enabled(Setting.DATABASE_EPEARL_OWNERS);
		broadcaster = enabled(Setting.BROADCASTER);
		placementHelperIframeAutoPlace = enabled(Setting.IFRAME_AUTO_PLACE);
		placementHelperMapArt = any(Setting.MAP_PLACE_HAND_RESTOCK, Setting.MAP_AUTO_PLACE, Setting.MAP_AUTO_REMOVE);
		placementHelperMapArtAutoPlace = enabled(Setting.MAP_AUTO_PLACE);
		placementHelperMapArtAutoRemove = enabled(Setting.MAP_AUTO_REMOVE);
		onTickInventory = any(Setting.NOTIFY_NEW_MAPART_IFRAME, Setting.MAP_SCAN_INVENTORY, Setting.MAP_ACTIONS, Setting.MAP_NAME_COLORS, Setting.MAP_TOOLTIPS,
				Setting.MAP_SCAN_CONTAINERS, Setting.MAP_SCAN_IFRAMES, Setting.CMD_EXPORT_MAPNAMES, Setting.MAP_PLACE_HAND_RESTOCK, Setting.MAP_AUTO_PLACE, Setting.MAP_AUTO_REMOVE);
		onTickIframes = any(Setting.NOTIFY_NEW_MAPART_IFRAME, Setting.MAP_SCAN_IFRAMES, Setting.MAP_HUD, Setting.MAP_NAME_COLORS, Setting.MAP_SCAN_CONTAINERS, Setting.CMD_EXPORT_MAPNAMES,
				Setting.MAP_PLACE_HAND_RESTOCK, Setting.MAP_AUTO_PLACE, Setting.MAP_AUTO_REMOVE);
		onTickContainer = any(Setting.MAP_SCAN_CONTAINERS, Setting.MAP_NAME_COLORS, Setting.MAP_TOOLTIPS, Setting.CMD_EXPORT_MAPNAMES);
		if(onTickInventory && !enabled(Setting.MAP_SCAN_INVENTORY)) System.getLogger("evmod").log(System.Logger.Level.WARNING,
				Setting.MAP_SCAN_INVENTORY.key+"=false, but inventory tracking remains active for other enabled map features; see settings.txt");
		if(enabled(Setting.PEARL_MESSAGES) && !epearlOwners) System.getLogger("evmod").log(System.Logger.Level.WARNING,
				Setting.PEARL_MESSAGES.key+": whisper activation requires "+Setting.DATABASE_EPEARL_OWNERS.key+"=true; the external-trigger hotkey remains available");
		muteMusicOnMenuScreen = enabled(Setting.MUTE_MAIN_MENU_MUSIC);
		containerOpenCloseListener = any(Setting.MAP_CACHE, Setting.INVENTORY_TOOLS);
		serverJoinListener = any(Setting.MAP_CACHE, Setting.DATABASE_REMOTE, Setting.CHAT_ACTIONS, Setting.CMD_TIMEONLINE);
		serverQuitListener = any(Setting.MAP_CACHE, Setting.DATABASE_REMOTE, Setting.CHAT_ACTIONS, Setting.CMD_EXPORT_MAPNAMES);
		blockClickListener = enabled(Setting.MAP_CACHE);
		playerMoveListener = enabled(Setting.PLAYER_POSITION_SYNC);
		gameMessageListener = any(Setting.WHISPER_SOUND, Setting.PEARL_MESSAGES, Setting.DATABASE_IGNORELIST_SYNC);
		gameMessageFilter = enabled(Setting.DATABASE_IGNORELIST_SYNC);
		tooltipMapHighlights = enabled(Setting.MAP_NAME_COLORS);
		tooltipMapMetadata = enabled(Setting.MAP_TOOLTIPS);
		tooltipRepairCost = enabled(Setting.REPAIR_COST_TOOLTIP);
		cmdExportMapNames = enabled(Setting.CMD_EXPORT_MAPNAMES);
		cmdMapArtGroup = enabled(Setting.CMD_MAPARTGROUP);
		cmdTimeOnline = enabled(Setting.CMD_TIMEONLINE);
	}

	private static Settings load(){
		final Path path = FabricLoader.getInstance().getConfigDir().resolve("evmod/settings.txt");
		try{
			final boolean exists = Files.exists(path);
			final List<Path> legacyFiles = LegacySettingsMigration.settingsFiles(path, FabricLoader.getInstance().getGameDir());
			final String original = exists ? Files.readString(path) : "";
			String text = original;
			for(Path legacy : legacyFiles) text = LegacySettingsMigration.mergeSettings(text, Files.readString(legacy));
			if(!exists && legacyFiles.isEmpty()){
				try(var defaults = Settings.class.getResourceAsStream("/assets/evmod/settings.txt")){
					text = new String(defaults.readAllBytes(), StandardCharsets.UTF_8);
				}
			}
			final Map<String, Boolean> values = LegacySettingsMigration.parseSettings(text);
			final String migrated = LegacySettingsMigration.settings(text, values, true);
			final boolean changed = !migrated.equals(text);
			final Settings settings = new Settings(changed ? LegacySettingsMigration.parseSettings(migrated) : values);
			if(!exists || !migrated.equals(original)){
				Files.createDirectories(path.getParent());
				LegacySettingsMigration.write(path, migrated);
			}
			for(Path legacy : legacyFiles){
				try{Files.delete(legacy);}
				catch(IOException e){System.getLogger("evmod").log(System.Logger.Level.WARNING, "Could not delete imported settings file: "+legacy, e);}
			}
			return settings;
		}
		catch(IOException e){throw new UncheckedIOException("Cannot read Év’s Mod startup settings", e);}
	}
}