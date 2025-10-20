package net.evmodder.KeyBound.onTick;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.item.Item.TooltipContext;
import net.evmodder.KeyBound.Configs;
import net.evmodder.KeyBound.apis.MapColorUtils;
import net.evmodder.KeyBound.apis.MapGroupUtils;
import net.evmodder.KeyBound.apis.MapRelationUtils;
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
		return nonFillerIds.stream().anyMatch(UpdateItemFrameHighlights::isInItemFrame)
				&& nonFillerIds.stream().anyMatch(Predicate.not(UpdateItemFrameHighlights::isInItemFrame));
		//Equivalent to:
//		return nonFillerIds.stream().map(ItemFrameHighlightUpdater::isInItemFrame).distinct().count() > 1;
	}

	private static final HashMap<ItemStack, List<Text>> tooltipCache = new HashMap<>();
	private static int lastHash;

	public static final void tooltipColors(ItemStack item, TooltipContext context, TooltipType type, List<Text> lines){
		if(!Configs.Visuals.MAP_HIGHLIGHT_TOOLTIP.getBooleanValue()) return;
		final int MAP_COLOR_IN_INV = Configs.Visuals.MAP_COLOR_IN_INV.getIntegerValue();
		final int MAP_COLOR_NOT_IN_GROUP = Configs.Visuals.MAP_COLOR_NOT_IN_GROUP.getIntegerValue();
		final int MAP_COLOR_UNLOCKED = Configs.Visuals.MAP_COLOR_UNLOCKED.getIntegerValue();
		final int MAP_COLOR_MULTI_INV = Configs.Visuals.MAP_COLOR_MULTI_INV.getIntegerValue();
		final int MAP_COLOR_UNLOADED = Configs.Visuals.MAP_COLOR_UNLOADED.getIntegerValue();
		final int MAP_COLOR_IN_IFRAME = Configs.Visuals.MAP_COLOR_IN_IFRAME.getIntegerValue();
		final int MAP_COLOR_UNNAMED = Configs.Visuals.MAP_COLOR_UNNAMED.getIntegerValue();

		final int currHash = UpdateInventoryHighlights.mapsInInvHash + UpdateContainerHighlights.mapsInContainerHash;
		if(lastHash != currHash){
			lastHash = currHash;
			tooltipCache.clear();
//			Main.LOGGER.info("TooltipMapNameColor: Clearing cache");
		}
		List<Text> cachedLines = tooltipCache.get(item);
		if(cachedLines != null){lines.clear(); lines.addAll(cachedLines); return;}

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
			if(colorIds.stream().anyMatch(UpdateInventoryHighlights::isInInventory)) asterisks.add(MAP_COLOR_IN_INV);
			if(states.stream().anyMatch(MapGroupUtils::shouldHighlightNotInCurrentGroup)) asterisks.add(MAP_COLOR_NOT_IN_GROUP);
			if(states.stream().anyMatch(s -> !s.locked)) asterisks.add(MAP_COLOR_UNLOCKED);
			if(colorIds.stream().anyMatch(UpdateContainerHighlights::hasDuplicateInContainer)) asterisks.add(MAP_COLOR_MULTI_INV);
			if(items.size() > states.size()) asterisks.add(MAP_COLOR_UNLOADED);
			else if(mixedOnDisplayAndNotOnDisplay(colorIds)) asterisks.add(MAP_COLOR_IN_IFRAME);
			if(items.stream().anyMatch(i -> i.getCustomName() == null)) asterisks.add(MAP_COLOR_UNNAMED);

			if(!asterisks.isEmpty()){
				asterisks = asterisks.stream().distinct().toList();
				MutableText text = lines.removeFirst().copy();
				asterisks.forEach(color -> text.append(Text.literal("*").withColor(color).formatted(Formatting.BOLD)));
				lines.addFirst(text);
			}
			tooltipCache.put(item, lines);
			return;
		}
		if(item.getItem() != Items.FILLED_MAP) return;
		MapIdComponent id = item.get(DataComponentTypes.MAP_ID);
		MapState state = id == null ? null : context.getMapState(id);
		if(state == null){
			if(item.getCustomName() == null) lines.addFirst(lines.removeFirst().copy().withColor(MAP_COLOR_UNNAMED));
			tooltipCache.put(item, lines);
			return;
		}
		UUID colordsId = MapGroupUtils.getIdForMapState(state);
		List<Integer> asterisks = new ArrayList<>();
		if(UpdateContainerHighlights.isInInvAndContainer(colordsId)) asterisks.add(MAP_COLOR_IN_INV);
		if(MapGroupUtils.shouldHighlightNotInCurrentGroup(state)) asterisks.add(MAP_COLOR_NOT_IN_GROUP);
		if(!state.locked) asterisks.add(MAP_COLOR_UNLOCKED);
		if(UpdateItemFrameHighlights.isInItemFrame(colordsId)) asterisks.add(MAP_COLOR_IN_IFRAME);
		if(UpdateContainerHighlights.hasDuplicateInContainer(colordsId)) asterisks.add(MAP_COLOR_MULTI_INV);
		if(asterisks.isEmpty()){
			if(item.getCustomName() == null) lines.addFirst(lines.removeFirst().copy().withColor(MAP_COLOR_UNNAMED));
			tooltipCache.put(item, lines);
			return;
		}
		final boolean nameColor = !(asterisks.get(0) == MAP_COLOR_UNNAMED
				|| (asterisks.get(0) == MAP_COLOR_MULTI_INV && Configs.Generic.SKIP_MONO_COLOR_MAPS.getBooleanValue() && MapColorUtils.isMonoColor(state.colors)));

		asterisks = asterisks.stream().distinct().toList(); // TODO: this line only exists in case of configurations where 2+ meanings share 1 color
		MutableText text = lines.removeFirst().copy();
		if(nameColor) text.withColor(asterisks.get(0));
		for(int i=nameColor?1:0; i<asterisks.size(); ++i) text.append(Text.literal("*").withColor(asterisks.get(i)));
		lines.addFirst(text);
		tooltipCache.put(item, lines);
	}
}