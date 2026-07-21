package net.evmodder.evmod.apis;

import java.util.stream.Stream;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;

public final class InvUtils{
	public static final Stream<ItemStack> getAllNestedItems(ItemStack item){
		final BundleContents contents = item.get(DataComponents.BUNDLE_CONTENTS);
		if(contents != null) return getAllNestedItems(contents.itemCopyStream()/*.sequential()*/);
		final ItemContainerContents container = item.get(DataComponents.CONTAINER);
		if(container != null) return getAllNestedItems(container.nonEmptyItemCopyStream()/*.sequential()*/);
		return Stream.of(item);
	}
	public static final Stream<ItemStack> getAllNestedItems(Stream<ItemStack> items){
		return items.flatMap(InvUtils::getAllNestedItems);
	}
	public static final Stream<ItemStack> getAllNestedItemsExcludingBundles(ItemStack item){
		final ItemContainerContents container = item.get(DataComponents.CONTAINER);
		if(container != null) return getAllNestedItemsExcludingBundles(container.nonEmptyItemCopyStream());
		return Stream.of(item);
	}
	public static final Stream<ItemStack> getAllNestedItemsExcludingBundles(Stream<ItemStack> items){
		return items.flatMap(InvUtils::getAllNestedItemsExcludingBundles);
	}
}