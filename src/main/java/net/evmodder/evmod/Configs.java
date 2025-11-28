package net.evmodder.evmod;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fi.dy.masa.malilib.config.ConfigUtils;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.IConfigHandler;
import fi.dy.masa.malilib.config.options.*;
import fi.dy.masa.malilib.hotkeys.KeyAction;
import fi.dy.masa.malilib.hotkeys.KeybindSettings;
import fi.dy.masa.malilib.hotkeys.KeybindSettings.Context;
import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.JsonUtils;
import net.evmodder.evmod.config.*;
import net.evmodder.evmod.config.ConfigPlayerList.NameAndUUID;

public class Configs implements IConfigHandler{
	private static final String CONFIG_FILE_NAME = Main.MOD_ID+"/"+Main.MOD_ID+".json";

	private static final String GENERIC_KEY = Main.MOD_ID+".config.generic";
	public static class Generic{
		public static final ConfigInteger CLICK_LIMIT_COUNT = new ConfigInteger("clickLimitCount", 69, 0, 100_000).apply(GENERIC_KEY);
		public static final ConfigInteger CLICK_LIMIT_DURATION = new ConfigInteger("clickLimitWindow", 99, 1, 72_000).apply(GENERIC_KEY);
		public static final ConfigBoolean CLICK_LIMIT_USER_INPUT = new ConfigBoolean("clickLimitUserInputs", true).apply(GENERIC_KEY);
		public static final ConfigBoolean CLICK_FILTER_USER_INPUT = new ConfigBoolean("clickBlockUserInputsDuringOperation", true).apply(GENERIC_KEY);

		public static final ConfigBoolean BUNDLE_SELECT_REVERSED = new ConfigBoolean("selectBundleSlotsInReverse", true).apply(GENERIC_KEY);

		public static final ConfigOptionList MAP_STATE_CACHE = new ConfigOptionList("mapStateCache",
//				new SimpleStringOption(Main.MOD_ID+".gui.label.cache_mapstate.", "a", "b", "c")).apply(GENERIC_KEY);
				OptionMapStateCache.MEMORY_AND_DISK).apply(GENERIC_KEY);
//		public static final ConfigMultiOptionList MAP_STATE_CACHE_TYPE = new ConfigMultiOptionList("mapStateCacheType",
//				List.of(MapStateCacheOptionType.BY_INV_POS, MapStateCacheOptionType.BY_NAME)).apply(GENERIC_KEY);

		public static final ConfigInteger MAX_IFRAME_TRACKING_DIST = new ConfigInteger("iFrameTrackingDist", /*default=*/128, 0, 10_000_000);
		public static double MAX_IFRAME_TRACKING_DIST_SQ;
		static{MAX_IFRAME_TRACKING_DIST.setValueChangeCallback(d -> MAX_IFRAME_TRACKING_DIST_SQ=Math.pow(d.getIntegerValue(), 2));}

		public static final ConfigBooleanHotkeyed PLACEMENT_HELPER_IFRAME = new ConfigBooleanHotkeyed("placementHelperIFrame", false, "").apply(GENERIC_KEY);
		public static final ConfigDouble PLACEMENT_HELPER_IFRAME_REACH = new ConfigDouble("placementHelperIFrameReach", 3.5d).apply(GENERIC_KEY);
		public static final ConfigBoolean PLACEMENT_HELPER_IFRAME_RAYCAST = new ConfigBoolean("placementHelperIFrameRaycast", true).apply(GENERIC_KEY);
		// ConfigOptionList: RaycastOnly, RaycastRotatePlayer, RaycastGrimRotate
//		public static final ConfigBoolean PLACEMENT_HELPER_IFRAME_ROTATE_PLAYER = new ConfigBoolean("placementHelperIFrameRotatePlayer", false).apply(GENERIC_KEY);
		public static final ConfigBoolean PLACEMENT_HELPER_IFRAME_MUST_CONNECT = new ConfigBoolean("placementHelperIFrameMustConnect", true).apply(GENERIC_KEY);
		public static final ConfigBoolean PLACEMENT_HELPER_IFRAME_MUST_MATCH_BLOCK = new ConfigBoolean("placementHelperIFrameMustMatchBlock", true).apply(GENERIC_KEY);

		public static final ConfigBoolean PLACEMENT_HELPER_MAPART = new ConfigBoolean("placementHelperMapArt", true).apply(GENERIC_KEY);
		public static final ConfigDouble PLACEMENT_HELPER_MAPART_REACH = new ConfigDouble("placementHelperMapArtReach", 3.9d).apply(GENERIC_KEY);
		public static final ConfigBoolean PLACEMENT_HELPER_MAPART_USE_NAMES = new ConfigBoolean("placementHelperMapArtUseNames", true).apply(GENERIC_KEY);
		public static final ConfigBoolean PLACEMENT_HELPER_MAPART_USE_IMAGE = new ConfigBoolean("placementHelperMapArtUseImage", true).apply(GENERIC_KEY);
		public static final ConfigBoolean PLACEMENT_HELPER_MAPART_FROM_BUNDLE = new ConfigBoolean("placementHelperMapArtFromBundle", true).apply(GENERIC_KEY);
		public static final ConfigBooleanHotkeyed MAPART_AUTOPLACE = new ConfigBooleanHotkeyed("mapArtAutoPlace", true, "").apply(GENERIC_KEY);
		public static final ConfigInteger MAPART_AUTOPLACE_INV_DELAY = new ConfigInteger("mapArtAutoPlaceInvDelay", 3, 0, 20).apply(GENERIC_KEY);

