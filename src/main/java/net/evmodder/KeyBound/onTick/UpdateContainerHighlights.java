package net.evmodder.KeyBound.onTick;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import net.evmodder.KeyBound.Main;
import net.evmodder.KeyBound.apis.MapColorUtils;
import net.evmodder.KeyBound.apis.MapGroupUtils;
import net.evmodder.KeyBound.apis.MapRelationUtils;
import net.evmodder.KeyBound.config.Configs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import net.minecraft.client.gui.screen.ingame.CartographyTableScreen;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapState;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class UpdateContainerHighlights{
	// These are only at this scope to avoid calls to "new"
	private static final HashSet<UUID> uniqueMapIds = new HashSet<>();
	private static ArrayList<Integer> asterisks = new ArrayList<>(7);

	// These actually need to be at this scope
	public static MutableText customTitle;
	public static int mapsInContainerHash;
	private static final HashSet<UUID> duplicatesInContainer = new HashSet<>(), inContainerAndInInv = new HashSet<>();

	public static boolean hasDuplicateInContainer(UUID colorsId){
		return duplicatesInContainer.contains(colorsId);
	}

	public static boolean isInInvAndContainer(UUID colorsId){
		return inContainerAndInInv.contains(colorsId);
	}

	private static final List<ItemStack> getAllMapItemsInContainer(List<Slot> slots){
		final List<Slot> containerSlots = slots.subList(0, slots.size()-36);
		return MapRelationUtils.getAllNestedItems(containerSlots.stream().map(Slot::getStack)).filter(s -> s.getItem() == Items.FILLED_MAP).toList();
	}
	private static final boolean mixedOnDisplayAndNotOnDisplay(List<UUID> nonFillerIds){
		return nonFillerIds.stream().anyMatch(UpdateItemFrameHighlights::isInItemFrame)
				&& nonFillerIds.stream().anyMatch(Predicate.not(UpdateItemFrameHighlights::isInItemFrame));
		//Equivalent to:
//		return nonFillerIds.stream().map(ItemFrameHighlightUpdater::isInItemFrame).distinct().count() > 1;
	}

	private static int lastHash;

	public static final void onUpdateTick(MinecraftClient client){
		if(client.player == null || client.world == null || !client.player.isAlive() ||
			client.currentScreen == null || !(client.currentScreen instanceof HandledScreen hs) ||
			client.currentScreen instanceof AnvilScreen || // These get false-flagged for "duplicate map in container" with i/o slots
			client.currentScreen instanceof CraftingScreen ||
			client.currentScreen instanceof CartographyTableScreen)
		{
			customTitle = null;
			mapsInContainerHash = 0;
			inContainerAndInInv.clear();
			return;
		}
		final boolean renderAsterisks = Configs.Visuals.MAP_HIGHLIGHT_CONTAINER_NAME.getBooleanValue();

		final List<ItemStack> items = getAllMapItemsInContainer(hs.getScreenHandler().slots);

		mapsInContainerHash = hs.getScreenHandler().syncId + items.hashCode();
		final int currHash = UpdateInventoryHighlights.mapsInInvHash + mapsInContainerHash;
		if(lastHash == currHash) return;
		lastHash = currHash;
		Main.LOGGER.info("ContainerHighlighter: Recomputing cache");

		if(items.isEmpty()) return;
		final List<MapState> states = items.stream().map(i -> FilledMapItem.getMapState(i, client.world)).filter(Objects::nonNull).toList();
		final List<UUID> nonTransparentIds = (!Configs.Generic.SKIP_TRANSPARENT_MAPS.getBooleanValue() ? states.stream() :
			states.stream().filter(s -> !MapColorUtils.isTransparentOrStone(s.colors))).map(MapGroupUtils::getIdForMapState).toList();
		final List<UUID> nonMonoColorIds = (!Configs.Generic.SKIP_MONO_COLOR_MAPS.getBooleanValue() ? states.stream() :
			states.stream().filter(s -> !MapColorUtils.isMonoColor(s.colors))).map(MapGroupUtils::getIdForMapState).toList();

		nonTransparentIds.stream().filter(UpdateInventoryHighlights::isInInventory).forEach(inContainerAndInInv::add);
		if(renderAsterisks){
			asterisks.clear();
			if(!inContainerAndInInv.isEmpty()) asterisks.add(Configs.Visuals.MAP_COLOR_IN_INV.getIntegerValue());
			if(states.stream().anyMatch(MapGroupUtils::shouldHighlightNotInCurrentGroup)) asterisks.add(Configs.Visuals.MAP_COLOR_NOT_IN_GROUP.getIntegerValue());
			if(states.stream().anyMatch(s -> !s.locked)) asterisks.add(Configs.Visuals.MAP_COLOR_UNLOCKED.getIntegerValue());
			if(items.size() > states.size()) asterisks.add(Configs.Visuals.MAP_COLOR_UNLOADED.getIntegerValue());
			else if(mixedOnDisplayAndNotOnDisplay(nonTransparentIds)) asterisks.add(Configs.Visuals.MAP_COLOR_IN_IFRAME.getIntegerValue());
		}
//		if(!nonFillerIds.stream().allMatch(new HashSet<>(nonFillerIds.size())::add)) asterisks.add(Main.MAP_COLOR_MULTI_INV); // Check duplicates within the container
		duplicatesInContainer.clear();
		uniqueMapIds.clear();
		nonMonoColorIds.stream().filter(Predicate.not(uniqueMapIds::add)).forEach(duplicatesInContainer::add);
		if(renderAsterisks){
			if(!duplicatesInContainer.isEmpty()) asterisks.add(Configs.Visuals.MAP_COLOR_MULTI_INV.getIntegerValue());
			if(items.stream().anyMatch(i -> i.getCustomName() == null)) asterisks.add(Configs.Visuals.MAP_COLOR_UNNAMED.getIntegerValue());
		}

		if(renderAsterisks && !asterisks.isEmpty()){
			Main.LOGGER.info("ContainerHighlighter: colored title! asterisks.size()="+asterisks.size());
			customTitle = hs.getTitle().copy();
			asterisks.stream().distinct() // TODO: the "distinct" only exists in case of configurations where 2+ settings share 1 color
				.forEach(color -> customTitle.append(Text.literal("*").withColor(color).formatted(Formatting.BOLD)));
		}
	}
}
