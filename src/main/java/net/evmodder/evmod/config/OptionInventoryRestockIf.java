package net.evmodder.evmod.config;

public enum OptionInventoryRestockIf implements ConfigOptionEntry {
	ANY("any"),
	ONE_TYPE("allOneType"),
	RESUPPLY("allResupply");

	private final String name;
	OptionInventoryRestockIf(String name){this.name = name;}

	@Override public String getStringValue(){return this.name;}
	@Override public String getOptionListName(){return "inventoryRestockIf";}
}