		public static final ConfigBooleanHotkeyed MAPART_AUTOREMOVE = new ConfigBooleanHotkeyed("mapArtAutoRemove", true, "").apply(GENERIC_KEY);
		public static final ConfigInteger MAPART_AUTOREMOVE_AFTER = new ConfigInteger("mapArtAutoRemoveThreshold", 2, 1, 20).apply(GENERIC_KEY);

		public static final ConfigString WHISPER_PLAY_SOUND = new ConfigString("whisperPlaySound", "{sound:block.note_block.bass, category:PLAYERS, volume:.7, pitch:2}").apply(GENERIC_KEY);
		public static final ConfigString WHISPER_PEARL_PULL = new ConfigString("whisperPearlPull", "(?:e?p|e?pearl|([iI]'?m ?)?r(ea)?dy)").apply(GENERIC_KEY);

		public static final ConfigBoolean MAPART_GROUP_INCLUDE_UNLOCKED = new ConfigBoolean("commandMapArtGroupIncludeUnlocked", false).apply(GENERIC_KEY);

		public static final ConfigInteger KEYBIND_BUNDLE_REMOVE_MAX = new ConfigInteger("keybindMapArtBundleRemoveMax", 64, 1, 64).apply(GENERIC_KEY);
		public static final ConfigBoolean KEYBIND_MAPART_MOVE_IGNORE_AIR_POCKETS = new ConfigBoolean("keybindMapArtMoveIgnoreAirPockets", false).apply(GENERIC_KEY);
		public static final ConfigBoolean SKIP_TRANSPARENT_MAPS = new ConfigBoolean("ignoreBlankMapsIndHighlightsAndKeybinds", true).apply(GENERIC_KEY);
		public static final ConfigBoolean SKIP_MONO_COLOR_MAPS = new ConfigBoolean("ignoreMonoColorMapsInHightlights", false).apply(GENERIC_KEY);

		public static final ConfigStringList SCROLL_ORDER = new ConfigStringList("hotbarSlotItemTypeScrollOrder", ImmutableList.of(
				"white, light_gray, gray, black, brown, red, orange, yellow, lime, green, cyan, light_blue, blue, purple, magenta, pink",
				", exposed, weathered, oxidized",
				",powered,detector,activator",
				"tube, brain, bubble, fire, horn",
				"oak, spruce, birch, jungle, acacia, dark_oak, mangrove, cherry, bamboo, crimson, warped"
		)).apply(GENERIC_KEY);
		public static final ConfigStringList SEND_ON_SERVER_JOIN = new ConfigStringList("sendChatsOnServerJoin", ImmutableList.of(
				"/mapartgroup set end_misc+end_big+end_square"
		)).apply(GENERIC_KEY);
		public static final ConfigBoolean LOG_COORDS_ON_SERVER_QUIT = new ConfigBoolean("logCoordsOnServerQuit", true).apply(GENERIC_KEY);

		public static final ConfigBoolean INV_RESTOCK_AUTO = new ConfigBoolean("inventoryRestockAuto", true).apply(GENERIC_KEY);
//		public static final ConfigOptionList INV_RESTOCK_AUTO_FOR_INV_ORGS = new ConfigOptionList("inventoryRestockAutoForOrganization", RestockAutoLayouts._2).apply(GENERIC_KEY);
		public static final ConfigStringList INV_RESTOCK_AUTO_FOR_INV_ORGS = new ConfigStringList("inventoryRestockAutoForOrganization", ImmutableList.of("2")).apply(GENERIC_KEY);

		public static final ConfigString TEMP_BROADCAST_ACCOUNT = new ConfigString("broadcastAccount", "AccountNameHere").apply(GENERIC_KEY);
		public static final ConfigString TEMP_BROADCAST_TIMESTAMP = new ConfigString("broadcastTimestamp", "1738990800").apply(GENERIC_KEY);
		public static final ConfigStringList TEMP_BROADCAST_MSGS = new ConfigStringList("broadcastMsgs", ImmutableList.of()).apply(GENERIC_KEY);

