package net.evmodder.KeyBound.config;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;
import fi.dy.masa.malilib.util.StringUtils;

public enum TooltipDisplayOption implements IConfigOptionListEntry {
	OFF("off", "keybound.gui.label.tooltip_display.off"),
	ON("on", "keybound.gui.label.tooltip_display.on"),
	ADVANCED_TOOLTIPS("advanced_tooltips", "keybound.gui.label.tooltip_display.advanced");

	private final String configString;
	private final String translationKey;

	TooltipDisplayOption(String configString, String translationKey){
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
		throw new IllegalArgumentException("TooltipDisplayOption: Invalid argument provided to fromString(): "+name);
	}
}
