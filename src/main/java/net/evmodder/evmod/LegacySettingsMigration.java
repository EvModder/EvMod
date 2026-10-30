package net.evmodder.evmod;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import fi.dy.masa.malilib.config.IConfigBase;
import net.fabricmc.loader.api.FabricLoader;

/** Temporary migration for the startup-setting and config-section changes. Remove after the upgrade window. */
final class LegacySettingsMigration{
	private LegacySettingsMigration(){}

	// Run during normal mod initialization, not while the mixin plugin loads Settings.
	static void mapGroups(boolean storeDataInInstanceFolder){
		// TODO: remove these legacy-patches in a future version
		final String mapGroupDir = (storeDataInInstanceFolder ? FabricLoader.getInstance().getGameDir()
				: FabricLoader.getInstance().getConfigDir())+"/"+Main.MOD_ID+"/mapart_groups/";
		try(var files = Files.walk(Paths.get(mapGroupDir))){
			final List<Path> paths = files.filter(Files::isRegularFile)
					.filter(p -> (p.getFileName().toString().indexOf('.') == -1 || (
						p.getNameCount() > 1 && p.getName(p.getNameCount()-2).toString().equals("seen")
						&& !p.getFileName().toString().startsWith("."))) && !p.getFileName().toString().endsWith(".group"))
					.toList();
			if(!paths.isEmpty()){
				paths.forEach(p -> p.toFile().renameTo(p.resolveSibling(p.getFileName().toString()+".group").toFile()));
				Main.LOGGER.info("[MIGRATION]: added '.group' file_ext to "+paths.size()+" group files");
			}
		}
		catch(IOException e){e.printStackTrace();}
	}

	private static boolean isCurrentSetting(String key){
		for(Setting setting : Setting.values()) if(setting.key.equals(key)) return true;
		return false;
	}
	private static Set<String> retiredSettings(){
		final Set<String> retired = new HashSet<>(List.of("enable_non_mapart_features", "listener.server_join",
				"listener.server_quit", "listener.container_open", "listener.click_block", "listener.game_message.read",
				"map_bot.loader", "command.assignpearl", "command.deletedmapsnearby", "command.exportmapimg", "command.maphashcode",
				"command.seen", "command.sendas", "mapart_features_only", "placement_helper.iframe",
				"placement_helper.mapart.auto", "map_highlights", "map_highlights.in_gui", "store_data_in_instance_folder"));
		for(Setting setting : Setting.values()) if(setting.legacyKey != null) retired.add(setting.legacyKey);
		retired.removeIf(LegacySettingsMigration::isCurrentSetting);
		return retired;
	}
	static Map<String, Boolean> parseSettings(String text){
		final Map<String, Boolean> values = new HashMap<>();
		final Set<String> retired = retiredSettings();
		for(String line : text.lines().toList()){
			final int comment = line.indexOf('#');
			if(comment != -1) line = line.substring(0, comment);
			final int separator = line.indexOf(':');
			if(separator == -1) continue;
			final String key = line.substring(0, separator).strip();
			if(!isCurrentSetting(key) && !retired.contains(key)) continue;
			final String value = line.substring(separator+1).strip();
			values.put(key, Boolean.parseBoolean(value));
		}
		return values;
	}
	private static void alias(Map<String, Boolean> values, String oldKey, String newKey){
		if(values.containsKey(oldKey)) values.putIfAbsent(newKey, values.get(oldKey));
	}
	static String settings(String text, Map<String, Boolean> values, boolean complete){
		final boolean legacy = values.keySet().stream().anyMatch(retiredSettings()::contains);
		values = new HashMap<>(values);
		if(values.containsKey("mapart_features_only")) values.putIfAbsent("enable_non_mapart_features", !values.get("mapart_features_only"));
		alias(values, "placement_helper.iframe", "placement_helper.iframe.autoplace");
		alias(values, "placement_helper.mapart.auto", "placement_helper.mapart.autoplace");
		alias(values, "map_highlights", "on_tick.iframes");
		alias(values, "map_highlights.in_gui", "on_tick.inventory");
		alias(values, "map_highlights.in_gui", "on_tick.container");

		final boolean mapartOnly = values.getOrDefault(Setting.MAPART_ONLY_DEFAULTS.key, !values.getOrDefault("enable_non_mapart_features", false));
		final Map<String, Boolean> additions = new LinkedHashMap<>();
		if(values.containsKey("store_data_in_instance_folder") && !values.containsKey(Setting.STORE_DATA_IN_CONFIG_FOLDER.key))
			additions.put(Setting.STORE_DATA_IN_CONFIG_FOLDER.key, !values.get("store_data_in_instance_folder"));
		if(legacy && !values.containsKey(Setting.MAPART_ONLY_DEFAULTS.key)) additions.put(Setting.MAPART_ONLY_DEFAULTS.key, mapartOnly);
		for(Setting setting : Setting.values()){
			if(values.containsKey(setting.key)) continue;
			if(setting.legacyKey != null && values.containsKey(setting.legacyKey)){
				additions.put(setting.key, values.get(setting.legacyKey));
			}
			else if(!mapartOnly && values.getOrDefault("enable_non_mapart_features", false)){
				// These capabilities previously shared the full-version gate instead of having individual settings.
				final Boolean enabled = switch(setting){
					case INVENTORY_TOOLS, CRAFTING, TRAVEL, APPEARANCE, CHAT_ACTIONS -> true;
					case WHISPER_SOUND -> values.getOrDefault("listener.game_message.read", false);
					case PEARL_MESSAGES -> values.getOrDefault("listener.game_message.read", false) && values.getOrDefault("epearl_owners", false);
					case FIREWORK_BOOST -> values.getOrDefault("listener.click_block", false);
					default -> null;
				};
				if(enabled != null) additions.put(setting.key, enabled);
			}
		}
		if(complete) for(Setting setting : Setting.values())
			if(!values.containsKey(setting.key)) additions.putIfAbsent(setting.key, setting.defaultEnabled);

		final List<String> kept = new ArrayList<>();
		for(String line : text.split("\\R", -1)){
			final int comment = line.indexOf('#');
			final String setting = comment == -1 ? line : line.substring(0, comment);
			final int separator = setting.indexOf(':');
			if(separator == -1 || (isCurrentSetting(setting.substring(0, separator).strip())
					&& !additions.containsKey(setting.substring(0, separator).strip()))) kept.add(line);
			else if(comment != -1) kept.add(line.substring(comment));
		}
		final String newline = text.contains("\r\n") ? "\r\n" : "\n";
		final StringBuilder result = new StringBuilder(String.join(newline, kept));
		for(var entry : additions.entrySet()){
			if(!result.isEmpty() && result.charAt(result.length()-1) != '\n') result.append(newline);
			result.append(entry.getKey()).append(": ").append(entry.getValue()).append(newline);
		}
		if(!text.endsWith("\n") && !result.isEmpty() && result.charAt(result.length()-1) == '\n') result.setLength(result.length()-newline.length());
		return result.toString();
	}
	static String settings(String text, Map<String, Boolean> values){return settings(text, values, false);}

