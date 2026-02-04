package net.evmodder.evmod.config;

public enum OptionBundleSelectPrioStow implements ConfigOptionEntry, OptionBundleSelectPrio{
	FIRST(BundleSelectPrio.FIRST, "first"),
	LAST(BundleSelectPrio.LAST, "last"),
	FULLEST_NOT_FULL(BundleSelectPrio.FULLEST_NOT_FULL, "fullestNotFull"),
	EMPTIEST(BundleSelectPrio.EMPTIEST, "emptiest");

	private final BundleSelectPrio prio;
	private final String name;
	OptionBundleSelectPrioStow(BundleSelectPrio prio, String name){this.prio = prio; this.name = name;}

	@Override public final BundleSelectPrio getSelectPrio(){return prio;}
	@Override public final String getStringValue(){return name;}
	@Override public final String getOptionListName(){return "bundleSelectionPriority";}
}