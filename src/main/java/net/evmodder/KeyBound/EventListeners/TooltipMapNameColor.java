package net.evmodder.KeyBound.EventListeners;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.item.Item.TooltipContext;
import net.evmodder.KeyBound.Main;
import net.evmodder.KeyBound.MapColorUtils;
import net.evmodder.KeyBound.MapGroupUtils;
import net.evmodder.KeyBound.MapRelationUtils;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapState;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class TooltipMapNameColor{
	private static final boolean mixedOnDisplayAndNotOnDisplay(List<UUID> nonFillerIds){
		return nonFillerIds.stream().anyMatch(ItemFrameHighlightUpdater::isInItemFrame)
				&& nonFillerIds.stream().anyMatch(Predicate.not(ItemFrameHighlightUpdater::isInItemFrame));
		//Equivalent to:
//		return nonFillerIds.stream().map(ItemFrameHighlightUpdater::isInItemFrame).distinct().count() > 1;
	}

	public static final void tooltipColors(ItemStack item, TooltipContext context, TooltipType type, List<Text> lines){
		ContainerComponent container = item.get(DataComponentTypes.CONTAINER);
		if(container != null){
			List<ItemStack> items = MapRelationUtils.getAllNestedItems(container.streamNonEmpty()).filter(i -> i.getItem() == Items.FILLED_MAP).toList();
			if(items.isEmpty()) return;
			final List<MapState> states = items.stream().map(i -> context.getMapState(i.get(DataComponentTypes.MAP_ID))).filter(Objects::nonNull).toList();
//			final List<UUID> nonFillerIds = states.stream().filter(Predicate.not(MapRelationUtils::isFillerMap)).map(MapGroupUtils::getIdForMapState).toList();
			final List<UUID> colorIds = states.stream().map(MapGroupUtils::getIdForMapState).toList();
//			final List<UUID> nonTransparentIds = (!Main.skipTransparentMaps ? states.stream() :
//				states.stream().filter(s -> !MapRelationUtils.isFullyTransparent(s.colors))).map(MapGroupUtils::getIdForMapState).toList();
//			final List<UUID> nonMonoColorIds = (!Main.skipMonoColorMaps ? states.stream() :
//				states.stream().filter(s -> !MapRelationUtils.isMonoColor(s.colors))).map(MapGroupUtils::getIdForMapState).toList();

			List<Integer> asterisks = new ArrayList<>(4);
			if(colorIds.stream().anyMatch(InventoryHighlightUpdater::isInInventory)) asterisks.add(Main.MAP_COLOR_IN_INV);
			if(states.stream().anyMatch(MapGroupUtils::shouldHighlightNotInCurrentGroup)) asterisks.add(Main.MAP_COLOR_NOT_IN_GROUP);
			if(states.stream().anyMatch(s -> !s.locked)) asterisks.add(Main.MAP_COLOR_UNLOCKED);
			if(colorIds.stream().anyMatch(ContainerHighlightUpdater::hasDuplicateInContainer)) asterisks.add(Main.MAP_COLOR_MULTI_INV);
			if(items.size() > states.size()) asterisks.add(Main.MAP_COLOR_UNLOADED);
			else if(mixedOnDisplayAndNotOnDisplay(colorIds)) asterisks.add(Main.MAP_COLOR_IN_IFRAME);
			if(items.stream().anyMatch(i -> i.getCustomName() == null)) asterisks.add(Main.MAP_COLOR_UNNAMED);

			if(!asterisks.isEmpty()){
				asterisks = asterisks.stream().distinct().toList();
				MutableText text = lines.removeFirst().copy();
				asterisks.forEach(color -> text.append(Text.literal("*").withColor(color).formatted(Formatting.BOLD)));
				lines.addFirst(text);
			}
			return;
		}
		if(item.getItem() != Items.FILLED_MAP) return;
		MapIdComponent id = item.get(DataComponentTypes.MAP_ID);
		MapState state = id == null ? null : context.getMapState(id);
		if(state == null){
			if(item.getCustomName() == null) lines.addFirst(lines.removeFirst().copy().withColor(Main.MAP_COLOR_UNNAMED));
			return;
		}
		UUID colordsId = MapGroupUtils.getIdForMapState(state);
		List<Integer> asterisks = new ArrayList<>();
		if(ContainerHighlightUpdater.isInInvAndContainer(colordsId)) asterisks.add(Main.MAP_COLOR_IN_INV);
		if(MapGroupUtils.shouldHighlightNotInCurrentGroup(state)) asterisks.add(Main.MAP_COLOR_NOT_IN_GROUP);
		if(!state.locked) asterisks.add(Main.MAP_COLOR_UNLOCKED);
		if(ItemFrameHighlightUpdater.isInItemFrame(colordsId)) asterisks.add(Main.MAP_COLOR_IN_IFRAME);
		if(ContainerHighlightUpdater.hasDuplicateInContainer(colordsId)) asterisks.add(Main.MAP_COLOR_MULTI_INV);
		if(asterisks.isEmpty()){
			if(item.getCustomName() == null) lines.addFirst(lines.removeFirst().copy().withColor(Main.MAP_COLOR_UNNAMED));
			return;
		}
		final boolean nameColor = !(asterisks.get(0) == Main.MAP_COLOR_UNNAMED
				|| (asterisks.get(0) == Main.MAP_COLOR_MULTI_INV && Main.skipMonoColorMaps && MapColorUtils.isMonoColor(state.colors)));

		asterisks = asterisks.stream().distinct().toList(); // TODO: this line only exists in case of configurations where 2+ meanings share 1 color
		MutableText text = lines.removeFirst().copy();
		if(nameColor) text.withColor(asterisks.get(0));
		for(int i=nameColor?1:0; i<asterisks.size(); ++i) text.append(Text.literal("*").withColor(asterisks.get(i)));
		lines.addFirst(text);
	}
}