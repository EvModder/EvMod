package net.evmodder.KeyBound.EventListeners;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import net.evmodder.KeyBound.Main;
import net.evmodder.KeyBound.MapColorUtils;
import net.evmodder.KeyBound.MapGroupUtils;
import net.evmodder.KeyBound.MapRelationUtils;
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

public class ContainerHighlightUpdater{
	// These are only at this scope to avoid calls to "new"
	private static final HashSet<UUID> uniqueMapIds = new HashSet<>();
	private static ArrayList<Integer> asterisks = new ArrayList<>(7);

	// These actually need to be at this scope
	public static MutableText customTitle;
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
		return nonFillerIds.stream().anyMatch(ItemFrameHighlightUpdater::isInItemFrame)
				&& nonFillerIds.stream().anyMatch(Predicate.not(ItemFrameHighlightUpdater::isInItemFrame));
		//Equivalent to:
//		return nonFillerIds.stream().map(ItemFrameHighlightUpdater::isInItemFrame).distinct().count() > 1;
	}

	public static final void onUpdateTick(MinecraftClient client){
		customTitle = null;
		inContainerAndInInv.clear();
		if(client.player == null || client.world == null || !client.player.isAlive()) return;
		if(client.currentScreen == null || !(client.currentScreen instanceof HandledScreen hs)) return;
		if(client.currentScreen instanceof AnvilScreen ||
			client.currentScreen instanceof CraftingScreen ||
			client.currentScreen instanceof CartographyTableScreen) return; // These get false-flagged for "duplicate map in container" with i/o slots

		final List<ItemStack> items = getAllMapItemsInContainer(hs.getScreenHandler().slots);
		if(items.isEmpty()) return;
		final List<MapState> states = items.stream().map(i -> FilledMapItem.getMapState(i, client.world)).filter(Objects::nonNull).toList();
		final List<UUID> nonTransparentIds = (!Main.skipTransparentMaps ? states.stream() :
			states.stream().filter(s -> !MapColorUtils.isTransparentOrStone(s.colors))).map(MapGroupUtils::getIdForMapState).toList();
		final List<UUID> nonMonoColorIds = (!Main.skipMonoColorMaps ? states.stream() :
			states.stream().filter(s -> !MapColorUtils.isMonoColor(s.colors))).map(MapGroupUtils::getIdForMapState).toList();

		asterisks.clear();
		nonTransparentIds.stream().filter(InventoryHighlightUpdater::isInInventory).forEach(inContainerAndInInv::add);
		if(!inContainerAndInInv.isEmpty()) asterisks.add(Main.MAP_COLOR_IN_INV);
		if(states.stream().anyMatch(MapGroupUtils::shouldHighlightNotInCurrentGroup)) asterisks.add(Main.MAP_COLOR_NOT_IN_GROUP);
		if(states.stream().anyMatch(s -> !s.locked)) asterisks.add(Main.MAP_COLOR_UNLOCKED);
		if(items.size() > states.size()) asterisks.add(Main.MAP_COLOR_UNLOADED);
		else if(mixedOnDisplayAndNotOnDisplay(nonTransparentIds)) asterisks.add(Main.MAP_COLOR_IN_IFRAME);
//		if(!nonFillerIds.stream().allMatch(new HashSet<>(nonFillerIds.size())::add)) asterisks.add(Main.MAP_COLOR_MULTI_INV); // Check duplicates within the container
		duplicatesInContainer.clear();
		uniqueMapIds.clear();
		nonMonoColorIds.stream().filter(Predicate.not(uniqueMapIds::add)).forEach(duplicatesInContainer::add);
		if(!duplicatesInContainer.isEmpty()) asterisks.add(Main.MAP_COLOR_MULTI_INV);
		if(items.stream().anyMatch(i -> i.getCustomName() == null)) asterisks.add(Main.MAP_COLOR_UNNAMED);

		if(!asterisks.isEmpty()){
			customTitle = hs.getTitle().copy();
			asterisks.stream().distinct() // TODO: the "distinct" only exists in case of configurations where 2+ settings share 1 color
				.forEach(color -> customTitle.append(Text.literal("*").withColor(color).formatted(Formatting.BOLD)));
		}
	}
}
