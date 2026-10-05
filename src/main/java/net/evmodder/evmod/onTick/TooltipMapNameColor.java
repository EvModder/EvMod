package net.evmodder.evmod.onTick;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.apis.InvUtils;
import net.evmodder.evmod.apis.MapColorUtils;
import net.evmodder.evmod.apis.MapGroupUtils;
import net.evmodder.evmod.apis.Tooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public final class TooltipMapNameColor implements Tooltip{
	private static final HashMap<ItemStack, List<Component>> tooltipCache = new HashMap<>();
	private static int lastHash;

	@Override public final void get(ItemStack item, TooltipContext context, TooltipFlag type, List<Component> lines){
		if(!Configs.Visuals.MAP_HIGHLIGHT_TOOLTIP.getBooleanValue()) return;
		final int MAP_COLOR_IN_INV = Configs.Visuals.MAP_COLOR_IN_INV.getIntegerValue();
		final int MAP_COLOR_NOT_IN_GROUP = Configs.Visuals.MAP_COLOR_NOT_IN_GROUP.getIntegerValue();
		final int MAP_COLOR_UNLOCKED = Configs.Visuals.MAP_COLOR_UNLOCKED.getIntegerValue();
		final int MAP_COLOR_MULTI_CONTAINER = Configs.Visuals.MAP_COLOR_MULTI_CONTAINER.getIntegerValue();
		final int MAP_COLOR_UNLOADED = Configs.Visuals.MAP_COLOR_UNLOADED.getIntegerValue();
		final int MAP_COLOR_IN_IFRAME = Configs.Visuals.MAP_COLOR_IN_IFRAME.getIntegerValue();
		final int MAP_COLOR_UNNAMED = Configs.Visuals.MAP_COLOR_UNNAMED.getIntegerValue();

		final int currHash = UpdateInventoryContents.getMapsInInvHash() + UpdateContainerContents.getMapsInContainerHash();
		if(lastHash != currHash){
			lastHash = currHash;
			tooltipCache.clear();
//			Main.LOGGER.info("TooltipMapNameColor: Clearing cache");
		}
		List<Component> cachedLines = tooltipCache.get(item);
		if(cachedLines != null){lines.clear(); lines.addAll(cachedLines); return;}

		if(item.getItem() != Items.FILLED_MAP){
			final var mapItems = InvUtils.getAllNestedItemViews(item).filter(s -> s.get(DataComponents.MAP_ID) != null).toList();
			if(mapItems.isEmpty()) return;
			final List<MapItemSavedData> states = mapItems.stream().map(i -> context.mapData(i.get(DataComponents.MAP_ID))).filter(Objects::nonNull).toList();
//			final List<UUID> nonFillerIds = states.stream().filter(Predicate.not(MapRelationUtils::isFillerMap)).map(MapGroupUtils::getIdForMapState).toList();
			final List<UUID> colorIds = states.stream().map(MapGroupUtils::getIdForMapState).toList();
			final List<UUID> unskippedIds = (
					Configs.Generic.SKIP_MONO_COLOR_MAPS.getBooleanValue() ? states.stream().filter(s -> !MapColorUtils.isMonoColor(s.colors)) :
					Configs.Generic.SKIP_VOID_MAPS.getBooleanValue() ? states.stream().filter(s -> !MapColorUtils.isFullyTransparent(s.colors)) :
					states.stream()).map(MapGroupUtils::getIdForMapState).toList();

			List<Integer> asterisks = new ArrayList<>(4);
			if(colorIds.stream().anyMatch(UpdateInventoryContents::isInInventory)) asterisks.add(MAP_COLOR_IN_INV);
			if(states.stream().anyMatch(MapGroupUtils::shouldHighlightNotInCurrentGroup)) asterisks.add(MAP_COLOR_NOT_IN_GROUP);
			if(states.stream().anyMatch(s -> !s.locked)) asterisks.add(MAP_COLOR_UNLOCKED);
			if(unskippedIds.stream().anyMatch(UpdateContainerContents::hasDuplicateInContainer)) asterisks.add(MAP_COLOR_MULTI_CONTAINER);
			if(mapItems.size() > states.size() + (!Configs.Generic.SKIP_NULL_MAPS.getBooleanValue() ? 0
					: mapItems.stream().filter(stack -> MapGroupUtils.nullMapIds.contains(stack.get(DataComponents.MAP_ID).id())).count()
			)){
				asterisks.add(MAP_COLOR_UNLOADED);
			}
			else if(UpdateItemFrameContents.mixedOnDisplayAndNotOnDisplay(unskippedIds)) asterisks.add(MAP_COLOR_IN_IFRAME);
			if(mapItems.stream().anyMatch(i -> i.get(DataComponents.CUSTOM_NAME) == null)) asterisks.add(MAP_COLOR_UNNAMED);

			if(!asterisks.isEmpty()){
				asterisks = asterisks.stream().distinct().toList();
				MutableComponent text = lines.removeFirst().copy();
				asterisks.forEach(color -> text.append(Component.literal("*").withColor(color).withStyle(ChatFormatting.BOLD)));
				lines.addFirst(text);
			}
			tooltipCache.put(item, lines);
			return;
		}
		MapId id = item.get(DataComponents.MAP_ID);
		MapItemSavedData state = id == null ? null : context.mapData(id);
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
		if(UpdateContainerContents.isInInvAndContainer(colorsId)) asterisks.add(MAP_COLOR_IN_INV);
		if(MapGroupUtils.shouldHighlightNotInCurrentGroup(state)) asterisks.add(MAP_COLOR_NOT_IN_GROUP);
		if(!state.locked) asterisks.add(MAP_COLOR_UNLOCKED);
		if(UpdateItemFrameContents.isInItemFrame(colorsId)) asterisks.add(MAP_COLOR_IN_IFRAME);
		if(UpdateContainerContents.hasDuplicateInContainer(colorsId) && !isSkipped) asterisks.add(MAP_COLOR_MULTI_CONTAINER);
		if(asterisks.isEmpty()){
			if(item.getCustomName() == null) lines.addFirst(lines.removeFirst().copy().withColor(MAP_COLOR_UNNAMED));
			tooltipCache.put(item, lines);
			return;
		}
		final boolean nameColor = asterisks.get(0) != MAP_COLOR_MULTI_CONTAINER; // This one is only permitted as an asterisk (idk why, ask older me)

		asterisks = asterisks.stream().distinct().toList(); // This line only exists in case of configurations where 2+ meanings share 1 color
		MutableComponent text = lines.removeFirst().copy();
		if(nameColor) text.withColor(asterisks.get(0));
		for(int i=nameColor?1:0; i<asterisks.size(); ++i) text.append(Component.literal("*").withColor(asterisks.get(i)));
		lines.addFirst(text);
		tooltipCache.put(item, lines);
	}
}