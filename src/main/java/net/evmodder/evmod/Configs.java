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

public final class Configs implements IConfigHandler{
	int guiTab; // used by ConfigGui

	//====================================================================================================
	// Generic
	//====================================================================================================
	public static final class Generic{
		private static final String GENERIC_KEY = Main.MOD_ID+".config.generic";

		public static final ConfigInteger CLICK_LIMIT_COUNT = new ConfigInteger("clickLimitCount", 69, 0, 100_000).apply(GENERIC_KEY);
		public static final ConfigInteger CLICK_LIMIT_DURATION = new ConfigInteger("clickLimitWindow", 95, 1, 72_000).apply(GENERIC_KEY);
		public static final ConfigBoolean CLICK_LIMIT_ADJUST_FOR_TPS = new ConfigBoolean("clickLimitAdjustForTPS", false);
		public static final ConfigBoolean CLICK_LIMIT_USER_INPUT = new ConfigBoolean("clickLimitUserInputs", true).apply(GENERIC_KEY);
		public static final ConfigBoolean CLICK_FILTER_USER_INPUT = new ConfigBoolean("clickBlockUserInputsDuringOperation", true).apply(GENERIC_KEY);

		public static final ConfigBoolean USE_BUNDLE_PACKET = new ConfigBoolean("useBundlePacket", true).apply(GENERIC_KEY);
		public static final ConfigBoolean BUNDLES_ARE_REVERSED = new ConfigBoolean("bundlesAreReversed", true).apply(GENERIC_KEY);

		public static final ConfigOptionList MAP_CACHE = new ConfigOptionList("mapStateCache", OptionMapStateCache.MEMORY_AND_DISK).apply(GENERIC_KEY);
		public static final ConfigBoolean MAP_CACHE_UNLOCKED = new ConfigBoolean("mapStateCacheUnlocked", true).apply(GENERIC_KEY);
		public static final ConfigBoolean MAP_CACHE_BY_ID = new ConfigBoolean("mapStateCacheById", false).apply(GENERIC_KEY);
		public static final ConfigBoolean MAP_CACHE_BY_NAME = new ConfigBoolean("mapStateCacheByName", true).apply(GENERIC_KEY);
		//ConfigOptionList MAP_CACHE_BY_NAME{UNIQUE, FIRST}
		public static final ConfigBoolean MAP_CACHE_BY_INV_POS = new ConfigBoolean("mapStateCacheByInvPos", false).apply(GENERIC_KEY);
		public static final ConfigBoolean MAP_CACHE_BY_EC_POS = new ConfigBoolean("mapStateCacheByEchestPos", true).apply(GENERIC_KEY);
		public static final ConfigBoolean MAP_CACHE_BY_CONTAINER_POS = new ConfigBoolean("mapStateCacheByContainerPos", false).apply(GENERIC_KEY);

//		public static final ConfigBoolean NEW_MAP_NOTIFIER_ITEM_ENTITY = new ConfigBoolean("itemEntityNewMapNotifier", false).apply(GENERIC_KEY);
		public static final ConfigBoolean NEW_MAP_NOTIFIER_IFRAME = new ConfigBoolean("iFrameNewMapNotifier", false).apply(GENERIC_KEY);

		public static final ConfigInteger MAX_IFRAME_TRACKING_DIST = new ConfigInteger("iFrameTrackingDist", /*default=*/128, 0, 10_000_000);
		public static double MAX_IFRAME_TRACKING_DIST_SQ;
		static{MAX_IFRAME_TRACKING_DIST.setValueChangeCallback(d -> MAX_IFRAME_TRACKING_DIST_SQ=Math.pow(d.getIntegerValue(), 2));}

		public static final ConfigBooleanHotkeyed IFRAME_AUTO_PLACER = new ConfigBooleanHotkeyed("iFrameAutoPlacer", false, "").apply(GENERIC_KEY);
		public static final ConfigDouble IFRAME_AUTO_PLACER_REACH = new ConfigDouble("iFrameAutoPlacerReach", 3.5d).apply(GENERIC_KEY);
		// ConfigOptionList: RaycastOnly, RaycastRotatePlayer, RaycastGrimRotate
		public static final ConfigBoolean IFRAME_AUTO_PLACER_RAYCAST = new ConfigBoolean("iFrameAutoPlacerRaycast", true).apply(GENERIC_KEY);
		public static final ConfigBoolean IFRAME_AUTO_PLACER_ROTATE_PLAYER = new ConfigBoolean("iFrameAutoPlacerRotatePlayer", false).apply(GENERIC_KEY);
		public static final ConfigBoolean IFRAME_AUTO_PLACER_MUST_CONNECT = new ConfigBoolean("iFrameAutoPlacerMustConnect", true).apply(GENERIC_KEY);
		public static final ConfigBoolean IFRAME_AUTO_PLACER_MUST_MATCH_BLOCK = new ConfigBoolean("iFrameAutoPlacerMustMatchBlock", true).apply(GENERIC_KEY);

