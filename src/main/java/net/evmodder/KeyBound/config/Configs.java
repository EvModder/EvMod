package net.evmodder.KeyBound.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.evmodder.KeyBound.ChatBroadcaster;
import net.evmodder.KeyBound.Main;
import net.evmodder.KeyBound.keybinds.ClickUtils;
import fi.dy.masa.malilib.config.ConfigUtils;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.IConfigHandler;
import fi.dy.masa.malilib.config.options.*;
import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.JsonUtils;

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
		public static final ConfigBoolean INVIS_IFRAMES = new ConfigBoolean("invisIFramesForMapArt", true).apply(VISUALS_KEY);
		public static final ConfigBoolean INVIS_IFRAMES_SEMI_TRANSPARENT = new ConfigBoolean("invisIFramesForMapArtSemiTransparentOnly", true).apply(VISUALS_KEY);

		public static final ConfigBoolean MAP_HIGHLIGHT_IFRAME = new ConfigBoolean("mapHighlightInIFrame", true).apply(VISUALS_KEY);
		// TODO: highlights

		public static final ConfigColor MAP_COLOR_UNLOADED = new ConfigColor("mapHighlightUnloaded", "#FFC8AAD2"); // 13150930 Peach
		public static final ConfigColor MAP_COLOR_UNLOCKED = new ConfigColor("mapHighlightUnlocked", "#FFE03165"); // 14692709 Redish
		public static final ConfigColor MAP_COLOR_UNNAMED = new ConfigColor("mapHighlightUnnamed", "#FFEED7D7"); // 15652823 Pink
		public static final ConfigColor MAP_COLOR_NOT_IN_GROUP = new ConfigColor("mapHighlightNotInGroup", "#FF0AC864"); // 706660 Green
		public static final ConfigColor MAP_COLOR_IN_INV = new ConfigColor("mapHighlightInInv", "#FFB4FFFF"); // 11862015 Aqua
		public static final ConfigColor MAP_COLOR_IN_IFRAME = new ConfigColor("mapHighlightInIFrame", "#FF55AAE6"); // 5614310 Blue
		public static final ConfigColor MAP_COLOR_MULTI_IFRAME = new ConfigColor("mapHighlightMultiIFrame", "#FFB450E6"); // 11817190 Purple
		public static final ConfigColor MAP_COLOR_MULTI_INV = new ConfigColor("mapHighlightMultiInv", "#FFB450E6"); // 11817190 Purple

