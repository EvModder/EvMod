package net.evmodder.evmod.onTick;

import java.util.List;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.apis.Tooltip;
import net.evmodder.evmod.config.OptionTooltipDisplay;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public final class TooltipRepairCost implements Tooltip{
	@Override public final void get(final ItemStack item, final TooltipContext _0, final TooltipFlag type, final List<Component> lines){
		switch((OptionTooltipDisplay)Configs.Visuals.REPAIR_COST_TOOLTIP.getOptionListValue()){
			case OFF: return;
			case ADVANCED_TOOLTIPS: if(type == TooltipFlag.NORMAL) return;
			case ON: /*no op*/
		}
		final int rc = item.getComponents().get(DataComponents.REPAIR_COST);
		if(rc == 0 && !item.isEnchanted() && !item.getComponents().has(DataComponents.STORED_ENCHANTMENTS)) return;
		//lines.add(Text.literal("RepairCost: ").formatted(Formatting.GRAY).append(Text.literal(""+rc).formatted(Formatting.GOLD)));
		lines.add(lines.removeLast().copy().append(Component.literal(", rc:").withStyle(ChatFormatting.GRAY).append(Component.literal(""+rc).withStyle(ChatFormatting.GOLD))));
	}
}