		public static final ConfigBoolean PLACEMENT_HELPER_MAPART = new ConfigBoolean("mapArtPlacer", true).apply(GENERIC_KEY);
		public static final ConfigDouble PLACEMENT_HELPER_MAPART_REACH = new ConfigDouble("mapArtPlacerReach", 3.9d).apply(GENERIC_KEY);
		public static final ConfigBoolean PLACEMENT_HELPER_MAPART_USE_NAMES = new ConfigBoolean("mapArtPlacerUseNames", true).apply(GENERIC_KEY);
		public static final ConfigBoolean PLACEMENT_HELPER_MAPART_USE_IMAGE = new ConfigBoolean("mapArtPlacerUseImage", true).apply(GENERIC_KEY);
		public static final ConfigBoolean PLACEMENT_HELPER_MAPART_FROM_BUNDLE = new ConfigBoolean("mapArtPlacerFromBundle", true).apply(GENERIC_KEY);
		public static final ConfigBooleanHotkeyed MAPART_AUTOPLACE = new ConfigBooleanHotkeyed("mapArtAutoPlace", true, "").apply(GENERIC_KEY);
		public static final ConfigInteger MAPART_AUTOPLACE_INV_DELAY = new ConfigInteger("mapArtAutoPlaceInvDelay", 5, 0, 20).apply(GENERIC_KEY);
		public static final ConfigBoolean MAPART_AUTOPLACE_ANTI_ROTATE = new ConfigBoolean("mapArtAutoPlaceAntiRotate", true).apply(GENERIC_KEY);
		public static final ConfigBoolean MAPART_AUTOPLACE_IFRAMES = new ConfigBoolean("mapArtAutoPlaceIFrames", true).apply(GENERIC_KEY);

		public static final ConfigBooleanHotkeyed MAPART_AUTOREMOVE = new ConfigBooleanHotkeyed("mapArtAutoRemove", false, "").apply(GENERIC_KEY);
		public static final ConfigInteger MAPART_AUTOREMOVE_AFTER = new ConfigInteger("mapArtAutoRemoveThreshold", 2, 1, 20, /*useSlider=*/true).apply(GENERIC_KEY);

		public static final ConfigString WHISPER_PLAY_SOUND = new ConfigString("whisperPlaySound",
				Main.mapArtFeaturesOnly ? "" : "{sound:block.note_block.bass, category:PLAYERS, volume:.7, pitch:2}").apply(GENERIC_KEY);
		public static final ConfigBoolean WHISPER_PLAY_SOUND_UNFOCUSED_ONLY = new ConfigBoolean("whisperPlaySoundUnfocusedOnly", false).apply(GENERIC_KEY);
		public static final ConfigString WHISPER_PEARL_PULL = new ConfigString("whisperPearlPull",
				Main.mapArtFeaturesOnly ? "" : "(tp|teleport|e?p|e?pearl|([iI]'?m ?)?r(ea)?dy)( pl(ea)?se?)?.?").apply(GENERIC_KEY);

		public static final ConfigString MAPART_GROUP_DEFAULT = new ConfigString("mapArtDefaultGroup", "seen/2b2t.org").apply(GENERIC_KEY);
		public static final ConfigBoolean MAPART_GROUP_INCLUDE_UNLOCKED = new ConfigBoolean("mapArtGroupIncludeUnlocked", true).apply(GENERIC_KEY);
		public static final ConfigBoolean MAPART_GROUP_ENFORCE_LOCKEDNESS_MATCH = new ConfigBoolean("mapArtGroupTreatUnlockedAsUnique",
				!Main.mapArtFeaturesOnly).apply(GENERIC_KEY);

		public static final ConfigInteger KEYBIND_BUNDLE_REMOVE_MAX = new ConfigInteger("keybindMapArtBundleRemoveMax", 64, 1, 64).apply(GENERIC_KEY);
		public static final ConfigBoolean KEYBIND_BUNDLE_PREFER_STOW = new ConfigBoolean("keybindMapArtBundlePreferStow", true).apply(GENERIC_KEY);
		public static final ConfigBoolean KEYBIND_BUNDLE_STOW_NON_SINGLE_MAPS = new ConfigBoolean("keybindMapArtBundleStowNonSingleMaps", true).apply(GENERIC_KEY);
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
		public static final ConfigStringList SEND_ON_SERVER_JOIN = new ConfigStringList("sendChatsOnServerJoin", ImmutableList.of()).apply(GENERIC_KEY);
		public static final ConfigBoolean LOG_COORDS_ON_SERVER_QUIT = new ConfigBoolean("logCoordsOnServerQuit", !Main.mapArtFeaturesOnly).apply(GENERIC_KEY);

