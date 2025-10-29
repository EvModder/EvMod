package net.evmodder.KeyBound.config;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;
import fi.dy.masa.malilib.util.StringUtils;

public enum MapStateCacheOptionType implements IConfigOptionListEntry {
	BY_ID("by_id", "keybound.gui.label.cache_mapstate_type.by_id"),
	BY_NAME("by_name", "keybound.gui.label.cache_mapstate_type.by_name"),
	BY_INV_POS("by_inv_pos", "keybound.gui.label.cache_mapstate_type.by_inv_pos");

	private final String configString;
	private final String translationKey;

	MapStateCacheOptionType(String configString, String translationKey){
		this.configString = configString;
		this.translationKey = translationKey;
	}

	@Override public String getStringValue(){return this.configString;}
	@Override public String getDisplayName(){return StringUtils.translate(this.translationKey);}

	@Override public IConfigOptionListEntry cycle(boolean forward){
		int id = ordinal();
		assert id >= 0 && id < values().length;
		return values()[forward ? (++id==values().length ? 0 : id) : (--id==0 ? values().length-1 : id)];
	}

	@Override public IConfigOptionListEntry fromString(String name){
		for(var v : values()) if(v.configString.equalsIgnoreCase(name)) return v;
		throw new IllegalArgumentException(getClass().getName()+": Invalid argument provided to fromString(): "+name);
	}
}