package net.evmodder.evmod.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import fi.dy.masa.malilib.MaLiLib;
import fi.dy.masa.malilib.config.options.ConfigStringList;
import fi.dy.masa.malilib.util.StringUtils;
import net.evmodder.evmod.Main;
import net.evmodder.evmod.apis.MojangProfileLookup;
import net.evmodder.evmod.apis.MojangProfileLookupConstants;

public class ConfigPlayerList extends ConfigStringList{
	public static class NameAndUUID{ // Basically, a record but mutable :P
		String name; UUID uuid;
		public NameAndUUID(String n, UUID u){name=n; uuid=u;}
	}

	private final List<NameAndUUID> defaultPlayers;
	private final ArrayList<NameAndUUID> players = new ArrayList<>();

	public ConfigPlayerList(String name, List<NameAndUUID> defaultPlayers, String comment, String prettyName, String translatedName){
		super(name, ImmutableList.copyOf(defaultPlayers.stream().map(nu -> nu.name).toList()), prettyName, translatedName);
		this.defaultPlayers = defaultPlayers;
		players.addAll(defaultPlayers);
	}
	public ConfigPlayerList(String name, List<NameAndUUID> defaultPlayers, String comment, String prettyName){
		this(name, defaultPlayers, comment, prettyName, name);
	}
	public ConfigPlayerList(String name, List<NameAndUUID> defaultPlayers, String comment){
		this(name, defaultPlayers, comment, StringUtils.splitCamelCase(name), name);
	}
	public ConfigPlayerList(String name, List<NameAndUUID> defaultPlayers){
		this(name, defaultPlayers, name + " Comment?", StringUtils.splitCamelCase(name), name);
	}

	public List<UUID> getUUIDs(){return players.stream().map(nu -> nu.uuid).toList();}

	@Override public void onValueChanged(){
		Main.LOGGER.info("onValueChanged, #players="+players.size()+",#strings="+getStrings().size());
		List<String> strings = getStrings();
		LinkedHashSet<String> set = new LinkedHashSet<>();
		set.addAll(strings);
		players.clear();
		set.stream().filter(n -> n != null && !n.isBlank()).map(n -> {
			NameAndUUID player = new NameAndUUID(n, null);
			UUID uuid = MojangProfileLookup.uuidLookup.get(n, u->{
				if(u != MojangProfileLookupConstants.UUID_404) player.uuid=u;
				else synchronized(players){ // Not a real player name
//					strings.remove(n);
					players.removeIf(p -> p.name == n);
//					onValueChanged();
				}
			});
			if(uuid != MojangProfileLookupConstants.UUID_404 && uuid != MojangProfileLookupConstants.UUID_LOADING) player.uuid = uuid;
			return player;
		}).forEach(players::add);
		super.onValueChanged();
	}
	@Override public void resetToDefault(){
		players.clear();
		players.addAll(defaultPlayers);
		super.resetToDefault();
	}

	@Override public void setValueFromJsonElement(JsonElement element){
		if(!element.isJsonArray()){
			MaLiLib.LOGGER.warn("Failed to set config value for '{}' from the JSON element '{}'", getName(), element);
			return;
		}
		JsonArray arr = element.getAsJsonArray();
//		Main.LOGGER.info("[ConfigPlayerList] fromJson, arr.size="+arr.size());
		List<String> strings = getStrings();
		players.clear();
		strings.clear();
		for(int i=0; i<arr.size(); ++i){
			final String nameAndUuid = arr.get(i).getAsString();
			final int sep = nameAndUuid.indexOf(':');
			final String cachedName = nameAndUuid.substring(0, sep);
			final String uuidStr = nameAndUuid.substring(sep+1);
			strings.add(cachedName);
			players.add(new NameAndUUID(cachedName, UUID.fromString(uuidStr)));
//			MojangProfileLookup.nameLookup.get(uuids.get(i), null); // Fetch up-to-date username
			MojangProfileLookup.nameLookup.putIfAbsent(players.get(i).uuid, cachedName); // Use cached name in the meantime
		}
	}

	@Override public JsonElement getAsJsonElement(){
		Main.LOGGER.info("toJson, #players="+players.size()+",#strings="+getStrings().size());
		JsonArray arr = new JsonArray();
		for(NameAndUUID nu : players){
			if(nu.uuid == null) continue;
			arr.add(new JsonPrimitive(nu.name + ":" + nu.uuid));
		}
		return arr;
	}
}