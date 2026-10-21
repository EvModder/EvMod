package net.evmodder.evmod.onTick;

import static net.evmodder.evmod.compat.MinecraftCompat.screen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.apis.MapColorUtils;
import net.evmodder.evmod.apis.MapGroupUtils;
import net.evmodder.evmod.apis.TickListener;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.CartographyTableScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
//? >=26.1 {
import net.minecraft.world.item.ItemInstance;
//?}
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public final class UpdateContainerContents implements TickListener{
	private static final HashSet<UUID> duplicatesInContainer = new HashSet<>(), inContainerAndInInv = new HashSet<>();
	// Cache invalidation token, not an item hash. Never reset on menu transitions.
	// Accessed by: MixinUpdateContainerContentsMapNames
	private static int mapsInContainerRevision;
	// Accessed by: MixinAbstractContainerScreenTitle
	@SuppressWarnings("unused") private static MutableComponent customTitle;

	public static final int getMapsInContainerRevision(){return mapsInContainerRevision;}  // Accessed by: TooltipMapNameColor, TooltipMapLoreMetadata
	public static final boolean hasDuplicateInContainer(final UUID colorsId){return duplicatesInContainer.contains(colorsId);}  // Accessed by: TooltipMapNameColor
	public static final boolean isInInvAndContainer(final UUID colorsId){return inContainerAndInInv.contains(colorsId);}  // Accessed by: TooltipMapNameColor

	// Constantly-recycled; defined here to avoid frequent calls to "new"
	private final HashSet<UUID> uniqueMapIds = new HashSet<>();
	private final ArrayList<Integer> asterisks = new ArrayList<>(7);
	private int lastHash = -1, lastContainerHash;
	private AbstractContainerMenu lastMenu;
	private boolean hasNestedMapItems;
	// Borrowed views, valid only during this tick; consumers must not mutate or retain this buffer.
	//? >=26.1 {
	private final List<ItemInstance> mapItems = new ArrayList<>();
	//?} else {
	/*private final List<ItemStack> mapItems = new ArrayList<>();*/
	//?}

	private final void handleContainerChangeEvent(final int syncId){
		// TODO: move logic from ContainerOpenCloseListener to here? Or otherwise delete this
	}
	// Traverse in the same depth-first order as InvUtils; never copy or modify the borrowed items.
	//? >=26.1 {
	private void collectMapItems(final ItemInstance stack, final boolean nested){
	//?} else {
	/*private void collectMapItems(final ItemStack stack, final boolean nested){*/
	//?}
		final BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
		if(bundle != null){
			for(var item : bundle.items()) collectMapItems(item, true);
			return;
		}
		final ItemContainerContents container = stack.get(DataComponents.CONTAINER);
		if(container != null){
			for(var item : container.nonEmptyItems()) collectMapItems(item, true);
			return;
		}
		if(stack.get(DataComponents.MAP_ID) != null){
			hasNestedMapItems |= nested;
			mapItems.add(stack);
		}
	}
	//? >=26.1 {
	private final List<ItemInstance> getAllMapItemsInContainer(final List<Slot> slots){
	//?} else {
	/*private final List<ItemStack> getAllMapItemsInContainer(final List<Slot> slots){*/
	//?}
		mapItems.clear();
		hasNestedMapItems = false;
		for(int i=0; i<slots.size()-36; ++i) collectMapItems(slots.get(i).getItem(), false);
		return mapItems;
	}
	@Override public final void onTickStart(final Minecraft client){
		if(client.player == null || client.level == null || !client.player.isAlive() ||
			screen(client) == null || !(screen(client) instanceof AbstractContainerScreen hs) ||
			hs.getMenu().containerId == 0 ||  // InventoryScreen/CreativeScreen/RecipeBookScreen (NOT a container)
			hs instanceof AnvilScreen ||  // These get false-flagged for "duplicate map in container" with i/o slots
			hs instanceof CraftingScreen ||
			hs instanceof CartographyTableScreen)
		{
			customTitle = null;
			if(lastMenu != null){++mapsInContainerRevision; lastMenu = null; mapItems.clear();}
			inContainerAndInInv.clear();
			duplicatesInContainer.clear();
			handleContainerChangeEvent(0);
			return;
		}
		{
			final AbstractContainerMenu sh = client.player.containerMenu;
			handleContainerChangeEvent(sh == null ? 0 : sh.containerId);
		}

		final boolean hadNestedMapItems = hasNestedMapItems;
		//? >=26.1 {
		final List<ItemInstance> mapItems = getAllMapItemsInContainer(hs.getMenu().slots);
		//?} else {
		/*final List<ItemStack> mapItems = getAllMapItemsInContainer(hs.getMenu().slots);*/
		//?}
		// Plain maps retain the old identity-hash shortcut. Nested maps still refresh every tick:
		// their map data can change without replacing the item, and copying used to invalidate implicitly.
		final int containerHash = hasNestedMapItems ? 0 : mapItems.hashCode();
		final int newRevision = mapsInContainerRevision + (hasNestedMapItems || hadNestedMapItems
				|| lastMenu != hs.getMenu() || lastContainerHash != containerHash ? 1 : 0);
		mapsInContainerRevision = newRevision;
		lastContainerHash = containerHash;
		lastMenu = hs.getMenu();

		final boolean SHOW_ASTERISKS = Configs.Visuals.MAP_HIGHLIGHT_CONTAINER_NAME.getBooleanValue();
		if(!SHOW_ASTERISKS && !Configs.Visuals.MAP_HIGHLIGHT_TOOLTIP.getBooleanValue()) return;

		final int hash = UpdateInventoryContents.getMapsInInvHash() + mapsInContainerRevision;
		if(lastHash == hash) return;
		lastHash = hash;
//		Main.LOGGER.info("ContainerHighlighter: Recomputing cache");

		inContainerAndInInv.clear();
		duplicatesInContainer.clear();
		customTitle = null;
		if(mapItems.isEmpty()) return;
		final List<MapItemSavedData> states = mapItems.stream().map(i -> MapItem.getSavedData(i.get(DataComponents.MAP_ID), client.level)).filter(Objects::nonNull).toList();
		final List<UUID> colorIds;
		{
			// More optimized compared to the commented-out version, but has the downside of skipping unlocked fully transparent maps (slightly incorrect)
			Stream<UUID> stream = states.stream().map(MapGroupUtils::getIdForMapState);
			if(Configs.Generic.SKIP_VOID_MAPS.getBooleanValue()) stream = stream.filter(id -> id != MapColorUtils.FULLY_TRANSPARENT_COLORS_ID);
			colorIds = stream.toList();
//			Stream<MapState> stream = states.stream();
//			if(Configs.Generic.SKIP_VOID_MAPS.getBooleanValue()) stream = stream.filter(s -> !MapColorUtils.isFullyTransparent(s.colors));
//			colorIds = stream.map(MapGroupUtils::getIdForMapState).toList();
		}
		colorIds.stream().filter(UpdateInventoryContents::isInInventory).forEach(inContainerAndInInv::add);

		uniqueMapIds.clear();
		final Stream<UUID> uniqueIdsStream = Configs.Generic.SKIP_MONO_COLOR_MAPS.getBooleanValue()
				? states.stream().filter(s -> !MapColorUtils.isMonoColor(s.colors)).map(MapGroupUtils::getIdForMapState)
				: colorIds.stream();
		uniqueIdsStream.filter(Predicate.not(uniqueMapIds::add)).forEach(duplicatesInContainer::add);

		if(SHOW_ASTERISKS){
			asterisks.clear();
			if(!inContainerAndInInv.isEmpty()) asterisks.add(Configs.Visuals.MAP_COLOR_IN_INV.getIntegerValue());
			if(states.stream().anyMatch(MapGroupUtils::shouldHighlightNotInCurrentGroup)) asterisks.add(Configs.Visuals.MAP_COLOR_NOT_IN_GROUP.getIntegerValue());
			if(states.stream().anyMatch(s -> !s.locked)) asterisks.add(Configs.Visuals.MAP_COLOR_UNLOCKED.getIntegerValue());
			if(mapItems.size() > states.size() + (!Configs.Generic.SKIP_NULL_MAPS.getBooleanValue() ? 0
					: mapItems.stream().filter(stack -> MapGroupUtils.isConfirmedNull(stack.get(DataComponents.MAP_ID).id())).count()
			)){
				asterisks.add(Configs.Visuals.MAP_COLOR_UNLOADED.getIntegerValue());
			}
			else if(UpdateItemFrameContents.mixedOnDisplayAndNotOnDisplay(colorIds)) asterisks.add(Configs.Visuals.MAP_COLOR_IN_IFRAME.getIntegerValue());
//			if(!nonFillerIds.stream().allMatch(new HashSet<>(nonFillerIds.size())::add)) asterisks.add(Main.MAP_COLOR_MULTI_INV); // duplicates within the container
			if(!duplicatesInContainer.isEmpty()) asterisks.add(Configs.Visuals.MAP_COLOR_MULTI_CONTAINER.getIntegerValue());
			if(mapItems.stream().anyMatch(i -> i.get(DataComponents.CUSTOM_NAME) == null)) asterisks.add(Configs.Visuals.MAP_COLOR_UNNAMED.getIntegerValue());

			if(!asterisks.isEmpty()){
//				Main.LOGGER.info("ContainerHighlighter: colored title! asterisks.size()="+asterisks.size());
				customTitle = hs.getTitle().copy();
				asterisks.stream().distinct()  // The "distinct" only exists in case of configurations where 2+ settings share 1 color
					.forEach(color -> customTitle.append(Component.literal("*").withColor(color).withStyle(ChatFormatting.BOLD)));
			}
		}
	}
}