		private static List<IConfigBase> availableOptions;
		public static final List<IConfigBase> getOptions(){
			if(availableOptions == null){
				Main main = Main.getInstance();
				availableOptions = new ArrayList<>();
				availableOptions.addAll(List.of(CLICK_LIMIT_COUNT, CLICK_LIMIT_DURATION, CLICK_LIMIT_USER_INPUT, CLICK_FILTER_USER_INPUT));
				if(main.placementHelperMapArt) availableOptions.add(BUNDLE_SELECT_REVERSED);
				if((main.serverJoinListener && main.serverQuitListener) || main.containerOpenCloseListener != null){
					availableOptions.add(MAP_STATE_CACHE);
//					if(MAP_STATE_CACHE.getOptionListValue() != MapStateCacheOption.OFF) availableOptions.add(MAP_STATE_CACHE_TYPE);
				}
				if(main.mapHighlights) availableOptions.add(MAX_IFRAME_TRACKING_DIST);
				if(main.placementHelperIframe) availableOptions.addAll(List.of(PLACEMENT_HELPER_IFRAME,
						//PLACEMENT_HELPER_IFRAME_REACH, PLACEMENT_HELPER_IFRAME_RAYCAST,
						PLACEMENT_HELPER_IFRAME_MUST_CONNECT, PLACEMENT_HELPER_IFRAME_MUST_MATCH_BLOCK));
				if(main.placementHelperMapArt){
					availableOptions.addAll(List.of(PLACEMENT_HELPER_MAPART,
							//PLACEMENT_HELPER_MAPART_REACH
							PLACEMENT_HELPER_MAPART_USE_NAMES, PLACEMENT_HELPER_MAPART_USE_IMAGE,
							PLACEMENT_HELPER_MAPART_FROM_BUNDLE));
					if(main.placementHelperMapArtAuto) availableOptions.addAll(List.of(MAPART_AUTOPLACE, MAPART_AUTOPLACE_INV_DELAY));
				}
				if(main.gameMessageListener) availableOptions.addAll(List.of(WHISPER_PLAY_SOUND, WHISPER_PEARL_PULL));
				if(main.cmdMapArtGroup) availableOptions.add(MAPART_GROUP_INCLUDE_UNLOCKED);
//				if(Main.keybindMapArtMoveBundle)
					availableOptions.add(KEYBIND_BUNDLE_REMOVE_MAX);
//				if(Main.keybindMapArtMove)
					availableOptions.add(KEYBIND_MAPART_MOVE_IGNORE_AIR_POCKETS);

				if(!Main.mapArtFeaturesOnly) availableOptions.add(SCROLL_ORDER);
//				if(Main.keybindMapArtMove || Main.keybindMapArtMoveBundle)
					availableOptions.add(SKIP_TRANSPARENT_MAPS);
				if(main.mapHighlights) availableOptions.add(SKIP_MONO_COLOR_MAPS);
				if(main.serverJoinListener) availableOptions.add(SEND_ON_SERVER_JOIN);
				if(main.serverQuitListener) availableOptions.add(LOG_COORDS_ON_SERVER_QUIT);
				if(main.inventoryRestockAuto) availableOptions.addAll(List.of(INV_RESTOCK_AUTO, INV_RESTOCK_AUTO_FOR_INV_ORGS));
				if(main.broadcaster) availableOptions.addAll(List.of(TEMP_BROADCAST_ACCOUNT, TEMP_BROADCAST_TIMESTAMP, TEMP_BROADCAST_MSGS));
			}
			return availableOptions;
		}
//		public static final List<IHotkey> HOTKEY_LIST = ImmutableList.of(
//				PLACEMENT_HELPER_IFRAME
//		);
	}

	private static final String VISUALS_KEY = Main.MOD_ID+".config.visuals";
	public static class Visuals{
//		public static final ConfigBoolean DISPLAY_TOTEM_COUNT = new ConfigBoolean("displayTotemCount", true).apply(VISUALS_KEY);
//		public static final ConfigBoolean SPAWNER_ACTIVATION_HIGHLIGHT = new ConfigBoolean("spawnerActivationHighlight", true).apply(VISUALS_KEY);
		public static final ConfigBoolean REPAIR_COST_HOTBAR_HUD = new ConfigBoolean("repairCostInHotbarHUD", false).apply(VISUALS_KEY);
		public static final ConfigOptionList REPAIR_COST_TOOLTIP = new ConfigOptionList("repairCostTooltip",
				Main.mapArtFeaturesOnly ? TooltipDisplayOption.OFF : TooltipDisplayOption.ADVANCED_TOOLTIPS).apply(VISUALS_KEY);
		public static final ConfigBoolean INVIS_IFRAMES = new ConfigBoolean("invisIFramesMapArt", true).apply(VISUALS_KEY);
		//InvisIFrameOption {ANY_ITEM, MAPART, PARTIALLY_TRANSPARENT_MAPART}
//		public static final ConfigOptionList INVIS_IFRAMES = new ConfigOptionList("invisIFrames", InvisIFrameOption.PARTIALLY_TRANSPARENT_MAPART}).apply(GENERIC_KEY);
		public static final ConfigBoolean INVIS_IFRAMES_SEMI_TRANSPARENT = new ConfigBoolean("invisIFramesMapArtOnlyForSemiTransparent", true).apply(VISUALS_KEY);

		public static final ConfigBoolean MAP_HIGHLIGHT_IFRAME = new ConfigBoolean("mapHighlightInIFrame", true).apply(VISUALS_KEY);
		public static final ConfigBoolean MAP_HIGHLIGHT_TOOLTIP = new ConfigBoolean("mapHighlightInTooltip", true).apply(VISUALS_KEY);
		public static final ConfigBoolean MAP_HIGHLIGHT_HOTBAR_HUD = new ConfigBoolean("mapHighlightInHotbarHUD", true).apply(VISUALS_KEY);
		public static final ConfigBoolean MAP_HIGHLIGHT_CONTAINER_NAME = new ConfigBoolean("mapHighlightInContainerName", true).apply(VISUALS_KEY);
	
		public static final ConfigBoolean MAP_HIGHLIGHT_IN_INV_INCLUDE_BUNDLES = new ConfigBoolean("mapHighlightInInvIncludeBundles", true).apply(VISUALS_KEY);

