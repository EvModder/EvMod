package net.evmodder.KeyBound.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import fi.dy.masa.malilib.MaLiLib;
import fi.dy.masa.malilib.config.ConfigType;
import fi.dy.masa.malilib.config.IConfigStringList;
import fi.dy.masa.malilib.config.options.ConfigBase;
import fi.dy.masa.malilib.util.StringUtils;
import net.evmodder.KeyBound.apis.MojangProfileLookup;

public class ConfigPlayerList extends ConfigBase<ConfigPlayerList> implements IConfigStringList{
	private class NameAndUUID{ // Basically, a record but mutable :P
		String name; UUID uuid;
		NameAndUUID(String n, UUID u){name=n; uuid=u;}
		String name(){return name;}
		UUID uuid(){return uuid;}
	}
	private final List<UUID> defaultUUIDs;
	private List<NameAndUUID> players;

	@Override public void setStrings(List<String> names){
//		names = names.stream().map(String::trim).toList();
		if(names.equals(getStrings())) return;
		LinkedHashSet<String> set = new LinkedHashSet<>();
		set.addAll(names);
		players = set.stream().map(n -> {
			NameAndUUID player = new NameAndUUID(n, null);
			player.uuid = MojangProfileLookup.uuidLookup.get(n, u->{
				if(u != MojangProfileLookup.UUID_404) player.uuid=u;
				else synchronized(players){players.removeIf(p -> p.name == n);} // Not a real player name
			});
			return player;
		}).toList();
	}
	public void setUUIDs(List<UUID> uuids){
		if(uuids.equals(getUUIDs())) return;
		LinkedHashSet<UUID> set = new LinkedHashSet<>();
		set.addAll(uuids);
		players = set.stream().map(u -> {
			NameAndUUID player = new NameAndUUID(null, u);
			player.name = MojangProfileLookup.nameLookup.get(u, n->{
				if(n != MojangProfileLookup.NAME_404) player.name=n;
				else synchronized(players){players.removeIf(p -> p.uuid == u);} // Not a real player UUID
			});
			return player;
		}).toList();
	}

	public ConfigPlayerList(String name, List<UUID> defaultUUIDs, String comment, String prettyName, String translatedName){
		super(ConfigType.STRING_LIST, name, comment, prettyName, translatedName);
		setUUIDs(defaultUUIDs);
		this.defaultUUIDs = getUUIDs();
	}
	public ConfigPlayerList(String name, List<UUID> defaultUUIDs, String comment, String prettyName){
		this(name, defaultUUIDs, comment, prettyName, name);
	}
	public ConfigPlayerList(String name, List<UUID> defaultUUIDs, String comment){
		this(name, defaultUUIDs, comment, StringUtils.splitCamelCase(name), name);
	}
	public ConfigPlayerList(String name, List<UUID> defaultUUIDs){
		this(name, defaultUUIDs, name + " Comment?", StringUtils.splitCamelCase(name), name);
	}

	@Override public List<String> getStrings(){return players.stream().map(NameAndUUID::name).toList();}
	public List<UUID> getUUIDs(){return players.stream().map(NameAndUUID::uuid).toList();}
	@Override public ImmutableList<String> getDefaultStrings(){
		return ImmutableList.copyOf(defaultUUIDs.stream().map(MojangProfileLookup.nameLookup::getSync).toList());
	}
	@Override public void setModified(){onValueChanged();}
	@Override public boolean isModified(){return !defaultUUIDs.equals(getUUIDs());}
	@Override public void resetToDefault(){setUUIDs(defaultUUIDs);}


	@Override public void setValueFromJsonElement(JsonElement element){
		if(!element.isJsonArray()){
			MaLiLib.LOGGER.warn("Failed to set config value for '{}' from the JSON element '{}'", getName(), element);
			return;
		}
		JsonArray arr = element.getAsJsonArray();
		final int size = arr.size();
		ArrayList<UUID> uuids = new ArrayList<>(size);
		for(int i=0; i<arr.size(); ++i){
			final String nameAndUuid = arr.get(i).getAsString();
			final int sep = nameAndUuid.indexOf(':');
			final String cachedName = nameAndUuid.substring(0, sep);
			final String uuidStr = nameAndUuid.substring(sep+1);
			uuids.set(i, UUID.fromString(uuidStr));
//			MojangProfileLookup.nameLookup.get(uuids.get(i), null); // Fetch up-to-date username
			MojangProfileLookup.nameLookup.putIfAbsent(uuids.get(i), cachedName); // Use cached name in the meantime
		}
		setUUIDs(uuids);
	}

	@Override public JsonElement getAsJsonElement(){
		JsonArray arr = new JsonArray();
		for(NameAndUUID nu : players) arr.add(new JsonPrimitive(nu.name + ":" + nu.uuid));
		return arr;
	}
}