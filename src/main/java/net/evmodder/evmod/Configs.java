package net.evmodder.evmod;

import java.io.IOException;
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
import fi.dy.masa.malilib.util.data.json.JsonUtils;
import net.evmodder.evmod.ConfigGui.ConfigGuiTab;
import net.evmodder.evmod.config.*;
import net.evmodder.evmod.config.ConfigPlayerList.NameAndUUID;

public final class Configs implements IConfigHandler{
	int guiTab;  // used by ConfigGui
	private double serverListScroll;
	double getServerListScroll(){return serverListScroll;}
	void setServerListScroll(double scroll){
		if(serverListScroll == scroll) return;
		serverListScroll = scroll;
		save();
	}
	private static final boolean MAPART_ONLY_DEFAULTS = Settings.get().enabled(Setting.MAPART_ONLY_DEFAULTS);

	//====================================================================================================
	// Generic
	//====================================================================================================
	public static final class Generic{
		private static final String GENERIC_KEY = Main.MOD_ID+".config.generic";

		public static final ConfigInteger CLICK_LIMIT_COUNT = new ConfigInteger("clickLimitCount", 77, 0, 100_000).apply(GENERIC_KEY);
		public static final ConfigInteger CLICK_LIMIT_WINDOW = new ConfigInteger("clickLimitWindow", 111, 1, 72_000).apply(GENERIC_KEY);
		public static final ConfigBoolean CLICK_LIMIT_ADJUST_FOR_TPS = new ConfigBoolean("clickLimitAdjustForTPS", false).apply(GENERIC_KEY);
		public static final ConfigBoolean CLICK_LIMIT_USER_INPUT = new ConfigBoolean("clickLimitUserInputs", true).apply(GENERIC_KEY);
		public static final ConfigBoolean CLICK_FILTER_USER_INPUT = new ConfigBoolean("clickBlockUserInputsDuringOperation", true).apply(GENERIC_KEY);

		public static final ConfigBoolean USE_BUNDLE_PACKET = new ConfigBoolean("useBundlePacket", true).apply(GENERIC_KEY);
		public static final ConfigBoolean BUNDLES_ARE_REVERSED = new ConfigBoolean("bundlesAreReversed", true).apply(GENERIC_KEY);

		public static final ConfigString MAPART_GROUP_DEFAULT = new ConfigString("mapArtDefaultGroup", "seen/2b2t.org").apply(GENERIC_KEY);
		public static final ConfigOptionList MAPART_GROUP_UNLOCKED_HANDLING = new ConfigOptionList("mapArtGroupUnlockedMapHandling",
				OptionUnlockedMapHandling.SKIP).apply(GENERIC_KEY);

//		public static final ConfigBoolean NEW_MAP_NOTIFIER_ITEM_ENTITY = new ConfigBoolean("itemEntityNewMapNotifier", false).apply(GENERIC_KEY);

		public static final ConfigInteger MAX_IFRAME_TRACKING_DIST = new ConfigInteger("iFrameTrackingDist", /*default=*/128, 0, 10_000_000).apply(GENERIC_KEY);
		public static double MAX_IFRAME_TRACKING_DIST_SQ;
		static{MAX_IFRAME_TRACKING_DIST.setValueChangeCallback(d -> MAX_IFRAME_TRACKING_DIST_SQ=Math.pow(d.getIntegerValue(), 2));}

		public static final ConfigBooleanHotkeyed IFRAME_AUTO_PLACER = new ConfigBooleanHotkeyed("iFrameAutoPlacer", false, "").apply(GENERIC_KEY);
		public static final ConfigBoolean IFRAME_AUTO_PLACER_MUST_CONNECT = new ConfigBoolean("iFrameAutoPlacerMustConnect", true).apply(GENERIC_KEY);
		public static final ConfigBoolean IFRAME_AUTO_PLACER_MUST_MATCH_BLOCK = new ConfigBoolean("iFrameAutoPlacerMustMatchBlock", true).apply(GENERIC_KEY);
		public static final ConfigDouble IFRAME_AUTO_PLACER_REACH = new ConfigDouble("iFrameAutoPlacerReach", 3.5d).apply(GENERIC_KEY);
		// ConfigOptionList: RaycastOnly, RaycastRotatePlayer, RaycastGrimRotate
		public static final ConfigBoolean IFRAME_AUTO_PLACER_RAYCAST = new ConfigBoolean("iFrameAutoPlacerRaycast", true).apply(GENERIC_KEY);
		public static final ConfigBoolean IFRAME_AUTO_PLACER_ROTATE_PLAYER = new ConfigBoolean("iFrameAutoPlacerRotatePlayer", false).apply(GENERIC_KEY);

		public static final ConfigBoolean IFRAME_DISALLOW_OFFHAND = new ConfigBoolean("iFrameDisallowOffhand", true).apply(GENERIC_KEY);

		public static final ConfigBoolean PLACEMENT_HELPER_MAPART = new ConfigBoolean("mapArtPlacer", true).apply(GENERIC_KEY);
		public static final ConfigBoolean PLACEMENT_HELPER_MAPART_USE_NAMES = new ConfigBoolean("mapArtPlacerUseNames", true).apply(GENERIC_KEY);
		public static final ConfigBoolean PLACEMENT_HELPER_MAPART_USE_IMAGE = new ConfigBoolean("mapArtPlacerUseImage", true).apply(GENERIC_KEY);
		public static final ConfigBoolean PLACEMENT_HELPER_MAPART_FROM_BUNDLE = new ConfigBoolean("mapArtPlacerFromBundle", true).apply(GENERIC_KEY);
		public static final ConfigBooleanHotkeyed MAPART_AUTOPLACE = new ConfigBooleanHotkeyed("mapArtAutoPlace", true, "").apply(GENERIC_KEY);
		public static final ConfigInteger MAPART_AUTOPLACE_INV_DELAY = new ConfigInteger("mapArtAutoPlaceInvDelay", 5, 0, 20).apply(GENERIC_KEY);
		public static final ConfigBoolean MAPART_AUTOPLACE_ANTI_ROTATE = new ConfigBoolean("mapArtAutoPlaceAntiRotate", true).apply(GENERIC_KEY);
		public static final ConfigBoolean MAPART_AUTOPLACE_IFRAMES = new ConfigBoolean("mapArtAutoPlaceIFrames", true).apply(GENERIC_KEY);
		public static final ConfigDouble MAPART_AUTOPLACE_REACH = new ConfigDouble("mapArtAutoPlaceReach", 3.9d).apply(GENERIC_KEY);

		public static final ConfigBooleanHotkeyed MAPART_AUTOREMOVE = new ConfigBooleanHotkeyed("mapArtAutoRemove", false, "").apply(GENERIC_KEY);
		public static final ConfigInteger MAPART_AUTOREMOVE_AFTER = new ConfigInteger("mapArtAutoRemoveThreshold", 2, 1, 20, /*useSlider=*/true).apply(GENERIC_KEY);
		public static final ConfigDouble MAPART_AUTOREMOVE_REACH = new ConfigDouble("mapArtAutoRemoveReach", 3.9d).apply(GENERIC_KEY);