		public static final ConfigColor MAP_COLOR_UNLOADED = new ConfigColor("highlightColorUnloaded", "#FFC8AAD2").apply(VISUALS_KEY); // 13150930 Peach
		public static final ConfigColor MAP_COLOR_UNLOCKED = new ConfigColor("highlightColorUnlocked", "#FFE03165").apply(VISUALS_KEY); // 14692709 Redish
		public static final ConfigColor MAP_COLOR_UNNAMED = new ConfigColor("highlightColorUnnamed", "#FFEED7D7").apply(VISUALS_KEY); // 15652823 Pink
		public static final ConfigColor MAP_COLOR_NOT_IN_GROUP = new ConfigColor("highlightColorNotInGroup", "#FF0AC864").apply(VISUALS_KEY); // 706660 Green
		public static final ConfigColor MAP_COLOR_IN_INV = new ConfigColor("highlightColorInInv", "#FFB4FFFF").apply(VISUALS_KEY); // 11862015 Aqua
		public static final ConfigColor MAP_COLOR_IN_IFRAME = new ConfigColor("highlightColorInIFrame", "#FF55AAE6").apply(VISUALS_KEY); // 5614310 Blue
		public static final ConfigColor MAP_COLOR_MULTI_IFRAME = new ConfigColor("highlightColorMultiIFrame", "#FFB450E6").apply(VISUALS_KEY); // 11817190 Purple
		public static final ConfigColor MAP_COLOR_MULTI_INV = new ConfigColor("highlightColorMultiInv", "#FFB450E6").apply(VISUALS_KEY); // 11817190 Purple

//		public static final ConfigBoolean MAP_METADATA_TOOLTIP = new ConfigBoolean("mapMetadataTooltip", true);
		public static final ConfigBoolean MAP_METADATA_TOOLTIP_STAIRCASE = new ConfigBoolean("mapMetadataTooltipStaircase", true).apply(VISUALS_KEY);
		public static final ConfigBoolean MAP_METADATA_TOOLTIP_MATERIAL = new ConfigBoolean("mapMetadataTooltipMaterial", true).apply(VISUALS_KEY);
		public static final ConfigBoolean MAP_METADATA_TOOLTIP_NUM_COLORS = new ConfigBoolean("mapMetadataTooltipNumColors", true).apply(VISUALS_KEY);
		public static final ConfigBoolean MAP_METADATA_TOOLTIP_NUM_COLOR_IDS = new ConfigBoolean("mapMetadataTooltipNumColorIds", true).apply(VISUALS_KEY);
		public static final ConfigBoolean MAP_METADATA_TOOLTIP_TRANSPARENCY = new ConfigBoolean("mapMetadataTooltipTransparency", true).apply(VISUALS_KEY);
		public static final ConfigBoolean MAP_METADATA_TOOLTIP_NOOBLINE = new ConfigBoolean("mapMetadataTooltipNoobline", true).apply(VISUALS_KEY);
		public static final ConfigBoolean MAP_METADATA_TOOLTIP_PERCENT_CARPET = new ConfigBoolean("mapMetadataTooltipPercentCarpet", true).apply(VISUALS_KEY);
		public static final ConfigBoolean MAP_METADATA_TOOLTIP_PERCENT_STAIRCASE = new ConfigBoolean("mapMetadataTooltipPercentStaircase", true).apply(VISUALS_KEY);

		public static final ConfigInteger EXPORT_MAP_IMG_UPSCALE = new ConfigInteger("exportMapImageUpscale", 128, 128, 1280).apply(VISUALS_KEY);
		public static final ConfigBoolean EXPORT_MAP_IMG_BORDER = new ConfigBoolean("exportMapImageBorder", false).apply(VISUALS_KEY);
		public static final ConfigColor EXPORT_MAP_IMG_BORDER_COLOR1 = new ConfigColor("exportMapImageBorderColor1", "#FFFFC864").apply(VISUALS_KEY); // -14236 Yellow
		public static final ConfigColor EXPORT_MAP_IMG_BORDER_COLOR2 = new ConfigColor("exportMapImageBorderColor2", "#00322D32").apply(VISUALS_KEY); // 3288370 Gray