		public static final ConfigBoolean INV_RESTOCK_AUTO = new ConfigBoolean("inventoryRestockAuto", !Main.mapArtFeaturesOnly).apply(GENERIC_KEY);
		public static final ConfigStringList INV_RESTOCK_AUTO_FOR_INV_ORGS = new ConfigStringList("inventoryRestockAutoForOrganization",
				ImmutableList.of("2")).apply(GENERIC_KEY);

		public static final ConfigString TEMP_BROADCAST_ACCOUNT = new ConfigString("broadcastAccount", "AccountNameHere").apply(GENERIC_KEY);
		public static final ConfigString TEMP_BROADCAST_TIMESTAMP = new ConfigString("broadcastTimestamp", "1738990800").apply(GENERIC_KEY);
		public static final ConfigStringList TEMP_BROADCAST_MSGS = new ConfigStringList("broadcastMsgs", ImmutableList.of()).apply(GENERIC_KEY);

		private static List<IConfigBase> options;
		private static final List<IConfigBase> getOptions(Settings settings){
			if(options != null) return options;
			options = new ArrayList<>();
			options.addAll(List.of(CLICK_LIMIT_COUNT, CLICK_LIMIT_DURATION, CLICK_LIMIT_ADJUST_FOR_TPS, CLICK_LIMIT_USER_INPUT, CLICK_FILTER_USER_INPUT,
					USE_BUNDLE_PACKET, BUNDLES_ARE_REVERSED));
			final boolean CAN_CACHE_MAPS = (settings.serverJoinListener && settings.serverQuitListener) || settings.containerOpenCloseListener;
			if(CAN_CACHE_MAPS) options.addAll(List.of(MAP_CACHE, MAP_CACHE_UNLOCKED));
			if(settings.serverJoinListener) options.add(MAP_CACHE_BY_ID);
			if(settings.containerOpenCloseListener) options.add(MAP_CACHE_BY_NAME);
			if(settings.serverJoinListener && settings.serverQuitListener) options.add(MAP_CACHE_BY_INV_POS);
			if(settings.containerOpenCloseListener) options.addAll(List.of(MAP_CACHE_BY_EC_POS, MAP_CACHE_BY_CONTAINER_POS));

			if(settings.mapHighlights) options.addAll(List.of(NEW_MAP_NOTIFIER_IFRAME, MAX_IFRAME_TRACKING_DIST));
			if(settings.placementHelperIframe) options.addAll(List.of(IFRAME_AUTO_PLACER,
					//PLACEMENT_HELPER_IFRAME_REACH, PLACEMENT_HELPER_IFRAME_RAYCAST,
					IFRAME_AUTO_PLACER_MUST_CONNECT, IFRAME_AUTO_PLACER_MUST_MATCH_BLOCK));
			else IFRAME_AUTO_PLACER.setBooleanValue(false);
			if(settings.placementHelperMapArt){
				options.addAll(List.of(PLACEMENT_HELPER_MAPART,
						//PLACEMENT_HELPER_MAPART_REACH
						PLACEMENT_HELPER_MAPART_USE_NAMES, PLACEMENT_HELPER_MAPART_USE_IMAGE,
						PLACEMENT_HELPER_MAPART_FROM_BUNDLE));
				if(settings.placementHelperMapArtAutoPlace) options.addAll(
						List.of(MAPART_AUTOPLACE, MAPART_AUTOPLACE_INV_DELAY, MAPART_AUTOPLACE_ANTI_ROTATE, MAPART_AUTOPLACE_IFRAMES));
				if(settings.placementHelperMapArtAutoRemove) options.addAll(List.of(MAPART_AUTOREMOVE, MAPART_AUTOREMOVE_AFTER));
				else MAPART_AUTOPLACE.setBooleanValue(false);
			}
			if(settings.gameMessageListener) options.addAll(List.of(WHISPER_PLAY_SOUND, WHISPER_PLAY_SOUND_UNFOCUSED_ONLY, WHISPER_PEARL_PULL));
			if(settings.cmdMapArtGroup) options.addAll(List.of(MAPART_GROUP_DEFAULT, MAPART_GROUP_INCLUDE_UNLOCKED, MAPART_GROUP_ENFORCE_LOCKEDNESS_MATCH));
//			if(settings.keybindMapArtMoveBundle)
				// Note: These settings are hidden because I don't think they should be changed.
//				options.addAll(KEYBIND_BUNDLE_REMOVE_MAX, KEYBIND_BUNDLE_PREFER_STOW, KEYBIND_BUNDLE_STOW_NON_SINGLE_MAPS);
//			if(settings.keybindMapArtMove)
				options.add(KEYBIND_MAPART_MOVE_IGNORE_AIR_POCKETS);
			if(!Main.mapArtFeaturesOnly) options.add(SCROLL_ORDER);
//			if(settings.keybindMapArtMove || settings.keybindMapArtMoveBundle)
				options.add(SKIP_TRANSPARENT_MAPS);
			if(settings.mapHighlights) options.add(SKIP_MONO_COLOR_MAPS);
			if(settings.serverJoinListener) options.add(SEND_ON_SERVER_JOIN);
			if(settings.serverQuitListener) options.add(LOG_COORDS_ON_SERVER_QUIT);
			if(settings.inventoryRestockAuto) options.addAll(List.of(INV_RESTOCK_AUTO, INV_RESTOCK_AUTO_FOR_INV_ORGS));
			if(settings.broadcaster) options.addAll(List.of(TEMP_BROADCAST_ACCOUNT, TEMP_BROADCAST_TIMESTAMP, TEMP_BROADCAST_MSGS));
			return options;
		}
	}


