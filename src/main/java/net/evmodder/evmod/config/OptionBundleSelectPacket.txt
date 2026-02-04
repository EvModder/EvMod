package net.evmodder.evmod.config;

public enum OptionBundleSelectPacket implements ConfigOptionEntry {
	OFF("off"),
	NORMAL("normal"),
	REVERSE("reverse");

	private final String name;
	OptionBundleSelectPacket(String name){this.name = name;}

	@Override public String getStringValue(){return this.name;}
	@Override public String getOptionListName(){return "bundleSelectPacket";}
}