	static List<Path> settingsFiles(Path file, Path gameDir){
		return List.of(file.resolveSibling("startup_settings.txt"), file.resolveSibling("enabled_features.txt"),
				gameDir.resolve("enabled_features.txt")).stream().distinct().filter(Files::exists).toList();
	}
	static String mergeSettings(String current, String legacy){
		current = settings(current, parseSettings(current));
		final Map<String, Boolean> values = parseSettings(current);
		final Map<String, Boolean> legacyValues = parseSettings(legacy);
		if(values.containsKey(Setting.MAPART_ONLY_DEFAULTS.key)) legacyValues.put(Setting.MAPART_ONLY_DEFAULTS.key, values.get(Setting.MAPART_ONLY_DEFAULTS.key));
		legacy = settings(legacy, legacyValues);
		if(current.isEmpty()) return legacy;
		final String newline = current.contains("\r\n") ? "\r\n" : "\n";
		final StringBuilder result = new StringBuilder(current);
		for(String line : legacy.split("\\R")){
			final int comment = line.indexOf('#');
			final String setting = comment == -1 ? line : line.substring(0, comment);
			final int separator = setting.indexOf(':');
			if(separator == -1) continue;
			final String key = setting.substring(0, separator).strip();
			if(values.containsKey(key)) continue;
			if(result.charAt(result.length()-1) != '\n') result.append(newline);
			result.append(line).append(newline);
			values.put(key, Boolean.parseBoolean(setting.substring(separator+1).strip()));
		}
		if(!current.endsWith("\n") && result.charAt(result.length()-1) == '\n') result.setLength(result.length()-newline.length());
		return result.toString();
	}

