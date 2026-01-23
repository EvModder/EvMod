package net.evmodder.evmod.config;

public enum OptionInventoryRestockLeave implements ConfigOptionEntry {
	NONE("none"),
	ONE_ITEM("oneItem"),
	ONE_STACK("oneStack");

	private final String name;
	OptionInventoryRestockLeave(String name){this.name = name;}

	@Override public String getStringValue(){return this.name;}
	@Override public String getOptionListName(){return "inventoryRestockLeave";}
}