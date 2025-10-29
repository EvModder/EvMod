package net.evmodder.KeyBound.config;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;
import fi.dy.masa.malilib.util.StringUtils;

public enum MapStateCacheOptionType implements IConfigOptionListEntry {
	BY_ID("byId", "keybound.gui.label.cacheMapstateType.byId"),
	BY_NAME("byName", "keybound.gui.label.cacheMapstateType.byName"),
	BY_INV_POS("byInvPos", "keybound.gui.label.cacheMapstateType.byInvPos");

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