//		public static final ConfigBoolean MAP_METADATA_TOOLTIP = new ConfigBoolean("mapMetadataTooltip", true);
		public static final ConfigBoolean MAP_METADATA_TOOLTIP_STAIRCASE = new ConfigBoolean("mapMetadataTooltipStaircase", true);
		public static final ConfigBoolean MAP_METADATA_TOOLTIP_MATERIAL = new ConfigBoolean("mapMetadataTooltipMaterial", true);
		public static final ConfigBoolean MAP_METADATA_TOOLTIP_NUM_COLORS = new ConfigBoolean("mapMetadataTooltipNumColors", true);
		public static final ConfigBoolean MAP_METADATA_TOOLTIP_NUM_COLOR_IDS = new ConfigBoolean("mapMetadataTooltipNumCOlorIds", true);
		public static final ConfigBoolean MAP_METADATA_TOOLTIP_TRANSPARENCY = new ConfigBoolean("mapMetadataTooltipTransparency", true);
		public static final ConfigBoolean MAP_METADATA_TOOLTIP_NOOBLINE = new ConfigBoolean("mapMetadataTooltipNoobline", true);
		public static final ConfigBoolean MAP_METADATA_TOOLTIP_PERCENT_CARPET = new ConfigBoolean("mapMetadataTooltipPercentCarpet", true);
		public static final ConfigBoolean MAP_METADATA_TOOLTIP_PERCENT_STAIRCASE = new ConfigBoolean("mapMetadataTooltipPercentStaircase", true);

		public static final ConfigInteger EXPORT_MAP_IMG_UPSCALE = new ConfigInteger("exportMapImageUpScale", 128, 128, 1280);
		public static final ConfigBoolean EXPORT_MAP_IMG_BORDER = new ConfigBoolean("exportMapImageBorder", false);
		public static final ConfigColor EXPORT_MAP_IMG_BORDER_COLOR1 = new ConfigColor("exportMapImageBorderColor1", "#FFFFC864"); // -14236 Yellow
		public static final ConfigColor EXPORT_MAP_IMG_BORDER_COLOR2 = new ConfigColor("exportMapImageBorderColor2", "#00322D32"); // 3288370 Gray

		public static final List<IConfigBase> getOptions(){
			List<IConfigBase> availableOptions = new ArrayList<>();
			availableOptions.addAll(List.of(
					INVIS_IFRAMES, INVIS_IFRAMES_SEMI_TRANSPARENT,
					MAP_HIGHLIGHT_IFRAME,

					MAP_COLOR_UNLOADED, MAP_COLOR_UNLOCKED, MAP_COLOR_UNNAMED, MAP_COLOR_NOT_IN_GROUP,
					MAP_COLOR_IN_INV, MAP_COLOR_IN_IFRAME, MAP_COLOR_MULTI_IFRAME, MAP_COLOR_MULTI_INV,

//					MAP_METADATA_TOOLTIP,
					MAP_METADATA_TOOLTIP_STAIRCASE, MAP_METADATA_TOOLTIP_MATERIAL,
					MAP_METADATA_TOOLTIP_NUM_COLORS, MAP_METADATA_TOOLTIP_NUM_COLOR_IDS,
					MAP_METADATA_TOOLTIP_TRANSPARENCY, MAP_METADATA_TOOLTIP_NOOBLINE,
					MAP_METADATA_TOOLTIP_PERCENT_CARPET, MAP_METADATA_TOOLTIP_PERCENT_STAIRCASE
			));
			if(Main.cmdExportMapImg){
				availableOptions.addAll(List.of(
						EXPORT_MAP_IMG_UPSCALE,
						EXPORT_MAP_IMG_BORDER,
						EXPORT_MAP_IMG_BORDER_COLOR1, EXPORT_MAP_IMG_BORDER_COLOR2));
			}
			return availableOptions;
		}
	}

	private static final String DATABASE_KEY = Main.MOD_ID+".config.database";
	public static class Database{
//		public static final ConfigOptionList PLACEMENT_WARN = new ConfigOptionList("placementWarn", MessageOutputType.ACTIONBAR).apply(DATABASE_KEY);
		public static final ConfigInteger CLIENT_ID = new ConfigInteger("clientId", 1, 0, 1000000).apply(DATABASE_KEY);
		public static final ConfigString CLIENT_KEY = new ConfigString("clientKey", "some_unique_key").apply(DATABASE_KEY);
		public static final ConfigString ADDRESS = new ConfigString("address", "evmodder.net:14441").apply(DATABASE_KEY);
//		public static final ConfigBoolean SHARE_IGNORES = new ConfigBoolean("shareIgnores", false).apply(DATABASE_KEY);
//		public static final ConfigStringList BORROW_IGNORES = new ConfigStringList("borrowIgnores", ImmutableList.of()).apply(DATABASE_KEY);

		public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
				CLIENT_ID, CLIENT_KEY,
				ADDRESS
//				SHARE_IGNORES, BORROW_IGNORES
		);
	}

	private static final String MISC_KEY = Main.MOD_ID+".config.miscellaneous";
	public static class Misc{
//		private static final String CLICK_LIMIT_COMMENT = "2b2t will kick you for setning >80 clicks in <80 ticks (Last checked: 2025-10-15)";//TODO: translation key?
		public static final ConfigInteger CLICK_LIMIT_COUNT = new ConfigInteger("clickLimitCount", 79, 0, 100_000).apply(MISC_KEY);
		public static final ConfigInteger CLICK_LIMIT_DURATION = new ConfigInteger("clickLimitWindow", 96, 1, 72_000).apply(MISC_KEY);

		private static void remakeClickUtils(IConfigBase _0){
			Main.clickUtils = new ClickUtils(CLICK_LIMIT_COUNT.getIntegerValue(), CLICK_LIMIT_DURATION.getIntegerValue());
		}
		static{
			CLICK_LIMIT_COUNT.setValueChangeCallback(Misc::remakeClickUtils);
			CLICK_LIMIT_DURATION.setValueChangeCallback(Misc::remakeClickUtils);
		}
		public static final ConfigBoolean MAP_CLICK_MOVE_NEIGHBORS = new ConfigBoolean("mapClickMoveNeighbors", true).apply(MISC_KEY);

		public static final ConfigInteger MAX_IFRAME_TRACKING_DIST = new ConfigInteger("iFrameTrackingDist", 128, 0, 10_000_000);
		public static double MAX_IFRAME_TRACKING_DIST_SQ;
		static{MAX_IFRAME_TRACKING_DIST.setValueChangeCallback(d -> MAX_IFRAME_TRACKING_DIST_SQ=Math.pow(d.getIntegerValue(), 2));}

		public static final ConfigBoolean PLACEMENT_HELPER_IFRAME = new ConfigBoolean("placementHelperIFrame", true).apply(MISC_KEY);
		public static final ConfigBoolean PLACEMENT_HELPER_IFRAME_MUST_CONNECT = new ConfigBoolean("placementHelperIFrameMustConnect", true).apply(MISC_KEY);
		public static final ConfigBoolean PLACEMENT_HELPER_IFRAME_MUST_MATCH_BLOCK = new ConfigBoolean("placementHelperIFrameMustMatchBlock", true).apply(MISC_KEY);
		public static final ConfigBoolean PLACEMENT_HELPER_MAPART = new ConfigBoolean("mapArtPlacementHelper", true).apply(MISC_KEY);
		public static final ConfigBoolean PLACEMENT_HELPER_MAPART_USE_NAMES = new ConfigBoolean("placementHelperMapArtUseNames", true).apply(MISC_KEY);
		public static final ConfigBoolean PLACEMENT_HELPER_MAPART_USE_IMAGE = new ConfigBoolean("placementHelperMapArtUseImage", true).apply(MISC_KEY);
		public static final ConfigBoolean PLACEMENT_HELPER_MAPART_AUTOPLACE = new ConfigBoolean("placementHelperMapArtAutoPlace", true).apply(MISC_KEY);//TODO: unfinished

		public static final ConfigString WHISPER_PLAY_SOUND = new ConfigString("whisperPlaySound", "{sound:block.note_block.bass, category:PLAYERS, volume:.7, pitch:2}").apply(MISC_KEY);
		public static final ConfigString WHISPER_PEARL_PULL = new ConfigString("whisperPearlPull", "(?:e?p|e?pearl|([iI]'?m ?)?r(ea)?dy)").apply(MISC_KEY);

		public static final ConfigBoolean MAPART_GROUP_INCLUDE_UNLOCKED = new ConfigBoolean("commandMapArtGroupIncludeUnlocked", false).apply(MISC_KEY);

		public static final ConfigInteger KEYBIND_BUNDLE_REMOVE_MAX = new ConfigInteger("keybindMapArtBundleRemoveMax", 64, 1, 64).apply(MISC_KEY);
		public static final ConfigBoolean KEYBIND_MAPART_MOVE_IGNORE_AIR_POCKETS = new ConfigBoolean("keybindMapArtMoveIgnoreAirPockets", false).apply(MISC_KEY);

		public static final ConfigString TEMP_BROADCAST_ACCOUNT = new ConfigString("broadcastAccount", "AccountNameHere").apply(MISC_KEY);
		public static final ConfigString TEMP_BROADCAST_TIMESTAMP = new ConfigString("broadcastTimestamp", "1738990800").apply(MISC_KEY);
		public static final ConfigStringList TEMP_BROADCAST_MSGS = new ConfigStringList("broadcastMsgs", ImmutableList.of()).apply(DATABASE_KEY);

		static{
			TEMP_BROADCAST_ACCOUNT.setValueChangeCallback(_0 -> ChatBroadcaster.refreshBroadcast());
			TEMP_BROADCAST_TIMESTAMP.setValueChangeCallback(_0 -> ChatBroadcaster.refreshBroadcast());
			TEMP_BROADCAST_MSGS.setValueChangeCallback(_0 -> ChatBroadcaster.refreshBroadcast());
		}

//		public static final ConfigBooleanHotkeyed ENTITY_DATA_SYNC = new ConfigBooleanHotkeyed("entityDataSync", false, "").apply(MISC_KEY);
//		public static final ConfigString TOOL_ITEM = new ConfigString("toolItem", "minecraft:stick").apply(MISC_KEY);

		public static final List<IConfigBase> getOptions(){
			List<IConfigBase> availableOptions = new ArrayList<>();
			availableOptions.addAll(List.of(CLICK_LIMIT_COUNT, CLICK_LIMIT_DURATION, MAP_CLICK_MOVE_NEIGHBORS));
			if(Main.placementHelperIframe) availableOptions.addAll(List.of(PLACEMENT_HELPER_IFRAME,
					PLACEMENT_HELPER_IFRAME_MUST_CONNECT, PLACEMENT_HELPER_IFRAME_MUST_MATCH_BLOCK));
			if(Main.placementHelperMapArt){
				availableOptions.addAll(List.of(PLACEMENT_HELPER_MAPART, PLACEMENT_HELPER_MAPART_USE_NAMES, PLACEMENT_HELPER_MAPART_USE_IMAGE));
				if(Main.placementHelperMapArtAuto) availableOptions.add(PLACEMENT_HELPER_MAPART_AUTOPLACE);
			}
			if(Main.whisperListener) availableOptions.addAll(List.of(WHISPER_PLAY_SOUND, WHISPER_PEARL_PULL));
			if(Main.cmdMapArtGroup) availableOptions.add(MAPART_GROUP_INCLUDE_UNLOCKED);
			if(Main.keybindBundleStowOrReverseStow) availableOptions.add(KEYBIND_BUNDLE_REMOVE_MAX);
			if(Main.keybindMapArtMove) availableOptions.add(KEYBIND_MAPART_MOVE_IGNORE_AIR_POCKETS);

			if(Main.broadcaster) availableOptions.addAll(List.of(TEMP_BROADCAST_ACCOUNT, TEMP_BROADCAST_TIMESTAMP, TEMP_BROADCAST_MSGS));
			return availableOptions;
		}
//		public static final List<IHotkey> HOTKEY_LIST = ImmutableList.of(
//				ENTITY_DATA_SYNC
//		);
	}

	public static void loadFromFile(){
		Path configFile = FileUtils.getConfigDirectoryAsPath().resolve(CONFIG_FILE_NAME);

		if(Files.exists(configFile) && Files.isReadable(configFile)){
			JsonElement element = JsonUtils.parseJsonFileAsPath(configFile);

			if(element != null && element.isJsonObject()){
				JsonObject root = element.getAsJsonObject();

				ConfigUtils.readConfigBase(root, "Hotkeys", Hotkeys.HOTKEY_LIST);
				ConfigUtils.readConfigBase(root, "Visuals", Visuals.getOptions());
				if(Main.database) ConfigUtils.readConfigBase(root, "Database", Database.OPTIONS);
				ConfigUtils.readConfigBase(root, "Miscellaneous", Misc.getOptions());

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
			ConfigUtils.writeConfigBase(root, "Visuals", Visuals.getOptions());
			if(Main.database) ConfigUtils.writeConfigBase(root, "Database", Database.OPTIONS);
			ConfigUtils.writeConfigBase(root, "Miscellaneous", Misc.getOptions());

			JsonUtils.writeJsonToFileAsPath(root, dir.resolve(CONFIG_FILE_NAME));
		}
		else Main.LOGGER.error("saveToFile(): Config Folder '{}' does not exist!", dir.toAbsolutePath());
	}

	@Override public void load(){loadFromFile();}
	@Override public void save(){saveToFile();}
}