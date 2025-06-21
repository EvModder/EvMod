package net.evmodder.KeyBound.EventListeners;

import java.util.List;
import net.minecraft.item.Item.TooltipContext;
import net.evmodder.KeyBound.Main;
import net.evmodder.KeyBound.MapGroupUtils;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.map.MapState;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class TooltipMapNameColor{
	private static final boolean isInInv(ItemStack item, TooltipContext context){
		//if(MapGroupUtils.mapsInGroup == null) return false;
		MapIdComponent id = item.get(DataComponentTypes.MAP_ID);
		if(id == null) return false;
		MapState state = context.getMapState(id);
		if(state == null) return false;
		return InventoryHighlightUpdater.isInInventory(MapGroupUtils.getIdForMapState(state));
	}
	private static final boolean isNotInCurrentGroup(ItemStack item, TooltipContext context){
		//if(MapGroupUtils.mapsInGroup == null) return false;
		MapIdComponent id = item.get(DataComponentTypes.MAP_ID);
		if(id == null) return false;
		MapState state = context.getMapState(id);
		if(state == null) return false;
		return MapGroupUtils.shouldHighlightNotInCurrentGroup(state);
	}
	private static final boolean isUnlockedMap(ItemStack item, TooltipContext context){
		MapIdComponent id = item.get(DataComponentTypes.MAP_ID);
		if(id == null) return false;
		MapState state = context.getMapState(id);
		if(state == null) return false;
		return state != null && !state.locked;
	}
	private static final boolean isOnDisplayMap(ItemStack item, TooltipContext context){
		MapIdComponent id = item.get(DataComponentTypes.MAP_ID);
		if(id == null) return false;
		MapState state = context.getMapState(id);
		if(state == null) return false;
		return ItemFrameHighlightUpdater.isInItemFrame(MapGroupUtils.getIdForMapState(state));
	}
	private static final boolean isUnnamedMap(ItemStack item){
		return item.getCustomName() == null && item.contains(DataComponentTypes.MAP_ID);
	}

	public static final void tooltipColors(ItemStack item, TooltipContext context, TooltipType type, List<Text> lines){
		ContainerComponent container = item.get(DataComponentTypes.CONTAINER);
		if(container != null){
			if(container.stream().anyMatch(i -> isInInv(i, context))){
				lines.addFirst(lines.removeFirst().copy().append(Text.literal("*").withColor(Main.MAP_COLOR_IN_INV).formatted(Formatting.BOLD)));
			}
			if(container.stream().anyMatch(i -> isNotInCurrentGroup(i, context))){
				lines.addFirst(lines.removeFirst().copy().append(Text.literal("*").withColor(Main.MAP_COLOR_NOT_IN_GROUP).formatted(Formatting.BOLD)));
			}
			if(container.stream().anyMatch(i -> isUnlockedMap(i, context))){
				lines.addFirst(lines.removeFirst().copy().append(Text.literal("*").withColor(Main.MAP_COLOR_UNLOCKED).formatted(Formatting.BOLD)));
			}
//			if(container.stream().anyMatch(i -> isOnDisplayMap(i, context))){
//				lines.addFirst(lines.removeFirst().copy().append(Text.literal("*").withColor(Main.MAP_COLOR_IN_IFRAME).formatted(Formatting.BOLD)));
//			}
			if(container.stream().anyMatch(i -> isUnnamedMap(i))){
				lines.addFirst(lines.removeFirst().copy().append(Text.literal("*").withColor(Main.MAP_COLOR_UNNAMED).formatted(Formatting.BOLD)));
			}
			return;
		}
		if(isNotInCurrentGroup(item, context)){
			MutableText text = lines.removeFirst().copy().withColor(Main.MAP_COLOR_NOT_IN_GROUP);
			if(isUnlockedMap(item, context)) text = text.append(Text.literal("*").withColor(Main.MAP_COLOR_UNLOCKED));
			lines.addFirst(text);
		}
		else if(isUnlockedMap(item, context)) lines.addFirst(lines.removeFirst().copy().withColor(Main.MAP_COLOR_UNLOCKED));
		else if(isOnDisplayMap(item, context)) lines.addFirst(lines.removeFirst().copy().withColor(Main.MAP_COLOR_IN_IFRAME));
		else if(isUnnamedMap(item)) lines.addFirst(lines.removeFirst().copy().withColor(Main.MAP_COLOR_UNNAMED));
	}
}