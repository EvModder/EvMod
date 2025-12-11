package net.evmodder.evmod.config;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;
import fi.dy.masa.malilib.util.StringUtils;
import net.evmodder.evmod.Main;

public enum OptionInvisIframes implements IConfigOptionListEntry {
	OFF("off", Main.MOD_ID+".gui.label.invisIFramesMapArt.off"),
	ANY_ITEM("all", Main.MOD_ID+".gui.label.invisIFramesMapArt.all"),
	MAPART("mapArt", Main.MOD_ID+".gui.label.invisIFramesMapArt.mapArt"),
	SEMI_TRANSPARENT_MAPART("mapArtSemiTransparent", Main.MOD_ID+".gui.label.invisIFramesMapArt.mapArtSemiTransparent");

	private final String configString;
	private final String translationKey;

	OptionInvisIframes(String configString, String translationKey){
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