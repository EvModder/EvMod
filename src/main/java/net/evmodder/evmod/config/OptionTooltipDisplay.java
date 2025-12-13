package net.evmodder.evmod.config;

public enum OptionTooltipDisplay implements ConfigOptionEntry {
	OFF("off"),
	ON("on"),
	ADVANCED_TOOLTIPS("advanced");

	private final String name;
	OptionTooltipDisplay(String name){this.name = name;}

	@Override public String getStringValue(){return this.name;}
	@Override public String getOptionListName(){return "tooltipDisplay";}
}