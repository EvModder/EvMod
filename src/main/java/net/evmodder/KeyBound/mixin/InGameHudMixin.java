package net.evmodder.KeyBound.mixin;

import net.evmodder.KeyBound.Main;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin{
	@ModifyVariable(method = "renderHeldItemTooltip", at = @At("STORE"), ordinal = 0)
	private MutableText showRepairCostNextToItemName(MutableText originalText){
		if(Main.rcTooltip == false) return originalText;
		MinecraftClient client = MinecraftClient.getInstance();
		ItemStack currentStack = client.player.getInventory().getMainHandStack();
		if(!currentStack.getComponents().contains(DataComponentTypes.REPAIR_COST)) return originalText;

		int rc = currentStack.getComponents().get(DataComponentTypes.REPAIR_COST);
		if(rc == 0 && !currentStack.hasEnchantments() && !currentStack.getComponents().contains(DataComponentTypes.STORED_ENCHANTMENTS)) return originalText;
		return originalText.append(Text.literal(" ʳᶜ").formatted(Formatting.GRAY)).append(Text.literal(""+rc).formatted(Formatting.GOLD));
	}
}