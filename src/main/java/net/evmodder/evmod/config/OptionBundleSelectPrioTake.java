package net.evmodder.evmod.config;

public enum OptionBundleSelectPrioTake implements ConfigOptionEntry, OptionBundleSelectPrio{
	FIRST(BundleSelectPrio.FIRST, "first"),
	LAST(BundleSelectPrio.LAST, "last"),
	EMPTIEST_NOT_EMPTY(BundleSelectPrio.EMPTIEST_NOT_EMPTY, "emptiestNotEmpty"),
	FULLEST(BundleSelectPrio.FULLEST, "fullest");

	private final BundleSelectPrio prio;
	private final String name;
	OptionBundleSelectPrioTake(BundleSelectPrio prio, String name){this.prio = prio; this.name = name;}

	@Override public final BundleSelectPrio getSelectPrio(){return prio;}
	@Override public final String getStringValue(){return name;}
	@Override public final String getOptionListName(){return "bundleSelectionPriority";}
}