		private static List<IConfigBase> availableOptions;
		public static final List<IConfigBase> getOptions(){
			if(availableOptions == null){
				Main main = Main.getInstance();
				availableOptions = new ArrayList<>();
				if(!Main.mapArtFeaturesOnly){
					availableOptions.add(REPAIR_COST_HOTBAR_HUD);
					if(main.tooltipRepairCost) availableOptions.add(REPAIR_COST_TOOLTIP);
				}
				availableOptions.addAll(List.of(INVIS_IFRAMES, INVIS_IFRAMES_SEMI_TRANSPARENT));
				if(main.mapHighlights){
					availableOptions.add(MAP_HIGHLIGHT_IFRAME);
					if(main.tooltipMapHighlights) availableOptions.add(MAP_HIGHLIGHT_TOOLTIP);
					availableOptions.add(MAP_HIGHLIGHT_HOTBAR_HUD);
					if(main.mapHighlightsInGUIs) availableOptions.add(MAP_HIGHLIGHT_CONTAINER_NAME);
					availableOptions.addAll(List.of(
						MAP_HIGHLIGHT_IN_INV_INCLUDE_BUNDLES,

						MAP_COLOR_UNLOADED, MAP_COLOR_UNLOCKED, MAP_COLOR_UNNAMED, MAP_COLOR_NOT_IN_GROUP,
						MAP_COLOR_IN_INV, MAP_COLOR_IN_IFRAME, MAP_COLOR_MULTI_IFRAME, MAP_COLOR_MULTI_INV
					));
				}
				if(main.tooltipMapMetadata) availableOptions.addAll(List.of(
//						MAP_METADATA_TOOLTIP,
						MAP_METADATA_TOOLTIP_STAIRCASE, MAP_METADATA_TOOLTIP_MATERIAL,
						MAP_METADATA_TOOLTIP_NUM_COLORS, MAP_METADATA_TOOLTIP_NUM_COLOR_IDS,
						MAP_METADATA_TOOLTIP_TRANSPARENCY, MAP_METADATA_TOOLTIP_NOOBLINE,
						MAP_METADATA_TOOLTIP_PERCENT_CARPET, MAP_METADATA_TOOLTIP_PERCENT_STAIRCASE
				));
				if(main.cmdExportMapImg){
					availableOptions.addAll(List.of(
							EXPORT_MAP_IMG_UPSCALE,
							EXPORT_MAP_IMG_BORDER,
							EXPORT_MAP_IMG_BORDER_COLOR1, EXPORT_MAP_IMG_BORDER_COLOR2));
				}
			}
			return availableOptions;
		}
	}

	private static final String HOTKEYS_KEY = Main.MOD_ID+".config.hotkeys";
	public static class Hotkeys{
		private static final KeybindSettings GUI_OR_INGAME_SETTINGS = KeybindSettings.create(Context.ANY,
				KeyAction.PRESS, /*allowExtraKeys=*/false, /*orderSensitive=*/true, /*exclusive=*/false, /*cancel=*/true);
		private static final KeybindSettings GUI_ALLOW_EXTRA_KEYS = KeybindSettings.create(Context.GUI,
				KeyAction.PRESS, /*allowExtraKeys=*/true, /*orderSensitive=*/true, /*exclusive=*/false, /*cancel=*/true);

		public static final ConfigHotkey OPEN_CONFIG_GUI = new ConfigHotkey("openConfigGui", "M,N").apply(HOTKEYS_KEY);

		public static final ConfigHotkey MAP_COPY = new ConfigHotkey("mapCopy", "T", KeybindSettings.GUI).apply(HOTKEYS_KEY);
		public static final ConfigBoolean MAP_COPY_BUNDLE_BETA = new ConfigBoolean("mapCopyBundleSelectPacket", true).apply(HOTKEYS_KEY);
		public static final ConfigHotkey MAP_LOAD = new ConfigHotkey("mapLoad", "E", KeybindSettings.GUI).apply(HOTKEYS_KEY);
		public static final ConfigHotkey MAP_MOVE = new ConfigHotkey("mapMove", "T", GUI_ALLOW_EXTRA_KEYS).apply(HOTKEYS_KEY);
		public static final ConfigHotkey MAP_MOVE_BUNDLE = new ConfigHotkey("mapMoveBundle", "D", KeybindSettings.GUI).apply(HOTKEYS_KEY);
		public static final ConfigHotkey MAP_MOVE_BUNDLE_REVERSE = new ConfigHotkey("mapMoveBundleReverse", "", KeybindSettings.GUI).apply(HOTKEYS_KEY);

		public static final ConfigBoolean MAP_CLICK_MOVE_NEIGHBORS = new ConfigBoolean("mapClickMoveNeighbors", true).apply(HOTKEYS_KEY);
		public static final ConfigHotkey MAP_CLICK_MOVE_NEIGHBORS_KEY = new ConfigHotkey("mapClickMoveNeighborsKey", "LEFT_ALT",
				KeybindSettings.MODIFIER_GUI).apply(HOTKEYS_KEY);

		public static final ConfigHotkey TOGGLE_CAPE = new ConfigHotkey("toggleCape", /*!Main.mapArtFeaturesOnly ? "," : */"");
		public static final ConfigBoolean SYNC_CAPE_WITH_ELYTRA = new ConfigBoolean("syncCapeWithElytra", false).apply(HOTKEYS_KEY);
		public static final ConfigHotkey TOGGLE_HAT = new ConfigHotkey("toggleHat", "");
		public static final ConfigHotkey TOGGLE_JACKET = new ConfigHotkey("toggleJacket", Main.mapArtFeaturesOnly ? "" : "I");
		public static final ConfigHotkey TOGGLE_SLEEVE_LEFT = new ConfigHotkey("toggleSleeveLeft", Main.mapArtFeaturesOnly ? "" : "I");
		public static final ConfigHotkey TOGGLE_SLEEVE_RIGHT = new ConfigHotkey("toggleSleeveRight", Main.mapArtFeaturesOnly ? "" : "I");
		public static final ConfigHotkey TOGGLE_PANTS_LEG_LEFT = new ConfigHotkey("togglePantsLegLeft", Main.mapArtFeaturesOnly ? "" : "I");
		public static final ConfigHotkey TOGGLE_PANTS_LEG_RIGHT = new ConfigHotkey("togglePantsLegRight",Main.mapArtFeaturesOnly ? "" : "I");

