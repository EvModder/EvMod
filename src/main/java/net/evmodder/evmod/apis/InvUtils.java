package net.evmodder.evmod.apis;

import static net.evmodder.evmod.compat.MinecraftCompat.nonEmptyItems;
import static net.evmodder.evmod.compat.MinecraftCompat.bundleItems;
import static net.evmodder.evmod.compat.MinecraftCompat.nonEmptyItemViews;

import java.util.stream.Stream;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
//? >=26.1 {
import net.minecraft.world.item.ItemInstance;
//?}
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;

public final class InvUtils{
	/** Depth-first, encounter-ordered views; no stacks are copied, including nested contents. */
	//? >=26.1 {
	public static final Stream<ItemInstance> getAllNestedItemViews(ItemInstance item){
	//?} else {
	/*public static final Stream<ItemStack> getAllNestedItemViews(ItemStack item){*/
	//?}
		final BundleContents contents = item.get(DataComponents.BUNDLE_CONTENTS);
		if(contents != null) return getAllNestedItemViews(bundleItems(contents));
		final ItemContainerContents container = item.get(DataComponents.CONTAINER);
		if(container != null) return getAllNestedItemViews(nonEmptyItemViews(container));
		return Stream.of(item);
	}
	//? >=26.1 {
	public static final Stream<ItemInstance> getAllNestedItemViews(Stream<? extends ItemInstance> items){
	//?} else {
	/*public static final Stream<ItemStack> getAllNestedItemViews(Stream<? extends ItemStack> items){*/
	//?}
		return items.flatMap(InvUtils::getAllNestedItemViews);
	}
	//? >=26.1 {
	public static final Stream<ItemInstance> getAllNestedItemViewsExcludingBundles(ItemInstance item){
	//?} else {
	/*public static final Stream<ItemStack> getAllNestedItemViewsExcludingBundles(ItemStack item){*/
	//?}
		final ItemContainerContents container = item.get(DataComponents.CONTAINER);
		if(container != null) return getAllNestedItemViewsExcludingBundles(nonEmptyItemViews(container));
		return Stream.of(item);
	}
	//? >=26.1 {
	public static final Stream<ItemInstance> getAllNestedItemViewsExcludingBundles(Stream<? extends ItemInstance> items){
	//?} else {
	/*public static final Stream<ItemStack> getAllNestedItemViewsExcludingBundles(Stream<? extends ItemStack> items){*/
	//?}
		return items.flatMap(InvUtils::getAllNestedItemViewsExcludingBundles);
	}
	// Stack/snapshot consumers retain the copying API rather than borrowing mutable legacy contents.
	public static final Stream<ItemStack> getAllNestedItems(ItemStack item){
		final BundleContents contents = item.get(DataComponents.BUNDLE_CONTENTS);
		if(contents != null) return getAllNestedItems(contents.itemCopyStream()/*.sequential()*/);
		final ItemContainerContents container = item.get(DataComponents.CONTAINER);
		if(container != null) return getAllNestedItems(nonEmptyItems(container)/*.sequential()*/);
		return Stream.of(item);
	}
	public static final Stream<ItemStack> getAllNestedItems(Stream<ItemStack> items){
		return items.flatMap(InvUtils::getAllNestedItems);
	}
	public static final Stream<ItemStack> getAllNestedItemsExcludingBundles(ItemStack item){
		final ItemContainerContents container = item.get(DataComponents.CONTAINER);
		if(container != null) return getAllNestedItemsExcludingBundles(nonEmptyItems(container));
		return Stream.of(item);
	}
	public static final Stream<ItemStack> getAllNestedItemsExcludingBundles(Stream<ItemStack> items){
		return items.flatMap(InvUtils::getAllNestedItemsExcludingBundles);
	}
}