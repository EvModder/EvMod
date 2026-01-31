package net.evmodder.evmod.onTick;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.item.Item.TooltipContext;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.apis.InvUtils;
import net.evmodder.evmod.apis.MapColorUtils;
import net.evmodder.evmod.apis.MapGroupUtils;
import net.evmodder.evmod.apis.Tooltip;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapState;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class TooltipMapNameColor implements Tooltip{
	private static final boolean mixedOnDisplayAndNotOnDisplay(List<UUID> nonFillerIds){
		return nonFillerIds.stream().anyMatch(UpdateItemFrameHighlights::isInItemFrame)
				&& nonFillerIds.stream().anyMatch(Predicate.not(UpdateItemFrameHighlights::isInItemFrame));
		//Equivalent to:
//		return nonFillerIds.stream().map(ItemFrameHighlightUpdater::isInItemFrame).distinct().count() > 1;
	}

	private static final HashMap<ItemStack, List<Text>> tooltipCache = new HashMap<>();
	private static int lastHash;

	@Override public final void get(ItemStack item, TooltipContext context, TooltipType type, List<Text> lines){
		if(!Configs.Visuals.MAP_HIGHLIGHT_TOOLTIP.getBooleanValue()) return;
		final int MAP_COLOR_IN_INV = Configs.Visuals.MAP_COLOR_IN_INV.getIntegerValue();
		final int MAP_COLOR_NOT_IN_GROUP = Configs.Visuals.MAP_COLOR_NOT_IN_GROUP.getIntegerValue();
		final int MAP_COLOR_UNLOCKED = Configs.Visuals.MAP_COLOR_UNLOCKED.getIntegerValue();
		final int MAP_COLOR_MULTI_CONTAINER = Configs.Visuals.MAP_COLOR_MULTI_CONTAINER.getIntegerValue();
		final int MAP_COLOR_UNLOADED = Configs.Visuals.MAP_COLOR_UNLOADED.getIntegerValue();
		final int MAP_COLOR_IN_IFRAME = Configs.Visuals.MAP_COLOR_IN_IFRAME.getIntegerValue();
		final int MAP_COLOR_UNNAMED = Configs.Visuals.MAP_COLOR_UNNAMED.getIntegerValue();

		final int currHash = UpdateInventoryHighlights.getMapInInvHash() + UpdateContainerHighlights.mapsInContainerHash;
		if(lastHash != currHash){
			lastHash = currHash;
			tooltipCache.clear();
//			Main.LOGGER.info("TooltipMapNameColor: Clearing cache");
		}
		List<Text> cachedLines = tooltipCache.get(item);
		if(cachedLines != null){lines.clear(); lines.addAll(cachedLines); return;}

		if(item.getItem() != Items.FILLED_MAP){
			final List<ItemStack> mapItems = InvUtils.getAllNestedItems(item).filter(i -> i.get(DataComponentTypes.MAP_ID) != null).toList();
			if(mapItems.isEmpty()) return;
			final List<MapState> states = mapItems.stream().map(i -> context.getMapState(i.get(DataComponentTypes.MAP_ID))).filter(Objects::nonNull).toList();
//			final List<UUID> nonFillerIds = states.stream().filter(Predicate.not(MapRelationUtils::isFillerMap)).map(MapGroupUtils::getIdForMapState).toList();
			final List<UUID> colorIds = states.stream().map(MapGroupUtils::getIdForMapState).toList();
			final List<UUID> unskippedIds = (
					Configs.Generic.SKIP_MONO_COLOR_MAPS.getBooleanValue() ? states.stream().filter(s -> !MapColorUtils.isMonoColor(s.colors)) :
					Configs.Generic.SKIP_VOID_MAPS.getBooleanValue() ? states.stream().filter(s -> !MapColorUtils.isFullyTransparent(s.colors)) :
					states.stream()).map(MapGroupUtils::getIdForMapState).toList();

			List<Integer> asterisks = new ArrayList<>(4);
			if(colorIds.stream().anyMatch(UpdateInventoryHighlights::isInInventory)) asterisks.add(MAP_COLOR_IN_INV);
			if(states.stream().anyMatch(MapGroupUtils::shouldHighlightNotInCurrentGroup)) asterisks.add(MAP_COLOR_NOT_IN_GROUP);
			if(states.stream().anyMatch(s -> !s.locked)) asterisks.add(MAP_COLOR_UNLOCKED);
			if(unskippedIds.stream().anyMatch(UpdateContainerHighlights::hasDuplicateInContainer)) asterisks.add(MAP_COLOR_MULTI_CONTAINER);
			if(mapItems.size() > states.size() + (!Configs.Generic.SKIP_NULL_MAPS.getBooleanValue() ? 0
					: mapItems.stream().filter(stack -> MapGroupUtils.nullMapIds.contains(stack.get(DataComponentTypes.MAP_ID).id())).count()
			)){
				asterisks.add(MAP_COLOR_UNLOADED);
			}
			else if(mixedOnDisplayAndNotOnDisplay(unskippedIds)) asterisks.add(MAP_COLOR_IN_IFRAME);
			if(mapItems.stream().anyMatch(i -> i.getCustomName() == null)) asterisks.add(MAP_COLOR_UNNAMED);

			if(!asterisks.isEmpty()){
				asterisks = asterisks.stream().distinct().toList();
				MutableText text = lines.removeFirst().copy();
				asterisks.forEach(color -> text.append(Text.literal("*").withColor(color).formatted(Formatting.BOLD)));
				lines.addFirst(text);
			}
			tooltipCache.put(item, lines);
			return;
		}
		MapIdComponent id = item.get(DataComponentTypes.MAP_ID);
		MapState state = id == null ? null : context.getMapState(id);
		if(state == null){
			if(Configs.Generic.SKIP_NULL_MAPS.getBooleanValue() && id != null && MapGroupUtils.isConfirmedNull(id.id())) return;
			if(item.getCustomName() == null) lines.addFirst(lines.removeFirst().copy().withColor(MAP_COLOR_UNNAMED));
			tooltipCache.put(item, lines);
			return;
		}
		UUID colorsId = MapGroupUtils.getIdForMapState(state);
		final boolean isSkipped =
				Configs.Generic.SKIP_MONO_COLOR_MAPS.getBooleanValue() ? MapColorUtils.isMonoColor(state.colors) :
				Configs.Generic.SKIP_VOID_MAPS.getBooleanValue() ?
						MapColorUtils.FULLY_TRANSPARENT_COLORS_ID.equals(colorsId)/*MapColorUtils.isFullyTransparent(state.colors)*/ :
				false;
		List<Integer> asterisks = new ArrayList<>();
		if(UpdateContainerHighlights.isInInvAndContainer(colorsId)) asterisks.add(MAP_COLOR_IN_INV);
		if(MapGroupUtils.shouldHighlightNotInCurrentGroup(state)) asterisks.add(MAP_COLOR_NOT_IN_GROUP);
		if(!state.locked) asterisks.add(MAP_COLOR_UNLOCKED);
		if(UpdateItemFrameHighlights.isInItemFrame(colorsId)) asterisks.add(MAP_COLOR_IN_IFRAME);
		if(UpdateContainerHighlights.hasDuplicateInContainer(colorsId) && !isSkipped) asterisks.add(MAP_COLOR_MULTI_CONTAINER);
		if(asterisks.isEmpty()){
			if(item.getCustomName() == null) lines.addFirst(lines.removeFirst().copy().withColor(MAP_COLOR_UNNAMED));
			tooltipCache.put(item, lines);
			return;
		}
		final boolean nameColor = asterisks.get(0) != MAP_COLOR_MULTI_CONTAINER; // This one is only permitted as an asterisk (idk why, ask older me)

		asterisks = asterisks.stream().distinct().toList(); // TODO: this line only exists in case of configurations where 2+ meanings share 1 color
		MutableText text = lines.removeFirst().copy();
		if(nameColor) text.withColor(asterisks.get(0));
		for(int i=nameColor?1:0; i<asterisks.size(); ++i) text.append(Text.literal("*").withColor(asterisks.get(i)));
		lines.addFirst(text);
		tooltipCache.put(item, lines);
	}
}