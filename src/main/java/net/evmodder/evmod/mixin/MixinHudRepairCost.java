package net.evmodder.evmod.mixin;

import net.evmodder.evmod.Configs;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Hud.class)
abstract class MixinHudRepairCost{
	@ModifyVariable(method="extractSelectedItemName", at=@At("STORE"), ordinal=0, order=1100)
	private final MutableComponent showRepairCostNextToItemName(MutableComponent originalText){
		if(!Configs.Visuals.REPAIR_COST_HOTBAR_HUD.getBooleanValue()) return originalText;
		final Minecraft client = Minecraft.getInstance();
		final ItemStack currentStack = client.player.getMainHandItem();
		MutableComponent text = originalText;

		if(currentStack.has(DataComponents.REPAIR_COST)){
			final int rc = currentStack.get(DataComponents.REPAIR_COST);
			if(rc != 0 || currentStack.isEnchanted() || currentStack.has(DataComponents.STORED_ENCHANTMENTS)){
				text = text.append(Component.literal(" \u02b3\u1d9c").withStyle(ChatFormatting.GRAY)).append(Component.literal(""+rc).withStyle(ChatFormatting.GOLD));
			}
		}
		return text;
	}
}