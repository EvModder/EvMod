package net.evmodder.KeyBound.config;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;
import fi.dy.masa.malilib.util.StringUtils;

public enum OptionInventoryRestockLimit implements IConfigOptionListEntry {
	LEAVE_ONE_ITEM("leaveOneItem", "keybound.gui.label.inventoryRestock.leaveOneItem"),
	LEAVE_ONE_STACK("leaveOneStack", "keybound.gui.label.inventoryRestock.leaveOneStack"),
	LEAVE_UNLESS_ONE_TYPE("leaveUnlessOneType", "keybound.gui.label.inventoryRestock.leaveUnlessOneType"),
	LEAVE_UNLESS_ALL_RESUPPLY("leaveUnlessAllResupply", "keybound.gui.label.inventoryRestock.leaveUnlessAllResupply")
	;

	private final String configString;
	private final String translationKey;

	OptionInventoryRestockLimit(String configString, String translationKey){
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