	//====================================================================================================
	// Visuals
	//====================================================================================================
	public static final class Visuals{
		private static final String VISUALS_KEY = Main.MOD_ID+".config.visuals";

//		public static final ConfigBoolean DISPLAY_TOTEM_COUNT = new ConfigBoolean("displayTotemCount", true).apply(VISUALS_KEY);
//		public static final ConfigBoolean SPAWNER_ACTIVATION_HIGHLIGHT = new ConfigBoolean("spawnerActivationHighlight", true).apply(VISUALS_KEY);
		public static final ConfigBoolean REPAIR_COST_HOTBAR_HUD = new ConfigBoolean("repairCostInHotbarHUD", false).apply(VISUALS_KEY);
		public static final ConfigOptionList REPAIR_COST_TOOLTIP = new ConfigOptionList("repairCostTooltip",
				Main.mapArtFeaturesOnly ? OptionTooltipDisplay.OFF : OptionTooltipDisplay.ADVANCED_TOOLTIPS).apply(VISUALS_KEY);
		public static final ConfigOptionList INVIS_IFRAMES = new ConfigOptionList("invisIFrames",
				OptionInvisIframes.SEMI_TRANSPARENT_MAPART).apply(VISUALS_KEY);

		public static final ConfigBoolean MAP_HIGHLIGHT_IFRAME = new ConfigBoolean("mapHighlightInIFrame", true).apply(VISUALS_KEY);
		public static final ConfigBoolean MAP_HIGHLIGHT_TOOLTIP = new ConfigBoolean("mapHighlightInTooltip", true).apply(VISUALS_KEY);
		public static final ConfigBoolean MAP_HIGHLIGHT_HOTBAR_HUD = new ConfigBoolean("mapHighlightInHotbarHUD", true).apply(VISUALS_KEY);
		public static final ConfigBoolean MAP_HIGHLIGHT_CONTAINER_NAME = new ConfigBoolean("mapHighlightInContainerName", true).apply(VISUALS_KEY);

		public static final ConfigBoolean MAP_HIGHLIGHT_IN_INV_INCLUDE_BUNDLES = new ConfigBoolean("mapHighlightInInvIncludeBundles", false).apply(VISUALS_KEY);

