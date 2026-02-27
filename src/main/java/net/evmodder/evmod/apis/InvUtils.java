package net.evmodder.evmod.apis;

import java.util.stream.Stream;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.ItemStack;

public final class InvUtils{
	public static final Stream<ItemStack> getAllNestedItems(ItemStack item){
		final BundleContentsComponent contents = item.get(DataComponentTypes.BUNDLE_CONTENTS);
		if(contents != null) return getAllNestedItems(contents.stream()/*.sequential()*/);
		final ContainerComponent container = item.get(DataComponentTypes.CONTAINER);
		if(container != null) return getAllNestedItems(container.streamNonEmpty()/*.sequential()*/);
		return Stream.of(item);
	}
	public static final Stream<ItemStack> getAllNestedItems(Stream<ItemStack> items){
		return items.flatMap(InvUtils::getAllNestedItems);
	}
	public static final Stream<ItemStack> getAllNestedItemsExcludingBundles(ItemStack item){
		final ContainerComponent container = item.get(DataComponentTypes.CONTAINER);
		if(container != null) return getAllNestedItemsExcludingBundles(container.streamNonEmpty());
		return Stream.of(item);
	}
	public static final Stream<ItemStack> getAllNestedItemsExcludingBundles(Stream<ItemStack> items){
		return items.flatMap(InvUtils::getAllNestedItemsExcludingBundles);
	}
}