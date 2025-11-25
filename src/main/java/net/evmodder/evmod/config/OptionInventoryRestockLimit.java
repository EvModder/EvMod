package net.evmodder.evmod.config;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;
import fi.dy.masa.malilib.util.StringUtils;
import net.evmodder.evmod.Main;

public enum OptionInventoryRestockLimit implements IConfigOptionListEntry {
	LEAVE_ONE_ITEM("leaveOneItem", Main.MOD_ID+".gui.label.inventoryRestock.leaveOneItem"),//TODO: leaveOne + leaveUnlessResupply
	LEAVE_ONE_STACK("leaveOneStack", Main.MOD_ID+".gui.label.inventoryRestock.leaveOneStack"),
	LEAVE_UNLESS_ONE_TYPE("leaveUnlessOneType", Main.MOD_ID+".gui.label.inventoryRestock.leaveUnlessOneType"),
	LEAVE_UNLESS_ALL_RESUPPLY("leaveUnlessAllResupply", Main.MOD_ID+".gui.label.inventoryRestock.leaveUnlessAllResupply")
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