		public static final ConfigColor MAP_COLOR_UNLOADED = new ConfigColor("highlightColorUnloaded", "#FFC8AAD2").apply(VISUALS_KEY); // 13150930 Peach
		public static final ConfigColor MAP_COLOR_UNLOCKED = new ConfigColor("highlightColorUnlocked", "#FFE03165").apply(VISUALS_KEY); // 14692709 Redish
		public static final ConfigColor MAP_COLOR_UNNAMED = new ConfigColor("highlightColorUnnamed", "#FFEED7D7").apply(VISUALS_KEY); // 15652823 Pink
		public static final ConfigColor MAP_COLOR_NOT_IN_GROUP = new ConfigColor("highlightColorNotInGroup", "#FF0AC864").apply(VISUALS_KEY); // 706660 Green
		public static final ConfigColor MAP_COLOR_IN_INV = new ConfigColor("highlightColorInInv", "#FFB4FFFF").apply(VISUALS_KEY); // 11862015 Aqua
		public static final ConfigColor MAP_COLOR_IN_IFRAME = new ConfigColor("highlightColorInIFrame", "#FF55AAE6").apply(VISUALS_KEY); // 5614310 Blue
		public static final ConfigColor MAP_COLOR_MULTI_IFRAME = new ConfigColor("highlightColorMultiIFrame", "#FFB450E6").apply(VISUALS_KEY); // 11817190 Purple
		public static final ConfigColor MAP_COLOR_MULTI_CONTAINER = new ConfigColor("highlightColorMultiContainer", "#FFB450E6").apply(VISUALS_KEY); // 11817190 Purple

//		public static final ConfigBoolean MAP_METADATA_TOOLTIP = new ConfigBoolean("mapMetadataTooltip", true);
		public static final ConfigBoolean MAP_METADATA_TOOLTIP_STAIRCASE = new ConfigBoolean("mapMetadataTooltipStaircase", true).apply(VISUALS_KEY);
		public static final ConfigBoolean MAP_METADATA_TOOLTIP_MATERIAL = new ConfigBoolean("mapMetadataTooltipMaterial", true).apply(VISUALS_KEY);
		public static final ConfigBoolean MAP_METADATA_TOOLTIP_NUM_COLORS = new ConfigBoolean("mapMetadataTooltipNumColors", true).apply(VISUALS_KEY);
		public static final ConfigBoolean MAP_METADATA_TOOLTIP_NUM_COLOR_IDS = new ConfigBoolean("mapMetadataTooltipNumColorIds", true).apply(VISUALS_KEY);
		public static final ConfigBoolean MAP_METADATA_TOOLTIP_TRANSPARENCY = new ConfigBoolean("mapMetadataTooltipTransparency", true).apply(VISUALS_KEY);
		public static final ConfigBoolean MAP_METADATA_TOOLTIP_NOOBLINE = new ConfigBoolean("mapMetadataTooltipNoobline", true).apply(VISUALS_KEY);
		public static final ConfigBoolean MAP_METADATA_TOOLTIP_PERCENT_CARPET = new ConfigBoolean("mapMetadataTooltipPercentCarpet", true).apply(VISUALS_KEY);
		public static final ConfigBoolean MAP_METADATA_TOOLTIP_PERCENT_STAIRCASE = new ConfigBoolean("mapMetadataTooltipPercentStaircase", true).apply(VISUALS_KEY);
		public static final ConfigBoolean MAP_METADATA_TOOLTIP_UUID = new ConfigBoolean("mapMetadataTooltipHashCode", false).apply(VISUALS_KEY);

		public static final ConfigInteger EXPORT_MAP_IMG_UPSCALE = new ConfigInteger("exportMapImageUpscale", 128, 128, 1280).apply(VISUALS_KEY);
		public static final ConfigBoolean EXPORT_MAP_IMG_BORDER = new ConfigBoolean("exportMapImageBorder", false).apply(VISUALS_KEY);
		public static final ConfigColor EXPORT_MAP_IMG_BORDER_COLOR1 = new ConfigColor("exportMapImageBorderColor1", "#FFFFC864").apply(VISUALS_KEY); // -14236 Yellow
		public static final ConfigColor EXPORT_MAP_IMG_BORDER_COLOR2 = new ConfigColor("exportMapImageBorderColor2", "#00322D32").apply(VISUALS_KEY); // 3288370 Gray

		private static List<IConfigBase> options;
		private static final List<IConfigBase> getOptions(Settings settings){
			if(options != null) return options;
			options = new ArrayList<>();
			if(!Main.mapArtFeaturesOnly){
				options.add(REPAIR_COST_HOTBAR_HUD);
				if(settings.tooltipRepairCost) options.add(REPAIR_COST_TOOLTIP);
			}
			options.add(INVIS_IFRAMES);
			if(settings.mapHighlights){
				options.add(MAP_HIGHLIGHT_IFRAME);
				if(settings.tooltipMapHighlights) options.add(MAP_HIGHLIGHT_TOOLTIP);
				options.add(MAP_HIGHLIGHT_HOTBAR_HUD);
				if(settings.mapHighlightsInGUIs) options.add(MAP_HIGHLIGHT_CONTAINER_NAME);
				options.addAll(List.of(
					MAP_HIGHLIGHT_IN_INV_INCLUDE_BUNDLES,
					MAP_COLOR_IN_INV, MAP_COLOR_NOT_IN_GROUP, MAP_COLOR_UNLOCKED,
					MAP_COLOR_UNLOADED, MAP_COLOR_UNNAMED, MAP_COLOR_IN_IFRAME,
					MAP_COLOR_MULTI_IFRAME, MAP_COLOR_MULTI_CONTAINER
				));
			}
			if(settings.tooltipMapMetadata) options.addAll(List.of(
//					MAP_METADATA_TOOLTIP,
					MAP_METADATA_TOOLTIP_STAIRCASE, MAP_METADATA_TOOLTIP_MATERIAL,
					MAP_METADATA_TOOLTIP_NUM_COLORS, MAP_METADATA_TOOLTIP_NUM_COLOR_IDS,
					MAP_METADATA_TOOLTIP_TRANSPARENCY, MAP_METADATA_TOOLTIP_NOOBLINE,
					MAP_METADATA_TOOLTIP_PERCENT_CARPET, MAP_METADATA_TOOLTIP_PERCENT_STAIRCASE, MAP_METADATA_TOOLTIP_UUID
			));
			if(settings.cmdExportMapImg){
				options.addAll(List.of(
						EXPORT_MAP_IMG_UPSCALE,
						EXPORT_MAP_IMG_BORDER,
						EXPORT_MAP_IMG_BORDER_COLOR1, EXPORT_MAP_IMG_BORDER_COLOR2));
			}
			return options;
		}
	}


