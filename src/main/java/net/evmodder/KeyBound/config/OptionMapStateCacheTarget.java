package net.evmodder.KeyBound.config;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;
import fi.dy.masa.malilib.util.StringUtils;
import net.evmodder.KeyBound.Main;

public enum OptionMapStateCacheTarget implements IConfigOptionListEntry {
	BY_ID("byId", Main.MOD_ID+".gui.label.cacheMapstate.target.byId"),
	BY_NAME("byName", Main.MOD_ID+".gui.label.cacheMapstate.target.byName"),
	BY_INV_POS("byInvPos", Main.MOD_ID+".gui.label.cacheMapstate.target.byInvPos");

	private final String configString;
	private final String translationKey;

	OptionMapStateCacheTarget(String configString, String translationKey){
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