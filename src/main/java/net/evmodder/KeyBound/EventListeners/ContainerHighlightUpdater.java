package net.evmodder.KeyBound.EventListeners;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.evmodder.KeyBound.Main;
import net.evmodder.KeyBound.MapGroupUtils;
import net.evmodder.KeyBound.MapRelationUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapState;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class ContainerHighlightUpdater{
	public static MutableText customTitle;
	public static HashSet<MapState> duplicatesInContainer;

	private static final boolean isNotInCurrentGroup(MapState state){
		return MapGroupUtils.shouldHighlightNotInCurrentGroup(state);
	}
	private static final boolean isUnlocked(MapState state){
		return !state.locked;
	}
	private static final boolean isInInv(MapState state){
		return !MapRelationUtils.isFillerMap(state) && InventoryHighlightUpdater.isInInventory(MapGroupUtils.getIdForMapState(state));
	}
	private static final boolean isOnDisplay(MapState state){
		return !MapRelationUtils.isFillerMap(state) && ItemFrameHighlightUpdater.isInItemFrame(MapGroupUtils.getIdForMapState(state));
	}
	private static final boolean isNotOnDisplay(MapState state){
		return !MapRelationUtils.isFillerMap(state) && !ItemFrameHighlightUpdater.isInItemFrame(MapGroupUtils.getIdForMapState(state));
	}
	private static final boolean isUnnamed(ItemStack item){
		return item.getCustomName() == null;
	}

	private static final Stream<ItemStack> getAllNestedItems(Stream<ItemStack> items){
		return items.flatMap(s -> {
			ContainerComponent container = s.get(DataComponentTypes.CONTAINER);
			return container == null ? Stream.of(s) : getAllNestedItems(container.streamNonEmpty());
		});
	}

	public static final void onUpdateTick(MinecraftClient client){
		if(client.player == null || client.world == null || !client.player.isAlive()) return;
		if(client.currentScreen == null || !(client.currentScreen instanceof HandledScreen hs)) return;

		final List<Slot> containerSlots = hs.getScreenHandler().slots.subList(0, hs.getScreenHandler().slots.size()-36);
		final List<ItemStack> items = getAllNestedItems(containerSlots.stream().map(Slot::getStack)).filter(s -> s.getItem() == Items.FILLED_MAP).toList();
		final List<MapState> states = items.stream().map(i -> FilledMapItem.getMapState(i, client.world)).filter(Objects::nonNull).toList();
		List<Integer> asterisks = new ArrayList<>(4);
		if(states.stream().anyMatch(ContainerHighlightUpdater::isInInv)) asterisks.add(Main.MAP_COLOR_IN_INV);
		if(states.stream().anyMatch(ContainerHighlightUpdater::isNotInCurrentGroup)) asterisks.add(Main.MAP_COLOR_NOT_IN_GROUP);
		if(states.stream().anyMatch(ContainerHighlightUpdater::isUnlocked)) asterisks.add(Main.MAP_COLOR_UNLOCKED);
		if(states.stream().anyMatch(ContainerHighlightUpdater::isOnDisplay) &&
				states.stream().anyMatch(ContainerHighlightUpdater::isNotOnDisplay)) asterisks.add(Main.MAP_COLOR_IN_IFRAME);
		if(items.stream().anyMatch(ContainerHighlightUpdater::isUnnamed)) asterisks.add(Main.MAP_COLOR_UNNAMED);
//		if(!states.stream().allMatch(new HashSet<>(states.size())::add)) asterisks.add(Main.MAP_COLOR_MULTI_INV); // Check duplicates within the container
		duplicatesInContainer = new HashSet<>(states.stream().filter(Predicate.not(new HashSet<>(states.size())::add)).toList());
		if(!duplicatesInContainer.isEmpty()) asterisks.add(Main.MAP_COLOR_MULTI_INV);

		if(asterisks.isEmpty()){
			customTitle = null;
		}
		else{
			asterisks = asterisks.stream().distinct().toList();
			customTitle = hs.getTitle().copy();
			asterisks.forEach(color -> customTitle.append(Text.literal("*").withColor(color).formatted(Formatting.BOLD)));
		}
	}
}