	//====================================================================================================
	// Hotkeys
	//====================================================================================================
	public static final class Hotkeys{
		private static final String HOTKEYS_KEY = Main.MOD_ID+".config.hotkeys";

		private static final KeybindSettings GUI_OR_INGAME_SETTINGS = KeybindSettings.create(Context.ANY,
				KeyAction.PRESS, /*allowExtraKeys=*/false, /*orderSensitive=*/true, /*exclusive=*/false, /*cancel=*/true);
		private static final KeybindSettings GUI_ALLOW_EXTRA_KEYS = KeybindSettings.create(Context.GUI,
				KeyAction.PRESS, /*allowExtraKeys=*/true, /*orderSensitive=*/true, /*exclusive=*/false, /*cancel=*/true);

		public static final ConfigHotkey OPEN_CONFIG_GUI = new ConfigHotkey("openConfigGui", "M,N").apply(HOTKEYS_KEY);

		public static final ConfigHotkey MAP_COPY = new ConfigHotkey("mapCopy", "T", KeybindSettings.GUI).apply(HOTKEYS_KEY);
		public static final ConfigHotkey MAP_LOAD = new ConfigHotkey("mapLoad", "E", KeybindSettings.GUI).apply(HOTKEYS_KEY);
		public static final ConfigHotkey MAP_MOVE = new ConfigHotkey("mapMove", "T", GUI_ALLOW_EXTRA_KEYS).apply(HOTKEYS_KEY);
		public static final ConfigHotkey MAP_MOVE_ALL_MODIFIER = new ConfigHotkey("mapMoveAllModifier", "SHIFT",
				KeybindSettings.MODIFIER_GUI).apply(HOTKEYS_KEY);
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
		public static final ConfigHotkey TOGGLE_PANTS_LEG_RIGHT = new ConfigHotkey("togglePantsLegRight", Main.mapArtFeaturesOnly ? "" : "I");

		public static final ConfigBooleanHotkeyed AIE_TRAVEL_HELPER = new ConfigBooleanHotkeyed("automaticInfiniteElytraTravelHelper", false,
				Main.mapArtFeaturesOnly ? "" : "SEMICOLON", KeybindSettings.NOCANCEL).apply(HOTKEYS_KEY);
		public static final ConfigBooleanHotkeyed EBOUNCE_TRAVEL_HELPER = new ConfigBooleanHotkeyed("eBounceTravelHelper", false,
				Main.mapArtFeaturesOnly ? "" : "A", KeybindSettings.PRESS_ALLOWEXTRA).apply(HOTKEYS_KEY);
		public static final ConfigHotkey EJECT_JUNK_ITEMS = new ConfigHotkey("ejectJunkItems",
				Main.mapArtFeaturesOnly ? "" : "R", GUI_OR_INGAME_SETTINGS).apply(HOTKEYS_KEY);
		public static final ConfigHotkey CRAFT_RESTOCK = new ConfigHotkey("craftingRestock",
				Main.mapArtFeaturesOnly ? "" : " ", KeybindSettings.GUI).apply(HOTKEYS_KEY);
		public static final ConfigHotkey HOTBAR_TYPE_INCR = new ConfigHotkey("hotbarSlotItemTypeIncrement", "").apply(HOTKEYS_KEY);
		public static final ConfigHotkey HOTBAR_TYPE_DECR = new ConfigHotkey("hotbarSlotItemTypeDecrement", "").apply(HOTKEYS_KEY);

		//TODO: ConfigSlotList, ConfigSlotListHotkeyed
		public static final ConfigHotkey INV_RESTOCK = new ConfigHotkey("inventoryRestock",
				Main.mapArtFeaturesOnly ? "" : "R", KeybindSettings.GUI).apply(HOTKEYS_KEY);
		public static final ConfigOptionList INV_RESTOCK_IF = new ConfigOptionList("inventoryRestockIf", OptionInventoryRestockIf.RESUPPLY).apply(HOTKEYS_KEY);
		public static final ConfigOptionList INV_RESTOCK_LEAVE = new ConfigOptionList("inventoryRestockLeave", OptionInventoryRestockLeave.ONE_ITEM).apply(HOTKEYS_KEY);
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
		public static final ConfigStringHotkeyed CHAT_MSG_3 = new ConfigStringHotkeyed("chatMessage3",
				"/cgetdata entity @e[type=item,distance=..5,limit=1] Item.components.minecraft:repair_cost", "").apply(HOTKEYS_KEY);
		public static final ConfigHotkey TRIGGER_CHAT_MSG_3 = new ConfigHotkey("triggerChatMessage3",
				Main.mapArtFeaturesOnly ? "" : "Z").apply(HOTKEYS_KEY);

