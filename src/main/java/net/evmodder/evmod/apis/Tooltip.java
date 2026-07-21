package net.evmodder.evmod.apis;

import java.util.List;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public interface Tooltip{
	public abstract void get(final ItemStack item, final TooltipContext context, final TooltipFlag type, final List<Component> lines);
	public static void register(final Tooltip tooltip){ItemTooltipCallback.EVENT.register(tooltip::get);}
}