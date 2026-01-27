package net.evmodder.evmod.mixin;

import net.evmodder.evmod.Configs;
import net.evmodder.evmod.apis.MapGroupUtils;
import net.evmodder.evmod.onTick.UpdateItemFrameHighlights;
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
abstract class MixinInGameHud{
	@ModifyVariable(method="renderHeldItemTooltip", at=@At("STORE"), ordinal=0)
	private final MutableText showRepairCostNextToItemName(MutableText originalText){
		final boolean rcHUD = Configs.Visuals.REPAIR_COST_HOTBAR_HUD.getBooleanValue();
		final boolean mapHighlightHUD = Configs.Visuals.MAP_HIGHLIGHT_HOTBAR_HUD.getBooleanValue();

		if(rcHUD == false && mapHighlightHUD == false) return originalText;
		final MinecraftClient client = MinecraftClient.getInstance();
		final ItemStack currentStack = client.player.getInventory().getMainHandStack();
		MutableText text = originalText;
		if(mapHighlightHUD){
			final MapIdComponent id = currentStack.get(DataComponentTypes.MAP_ID);
			if(id != null){
				final MapState state = client.world.getMapState(id);
				if(state != null && MapGroupUtils.shouldHighlightNotInCurrentGroup(state)){
					text = text.withColor(Configs.Visuals.MAP_COLOR_NOT_IN_GROUP.getIntegerValue());
					if(!state.locked) text = text.append(Text.literal("*").withColor(Configs.Visuals.MAP_COLOR_UNLOCKED.getIntegerValue()));
				}
				else if(state != null && !state.locked) text = text.withColor(Configs.Visuals.MAP_COLOR_UNLOCKED.getIntegerValue());
				else if(state != null && UpdateItemFrameHighlights.isInItemFrame(MapGroupUtils.getIdForMapState(state)))
					text = text.withColor(Configs.Visuals.MAP_COLOR_IN_IFRAME.getIntegerValue());
				else if(currentStack.getCustomName() == null) text = text.withColor(Configs.Visuals.MAP_COLOR_UNNAMED.getIntegerValue());
			}
		}
		if(rcHUD && currentStack.contains(DataComponentTypes.REPAIR_COST)){
			final int rc = currentStack.get(DataComponentTypes.REPAIR_COST);
			if(rc != 0 || currentStack.hasEnchantments() || currentStack.contains(DataComponentTypes.STORED_ENCHANTMENTS)){
				text = text.append(Text.literal(" \u02b3\u1d9c").formatted(Formatting.GRAY)).append(Text.literal(""+rc).formatted(Formatting.GOLD));
			}
		}
		return text;
	}
}