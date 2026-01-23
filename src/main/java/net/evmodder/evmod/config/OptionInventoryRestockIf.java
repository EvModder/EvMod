package net.evmodder.evmod.config;

public enum OptionInventoryRestockLimit implements ConfigOptionEntry {
	LEAVE_ONE_ITEM("leaveOneItem"), //TODO: leaveOne + leaveUnlessResupply
	LEAVE_ONE_STACK("leaveOneStack"),
	LEAVE_UNLESS_ONE_TYPE("leaveUnlessOneType"),
	LEAVE_UNLESS_ALL_RESUPPLY("leaveUnlessAllResupply");

	private final String name;
	OptionInventoryRestockLimit(String name){this.name = name;}

	@Override public String getStringValue(){return this.name;}
	@Override public String getOptionListName(){return "inventoryRestock";}
}