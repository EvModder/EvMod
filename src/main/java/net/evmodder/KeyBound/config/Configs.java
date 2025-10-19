package net.evmodder.KeyBound.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.evmodder.KeyBound.Main;

import fi.dy.masa.malilib.config.ConfigUtils;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.IConfigHandler;
import fi.dy.masa.malilib.config.options.*;
import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.JsonUtils;
import fi.dy.masa.malilib.util.MessageOutputType;

public class Configs implements IConfigHandler{
	private static final String CONFIG_FILE_NAME = Main.MOD_ID+"/keybound.json";

	private static final String KEYBINDS_KEY = Main.MOD_ID+".config.keybinds";
	public static class Hotkeys{
		public static final ConfigHotkey EJECT_JUNK_ITEMS = new ConfigHotkey("ejectJunkItems", "R").apply(KEYBINDS_KEY);
		public static final ConfigHotkey EBOUNCE_TRAVEL_HELPER = new ConfigHotkey("eBounceTravelHelper", "LEFT_CONTROL,LEFT_ALT,E").apply(KEYBINDS_KEY);

		public static final List<ConfigHotkey> HOTKEY_LIST = ImmutableList.of(
				EJECT_JUNK_ITEMS,
				EBOUNCE_TRAVEL_HELPER
		);
	}

	private static final String VISUALS_KEY = Main.MOD_ID+".config.visuals";
	public static class Visuals{
		public static final ConfigBoolean MAP_HIGHLIGHT_IFRAME = new ConfigBoolean("mapHighlightInItemFrame", true).apply(VISUALS_KEY);
		public static final ConfigDouble GHOST_BLOCK_ALPHA = new ConfigDouble("ghostBlockAlpha", 0.5, 0, 1).apply(VISUALS_KEY);
		public static final ConfigStringList IGNORABLE_EXISTING_BLOCKS = new ConfigStringList("ignorableExistingBlocks", ImmutableList.of()).apply(VISUALS_KEY);

		public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
				MAP_HIGHLIGHT_IFRAME,
				IGNORABLE_EXISTING_BLOCKS,
				GHOST_BLOCK_ALPHA
		);
	}

	private static final String DATABASE_KEY = Main.MOD_ID+".config.database";
	public static class Database{
		public static final ConfigOptionList PLACEMENT_WARN = new ConfigOptionList("placementWarn", MessageOutputType.ACTIONBAR).apply(DATABASE_KEY);
		public static final ConfigInteger COMMAND_TASK_INTERVAL = new ConfigInteger("commandTaskInterval", 1, 1, 1000).apply(DATABASE_KEY);
		public static final ConfigString TOOL_ITEM = new ConfigString("toolItem", "minecraft:stick").apply(DATABASE_KEY);

		public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
				PLACEMENT_WARN,
				COMMAND_TASK_INTERVAL,
				TOOL_ITEM
		);
	}

	private static final String MISC_KEY = Main.MOD_ID+".config.miscellaneous";
	public static class Misc{
		public static final ConfigBoolean IFRAME_PLACEMENT_HELPER = new ConfigBoolean("iFramePlacementHelper", true).apply(MISC_KEY);
		public static final ConfigBoolean IFRAME_PLACEMENT_HELPER_MUST_CONNECT = new ConfigBoolean("iFramePlacementHelperMustConnect", true).apply(MISC_KEY);
		public static final ConfigBoolean IFRAME_PLACEMENT_HELPER_MUST_MATCH_BLOCK = new ConfigBoolean("iFramePlacementHelperMustMatchBlock", true).apply(MISC_KEY);

		public static final ConfigBooleanHotkeyed ENTITY_DATA_SYNC = new ConfigBooleanHotkeyed("entityDataSync", false, "").apply(MISC_KEY);
		public static final ConfigString TOOL_ITEM = new ConfigString("toolItem", "minecraft:stick").apply(MISC_KEY);

		public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
				IFRAME_PLACEMENT_HELPER,
				IFRAME_PLACEMENT_HELPER_MUST_CONNECT,
				IFRAME_PLACEMENT_HELPER_MUST_MATCH_BLOCK,
				ENTITY_DATA_SYNC,
				TOOL_ITEM
		);

		public static final List<IHotkey> HOTKEY_LIST = ImmutableList.of(
				ENTITY_DATA_SYNC
		);
	}

	public static void loadFromFile(){
		Path configFile = FileUtils.getConfigDirectoryAsPath().resolve(CONFIG_FILE_NAME);

		if(Files.exists(configFile) && Files.isReadable(configFile)){
			JsonElement element = JsonUtils.parseJsonFileAsPath(configFile);

			if(element != null && element.isJsonObject()){
				JsonObject root = element.getAsJsonObject();

				ConfigUtils.readConfigBase(root, "Hotkeys", Hotkeys.HOTKEY_LIST);
				ConfigUtils.readConfigBase(root, "Visuals", Visuals.OPTIONS);
				ConfigUtils.readConfigBase(root, "Database", Database.OPTIONS);
				ConfigUtils.readConfigBase(root, "Miscellaneous", Misc.OPTIONS);

//				RenderCompat.checkGpuVisuals();
				// Main.debugLog("loadFromFile(): Successfully loaded config file '{}'.", configFile.toAbsolutePath());
			}
			else Main.LOGGER.error("loadFromFile(): Failed to load config file '{}'.", configFile.toAbsolutePath());
		}

//		DataManager.setToolItem(Generic.TOOL_ITEM.getStringValue());
//		if(MinecraftClient.getInstance().world != null){
//			DataManager.getInstance().setToolItemComponents(Generic.TOOL_ITEM_COMPONENTS.getStringValue(),
//					MinecraftClient.getInstance().world.getRegistryManager());
//		}
//		InventoryUtils.setPickBlockableSlots(Generic.PICK_BLOCKABLE_SLOTS.getStringValue());
//		DataManager.getSelectionManager().checkSelectionModeConfig();
	}

	public static void saveToFile(){
		Path dir = FileUtils.getConfigDirectoryAsPath();

		if(!Files.exists(dir)){
			FileUtils.createDirectoriesIfMissing(dir);
			// Main.debugLog("saveToFile(): Creating directory '{}'.", dir.toAbsolutePath());
		}

		if(Files.isDirectory(dir)){
			JsonObject root = new JsonObject();

			ConfigUtils.writeConfigBase(root, "Hotkeys", Hotkeys.HOTKEY_LIST);
			ConfigUtils.writeConfigBase(root, "Visuals", Visuals.OPTIONS);
			ConfigUtils.writeConfigBase(root, "Database", Database.OPTIONS);
			ConfigUtils.writeConfigBase(root, "Miscellaneous", Misc.OPTIONS);

			JsonUtils.writeJsonToFileAsPath(root, dir.resolve(CONFIG_FILE_NAME));
		}
		else Main.LOGGER.error("saveToFile(): Config Folder '{}' does not exist!", dir.toAbsolutePath());
	}

	@Override public void load(){loadFromFile();}
	@Override public void save(){saveToFile();}
}