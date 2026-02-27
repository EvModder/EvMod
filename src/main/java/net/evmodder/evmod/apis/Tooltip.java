package net.evmodder.evmod.apis;

import java.util.List;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item.TooltipContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;

public interface Tooltip{
	public abstract void get(final ItemStack item, final TooltipContext context, final TooltipType type, final List<Text> lines);
	public static void register(final Tooltip tooltip){ItemTooltipCallback.EVENT.register(tooltip::get);}
}