		public static final ConfigBooleanHotkeyed AIE_TRAVEL_HELPER = new ConfigBooleanHotkeyed("automaticInfiniteElytraTravelHelper", false,
				Main.mapArtFeaturesOnly ? "" : "SEMICOLON", KeybindSettings.NOCANCEL).apply(HOTKEYS_KEY);
		public static final ConfigBooleanHotkeyed EBOUNCE_TRAVEL_HELPER = new ConfigBooleanHotkeyed("eBounceTravelHelper", false,
				Main.mapArtFeaturesOnly ? "" : "A", KeybindSettings.PRESS_ALLOWEXTRA).apply(HOTKEYS_KEY);
		public static final ConfigHotkey EJECT_JUNK_ITEMS = new ConfigHotkey("ejectJunkItems",
				Main.mapArtFeaturesOnly ? "" : "R", GUI_OR_INGAME_SETTINGS).apply(HOTKEYS_KEY);
		public static final ConfigBooleanHotkeyed CRAFT_RESTOCK = new ConfigBooleanHotkeyed("craftingRestock", true,
				" ", KeybindSettings.GUI).apply(HOTKEYS_KEY);
		public static final ConfigHotkey HOTBAR_TYPE_INCR = new ConfigHotkey("hotbarSlotItemTypeIncrement", "").apply(HOTKEYS_KEY);
		public static final ConfigHotkey HOTBAR_TYPE_DECR = new ConfigHotkey("hotbarSlotItemTypeDecrement", "").apply(HOTKEYS_KEY);

		//TODO: ConfigSlotList, ConfigSlotListHotkeyed
		public static final ConfigHotkey INV_RESTOCK = new ConfigHotkey("inventoryRestock",
				Main.mapArtFeaturesOnly ? "" : "R", KeybindSettings.GUI).apply(HOTKEYS_KEY);
		public static final ConfigOptionList INV_RESTOCK_LIMITS = new ConfigOptionList("inventoryRestockLimits",
				OptionInventoryRestockLimit.LEAVE_UNLESS_ALL_RESUPPLY).apply(HOTKEYS_KEY);
		public static final ConfigStringList INV_RESTOCK_BLACKLIST = new ConfigStringList("inventoryRestockBlacklist", ImmutableList.of(
//				"ender_chest", "filled_map"
		)).apply(HOTKEYS_KEY);
		public static final ConfigStringList INV_RESTOCK_WHITELIST = new ConfigStringList("inventoryRestockWhitelist", ImmutableList.of(
				"firework_rocket", "end_crystal", "respawn_anchor", "obsidian", "ender_pearl", "chorus_fruit", "wind_charge", "experience_bottle",
				"arrow", "spectral_arrow", "tipped_arrow", "golden_carrot", "enchanted_golden_apple", "cooked_beef", "cooked_porkchop", "cooked_chicken"
		)).apply(HOTKEYS_KEY);

		public static final ConfigStringList INV_ORGANIZE_1 = new ConfigStringList("inventoryOrganize1", ImmutableList.of(
				"# Armor",
				"5:carved_pumpkin", "6:elytra",
				"5:netherite_helmet", "6:netherite_chestplate", "7:netherite_leggings", "8:netherite_boots",
				"5:diamond_helmet", "6:diamond_chestplate", "7:diamond_leggings", "8:diamond_boots",

				"# Offhand",
				"45:totem_of_undying", "45:enchanted_golden_apple", "45:golden_carrot", "45:cooked_beef",

				"# Hotbar LHS",
				"36:netherite_sword", "36:diamond_sword", "36:netherite_axe", "36:diamond_axe",
				"37:netherite_pickaxe", "37:diamond_pickaxe",
				"38:netherite_axe", "38:diamond_axe", "38:netherite_shovel", "38:diamond_shovel", "38:firework_rocket", "38:end_crystal",
				"39:end_crystal", "39:netherite_shovel", "39:diamond_shovel", "39:firework_rocket", "39:fishing_rod",
				"40:fishing_rod",

				"# Hotbar RHS",
				"44:golden_carrot", "44:cooked_beef", "44:enchanted_golden_apple",
				"43:enchanted_golden_apple",

				"# Inventory top LHS",
				"9:netherite_chestplate", "9:diamond_chestplate", "9:elytra", "9:ender_chest", "9:totem_of_undying",
				"10:ender_chest", "10:totem_of_undying",
				"11:totem_of_undying", "12:totem_of_undying", "13:totem_of_undying",

				"# Extra rockets, food",
				"30:firework_rocket", "21:firework_rocket",
				"35:enchanted_golden_apple", "35:golden_carrot", "35:cooked_beef", "35:cooked_porkchop", "35:pumpkin_pie",

				"# Fill extra hotbar slots with crystals",
				"36:end_crystal", "37:end_crystal", "38:end_crystal", "39:end_crystal", "40:end_crystal", "41:end_crystal", "42:end_crystal"
		)).apply(HOTKEYS_KEY);
		public static final ConfigHotkey TRIGGER_INV_ORGANIZE_1 = new ConfigHotkey("triggerInventoryOrganize1",
				Main.mapArtFeaturesOnly ? "" : "E", KeybindSettings.GUI).apply(HOTKEYS_KEY);
		public static final ConfigStringList INV_ORGANIZE_2 = new ConfigStringList("inventoryOrganize2", ImmutableList.of(
				"# Armor",
				"5:carved_pumpkin",
				"6:elytra",
				"5:netherite_helmet", "6:netherite_chestplate", "7:netherite_leggings", "8:netherite_boots",
				"5:diamond_helmet", "6:diamond_chestplate", "7:diamond_leggings", "8:diamond_boots",

				"# Offhand",
				"45:totem_of_undying",

				"# Hotbar LHS",
				"36:netherite_sword", "36:diamond_sword", "36:netherite_axe", "36:diamond_axe",
				"37:netherite_pickaxe", "37:diamond_pickaxe",
				"38:netherite_axe", "38:diamond_axe", "38:netherite_shovel", "38:diamond_shovel", "38:firework_rocket",
				"39:firework_rocket",

				"# Hotbar RHS",
				"44:golden_carrot", "44:cooked_beef", "44:enchanted_golden_apple"
		)).apply(HOTKEYS_KEY);
		public static final ConfigHotkey TRIGGER_INV_ORGANIZE_2 = new ConfigHotkey("triggerInventoryOrganize2", "", KeybindSettings.GUI).apply(HOTKEYS_KEY);
		public static final ConfigStringList INV_ORGANIZE_3 = new ConfigStringList("inventoryOrganize3", ImmutableList.of()).apply(HOTKEYS_KEY);
		public static final ConfigHotkey TRIGGER_INV_ORGANIZE_3 = new ConfigHotkey("triggerInventoryOrganize3", "", KeybindSettings.GUI).apply(HOTKEYS_KEY);

