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
//		String name(){return name;}
//		UUID uuid(){return uuid;}
	}
	private final List<UUID> defaultUUIDs;
	private final List<NameAndUUID> players = new ArrayList<>();
	private List<String> strings = new ArrayList<>(); // Needed due to some B.S.

	@Override public void setStrings(List<String> strings){
//		names = names.stream().map(String::trim).toList();
		if(strings.equals(getStrings())) return;
		LinkedHashSet<String> set = new LinkedHashSet<>();
		set.addAll(strings);
		players.clear();
		set.stream().map(n -> {
			NameAndUUID player = new NameAndUUID(n, null);
			player.uuid = MojangProfileLookup.uuidLookup.get(n, u->{
				if(u != MojangProfileLookup.UUID_404) player.uuid=u;
				else synchronized(players){players.removeIf(p -> p.name == n);} // Not a real player name
			});
			return player;
		}).forEach(players::add);
		this.strings = strings;
		onValueChanged();
	}
	private void setUUIDs(List<UUID> uuids){
		players.clear();
		set.stream().map(u -> {
			NameAndUUID player = new NameAndUUID(null, u);
			player.name = MojangProfileLookup.nameLookup.get(u, n->{
				if(n != MojangProfileLookup.NAME_404) player.name=n;
				else synchronized(players){players.removeIf(p -> p.uuid == u);} // Not a real player UUID
			});
			return player;
		}).forEach(players::add);
		getStrings();
		onValueChanged();
	}

	public ConfigPlayerList(String name, List<UUID> defaultUUIDs, String comment, String prettyName, String translatedName){
		super(ConfigType.STRING_LIST, name, comment, prettyName, translatedName);
		for(UUID uuid : defaultUUIDs){
			NameAndUUID player = new NameAndUUID(null, uuid);
			player.name = MojangProfileLookup.nameLookup.get(uuid, n->player.name=n);
			players.add(player);
//			player.name = MojangProfileLookup.nameLookup.get(uuid, n->{
//				if(n != MojangProfileLookup.NAME_404) player.name=n;
//				else synchronized(players){players.removeIf(p -> p.uuid == uuid);} // Not a real player UUID!!
//			});
		}
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

	public List<UUID> getUUIDs(){return players.stream().map(nu -> nu.uuid).toList();}
	@Override public List<String> getStrings(){
		// Gotta do this instead of stream.toList() due to MaLiLib modifying the return value and expecting those changes to be reflected
		strings.clear();
		for(NameAndUUID nu : players) strings.add(nu.name);
		return strings;
	}
	@Override public ImmutableList<String> getDefaultStrings(){
		return ImmutableList.copyOf(defaultUUIDs.stream().map(MojangProfileLookup.nameLookup::getSync).toList());
	}
	@Override public void setModified(){setStrings(strings); onValueChanged();}
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
			uuids.add(uuidStr.equals("null") ? null : UUID.fromString(uuidStr));
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