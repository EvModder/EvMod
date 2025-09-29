package net.evmodder.KeyBound.events;

import java.util.List;
import net.minecraft.item.Item.TooltipContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class TooltipRepairCost{
	public static final void addRC(ItemStack item, TooltipContext context, TooltipType type, List<Text> lines){
		if(type == TooltipType.BASIC) return;
		final int rc = item.getComponents().get(DataComponentTypes.REPAIR_COST);
		if(rc == 0 && !item.hasEnchantments() && !item.getComponents().contains(DataComponentTypes.STORED_ENCHANTMENTS)) return;
		//lines.add(Text.literal("RepairCost: ").formatted(Formatting.GRAY).append(Text.literal(""+rc).formatted(Formatting.GOLD)));
		Text last = lines.removeLast().copy().append(Text.literal(", rc:").formatted(Formatting.GRAY).append(Text.literal(""+rc).formatted(Formatting.GOLD)));
		lines.add(last);
	}
}