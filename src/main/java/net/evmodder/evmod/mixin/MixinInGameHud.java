package net.evmodder.evmod.mixin;

import net.evmodder.evmod.Configs;
import net.evmodder.evmod.apis.MapGroupUtils;
import net.evmodder.evmod.onTick.UpdateItemFrameContents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Hud.class)
abstract class MixinInGameHud{
	@ModifyVariable(method="extractSelectedItemName", at=@At("STORE"), ordinal=0)
	private final MutableComponent showRepairCostNextToItemName(MutableComponent originalText){
		final boolean rcHUD = Configs.Visuals.REPAIR_COST_HOTBAR_HUD.getBooleanValue();
		final boolean mapHighlightHUD = Configs.Visuals.MAP_HIGHLIGHT_HOTBAR_HUD.getBooleanValue();

		if(rcHUD == false && mapHighlightHUD == false) return originalText;
		final Minecraft client = Minecraft.getInstance();
		final ItemStack currentStack = client.player.getMainHandItem();
		MutableComponent text = originalText;
		if(mapHighlightHUD){
			final MapId id = currentStack.get(DataComponents.MAP_ID);
			if(id != null){
				final MapItemSavedData state = client.level.getMapData(id);
				if(state != null && MapGroupUtils.shouldHighlightNotInCurrentGroup(state)){
					text = text.withColor(Configs.Visuals.MAP_COLOR_NOT_IN_GROUP.getIntegerValue());
					if(!state.locked) text = text.append(Component.literal("*").withColor(Configs.Visuals.MAP_COLOR_UNLOCKED.getIntegerValue()));
				}
				else if(state != null && !state.locked) text = text.withColor(Configs.Visuals.MAP_COLOR_UNLOCKED.getIntegerValue());
				else if(state != null && UpdateItemFrameContents.isInItemFrame(MapGroupUtils.getIdForMapState(state)))
					text = text.withColor(Configs.Visuals.MAP_COLOR_IN_IFRAME.getIntegerValue());
				else if(currentStack.getCustomName() == null) text = text.withColor(Configs.Visuals.MAP_COLOR_UNNAMED.getIntegerValue());
			}
		}
		if(rcHUD && currentStack.has(DataComponents.REPAIR_COST)){
			final int rc = currentStack.get(DataComponents.REPAIR_COST);
			if(rc != 0 || currentStack.isEnchanted() || currentStack.has(DataComponents.STORED_ENCHANTMENTS)){
				text = text.append(Component.literal(" \u02b3\u1d9c").withStyle(ChatFormatting.GRAY)).append(Component.literal(""+rc).withStyle(ChatFormatting.GOLD));
			}
		}
		return text;
	}
}