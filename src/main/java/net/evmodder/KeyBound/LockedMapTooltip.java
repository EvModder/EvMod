package net.evmodder.KeyBound;

import java.util.List;
import net.minecraft.item.Item.TooltipContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.map.MapState;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;

final class LockedMapTooltip{
	public static final void redName(ItemStack item, TooltipContext context, TooltipType type, List<Text> lines){
//		if(type == TooltipType.BASIC){
//			Main.LOGGER.info("type == BASIC");
//			return;
//		}
		MapIdComponent id = item.get(DataComponentTypes.MAP_ID);
		if(id == null){
			Main.LOGGER.info("mapId is null");
			return;
		}
		MapState state = context.getMapState(id);
		if(state == null){
			Main.LOGGER.info("mapState is null");
			return;
		}
		if(state.locked){
			Main.LOGGER.info("state is locked");
			return;
		}
		lines.addFirst(lines.removeFirst().copy().withColor(14692709));
	}
}