package net.evmodder.KeyBound.apis;

import java.util.List;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item.TooltipContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;

public interface Tooltip{
	public abstract void get(ItemStack item, TooltipContext context, TooltipType type, List<Text> lines);

	public static void register(Tooltip tooltip){
		ItemTooltipCallback.EVENT.register(tooltip::get);
	}
}