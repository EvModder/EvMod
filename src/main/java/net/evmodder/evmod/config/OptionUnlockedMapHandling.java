package net.evmodder.evmod.config;

public enum OptionUnlockedMapHandling implements ConfigOptionEntry {
	SKIP("skip"),
	UNIQUE("unique"),
	EQUIVALENT("equivalent");

	private final String name;
	OptionUnlockedMapHandling(String name){this.name = name;}

	@Override public String getStringValue(){return this.name;}
	@Override public String getOptionListName(){return "unlockedMapHandling";}
}