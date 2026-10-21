package net.evmodder.evmod.mixin;

import net.evmodder.evmod.Configs;
import net.evmodder.evmod.apis.MapGroupUtils;
import net.evmodder.evmod.onTick.UpdateItemFrameContents;
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
abstract class MixinHudMap{
	@ModifyVariable(method="extractSelectedItemName", at=@At("STORE"), ordinal=0)
	private MutableComponent highlightMapName(MutableComponent originalText){
		if(!Configs.Visuals.MAP_HIGHLIGHT_HOTBAR_HUD.getBooleanValue()) return originalText;
		final Minecraft client = Minecraft.getInstance();
		final ItemStack currentStack = client.player.getMainHandItem();
		MutableComponent text = originalText;
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
		return text;
	}
}