		public static final ConfigBoolean SKIP_NULL_MAPS = new ConfigBoolean("ignoreNullMapsInHighlightsAndKeybinds", false).apply(GENERIC_KEY);
		public static final ConfigBoolean SKIP_VOID_MAPS = new ConfigBoolean("ignoreTransparentMapsInHighlightsAndKeybinds", true).apply(GENERIC_KEY);
		public static final ConfigBoolean SKIP_MONO_COLOR_MAPS = new ConfigBoolean("ignoreMonoColorMapsInHightlights", false).apply(GENERIC_KEY);

		public static final ConfigString WHISPER_PLAY_SOUND = new ConfigString("whisperPlaySound",
				MAPART_ONLY_DEFAULTS ? "" : "{sound:block.note_block.bass, category:PLAYERS, volume:.7, pitch:2}").apply(GENERIC_KEY);
		public static final ConfigString WHISPER_PLAY_SOUND_UNFOCUSED = new ConfigString("whisperPlaySoundUnfocused",
				MAPART_ONLY_DEFAULTS ? "" : "{sound:block.note_block.bass, category:PLAYERS, volume:4, pitch:2}").apply(GENERIC_KEY);
		public static final ConfigString WHISPER_PEARL_PULL = new ConfigString("whisperPearlPull",
				MAPART_ONLY_DEFAULTS ? "" : "!?(load|go|14|tp|teleport|e?p|e?pearl|([iI]'?m ?)?r(ea)?dy)( me)?( pl(ea)?se?)?[.!?]?").apply(GENERIC_KEY);
		public static final ConfigBoolean MUTE_MUSIC_ON_MENU_SCREEN = new ConfigBoolean("muteMusicOnMenuScreen", false).apply(GENERIC_KEY);
		public static final ConfigBoolean REMEMBER_SERVER_LIST_SCROLL = new ConfigBoolean("rememberServerListScroll", false).apply(GENERIC_KEY);
		public static final ConfigBoolean DONT_WASTE_FIREWORKS = new ConfigBoolean("dontWasteFireworks", !MAPART_ONLY_DEFAULTS).apply(GENERIC_KEY);

//		public static final ConfigBoolean MAPART_GROUP_INCLUDE_UNLOCKED = new ConfigBoolean("mapArtGroupIncludeUnlocked", true).apply(GENERIC_KEY);
//		public static final ConfigBoolean MAPART_GROUP_ENFORCE_LOCKEDNESS_MATCH = new ConfigBoolean("mapArtGroupTreatUnlockedAsUnique", false).apply(GENERIC_KEY);

		public static final ConfigStringList SEND_ON_SERVER_JOIN = new ConfigStringList("sendChatsOnServerJoin", ImmutableList.of()).apply(GENERIC_KEY);
		public static final ConfigBoolean LOG_COORDS_ON_SERVER_QUIT = new ConfigBoolean("logCoordsOnServerQuit", !MAPART_ONLY_DEFAULTS).apply(GENERIC_KEY);

		public static final ConfigOptionList INV_RESTOCK_IF = new ConfigOptionList("inventoryRestockIf", OptionInventoryRestockIf.RESUPPLY).apply(GENERIC_KEY);
		public static final ConfigOptionList INV_RESTOCK_LEAVE = new ConfigOptionList("inventoryRestockLeave", OptionInventoryRestockLeave.ONE_ITEM).apply(GENERIC_KEY);
		public static final ConfigStringList INV_RESTOCK_BLACKLIST = new ConfigStringList("inventoryRestockBlacklist", ImmutableList.of(
//				"ender_chest", "filled_map"
		)).apply(GENERIC_KEY);
		public static final ConfigStringList INV_RESTOCK_WHITELIST = new ConfigStringList("inventoryRestockWhitelist", ImmutableList.of(
				"firework_rocket", "end_crystal", "respawn_anchor", "obsidian", "ender_pearl", "chorus_fruit", "wind_charge", "experience_bottle",
				"arrow", "spectral_arrow", "tipped_arrow", "golden_carrot", "enchanted_golden_apple", "cooked_beef", "cooked_porkchop", "cooked_chicken"
		)).apply(GENERIC_KEY);
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
				"38:netherite_axe", "38:diamond_axe", "38:netherite_shovel", "38:diamond_shovel", "38:diamond_hoe", "38:netherite_hoe",
					"38:firework_rocket", "38:end_crystal",
				"39:netherite_shovel", "39:diamond_shovel", "39:diamond_hoe", "39:netherite_hoe", "39:fishing_rod", "39:firework_rocket",
				"40:diamond_hoe", "40:netherite_hoe", "40:fishing_rod", "40:firework_rocket",
				"41:firework_rocket",

				"# Hotbar RHS",
				"44:golden_carrot", "44:cooked_beef", "44:enchanted_golden_apple",
				"43:enchanted_golden_apple",

				"# Inventory top LHS",
				"9:netherite_chestplate", "9:diamond_chestplate", "9:elytra", "9:netherite_helmet", "9:diamond_helmet", "9:ender_chest", "9:totem_of_undying",
				"10:netherite_helmet", "10:diamond_helmet", "10:ender_chest", "10:totem_of_undying",
				"11:ender_chest", "11:totem_of_undying", "12:totem_of_undying", "13:totem_of_undying",

				"# Extra rockets, food",
				"30:firework_rocket", "21:firework_rocket",
				"35:enchanted_golden_apple", "35:golden_carrot", "35:cooked_beef", "35:cooked_porkchop", "35:pumpkin_pie",

