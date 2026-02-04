package net.evmodder.evmod.config;

public interface OptionBundleSelectPrio{
	public enum BundleSelectPrio{
		FIRST, LAST,
		FULLEST, FULLEST_NOT_FULL,
		EMPTIEST, EMPTIEST_NOT_EMPTY
	}

	BundleSelectPrio getSelectPrio();
}
