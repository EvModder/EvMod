package net.evmodder.evmod.onTick;

import java.util.List;
import net.minecraft.item.Item.TooltipContext;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.apis.Tooltip;
import net.evmodder.evmod.config.TooltipDisplayOption;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class TooltipRepairCost implements Tooltip{
	@Override public final void get(ItemStack item, TooltipContext context, TooltipType type, List<Text> lines){
		switch((TooltipDisplayOption)Configs.Visuals.REPAIR_COST_TOOLTIP.getOptionListValue()){
			case OFF: return;
			case ADVANCED_TOOLTIPS: if(type == TooltipType.BASIC) return;
			case ON: /*no op*/
		}
		final int rc = item.getComponents().get(DataComponentTypes.REPAIR_COST);
		if(rc == 0 && !item.hasEnchantments() && !item.getComponents().contains(DataComponentTypes.STORED_ENCHANTMENTS)) return;
		//lines.add(Text.literal("RepairCost: ").formatted(Formatting.GRAY).append(Text.literal(""+rc).formatted(Formatting.GOLD)));
		Text last = lines.removeLast().copy().append(Text.literal(", rc:").formatted(Formatting.GRAY).append(Text.literal(""+rc).formatted(Formatting.GOLD)));
		lines.add(last);
	}
}