		public static final ConfigStringHotkeyed REMOTE_MSG_1 = new ConfigStringHotkeyed("remoteMessage1",
				"EPEARL_TRIGGER,6f7fa766-4fe6-42fe-b589-22e4ec9a077c", "").apply(HOTKEYS_KEY);
		public static final ConfigStringHotkeyed REMOTE_MSG_2 = new ConfigStringHotkeyed("remoteMessage2", "", "").apply(HOTKEYS_KEY);
		public static final ConfigStringHotkeyed REMOTE_MSG_3 = new ConfigStringHotkeyed("remoteMessage3", "", "").apply(HOTKEYS_KEY);

		public static final ConfigYawPitchHotkeyed SNAP_ANGLE_1 = new ConfigYawPitchHotkeyed("snapAngle1", 148, -73, "").apply(HOTKEYS_KEY);
		public static final ConfigYawPitchHotkeyed SNAP_ANGLE_2 = new ConfigYawPitchHotkeyed("snapAngle2", -159, -51, "").apply(HOTKEYS_KEY);

		private static List<IConfigBase> options;
		private static final List<IConfigBase> getOptions(Settings settings){
			if(options != null) return options;
			options = new ArrayList<>();
			options.addAll(List.of(
					OPEN_CONFIG_GUI,
					MAP_COPY, MAP_LOAD, MAP_MOVE, MAP_MOVE_ALL_MODIFIER,
					MAP_MOVE_BUNDLE, MAP_MOVE_BUNDLE_REVERSE,
					MAP_CLICK_MOVE_NEIGHBORS, MAP_CLICK_MOVE_NEIGHBORS_KEY
			));
			if(!Main.mapArtFeaturesOnly) options.addAll(List.of(
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
					INV_RESTOCK, INV_RESTOCK_IF, INV_RESTOCK_LEAVE, INV_RESTOCK_BLACKLIST, INV_RESTOCK_WHITELIST,

					CHAT_MSG_1, CHAT_MSG_2, CHAT_MSG_3
			));
			if(settings.database) options.addAll(List.of(
					REMOTE_MSG_1, REMOTE_MSG_2, REMOTE_MSG_3
			));
			if(!Main.mapArtFeaturesOnly) options.addAll(List.of(
					SNAP_ANGLE_1, SNAP_ANGLE_2
			));
			return options;
		}
	}


	//====================================================================================================
	// Database
	//====================================================================================================
	private static final String DATABASE_KEY = Main.MOD_ID+".config.database";
	public static class Database{
//		public static final ConfigOptionList PLACEMENT_WARN = new ConfigOptionList("placementWarn", MessageOutputType.ACTIONBAR).apply(DATABASE_KEY);
		public static final ConfigInteger CLIENT_ID = new ConfigInteger("clientId", InitUtils.DUMMY_CLIENT_ID, 0, 1000000).apply(DATABASE_KEY);
		public static final ConfigString CLIENT_KEY = new ConfigString("clientKey", "").apply(DATABASE_KEY);
		public static final ConfigString ADDRESS = new ConfigString("address", "evmodder.net:14441").apply(DATABASE_KEY);
		public static final ConfigBoolean SAVE_MAPART = new ConfigBoolean("saveSeenMapArt", !Main.mapArtFeaturesOnly).apply(DATABASE_KEY);
		public static final ConfigBoolean SHARE_MAPART = new ConfigBoolean("shareSeenMapArt", !Main.mapArtFeaturesOnly).apply(DATABASE_KEY);
		public static final ConfigBoolean EPEARL_OWNERS_BY_UUID = new ConfigBoolean("saveEpearlOwnersByUUID", !Main.mapArtFeaturesOnly).apply(DATABASE_KEY);
		public static final ConfigBoolean EPEARL_OWNERS_BY_XZ = new ConfigBoolean("saveEpearlOwnersByXZ", false).apply(DATABASE_KEY);
		public static final ConfigBoolean SHARE_EPEARL_OWNERS = new ConfigBoolean("shareEpearlOwners", !Main.mapArtFeaturesOnly).apply(DATABASE_KEY);
		public static final ConfigBoolean SAVE_IGNORES = new ConfigBoolean("saveIgnoreList", !Main.mapArtFeaturesOnly).apply(DATABASE_KEY);
		public static final ConfigBoolean SHARE_IGNORES = new ConfigBoolean("shareIgnoreList", false).apply(DATABASE_KEY);
		// Requires SAVE_IGNORES=true
		public static final ConfigPlayerList BORROW_IGNORES = (ConfigPlayerList)new ConfigPlayerList("borrowIgnoreLists", List.of(
				new NameAndUUID("EvDoc", UUID.fromString("34471e8d-d0c5-47b9-b8e1-b5b9472affa4")),
				new NameAndUUID("EvModder", UUID.fromString("0e314b60-29c7-4e35-bef3-3c652c8fb467"))
		)).apply(DATABASE_KEY);
		// Note: I decided to disable this by default, and hide it, because running a single proxy/bot is much better suited for collecting this data
		public static final ConfigBoolean SHARE_JOIN_QUIT = new ConfigBoolean("shareJoinQuit", false).apply(DATABASE_KEY);