				"# Fill extra hotbar slots with crystals",
				"36:end_crystal", "37:end_crystal", "38:end_crystal", "39:end_crystal", "40:end_crystal", "41:end_crystal", "42:end_crystal"
		)).apply(GENERIC_KEY);
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
		)).apply(GENERIC_KEY);
		public static final ConfigStringList INV_ORGANIZE_3 = new ConfigStringList("inventoryOrganize3", ImmutableList.of()).apply(GENERIC_KEY);

		public static final ConfigBoolean INV_RESTOCK_AUTO = new ConfigBoolean("inventoryRestockAuto", !MAPART_ONLY_DEFAULTS).apply(GENERIC_KEY);
		public static final ConfigStringList INV_RESTOCK_AUTO_FOR_INV_ORGS = new ConfigStringList("inventoryRestockAutoForOrganization",
				ImmutableList.of("2")).apply(GENERIC_KEY);

		public static final ConfigString TEMP_BROADCAST_ACCOUNT = new ConfigString("broadcastAccount", "AccountNameHere").apply(GENERIC_KEY);
		public static final ConfigString TEMP_BROADCAST_TIMESTAMP = new ConfigString("broadcastTimestamp", "1738990800").apply(GENERIC_KEY);
		public static final ConfigStringList TEMP_BROADCAST_MSGS = new ConfigStringList("broadcastMsgs", ImmutableList.of()).apply(GENERIC_KEY);

		public static final ConfigBoolean DISABLE_DRAG_CLICK_ON_MAPS_AND_BUNDLES = new ConfigBoolean("fix2b2tGhostItems", true).apply(GENERIC_KEY);

		private static List<IConfigBase> configs;
		private static final List<IConfigBase> getConfigs(Settings settings){
			if(configs != null) return configs;
			configs = new ArrayList<>();
			if(!settings.enabled(Setting.CLICK_SAFETY)){
				CLICK_LIMIT_USER_INPUT.setBooleanValue(false);
				CLICK_FILTER_USER_INPUT.setBooleanValue(false);
			}
			if(settings.usesClicks()){
				configs.addAll(List.of(CLICK_LIMIT_COUNT, CLICK_LIMIT_WINDOW, CLICK_LIMIT_ADJUST_FOR_TPS));
				if(settings.enabled(Setting.CLICK_SAFETY)) configs.addAll(List.of(CLICK_LIMIT_USER_INPUT, CLICK_FILTER_USER_INPUT));
			}
			configs.addAll(List.of(USE_BUNDLE_PACKET, BUNDLES_ARE_REVERSED));
			if(settings.cmdMapArtGroup){
				configs.addAll(List.of(MAPART_GROUP_DEFAULT, MAPART_GROUP_UNLOCKED_HANDLING));
			}
			if(settings.onTickIframes) configs.add(MAX_IFRAME_TRACKING_DIST);
			if(settings.placementHelperIframeAutoPlace){
				configs.addAll(List.of(IFRAME_AUTO_PLACER, IFRAME_AUTO_PLACER_MUST_CONNECT, IFRAME_AUTO_PLACER_MUST_MATCH_BLOCK));
				if(settings.enabled(Setting.SHOW_NICHE_CONFIG_SETTINGS)) configs.addAll(List.of(IFRAME_AUTO_PLACER_REACH, IFRAME_AUTO_PLACER_RAYCAST, IFRAME_AUTO_PLACER_ROTATE_PLAYER));
			}
			else IFRAME_AUTO_PLACER.setBooleanValue(false);
			configs.add(IFRAME_DISALLOW_OFFHAND);
			if(settings.placementHelperMapArt){
				configs.addAll(List.of(PLACEMENT_HELPER_MAPART,
						PLACEMENT_HELPER_MAPART_USE_NAMES, PLACEMENT_HELPER_MAPART_USE_IMAGE,
						PLACEMENT_HELPER_MAPART_FROM_BUNDLE));
				if(settings.placementHelperMapArtAutoPlace){
					configs.addAll(List.of(MAPART_AUTOPLACE, MAPART_AUTOPLACE_INV_DELAY, MAPART_AUTOPLACE_ANTI_ROTATE, MAPART_AUTOPLACE_IFRAMES));
					if(settings.enabled(Setting.SHOW_NICHE_CONFIG_SETTINGS)) configs.add(MAPART_AUTOPLACE_REACH);
				}
				else MAPART_AUTOPLACE.setBooleanValue(false);
				if(settings.placementHelperMapArtAutoRemove){
					configs.addAll(List.of(MAPART_AUTOREMOVE, MAPART_AUTOREMOVE_AFTER));
					if(settings.enabled(Setting.SHOW_NICHE_CONFIG_SETTINGS)) configs.add(MAPART_AUTOREMOVE_REACH);
				}
				else MAPART_AUTOREMOVE.setBooleanValue(false);
			}
			else{
				MAPART_AUTOPLACE.setBooleanValue(false);
				MAPART_AUTOREMOVE.setBooleanValue(false);
			}
			configs.addAll(List.of(SKIP_NULL_MAPS, SKIP_VOID_MAPS, SKIP_MONO_COLOR_MAPS));

			if(settings.enabled(Setting.WHISPER_SOUND)) configs.addAll(List.of(WHISPER_PLAY_SOUND, WHISPER_PLAY_SOUND_UNFOCUSED));
			if(settings.enabled(Setting.PEARL_MESSAGES)) configs.add(WHISPER_PEARL_PULL);
			if(settings.muteMusicOnMenuScreen) configs.add(MUTE_MUSIC_ON_MENU_SCREEN);
			configs.add(REMEMBER_SERVER_LIST_SCROLL);
			if(settings.enabled(Setting.FIREWORK_BOOST)) configs.add(DONT_WASTE_FIREWORKS);
			if(settings.enabled(Setting.CHAT_ACTIONS)) configs.addAll(List.of(SEND_ON_SERVER_JOIN, LOG_COORDS_ON_SERVER_QUIT));
			if(settings.enabled(Setting.INVENTORY_TOOLS)) configs.addAll(List.of(INV_RESTOCK_IF, INV_RESTOCK_LEAVE, INV_RESTOCK_BLACKLIST, INV_RESTOCK_WHITELIST,
					INV_ORGANIZE_1, INV_ORGANIZE_2, INV_ORGANIZE_3));
			if(settings.enabled(Setting.INVENTORY_TOOLS)) configs.addAll(List.of(INV_RESTOCK_AUTO, INV_RESTOCK_AUTO_FOR_INV_ORGS));
			else INV_RESTOCK_AUTO.setBooleanValue(false);
			if(settings.broadcaster) configs.addAll(List.of(TEMP_BROADCAST_ACCOUNT, TEMP_BROADCAST_TIMESTAMP, TEMP_BROADCAST_MSGS));
			if(settings.enabled(Setting.SHOW_NICHE_CONFIG_SETTINGS) && settings.enabled(Setting.GHOST_ITEM_FIX)) configs.add(DISABLE_DRAG_CLICK_ON_MAPS_AND_BUNDLES);
			return configs;
		}
	}


	//====================================================================================================
	// Visuals
	//====================================================================================================
	public static final class Visuals{
		private static final String VISUALS_KEY = Main.MOD_ID+".config.visuals";

		public static final ConfigBoolean NEW_MAP_NOTIFIER_IFRAME = new ConfigBoolean("iFrameNewMapNotifier", false).apply(VISUALS_KEY);

		public static final ConfigBoolean CLICK_DISPLAY_AVAILABLE_PERSISTENT = new ConfigBoolean("clickDisplayAvailablePersistent", false).apply(VISUALS_KEY);

//		public static final ConfigBoolean DISPLAY_TOTEM_COUNT = new ConfigBoolean("displayTotemCount", true).apply(VISUALS_KEY);
//		public static final ConfigBoolean SPAWNER_ACTIVATION_HIGHLIGHT = new ConfigBoolean("spawnerActivationHighlight", true).apply(VISUALS_KEY);
		public static final ConfigBoolean REPAIR_COST_HOTBAR_HUD = new ConfigBoolean("repairCostInHotbarHUD", false).apply(VISUALS_KEY);
		public static final ConfigOptionList REPAIR_COST_TOOLTIP = new ConfigOptionList("repairCostTooltip",
				MAPART_ONLY_DEFAULTS ? OptionTooltipDisplay.OFF : OptionTooltipDisplay.ADVANCED_TOOLTIPS).apply(VISUALS_KEY);
		public static final ConfigOptionList INVIS_IFRAMES = new ConfigOptionList("invisIFrames",
				OptionInvisIframes.SEMI_TRANSPARENT_MAPART).apply(VISUALS_KEY);
		public static final ConfigInteger STATIC_ENTITY_RENDER_RANGE = new ConfigInteger("staticEntityRenderRange", 0, 0, 10_000_000).apply(VISUALS_KEY);
		public static final ConfigInteger STATIC_ENTITY_RENDER_LIMIT = new ConfigInteger("staticEntityRenderLimit", 16_000, 0, 1_000_000).apply(VISUALS_KEY);

		public static final ConfigBoolean MAP_HIGHLIGHT_IFRAME = new ConfigBoolean("mapHighlightInIFrame", true).apply(VISUALS_KEY);
		public static final ConfigBoolean MAP_HIGHLIGHT_CONTAINER_NAME = new ConfigBoolean("mapHighlightInContainerName", true).apply(VISUALS_KEY);
		public static final ConfigBoolean MAP_HIGHLIGHT_TOOLTIP = new ConfigBoolean("mapHighlightInTooltip", true).apply(VISUALS_KEY);
		public static final ConfigBoolean MAP_HIGHLIGHT_HOTBAR_HUD = new ConfigBoolean("mapHighlightInHotbarHUD", true).apply(VISUALS_KEY);

		public static final ConfigColor MAP_COLOR_IN_INV = new ConfigColor("highlightColorInInv", "#FFB4FFFF").apply(VISUALS_KEY);  // 11862015 Aqua
		public static final ConfigColor MAP_COLOR_NOT_IN_GROUP = new ConfigColor("highlightColorNotInGroup", "#FF0AC864").apply(VISUALS_KEY);  // 706660 Green
		public static final ConfigColor MAP_COLOR_UNLOCKED = new ConfigColor("highlightColorUnlocked", "#FFE03165").apply(VISUALS_KEY);  // 14692709 Redish
		public static final ConfigColor MAP_COLOR_UNLOADED = new ConfigColor("highlightColorUnloaded", "#FFC8AAD2").apply(VISUALS_KEY);  // 13150930 Peach
		public static final ConfigColor MAP_COLOR_UNNAMED = new ConfigColor("highlightColorUnnamed", "#FFEED7D7").apply(VISUALS_KEY);  // 15652823 Pink
		public static final ConfigColor MAP_COLOR_IN_IFRAME = new ConfigColor("highlightColorInIFrame", "#FF55AAE6").apply(VISUALS_KEY);  // 5614310 Blue
		public static final ConfigColor MAP_COLOR_MULTI_IFRAME = new ConfigColor("highlightColorMultiIFrame", "#FFB450E6").apply(VISUALS_KEY);  // 11817190 Purple
		public static final ConfigColor MAP_COLOR_MULTI_CONTAINER = new ConfigColor("highlightColorMultiContainer", "#FFB450E6").apply(VISUALS_KEY);  // 11817190 Purple

		public static final ConfigBoolean MAP_HIGHLIGHT_IN_INV_INCLUDE_BUNDLES = new ConfigBoolean("mapHighlightInInvIncludeBundles", false).apply(VISUALS_KEY);

//		public static final ConfigBoolean MAP_METADATA_TOOLTIP = new ConfigBoolean("mapMetadataTooltip", true);
		// Line 0: "59d2da2c-e527-38cd-8557-62216bf65a57"
		public static final ConfigBoolean MAP_METADATA_TOOLTIP_UUID = new ConfigBoolean("mapMetadataTooltipHashCode", false).apply(VISUALS_KEY);
		// Line 1: "Type: = (65%), fullblock (42% carpet)"
		public static final ConfigBoolean MAP_METADATA_TOOLTIP_STAIRCASE = new ConfigBoolean("mapMetadataTooltipStaircase", true).apply(VISUALS_KEY);
		public static final ConfigBoolean MAP_METADATA_TOOLTIP_STAIRCASE_PERCENT = new ConfigBoolean("mapMetadataTooltipPercentStaircase", true).apply(VISUALS_KEY);
		public static final ConfigBoolean MAP_METADATA_TOOLTIP_MATERIAL = new ConfigBoolean("mapMetadataTooltipMaterial", true).apply(VISUALS_KEY);
		public static final ConfigBoolean MAP_METADATA_TOOLTIP_CARPET_PERCENT = new ConfigBoolean("mapMetadataTooltipPercentCarpet", false).apply(VISUALS_KEY);
		// Line 2: "Colors: 128 (Blocks: 52)"
		public static final ConfigBoolean MAP_METADATA_TOOLTIP_NUM_COLORS = new ConfigBoolean("mapMetadataTooltipNumColors", true).apply(VISUALS_KEY);
		public static final ConfigBoolean MAP_METADATA_TOOLTIP_NUM_COLOR_IDS = new ConfigBoolean("mapMetadataTooltipNumColorIds", true).apply(VISUALS_KEY);
		// Extra lines:
		public static final ConfigBoolean MAP_METADATA_TOOLTIP_WATER_COLORS = new ConfigBoolean("mapMetadataTooltipWaterColors", true).apply(VISUALS_KEY);
		public static final ConfigBoolean MAP_METADATA_TOOLTIP_WATER_COLORS_PERCENT = new ConfigBoolean("mapMetadataTooltipWaterColorsPercent", true).apply(VISUALS_KEY);

		public static final ConfigBoolean MAP_METADATA_TOOLTIP_TRANSPARENT = new ConfigBoolean("mapMetadataTooltipTransparent", true).apply(VISUALS_KEY);
		public static final ConfigBoolean MAP_METADATA_TOOLTIP_TRANSPARENT_PERCENT = new ConfigBoolean("mapMetadataTooltipPercentTransparent", false).apply(VISUALS_KEY);
		public static final ConfigBoolean MAP_METADATA_TOOLTIP_VOID_SHADOW = new ConfigBoolean("mapMetadataTooltipVoidShadow", true).apply(VISUALS_KEY);

		public static final ConfigBoolean MAP_METADATA_TOOLTIP_NOOBLINE = new ConfigBoolean("mapMetadataTooltipNoobline", true).apply(VISUALS_KEY);

		public static final ConfigInteger EXPORT_MAP_IMG_UPSCALE = new ConfigInteger("exportMapImageUpscale", 128, 128, 1280).apply(VISUALS_KEY);
		public static final ConfigBoolean EXPORT_MAP_IMG_BORDER = new ConfigBoolean("exportMapImageBorder", false).apply(VISUALS_KEY);
		public static final ConfigColor EXPORT_MAP_IMG_BORDER_COLOR1 = new ConfigColor("exportMapImageBorderColor1", "#FFFFC864").apply(VISUALS_KEY);  // -14236 Yellow
		public static final ConfigColor EXPORT_MAP_IMG_BORDER_COLOR2 = new ConfigColor("exportMapImageBorderColor2", "#00322D32").apply(VISUALS_KEY);  // 3288370 Gray
		public static final ConfigBoolean EXPORT_MAP_IMG_ATOMIC_NAMING = new ConfigBoolean("exportMapAtomicIdNaming", false).apply(VISUALS_KEY);

		private static List<IConfigBase> configs;
		private static final List<IConfigBase> getConfigs(Settings settings){
			if(configs != null) return configs;
			configs = new ArrayList<>();
			if(settings.cmdMapArtGroup && settings.enabled(Setting.NOTIFY_NEW_MAPART_IFRAME)) configs.add(NEW_MAP_NOTIFIER_IFRAME);
			if(settings.usesClicks() && settings.enabled(Setting.SHOW_NICHE_CONFIG_SETTINGS)) configs.add(CLICK_DISPLAY_AVAILABLE_PERSISTENT);
			if(settings.enabled(Setting.REPAIR_COST_TOOLTIP)){
				configs.add(REPAIR_COST_HOTBAR_HUD);
				if(settings.tooltipRepairCost) configs.add(REPAIR_COST_TOOLTIP);
			}
			if(settings.enabled(Setting.MAP_SCAN_IFRAMES)) configs.add(INVIS_IFRAMES);
			if(settings.enabled(Setting.STATIC_ENTITY_RENDERING)) configs.addAll(List.of(STATIC_ENTITY_RENDER_RANGE, STATIC_ENTITY_RENDER_LIMIT));
			if(settings.tooltipMapHighlights || settings.onTickIframes || settings.onTickContainer){
				if(settings.enabled(Setting.MAP_SCAN_IFRAMES)) configs.add(MAP_HIGHLIGHT_IFRAME);
				else MAP_HIGHLIGHT_IFRAME.setBooleanValue(false);
				if(settings.enabled(Setting.MAP_SCAN_CONTAINERS)) configs.add(MAP_HIGHLIGHT_CONTAINER_NAME);
				else MAP_HIGHLIGHT_CONTAINER_NAME.setBooleanValue(false);
				if(settings.tooltipMapHighlights) configs.add(MAP_HIGHLIGHT_TOOLTIP);
				if(settings.enabled(Setting.MAP_HUD)) configs.add(MAP_HIGHLIGHT_HOTBAR_HUD);
				configs.addAll(List.of(
					MAP_COLOR_IN_INV, MAP_COLOR_NOT_IN_GROUP, MAP_COLOR_UNLOCKED,
					MAP_COLOR_UNLOADED, MAP_COLOR_UNNAMED, MAP_COLOR_IN_IFRAME,
					MAP_COLOR_MULTI_IFRAME, MAP_COLOR_MULTI_CONTAINER,
					MAP_HIGHLIGHT_IN_INV_INCLUDE_BUNDLES
				));
			}
			if(settings.tooltipMapMetadata) configs.addAll(List.of(
//					MAP_METADATA_TOOLTIP,
					MAP_METADATA_TOOLTIP_UUID,
					MAP_METADATA_TOOLTIP_STAIRCASE, MAP_METADATA_TOOLTIP_STAIRCASE_PERCENT,
					MAP_METADATA_TOOLTIP_MATERIAL, MAP_METADATA_TOOLTIP_CARPET_PERCENT,
					MAP_METADATA_TOOLTIP_NUM_COLORS, MAP_METADATA_TOOLTIP_NUM_COLOR_IDS,
					MAP_METADATA_TOOLTIP_WATER_COLORS, MAP_METADATA_TOOLTIP_WATER_COLORS_PERCENT,
					MAP_METADATA_TOOLTIP_TRANSPARENT, MAP_METADATA_TOOLTIP_TRANSPARENT_PERCENT, MAP_METADATA_TOOLTIP_VOID_SHADOW,
					MAP_METADATA_TOOLTIP_NOOBLINE
			));
			configs.addAll(List.of(
					EXPORT_MAP_IMG_UPSCALE,
					EXPORT_MAP_IMG_BORDER,
					EXPORT_MAP_IMG_BORDER_COLOR1, EXPORT_MAP_IMG_BORDER_COLOR2));
			if(settings.enabled(Setting.SHOW_NICHE_CONFIG_SETTINGS)) configs.add(EXPORT_MAP_IMG_ATOMIC_NAMING);
			return configs;
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
		public static final ConfigHotkey MAP_MOVE_NEIGHBORS = new ConfigHotkey("mapMoveNeighbors", "LEFT_ALT", KeybindSettings.MODIFIER_GUI).apply(HOTKEYS_KEY);

		public static final ConfigHotkey MAP_MOVE_BUNDLE = new ConfigHotkey("mapMoveBundle", "D", KeybindSettings.GUI).apply(HOTKEYS_KEY);
		public static final ConfigHotkey MAP_MOVE_BUNDLE_REVERSE = new ConfigHotkey("mapMoveBundleReverse", "", KeybindSettings.GUI).apply(HOTKEYS_KEY);
		public static final ConfigHotkey MAP_MOVE_ALL_MODIFIER = new ConfigHotkey("mapMoveAllModifier", "LEFT_SHIFT", KeybindSettings.MODIFIER_GUI).apply(HOTKEYS_KEY);
		public static final ConfigBoolean MAP_MOVE_IGNORE_AIR_POCKETS = new ConfigBoolean("mapMoveIgnoreAirPockets", false).apply(HOTKEYS_KEY);
		public static final ConfigBoolean MAP_MOVE_BUNDLE_PREFER_STOW = new ConfigBoolean("mapMoveBundlePreferStow", true).apply(HOTKEYS_KEY);
		public static final ConfigBoolean MAP_MOVE_BUNDLE_STOW_NON_SINGLE_MAPS = new ConfigBoolean("mapMoveBundleStowNonSingleMaps", true).apply(HOTKEYS_KEY);
		public static final ConfigInteger MAP_MOVE_BUNDLE_TAKE_MAX = new ConfigInteger("mapMoveBundleTakeMax", 64, 1, 64).apply(HOTKEYS_KEY);
		public static final ConfigOptionList MAP_MOVE_BUNDLE_SELECT_PRIORITY_STOW = new ConfigOptionList(
				"mapMoveBundleSelectPrioForStow", OptionBundleSelectPrioStow.FULLEST_NOT_FULL).apply(HOTKEYS_KEY);
		public static final ConfigOptionList MAP_MOVE_BUNDLE_SELECT_PRIORITY_TAKE = new ConfigOptionList(
				"mapMoveBundleSelectPrioForTake", OptionBundleSelectPrioTake.EMPTIEST_NOT_EMPTY).apply(HOTKEYS_KEY);

		private static final KeybindSettings ARMOR_SWAP_SETTINGS = KeybindSettings.create(Context.INGAME,
				KeyAction.PRESS, /*allowExtraKeys=*/true, /*orderSensitive=*/true, /*exclusive=*/false, /*cancel=*/false);
		public static final ConfigHotkey SWAP_SPECIAL_HELMET = new ConfigHotkey("swapSpecialHelmet", MAPART_ONLY_DEFAULTS ? "" : "COMMA", ARMOR_SWAP_SETTINGS).apply(HOTKEYS_KEY);
		public static final ConfigHotkey SWAP_LEATHER_BOOTS = new ConfigHotkey("swapLeatherBoots", MAPART_ONLY_DEFAULTS ? "" : "COMMA", ARMOR_SWAP_SETTINGS).apply(HOTKEYS_KEY);
		public static final ConfigHotkey SWAP_GOLD_HELMET = new ConfigHotkey("swapGoldHelmet", MAPART_ONLY_DEFAULTS ? "" : "COMMA", ARMOR_SWAP_SETTINGS).apply(HOTKEYS_KEY);
		public static final ConfigHotkey SWAP_GOLD_LEGGINGS = new ConfigHotkey("swapGoldLeggings", MAPART_ONLY_DEFAULTS ? "" : "COMMA", ARMOR_SWAP_SETTINGS).apply(HOTKEYS_KEY);
		public static final ConfigHotkey SWAP_GOLD_BOOTS = new ConfigHotkey("swapGoldBoots", MAPART_ONLY_DEFAULTS ? "" : "COMMA", ARMOR_SWAP_SETTINGS).apply(HOTKEYS_KEY);

		public static final ConfigHotkey TOGGLE_HAT = new ConfigHotkey("toggleHat", "").apply(HOTKEYS_KEY);
		public static final ConfigHotkey TOGGLE_JACKET = new ConfigHotkey("toggleJacket", MAPART_ONLY_DEFAULTS ? "" : "I").apply(HOTKEYS_KEY);
		public static final ConfigHotkey TOGGLE_SLEEVE_LEFT = new ConfigHotkey("toggleSleeveLeft", MAPART_ONLY_DEFAULTS ? "" : "I").apply(HOTKEYS_KEY);
		public static final ConfigHotkey TOGGLE_SLEEVE_RIGHT = new ConfigHotkey("toggleSleeveRight", MAPART_ONLY_DEFAULTS ? "" : "I").apply(HOTKEYS_KEY);
		public static final ConfigHotkey TOGGLE_PANTS_LEG_LEFT = new ConfigHotkey("togglePantsLegLeft", MAPART_ONLY_DEFAULTS ? "" : "I").apply(HOTKEYS_KEY);
		public static final ConfigHotkey TOGGLE_PANTS_LEG_RIGHT = new ConfigHotkey("togglePantsLegRight", MAPART_ONLY_DEFAULTS ? "" : "I").apply(HOTKEYS_KEY);
		public static final ConfigHotkey TOGGLE_CAPE = new ConfigHotkey("toggleCape", "").apply(HOTKEYS_KEY);
		public static final ConfigBoolean SYNC_CAPE_WITH_ELYTRA = new ConfigBoolean("syncCapeWithElytra", false).apply(HOTKEYS_KEY);
		public static final ConfigBoolean SYNC_ARMOR_WITH_ELYTRA = new ConfigBoolean("syncArmorWithElytra", true).apply(HOTKEYS_KEY);

		public static final ConfigBooleanHotkeyed AIE_TRAVEL_HELPER = new ConfigBooleanHotkeyed("automaticInfiniteElytraTravelHelper", false,
				MAPART_ONLY_DEFAULTS ? "" : "SEMICOLON", KeybindSettings.NOCANCEL).apply(HOTKEYS_KEY);
		public static final ConfigBooleanHotkeyed EBOUNCE_TRAVEL_HELPER = new ConfigBooleanHotkeyed("eBounceTravelHelper", false,
				MAPART_ONLY_DEFAULTS ? "" : "A", KeybindSettings.PRESS_ALLOWEXTRA).apply(HOTKEYS_KEY);
		public static final ConfigYawPitchHotkeyed SNAP_ANGLE_1 = new ConfigYawPitchHotkeyed("snapAngle1", 148, -73, "").apply(HOTKEYS_KEY);
		public static final ConfigYawPitchHotkeyed SNAP_ANGLE_2 = new ConfigYawPitchHotkeyed("snapAngle2", -159, -51, "").apply(HOTKEYS_KEY);

		public static final ConfigHotkey EJECT_JUNK_ITEMS = new ConfigHotkey("ejectJunkItems",
				MAPART_ONLY_DEFAULTS ? "" : "R", GUI_OR_INGAME_SETTINGS).apply(HOTKEYS_KEY);
		public static final ConfigHotkey CRAFT_RESTOCK = new ConfigHotkey("craftingRestock", "TAB", GUI_ALLOW_EXTRA_KEYS).apply(HOTKEYS_KEY);
		public static final ConfigBoolean CRAFT_RESTOCK_SMART = new ConfigBoolean("craftingRestockSmart", true).apply(HOTKEYS_KEY);
		public static final ConfigHotkey CRAFT_BULK_MODIFIER = new ConfigHotkey(
				"craftingBulkModifier", "LEFT_SHIFT", KeybindSettings.MODIFIER_GUI).apply(HOTKEYS_KEY);
		public static final ConfigStringList SCROLL_ORDER = new ConfigStringList("hotbarSlotItemTypeScrollOrder", ImmutableList.of(
				"white, light_gray, gray, black, brown, red, orange, yellow, lime, green, cyan, light_blue, blue, purple, magenta, pink",
				", exposed, weathered, oxidized",
				",powered,detector,activator",
				"tube, brain, bubble, fire, horn",
				"oak, spruce, birch, jungle, acacia, dark_oak, mangrove, cherry, bamboo, crimson, warped"
		)).apply(HOTKEYS_KEY);
		public static final ConfigHotkey HOTBAR_TYPE_INCR = new ConfigHotkey("hotbarSlotItemTypeIncrement", "").apply(HOTKEYS_KEY);
		public static final ConfigHotkey HOTBAR_TYPE_DECR = new ConfigHotkey("hotbarSlotItemTypeDecrement", "").apply(HOTKEYS_KEY);

		//TODO: ConfigSlotList, ConfigSlotListHotkeyed
		public static final ConfigHotkey INV_RESTOCK = new ConfigHotkey("inventoryRestock",
				MAPART_ONLY_DEFAULTS ? "" : "R", KeybindSettings.GUI).apply(HOTKEYS_KEY);

		public static final ConfigHotkey TRIGGER_INV_ORGANIZE_1 = new ConfigHotkey("triggerInventoryOrganize1",
				MAPART_ONLY_DEFAULTS ? "" : "E", KeybindSettings.GUI).apply(HOTKEYS_KEY);
		public static final ConfigHotkey TRIGGER_INV_ORGANIZE_2 = new ConfigHotkey("triggerInventoryOrganize2", "", KeybindSettings.GUI).apply(HOTKEYS_KEY);
		public static final ConfigHotkey TRIGGER_INV_ORGANIZE_3 = new ConfigHotkey("triggerInventoryOrganize3", "", KeybindSettings.GUI).apply(HOTKEYS_KEY);

		public static final ConfigStringHotkeyed CHAT_MSG_1 = new ConfigStringHotkeyed("chatMessage1", "meow :3", "").apply(HOTKEYS_KEY);
		public static final ConfigStringHotkeyed CHAT_MSG_2 = new ConfigStringHotkeyed("chatMessage2", "/home", "").apply(HOTKEYS_KEY);
		public static final ConfigStringHotkeyed CHAT_MSG_3 = new ConfigStringHotkeyed("chatMessage3",
				"/cgetdata entity @e[type=item,distance=..5,limit=1] Item.components.minecraft:repair_cost",
				MAPART_ONLY_DEFAULTS ? "" : "Z").apply(HOTKEYS_KEY);

		public static final ConfigStringHotkeyed REMOTE_MSG_1 = new ConfigStringHotkeyed("remoteMessage1",
				"EPEARL_TRIGGER,6f7fa766-4fe6-42fe-b589-22e4ec9a077c", "").apply(HOTKEYS_KEY);
		public static final ConfigStringHotkeyed REMOTE_MSG_2 = new ConfigStringHotkeyed("remoteMessage2", "", "").apply(HOTKEYS_KEY);
		public static final ConfigStringHotkeyed REMOTE_MSG_3 = new ConfigStringHotkeyed("remoteMessage3", "", "").apply(HOTKEYS_KEY);

		public static final ConfigString PP_IP_1 = new ConfigString("pearlPlusIp1", "").apply(HOTKEYS_KEY);
		public static final ConfigString PP_TOKEN_1 = new ConfigString("pearlPlusToken1", "").apply(HOTKEYS_KEY);
		public static final ConfigHotkey PP_HOTKEY_1 = new ConfigHotkey("pearlPlusHotkey1", "").apply(HOTKEYS_KEY);

		private static List<IConfigBase> configs;

		private static final List<IConfigBase> getConfigs(Settings settings){
			if(configs != null) return configs;
			configs = new ArrayList<>();
			configs.add(OPEN_CONFIG_GUI);
			if(settings.enabled(Setting.MAP_ACTIONS)){
				configs.addAll(List.of(MAP_COPY, MAP_LOAD, MAP_MOVE, MAP_MOVE_NEIGHBORS, MAP_MOVE_BUNDLE, MAP_MOVE_BUNDLE_REVERSE));
				if(settings.enabled(Setting.SHOW_NICHE_CONFIG_SETTINGS)) configs.addAll(List.of(MAP_MOVE_ALL_MODIFIER, MAP_MOVE_IGNORE_AIR_POCKETS,
						MAP_MOVE_BUNDLE_PREFER_STOW, MAP_MOVE_BUNDLE_STOW_NON_SINGLE_MAPS, MAP_MOVE_BUNDLE_TAKE_MAX,
						MAP_MOVE_BUNDLE_SELECT_PRIORITY_STOW, MAP_MOVE_BUNDLE_SELECT_PRIORITY_TAKE));
			}
			if(settings.enabled(Setting.ARMOR_SWAP)) configs.addAll(List.of(SWAP_SPECIAL_HELMET, SWAP_LEATHER_BOOTS,
					SWAP_GOLD_HELMET, SWAP_GOLD_LEGGINGS, SWAP_GOLD_BOOTS));
			if(settings.enabled(Setting.APPEARANCE)) configs.addAll(List.of(TOGGLE_HAT, TOGGLE_JACKET, TOGGLE_SLEEVE_LEFT,
					TOGGLE_SLEEVE_RIGHT, TOGGLE_PANTS_LEG_LEFT, TOGGLE_PANTS_LEG_RIGHT, TOGGLE_CAPE, SYNC_CAPE_WITH_ELYTRA));
			if(settings.enabled(Setting.ARMOR_SWAP)) configs.add(SYNC_ARMOR_WITH_ELYTRA);
			if(settings.enabled(Setting.TRAVEL)) configs.addAll(List.of(AIE_TRAVEL_HELPER, EBOUNCE_TRAVEL_HELPER, SNAP_ANGLE_1, SNAP_ANGLE_2));
			if(settings.any(Setting.TRAVEL, Setting.INVENTORY_TOOLS)) configs.add(EJECT_JUNK_ITEMS);
			if(settings.enabled(Setting.CRAFTING)) configs.addAll(List.of(CRAFT_RESTOCK, CRAFT_RESTOCK_SMART, CRAFT_BULK_MODIFIER));
			if(settings.enabled(Setting.INVENTORY_TOOLS)) configs.addAll(List.of(HOTBAR_TYPE_INCR, HOTBAR_TYPE_DECR, SCROLL_ORDER,
					INV_RESTOCK, 
					TRIGGER_INV_ORGANIZE_1, TRIGGER_INV_ORGANIZE_2, TRIGGER_INV_ORGANIZE_3));
			if(settings.enabled(Setting.CHAT_ACTIONS)) configs.addAll(List.of(CHAT_MSG_1, CHAT_MSG_2, CHAT_MSG_3));
			if(settings.usesRemoteCommandHotkeys()) configs.addAll(List.of(REMOTE_MSG_1, REMOTE_MSG_2, REMOTE_MSG_3));
			if(settings.enabled(Setting.PEARL_MESSAGES)) configs.addAll(List.of(PP_IP_1, PP_TOKEN_1, PP_HOTKEY_1));
			return configs;
		}
	}


	//====================================================================================================
	// Database
	//====================================================================================================
	private static final String DATABASE_KEY = Main.MOD_ID+".config.database";
	public static class Database{
		public static final ConfigOptionList MAP_CACHE = new ConfigOptionList("mapStateCache", OptionMapStateCache.MEMORY_AND_DISK).apply(DATABASE_KEY);
		public static final ConfigBoolean MAP_CACHE_UNLOCKED = new ConfigBoolean("mapStateCacheUnlocked", true).apply(DATABASE_KEY);
		public static final ConfigBoolean MAP_CACHE_BY_ID = new ConfigBoolean("mapStateCacheById", false).apply(DATABASE_KEY);
		public static final ConfigBoolean MAP_CACHE_BY_NAME = new ConfigBoolean("mapStateCacheByName", true).apply(DATABASE_KEY);
		//ConfigOptionList MAP_CACHE_BY_NAME{UNIQUE, FIRST}
		public static final ConfigBoolean MAP_CACHE_BY_INV_POS = new ConfigBoolean("mapStateCacheByInvPos", false).apply(DATABASE_KEY);
		public static final ConfigBoolean MAP_CACHE_BY_EC_POS = new ConfigBoolean("mapStateCacheByEchestPos", true).apply(DATABASE_KEY);
		public static final ConfigBoolean MAP_CACHE_BY_CONTAINER_POS = new ConfigBoolean("mapStateCacheByContainerPos", false).apply(DATABASE_KEY);

		public static final ConfigBoolean SAVE_MAPART = new ConfigBoolean("saveSeenMapArt", !MAPART_ONLY_DEFAULTS).apply(DATABASE_KEY);
		public static final ConfigBoolean EPEARL_OWNERS_BY_UUID = new ConfigBoolean("saveEpearlOwnersByUUID", !MAPART_ONLY_DEFAULTS).apply(DATABASE_KEY);
		public static final ConfigBoolean EPEARL_OWNERS_BY_XZ = new ConfigBoolean("saveEpearlOwnersByXZ", false).apply(DATABASE_KEY);
		public static final ConfigBoolean SAVE_IGNORES = new ConfigBoolean("saveIgnoreList", !MAPART_ONLY_DEFAULTS).apply(DATABASE_KEY);
		// Requires SAVE_IGNORES=true; cached lists also work without a remote connection.
		public static final ConfigPlayerList BORROW_IGNORES = (ConfigPlayerList)new ConfigPlayerList("borrowIgnoreLists", 
			List.of(
				new NameAndUUID("EvDoc", UUID.fromString("34471e8d-d0c5-47b9-b8e1-b5b9472affa4")),
				new NameAndUUID("EvModder", UUID.fromString("0e314b60-29c7-4e35-bef3-3c652c8fb467"))
			)
		).apply(DATABASE_KEY);

//		public static final ConfigOptionList PLACEMENT_WARN = new ConfigOptionList("placementWarn", MessageOutputType.ACTIONBAR).apply(DATABASE_KEY);
		public static final ConfigInteger CLIENT_ID = new ConfigInteger("clientId", InitUtils.DUMMY_CLIENT_ID, 0, Integer.MAX_VALUE).apply(DATABASE_KEY);
		public static final ConfigString CLIENT_KEY = new ConfigString("clientKey", "").apply(DATABASE_KEY);
		public static final ConfigString ADDRESS = new ConfigString("address", "").apply(DATABASE_KEY);
		public static final ConfigBoolean SHARE_MAPART = new ConfigBoolean("shareSeenMapArt", false).apply(DATABASE_KEY);
		public static final ConfigBoolean SHARE_EPEARL_OWNERS = new ConfigBoolean("shareEpearlOwners", false).apply(DATABASE_KEY);
		public static final ConfigBoolean SHARE_IGNORES = new ConfigBoolean("shareIgnoreList", false).apply(DATABASE_KEY);
		// Note: I decided to disable this by default, and hide it, because running a single proxy/bot is much better suited for collecting this data
		public static final ConfigBoolean SHARE_JOIN_QUIT = new ConfigBoolean("shareJoinQuit", false).apply(DATABASE_KEY);

		private static List<IConfigBase> configs;
		private static final List<IConfigBase> getConfigs(Settings settings){
			if(configs != null) return configs;
			configs = new ArrayList<>();
			if(settings.enabled(Setting.MAP_CACHE)) configs.addAll(List.of(MAP_CACHE, MAP_CACHE_UNLOCKED,
					MAP_CACHE_BY_ID, MAP_CACHE_BY_NAME, MAP_CACHE_BY_INV_POS, MAP_CACHE_BY_EC_POS, MAP_CACHE_BY_CONTAINER_POS));
			else{
				MAP_CACHE.setOptionListValue(OptionMapStateCache.OFF);
				for(ConfigBoolean option : List.of(MAP_CACHE_BY_ID, MAP_CACHE_BY_NAME, MAP_CACHE_BY_INV_POS,
						MAP_CACHE_BY_EC_POS, MAP_CACHE_BY_CONTAINER_POS)) option.setBooleanValue(false);
			}
			if(settings.enabled(Setting.DATABASE_MAP_HISTORY)) configs.add(SAVE_MAPART);
			else SAVE_MAPART.setBooleanValue(false);
			if(settings.epearlOwners) configs.addAll(List.of(EPEARL_OWNERS_BY_UUID, EPEARL_OWNERS_BY_XZ));
			if(settings.gameMessageFilter) configs.addAll(List.of(SAVE_IGNORES, BORROW_IGNORES));
			else SAVE_IGNORES.setBooleanValue(false);

			if(settings.database) configs.addAll(List.of(CLIENT_ID, CLIENT_KEY, ADDRESS));
			else{
				ADDRESS.setValueFromString("");
				SHARE_EPEARL_OWNERS.setBooleanValue(false);
			}
			if(settings.database && settings.enabled(Setting.DATABASE_MAP_HISTORY)) configs.add(SHARE_MAPART);
			else SHARE_MAPART.setBooleanValue(false);
			if(settings.epearlOwners && settings.database) configs.add(SHARE_EPEARL_OWNERS);
			if(settings.database && settings.gameMessageFilter) configs.add(SHARE_IGNORES);
			else SHARE_IGNORES.setBooleanValue(false);
			if(settings.database && (settings.serverJoinListener || settings.serverQuitListener) && settings.enabled(Setting.SHOW_NICHE_CONFIG_SETTINGS)) configs.add(SHARE_JOIN_QUIT);

			return configs;
		}
	}

	//====================================================================================================
	// Config option saving/loading/fetching
	//====================================================================================================
	private final Settings settings;
	final List<ConfigGuiTab> guiTabs;
	final String dataTabTranslationKey;
	Configs(Settings settings){
		this.settings = settings;
		getAllConfigs();
		dataTabTranslationKey = Main.MOD_ID+".gui.button."+(settings.database ? "database" : "data");
		final List<ConfigGuiTab> tabs = new ArrayList<>();
		tabs.addAll(List.of(ConfigGuiTab.ALL, ConfigGuiTab.GENERIC, ConfigGuiTab.VISUALS, ConfigGuiTab.HOTKEYS));
		if(!getDatabaseConfigs().isEmpty()) tabs.add(ConfigGuiTab.DATABASE);
		guiTabs = List.copyOf(tabs);
	}

	final List<IConfigBase> getGenericConfigs(){return Generic.getConfigs(settings);}
	final List<IConfigBase> getVisualsConfigs(){return Visuals.getConfigs(settings);}
	final List<IConfigBase> getHotkeysConfigs(){return Hotkeys.getConfigs(settings);}
	final List<IConfigBase> getDatabaseConfigs(){return Database.getConfigs(settings);}

	private static List<IConfigBase> allConfigs;
	List<IConfigBase> getAllConfigs(){
		if(allConfigs != null) return allConfigs;
		allConfigs = new ArrayList<>();
		allConfigs.addAll(getGenericConfigs());
		allConfigs.addAll(getVisualsConfigs());
		allConfigs.addAll(getHotkeysConfigs());
		allConfigs.addAll(getDatabaseConfigs());
		return allConfigs;
	}

	private static final Path CONFIG_DIR = Path.of(Main.CONFIG_DIR);
	private static final Path CONFIG_FILE = CONFIG_DIR.resolve("configs.json");

	private static JsonElement readConfig(Path file){
		//? >=1.21.11 {
		return JsonUtils.parseJsonFile(file);
		//?} else {
		/*return JsonUtils.parseJsonFile(file.toFile());*/
		//?}
	}

	private static boolean writeConfig(JsonObject root, Path file){
		//? >=1.21.11 {
		return JsonUtils.writeJsonToFile(root, file);
		//?} else {
		/*JsonUtils.writeJsonToFile(root, file.toFile());
		return true;*/
		//?}
	}

	@Override public void load(){
		try{LegacySettingsMigration.importConfigs(CONFIG_FILE);}
		catch(IOException e){Main.LOGGER.error("Configs.load(): Could not import legacy config settings.", e);}
		if(!Files.exists(CONFIG_FILE) || !Files.isReadable(CONFIG_FILE)) return;
		final JsonElement element = readConfig(CONFIG_FILE);
		if(element == null || !element.isJsonObject()){
			Main.LOGGER.error("Configs.load(): Failed to load config file '{}'.", CONFIG_FILE.toAbsolutePath());
			return;
		}
		final JsonObject root = element.getAsJsonObject();
		if(LegacySettingsMigration.configs(root)){
			try{LegacySettingsMigration.writeConfigs(CONFIG_FILE, root);}
			catch(IOException e){Main.LOGGER.error("Configs.load(): Could not save migrated config settings.", e);}
		}
		final JsonElement curTab = root.get("guiTab");
		if(curTab != null) guiTab = curTab.getAsInt();
		final JsonElement scroll = root.get("serverListScroll");
		if(scroll != null){
			try{
				final double value = scroll.getAsDouble();
				if(Double.isFinite(value) && value >= 0) serverListScroll = value;
			}
			catch(RuntimeException e){Main.LOGGER.warn("Ignoring invalid server-list scroll position", e);}
		}
		ConfigUtils.readConfigBase(root, "Generic", getGenericConfigs());
		ConfigUtils.readConfigBase(root, "Visuals", getVisualsConfigs());
		ConfigUtils.readConfigBase(root, "Hotkeys", getHotkeysConfigs());
		ConfigUtils.readConfigBase(root, "Database", getDatabaseConfigs());
//		Main.LOGGER.debug("Configs.load(): Successfully loaded config file '{}'.", CONFIG_FILE.toAbsolutePath());
	}

	@Override public void save(){
		try{Files.createDirectories(CONFIG_DIR);}
		catch(IOException e){
			Main.LOGGER.error("Configs.save(): Failed to create config folder '{}'.", CONFIG_DIR.toAbsolutePath(), e);
			return;
		}
		// Preserve saved options for disabled features while updating the active options.
		final JsonElement saved = Files.exists(CONFIG_FILE) ? readConfig(CONFIG_FILE) : null;
		final JsonObject root = saved != null && saved.isJsonObject() ? saved.getAsJsonObject() : new JsonObject();
		root.addProperty("guiTab", guiTab);
		root.addProperty("serverListScroll", serverListScroll);
		ConfigUtils.writeConfigBase(root, "Generic", getGenericConfigs());
		ConfigUtils.writeConfigBase(root, "Visuals", getVisualsConfigs());
		ConfigUtils.writeConfigBase(root, "Hotkeys", getHotkeysConfigs());
		ConfigUtils.writeConfigBase(root, "Database", getDatabaseConfigs());
		if(!writeConfig(root, CONFIG_FILE)){
			Main.LOGGER.error("Configs.save(): Failed to write config file '{}'.", CONFIG_FILE.toAbsolutePath());
		}
	}
}