		public static final ConfigStringHotkeyed CHAT_MSG_1 = new ConfigStringHotkeyed("chatMessage1", "meow :3", "").apply(HOTKEYS_KEY);
		public static final ConfigStringHotkeyed CHAT_MSG_2 = new ConfigStringHotkeyed("chatMessage2", "/home", "").apply(HOTKEYS_KEY);
		public static final ConfigStringHotkeyed CHAT_MSG_3 = new ConfigStringHotkeyed("chatMessage3", "/cgetdata entity @e[type=item,distance=..5,limit=1] Item.components.minecraft:repair_cost", "").apply(HOTKEYS_KEY);
		public static final ConfigHotkey TRIGGER_CHAT_MSG_3 = new ConfigHotkey("triggerChatMessage3",
				Main.mapArtFeaturesOnly ? "" : "Z").apply(HOTKEYS_KEY);

		public static final ConfigStringHotkeyed REMOTE_MSG_1 = new ConfigStringHotkeyed("remoteMessage1", "EPEARL_TRIGGER,6f7fa766-4fe6-42fe-b589-22e4ec9a077c", "").apply(HOTKEYS_KEY);
		public static final ConfigStringHotkeyed REMOTE_MSG_2 = new ConfigStringHotkeyed("remoteMessage2", "", "").apply(HOTKEYS_KEY);
		public static final ConfigStringHotkeyed REMOTE_MSG_3 = new ConfigStringHotkeyed("remoteMessage3", "", "").apply(HOTKEYS_KEY);

		public static final ConfigYawPitchHotkeyed SNAP_ANGLE_1 = new ConfigYawPitchHotkeyed("snapAngle1", 148, -73, "").apply(HOTKEYS_KEY);
		public static final ConfigYawPitchHotkeyed SNAP_ANGLE_2 = new ConfigYawPitchHotkeyed("snapAngle2", -159, -51, "").apply(HOTKEYS_KEY);

