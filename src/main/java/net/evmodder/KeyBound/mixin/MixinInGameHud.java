package net.evmodder.KeyBound.mixin;

import net.evmodder.KeyBound.Main;
import net.evmodder.KeyBound.EventListeners.TooltipMapNameColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.map.MapState;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(InGameHud.class)
public abstract class MixinInGameHud{
	@ModifyVariable(method = "renderHeldItemTooltip", at = @At("STORE"), ordinal = 0)
	private MutableText showRepairCostNextToItemName(MutableText originalText){
		if(Main.rcHotbarHUD == false && Main.mapColorHUD == false) return originalText;
		MinecraftClient client = MinecraftClient.getInstance();
		ItemStack currentStack = client.player.getInventory().getMainHandStack();
		MutableText text = originalText;
		if(Main.mapColorHUD){
			MapIdComponent id = currentStack.get(DataComponentTypes.MAP_ID);
			if(id != null){
				MapState state = client.world.getMapState(id);
				if(state != null && !state.locked) text = text.withColor(TooltipMapNameColor.UNLOCKED_COLOR);
				else if(currentStack.getCustomName() == null) text = text.withColor(TooltipMapNameColor.UNNAMED_COLOR);
			}
		}
		if(Main.rcHotbarHUD && currentStack.contains(DataComponentTypes.REPAIR_COST)){
			int rc = currentStack.get(DataComponentTypes.REPAIR_COST);
			if(rc != 0 || currentStack.hasEnchantments() || currentStack.contains(DataComponentTypes.STORED_ENCHANTMENTS)){
				text = text.append(Text.literal(" \u02b3\u1d9c").formatted(Formatting.GRAY)).append(Text.literal(""+rc).formatted(Formatting.GOLD));
			}
		}
		return text;
	}
}