package net.evmodder.mixin;

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
public class InGameHudMixin{

	// New (nicer) way of doing it
	@ModifyVariable(method = "renderHeldItemTooltip", at = @At("STORE"), ordinal = 0)
	private MutableText showRepairCostNextToItemName(MutableText originalText){
		MinecraftClient client = MinecraftClient.getInstance();
		//if(client == null || client.player == null) return originalText;
		ItemStack currentStack = client.player.getInventory().getMainHandStack();
		if(!currentStack.getComponents().contains(DataComponentTypes.REPAIR_COST)) return originalText;

		int rc = currentStack.getComponents().get(DataComponentTypes.REPAIR_COST);
		if(rc == 0 && !currentStack.isEnchantable()) return originalText;
		return originalText.append(Text.literal(" | ").formatted(Formatting.DARK_GRAY)).append(Text.literal("rc"+rc).formatted(Formatting.GOLD));
	}

	// Old (messy) way of doing it
	/*@Inject(method = "renderHeldItemTooltip", cancellable = true, at = @At("HEAD"))
	public void showRepairCostNextToItemName(DrawContext context, CallbackInfo ci){
		ci.cancel();
		InGameHud inst = (InGameHud)(Object)this;
		MinecraftClient client = MinecraftClient.getInstance();
		ItemStack currentStack = ((InGameHudAccessor)inst).getCurrentStack();
		int heldItemTooltipFade = ((InGameHudAccessor)inst).getHeldItemTooltipFade();

		// Copied from the real renderHeldItemTooltip, except for the part in the comment block
		client.getProfiler().push("selectedItemName");
		if(heldItemTooltipFade > 0 && !currentStack.isEmpty()) {
			MutableText mutableText = Text.empty().append(currentStack.getName()).formatted(currentStack.getRarity().getFormatting());
			if(currentStack.contains(DataComponentTypes.CUSTOM_NAME)) mutableText.formatted(Formatting.ITALIC);

			////////////// injected
			if(currentStack.getComponents().contains(DataComponentTypes.REPAIR_COST)){
				int rc = currentStack.getComponents().get(DataComponentTypes.REPAIR_COST);
				mutableText.append(Text.literal(" rc"+rc).formatted(Formatting.GOLD));
			}
			//////////////

			int i = inst.getTextRenderer().getWidth(mutableText);
			int j = (context.getScaledWindowWidth() - i) / 2;
			int k = context.getScaledWindowHeight() - 59;

			if(!client.interactionManager.hasStatusBars()) k += 14;
			int l = (int)((float)heldItemTooltipFade * 256.0F / 10.0F);
			if(l > 255) l = 255;

			if(l > 0) context.drawTextWithBackground(inst.getTextRenderer(), mutableText, j, k, i, Argb.withAlpha(l, -1));
		}
		client.getProfiler().pop();
	}*/
}