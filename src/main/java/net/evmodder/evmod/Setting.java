package net.evmodder.evmod;

import java.util.Locale;

/** User-facing startup settings. Registered hooks are derived from this selection. */
public enum Setting{
	MAPART_ONLY_DEFAULTS("mapart_only_mode", true, null),
	SHOW_NICHE_CONFIG_SETTINGS("gui.show_niche_config_settings", false, "show_niche_config_settings"),
	STORE_DATA_IN_CONFIG_FOLDER("data.store_in_config_folder", false, null),

	CLICK_SAFETY("inventory.click_safety", true, null),
	CMD_EXPORT_MAPNAMES("command.exportmapnames", true, "command.exportmapnames"),
	CMD_MAPARTGROUP("command.mapartgroup", true, "command.mapartgroup"),
	GHOST_ITEM_FIX("inventory.prevent_drag_ghost_items", true, null),
	IFRAME_AUTO_PLACE("auto_place.iframes", true, "placement_helper.iframe.autoplace"),
	MAP_ACTIONS("map.inventory_keybinds", true, null),
	MAP_AUTO_PLACE("auto_place.mapart", true, "placement_helper.mapart.autoplace"),
	MAP_AUTO_REMOVE("auto_remove.mapart", true, "placement_helper.mapart.autoremove"),
	MAP_CACHE("map.state_cache", true, null), MAP_HUD("map.highlights.hotbar", true, null),
	MAP_NAME_COLORS("map.highlights.tooltips", true, "tooltip.map_highlights"),
	MAP_PLACE_HAND_RESTOCK("map.hand_restock_on_place", true, "placement_helper.mapart"),
	MAP_SCAN_CONTAINERS("map.highlights.containers", true, "on_tick.container"),
	MAP_SCAN_IFRAMES("map.iframe_visuals", true, "on_tick.iframes"),
	MAP_SCAN_INVENTORY("map.track_inventory", true, "on_tick.inventory"),
	MAP_TOOLTIPS("item_info.map_metadata", true, "tooltip.map_metadata"),
	STATIC_ENTITY_RENDERING("client.static_entity_rendering", true, null),

	CMD_TIMEONLINE("command.timeonline", false, "command.timeonline"),
	DATABASE_EPEARL_OWNERS("data.epearl_owners", false, "epearl_owners"),
	DATABASE_IGNORELIST_SYNC("data.ignorelist_sync", false, "listener.game_message.filter"),
	DATABASE_MAP_HISTORY("data.map_history", true, null),
	DATABASE_REMOTE("data.remote_db", false, "database"),

	APPEARANCE("player.keybind_toggle.skin_layers", false, null), ARMOR_SWAP("player.keybind_toggle.armor", false, null),
	BROADCASTER("chat.broadcaster", false, "broadcaster"), CHAT_ACTIONS("chat.shortcuts", false, null),
	CRAFTING("inventory.crafting_restock", false, null), FIREWORK_BOOST("elytra.prevent_firework_waste", false, null),
	INVENTORY_TOOLS("inventory.utility_keybinds", false, null),
	MAC_INSTANT_FULLSCREEN("client.mac_instant_fullscreen", false, null),
	MUTE_MAIN_MENU_MUSIC("client.mute_main_menu_music", false, "mute_music_on_menu_screen"),
	PEARL_MESSAGES("player.pull_pearls_on_request", false, null), PLAYER_POSITION_SYNC("data.ipc.player_pos", false, "listener.player_move"),
	REPAIR_COST_TOOLTIP("item_info.repair_cost", false, "tooltip.repair_cost"),
	TRAVEL("elytra.travel_helpers", false, null), WHISPER_SOUND("notify.chat.whisper", false, null),
	NOTIFY_NEW_MAPART_IFRAME("notify.new_mapart.iframe", true, null);

	public final String key;
	final boolean defaultEnabled;
	final String legacyKey;
	Setting(boolean defaultEnabled){this(defaultEnabled, null);}
	Setting(boolean defaultEnabled, String legacyKey){
		this(null, defaultEnabled, legacyKey);
	}
	Setting(String key, boolean defaultEnabled, String legacyKey){
		this.defaultEnabled = defaultEnabled;
		this.legacyKey = legacyKey;
		this.key = key == null ? name().toLowerCase(Locale.ROOT) : key;
	}
}