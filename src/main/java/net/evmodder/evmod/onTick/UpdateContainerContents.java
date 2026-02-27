package net.evmodder.evmod.onTick;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.apis.InvUtils;
import net.evmodder.evmod.apis.MapColorUtils;
import net.evmodder.evmod.apis.MapGroupUtils;
import net.evmodder.evmod.apis.TickListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import net.minecraft.client.gui.screen.ingame.CartographyTableScreen;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.map.MapState;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class UpdateContainerContents implements TickListener{
	private static final HashSet<UUID> duplicatesInContainer = new HashSet<>(), inContainerAndInInv = new HashSet<>();
	private static int mapsInContainerHash;
	public static MutableText customTitle; // Accessor: MixinHandledScreen

	public static final int getMapsInContainerHash(){return mapsInContainerHash;} // Accessors: TooltipMapNameColor, TooltipMapLoreMetadata
	public static final boolean hasDuplicateInContainer(final UUID colorsId){return duplicatesInContainer.contains(colorsId);} // Accessor: TooltipMapNameColor
	public static final boolean isInInvAndContainer(final UUID colorsId){return inContainerAndInInv.contains(colorsId);} // Accessor: TooltipMapNameColor

	// Constantly-recycled; defined here to avoid frequent calls to "new"
	private final HashSet<UUID> uniqueMapIds = new HashSet<>();
	private final ArrayList<Integer> asterisks = new ArrayList<>(7);
	private int lastHash;

	private final void handleContainerChangeEvent(final int syncId){
		// TODO: move logic from ContainerOpenCloseListener to here? Or otherwise delete this
	}
	private final List<ItemStack> getAllMapItemsInContainer(final List<Slot> slots){
		final List<Slot> containerSlots = slots.subList(0, slots.size()-36);
		return InvUtils.getAllNestedItems(containerSlots.stream().map(Slot::getStack))
//				.filter(s -> s.getItem() == Items.FILLED_MAP)
				.filter(s -> s.get(DataComponentTypes.MAP_ID) != null)
				.toList();
	}
	@Override public final void onTickStart(final MinecraftClient client){
		if(client.player == null || client.world == null || !client.player.isAlive() ||
			client.currentScreen == null || !(client.currentScreen instanceof HandledScreen hs) ||
			hs.getScreenHandler().syncId == 0 || // InventoryScreen/CreativeScreen/RecipeBookScreen (NOT a container)
			hs instanceof AnvilScreen || // These get false-flagged for "duplicate map in container" with i/o slots
			hs instanceof CraftingScreen ||
			hs instanceof CartographyTableScreen)
		{
			customTitle = null;
			mapsInContainerHash = 0;
			inContainerAndInInv.clear();
			handleContainerChangeEvent(0);
			return;
		}
		{
			final ScreenHandler sh = client.player.currentScreenHandler;
			handleContainerChangeEvent(sh == null ? 0 : sh.syncId);
		}

		final List<ItemStack> mapItems = getAllMapItemsInContainer(hs.getScreenHandler().slots);
		mapsInContainerHash = hs.getScreenHandler().syncId + mapItems.hashCode();

		final boolean SHOW_ASTERISKS = Configs.Visuals.MAP_HIGHLIGHT_CONTAINER_NAME.getBooleanValue();
		if(!SHOW_ASTERISKS && !Configs.Visuals.MAP_HIGHLIGHT_TOOLTIP.getBooleanValue()) return;

		final int currHash = UpdateInventoryContents.getMapsInInvHash() + mapsInContainerHash;
		if(lastHash == currHash) return;
		lastHash = currHash;
//		Main.LOGGER.info("ContainerHighlighter: Recomputing cache");

		if(mapItems.isEmpty()) return;
		final List<MapState> states = mapItems.stream().map(i -> FilledMapItem.getMapState(i, client.world)).filter(Objects::nonNull).toList();
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

		duplicatesInContainer.clear();
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
					: mapItems.stream().filter(stack -> MapGroupUtils.nullMapIds.contains(stack.get(DataComponentTypes.MAP_ID).id())).count()
			)){
				asterisks.add(Configs.Visuals.MAP_COLOR_UNLOADED.getIntegerValue());
			}
			else if(UpdateItemFrameContents.mixedOnDisplayAndNotOnDisplay(colorIds)) asterisks.add(Configs.Visuals.MAP_COLOR_IN_IFRAME.getIntegerValue());
//			if(!nonFillerIds.stream().allMatch(new HashSet<>(nonFillerIds.size())::add)) asterisks.add(Main.MAP_COLOR_MULTI_INV); // duplicates within the container
			if(!duplicatesInContainer.isEmpty()) asterisks.add(Configs.Visuals.MAP_COLOR_MULTI_CONTAINER.getIntegerValue());
			if(mapItems.stream().anyMatch(i -> i.getCustomName() == null)) asterisks.add(Configs.Visuals.MAP_COLOR_UNNAMED.getIntegerValue());

			if(!asterisks.isEmpty()){
//				Main.LOGGER.info("ContainerHighlighter: colored title! asterisks.size()="+asterisks.size());
				customTitle = hs.getTitle().copy();
				asterisks.stream().distinct() // The "distinct" only exists in case of configurations where 2+ settings share 1 color
					.forEach(color -> customTitle.append(Text.literal("*").withColor(color).formatted(Formatting.BOLD)));
			}
		}
	}
}