	private static boolean renameOption(JsonObject root, String section, String oldKey, String newKey){
		final JsonElement existing = root.get(section);
		if(existing == null || !existing.isJsonObject()) return false;
		final JsonObject options = existing.getAsJsonObject();
		if(!options.has(oldKey)) return false;
		if(!options.has(newKey)) options.add(newKey, options.get(oldKey));
		options.remove(oldKey);
		return true;
	}
	private static boolean moveOption(JsonObject root, JsonObject generic, String section, String key){
		if(!generic.has(key)) return false;
		final JsonElement existing = root.get(section);
		if(existing != null && !existing.isJsonObject()) return false;
		final JsonObject destination = existing == null ? new JsonObject() : existing.getAsJsonObject();
		if(!destination.has(key)) destination.add(key, generic.get(key));
		root.add(section, destination);
		generic.remove(key);
		return true;
	}
	static boolean configs(JsonObject root){
		for(String section : List.of("Generic", "Visuals", "Hotkeys", "Database")){
			if(root.has(section) && !root.get(section).isJsonObject()) return false;
		}
		boolean changed = renameOption(root, "Database", "saveSeenMapart", "saveSeenMapArt");
		changed |= renameOption(root, "Generic", "mapArtPlacerReach", "mapArtAutoPlaceReach");
		final JsonElement existing = root.get("Generic");
		if(existing != null && existing.isJsonObject()){
			final JsonObject generic = existing.getAsJsonObject();
			changed |= moveOption(root, generic, "Visuals", "clickDisplayAvailablePersistent");
			changed |= moveOption(root, generic, "Visuals", "iFrameNewMapNotifier");
			changed |= moveOption(root, generic, "Hotkeys", "hotbarSlotItemTypeScrollOrder");
			for(String key : List.of("mapStateCache", "mapStateCacheUnlocked", "mapStateCacheById", "mapStateCacheByName",
					"mapStateCacheByInvPos", "mapStateCacheByEchestPos", "mapStateCacheByContainerPos")){
				changed |= moveOption(root, generic, "Database", key);
			}
		}
		final JsonElement hotkeys = root.get("Hotkeys");
		if(hotkeys != null && hotkeys.isJsonObject()){
			for(String key : List.of("inventoryRestockIf", "inventoryRestockLeave", "inventoryRestockBlacklist", "inventoryRestockWhitelist",
					"inventoryOrganize1", "inventoryOrganize2", "inventoryOrganize3")){
				changed |= moveOption(root, hotkeys.getAsJsonObject(), "Generic", key);
			}
		}
		final Set<String> sections = new HashSet<>(List.of("guiTab", "serverListScroll"));
		for(Class<?> section : List.of(Configs.Generic.class, Configs.Visuals.class, Configs.Hotkeys.class, Configs.Database.class)){
			sections.add(section.getSimpleName());
			final JsonElement options = root.get(section.getSimpleName());
			if(options == null || !options.isJsonObject()) continue;
			final Set<String> known = new HashSet<>();
			try{
				for(var field : section.getFields()) if(IConfigBase.class.isAssignableFrom(field.getType()))
					known.add(((IConfigBase)field.get(null)).getName());
			}
			catch(IllegalAccessException e){throw new AssertionError(e);}
			changed |= options.getAsJsonObject().keySet().removeIf(key -> !known.contains(key));
		}
		changed |= root.keySet().removeIf(key -> !sections.contains(key));
		return changed;
	}

	private static JsonObject readObject(Path file) throws IOException{
		try(var reader = Files.newBufferedReader(file)){
			final JsonElement parsed = JsonParser.parseReader(reader);
			if(!parsed.isJsonObject()) throw new IOException("Expected a config object in "+file);
			final JsonObject root = parsed.getAsJsonObject();
			for(String section : List.of("Generic", "Visuals", "Hotkeys", "Database")){
				if(root.has(section) && !root.get(section).isJsonObject()) throw new IOException("Invalid config section: "+section);
			}
			return root;
		}
		catch(JsonParseException e){throw new IOException("Cannot parse legacy config "+file, e);}
	}
	private static boolean mergeConfigs(JsonObject current, JsonObject legacy){
		boolean changed = false;
		for(var entry : legacy.entrySet()){
			final JsonElement existing = current.get(entry.getKey());
			if(existing == null){current.add(entry.getKey(), entry.getValue().deepCopy()); changed = true;}
			else if(existing.isJsonObject() && entry.getValue().isJsonObject())
				changed |= mergeConfigs(existing.getAsJsonObject(), entry.getValue().getAsJsonObject());
		}
		return changed;
	}
	static void importConfigs(Path file) throws IOException{
		final Path legacyFile = file.resolveSibling("evmod.json");
		if(!Files.exists(legacyFile)) return;
		final JsonObject legacy = readObject(legacyFile);
		final boolean exists = Files.exists(file);
		final JsonObject current = exists ? readObject(file) : new JsonObject();
		configs(legacy);
		boolean changed = configs(current);
		changed |= mergeConfigs(current, legacy);
		if(!exists || changed) writeConfigs(file, current);
		Files.delete(legacyFile);  // Only after successful import; explicit modern values are authoritative.
	}

	static void write(Path file, String text) throws IOException{
		final Path temporary = Files.createTempFile(file.getParent(), ".evmod-migrate-", ".tmp");
		try{
			Files.writeString(temporary, text);
			try{Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);}
			catch(AtomicMoveNotSupportedException e){Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);}
		}
		finally{Files.deleteIfExists(temporary);}
	}
	static void writeConfigs(Path file, JsonObject root) throws IOException{
		write(file, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root));
	}
}