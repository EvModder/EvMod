package net.evmodder.evmod.config;

public enum OptionInvisIframes implements ConfigOptionEntry {
	OFF("off"),
	ANY_ITEM("all"),
	MAPART("mapArt"),
	SEMI_TRANSPARENT_MAPART("mapArtSemiTransparent");

	private final String name;
	OptionInvisIframes(String name){this.name = name;}

	@Override public String getStringValue(){return this.name;}
	@Override public String getOptionListName(){return "invisIFrames";}
}