		private static List<IConfigBase> options;
		private static final List<IConfigBase> getOptions(Settings settings){
			if(options != null) return options;
			options = new ArrayList<>();
			if(settings.database) options.addAll(List.of(CLIENT_ID, CLIENT_KEY, ADDRESS));
			options.add(SAVE_MAPART);
			if(settings.database) options.add(SHARE_MAPART);
			if(settings.epearlOwners){
				options.addAll(List.of(EPEARL_OWNERS_BY_UUID, EPEARL_OWNERS_BY_XZ));
				if(settings.database) options.add(SHARE_EPEARL_OWNERS);
			}
			if(settings.gameMessageListener || settings.gameMessageFilter) options.add(SAVE_IGNORES);
			if(settings.database && settings.gameMessageListener) options.add(SHARE_IGNORES);
			if(settings.database && settings.gameMessageFilter) options.add(BORROW_IGNORES);
//			if(settings.database && (settings.serverJoinListener || settings.serverQuitListener)) options.add(SHARE_JOIN_QUIT);
			return options;
		}
	}

	//====================================================================================================
	// Config option saving/loading/fetching
	//====================================================================================================
	private static final String CONFIG_FILE_NAME = Main.MOD_ID+"/"+Main.MOD_ID+".json";

	private final Settings settings;
	Configs(Settings settings){this.settings = settings;}

	final List<IConfigBase> getGenericOptions(){return Generic.getOptions(settings);}
	final List<IConfigBase> getVisualsOptions(){return Visuals.getOptions(settings);}
	final List<IConfigBase> getHotkeysOptions(){return Hotkeys.getOptions(settings);}
	final List<IConfigBase> getDatabaseOptions(){return Database.getOptions(settings);}

	private static List<IConfigBase> allOptions;
	final List<IConfigBase> getAllOptions(){
		if(allOptions != null) return allOptions;
		allOptions = new ArrayList<>();
		allOptions.addAll(getGenericOptions());
		allOptions.addAll(getVisualsOptions());
		allOptions.addAll(getHotkeysOptions());
		allOptions.addAll(getDatabaseOptions());
		return allOptions;
	}

	@Override public void load(){
		Path configFile = FileUtils.getConfigDirectoryAsPath().resolve(CONFIG_FILE_NAME);
		if(Files.exists(configFile) && Files.isReadable(configFile)){
			JsonElement element = JsonUtils.parseJsonFileAsPath(configFile);
			if(element != null && element.isJsonObject()){
				JsonObject root = element.getAsJsonObject();
				JsonElement ele = root.get("guiTab");
				if(ele != null) guiTab = ele.getAsInt();
				ConfigUtils.readConfigBase(root, "Generic", getGenericOptions());
				ConfigUtils.readConfigBase(root, "Visuals", getVisualsOptions());
				ConfigUtils.readConfigBase(root, "Hotkeys", getHotkeysOptions());
				if(settings.database) ConfigUtils.readConfigBase(root, "Database", getDatabaseOptions());
				// Main.debugLog("loadFromFile(): Successfully loaded config file '{}'.", configFile.toAbsolutePath());
			}
			else Main.LOGGER.error("loadFromFile(): Failed to load config file '{}'.", configFile.toAbsolutePath());
		}
	}

	@Override public void save(){
		Path dir = FileUtils.getConfigDirectoryAsPath();
		if(!Files.exists(dir)){
			FileUtils.createDirectoriesIfMissing(dir);
			// Main.debugLog("saveToFile(): Creating directory '{}'.", dir.toAbsolutePath());
		}
		if(Files.isDirectory(dir)){
			JsonObject root = new JsonObject();
			root.addProperty("guiTab", guiTab);
			ConfigUtils.writeConfigBase(root, "Generic", getGenericOptions());
			ConfigUtils.writeConfigBase(root, "Visuals", getVisualsOptions());
			ConfigUtils.writeConfigBase(root, "Hotkeys", getHotkeysOptions());
			if(settings.database) ConfigUtils.writeConfigBase(root, "Database", getDatabaseOptions());
			JsonUtils.writeJsonToFileAsPath(root, dir.resolve(CONFIG_FILE_NAME));
		}
		else Main.LOGGER.error("saveToFile(): Config Folder '{}' does not exist!", dir.toAbsolutePath());
	}
}