		private static List<IConfigBase> availableOptions;
		public static final List<IConfigBase> getOptions(){
			if(availableOptions == null){
				availableOptions = new ArrayList<>();
				availableOptions.addAll(List.of(
						OPEN_CONFIG_GUI,
						MAP_COPY, MAP_COPY_BUNDLE_BETA, MAP_LOAD, MAP_MOVE, MAP_MOVE_BUNDLE, MAP_MOVE_BUNDLE_REVERSE,
						MAP_CLICK_MOVE_NEIGHBORS, MAP_CLICK_MOVE_NEIGHBORS_KEY
				));
				if(!Main.mapArtFeaturesOnly) availableOptions.addAll(List.of(
						TOGGLE_CAPE, SYNC_CAPE_WITH_ELYTRA,
						TOGGLE_HAT, TOGGLE_JACKET, TOGGLE_SLEEVE_LEFT, TOGGLE_SLEEVE_RIGHT, TOGGLE_PANTS_LEG_LEFT, TOGGLE_PANTS_LEG_RIGHT,
						AIE_TRAVEL_HELPER,
						EBOUNCE_TRAVEL_HELPER,
						EJECT_JUNK_ITEMS,
						CRAFT_RESTOCK,
						HOTBAR_TYPE_INCR, HOTBAR_TYPE_DECR,
						INV_ORGANIZE_1, TRIGGER_INV_ORGANIZE_1,
						INV_ORGANIZE_2, TRIGGER_INV_ORGANIZE_2,
						INV_ORGANIZE_3, TRIGGER_INV_ORGANIZE_3,
						INV_RESTOCK, INV_RESTOCK_LIMITS, INV_RESTOCK_BLACKLIST, INV_RESTOCK_WHITELIST,

						CHAT_MSG_1, CHAT_MSG_2, CHAT_MSG_3
				));
				if(Main.remoteSender != null) availableOptions.addAll(List.of(
						REMOTE_MSG_1, REMOTE_MSG_2, REMOTE_MSG_3
				));
				if(!Main.mapArtFeaturesOnly) availableOptions.addAll(List.of(
						SNAP_ANGLE_1, SNAP_ANGLE_2
				));
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
		public static final ConfigBoolean SHARE_MAPART = new ConfigBoolean("shareMapArt", true).apply(DATABASE_KEY); //TODO: implement
		public static final ConfigBoolean EPEARL_OWNERS_BY_UUID = new ConfigBoolean("epearlDatabaseUUID", true).apply(DATABASE_KEY);
		public static final ConfigBoolean EPEARL_OWNERS_BY_XZ = new ConfigBoolean("epearlDatabaseXZ", false).apply(DATABASE_KEY);
		//public static final ConfigBoolean SHARE_EPEARL_OWNERS = new ConfigBoolean("shareMapArt", true).apply(GENERIC_KEY); //TODO: implement
		public static final ConfigBoolean SHARE_IGNORES = new ConfigBoolean("shareIgnoreList", false).apply(DATABASE_KEY);
		public static final ConfigPlayerList BORROW_IGNORES = (ConfigPlayerList)new ConfigPlayerList("borrowIgnoreLists", List.of(
				new NameAndUUID("EvDoc", UUID.fromString("34471e8d-d0c5-47b9-b8e1-b5b9472affa4")),
				new NameAndUUID("EvModder", UUID.fromString("0e314b60-29c7-4e35-bef3-3c652c8fb467"))
		)).apply(DATABASE_KEY);
		public static final ConfigBoolean SHARE_JOIN_QUIT = new ConfigBoolean("shareJoinQuit", true).apply(DATABASE_KEY);

		private static List<IConfigBase> availableOptions;
		public static final List<IConfigBase> getOptions(){
			if(availableOptions == null){
				Main main = Main.getInstance();
				availableOptions = new ArrayList<>();
				availableOptions.addAll(List.of(CLIENT_ID, CLIENT_KEY, ADDRESS, SHARE_MAPART));
				if(Main.epearlLookup != null) availableOptions.addAll(List.of(EPEARL_OWNERS_BY_UUID, EPEARL_OWNERS_BY_XZ));
				if(main.gameMessageListener) availableOptions.add(SHARE_IGNORES);
				if(main.gameMessageFilter != null) availableOptions.add(BORROW_IGNORES);
				if(main.serverJoinListener || main.serverQuitListener) availableOptions.add(SHARE_JOIN_QUIT);
			}
			return availableOptions;
		}
	}

	private static List<IConfigBase> allOptions;
	static List<IConfigBase> allOptions(){
		if(allOptions == null){
			allOptions = new ArrayList<>();
			allOptions.addAll(Generic.getOptions());
			allOptions.addAll(Visuals.getOptions());
			allOptions.addAll(Hotkeys.getOptions());
			if(Main.remoteSender != null) allOptions.addAll(Database.getOptions());
		}
		return allOptions;
	}

	public static void loadFromFile(){
		Path configFile = FileUtils.getConfigDirectoryAsPath().resolve(CONFIG_FILE_NAME);

		if(Files.exists(configFile) && Files.isReadable(configFile)){
			JsonElement element = JsonUtils.parseJsonFileAsPath(configFile);

			if(element != null && element.isJsonObject()){
				JsonObject root = element.getAsJsonObject();

				ConfigUtils.readConfigBase(root, "Generic", Generic.getOptions());
				ConfigUtils.readConfigBase(root, "Visuals", Visuals.getOptions());
				ConfigUtils.readConfigBase(root, "Hotkeys", Hotkeys.getOptions());
				if(Main.remoteSender != null) ConfigUtils.readConfigBase(root, "Database", Database.getOptions());

				// Main.debugLog("loadFromFile(): Successfully loaded config file '{}'.", configFile.toAbsolutePath());
			}
			else Main.LOGGER.error("loadFromFile(): Failed to load config file '{}'.", configFile.toAbsolutePath());
		}
	}

	public static void saveToFile(){
		Path dir = FileUtils.getConfigDirectoryAsPath();

		if(!Files.exists(dir)){
			FileUtils.createDirectoriesIfMissing(dir);
			// Main.debugLog("saveToFile(): Creating directory '{}'.", dir.toAbsolutePath());
		}

		if(Files.isDirectory(dir)){
			JsonObject root = new JsonObject();

			ConfigUtils.writeConfigBase(root, "Generic", Generic.getOptions());
			ConfigUtils.writeConfigBase(root, "Visuals", Visuals.getOptions());
			ConfigUtils.writeConfigBase(root, "Hotkeys", Hotkeys.getOptions());
			if(Main.remoteSender != null) ConfigUtils.writeConfigBase(root, "Database", Database.getOptions());

			JsonUtils.writeJsonToFileAsPath(root, dir.resolve(CONFIG_FILE_NAME));
		}
		else Main.LOGGER.error("saveToFile(): Config Folder '{}' does not exist!", dir.toAbsolutePath());
	}

	@Override public void load(){loadFromFile();}
	@Override public void save(){saveToFile();}
}