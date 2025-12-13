package net.evmodder.evmod.config;

public enum OptionMapStateCache implements ConfigOptionEntry {
	OFF("off"),
	MEMORY("memory"),
	MEMORY_AND_DISK("memoryAndDisk");

	private final String name;
	OptionMapStateCache(String name){this.name = name;}

	@Override public String getStringValue(){return this.name;}
	@Override public String getOptionListName(){return "cacheMapState";}
}