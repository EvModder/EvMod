package net.evmodder.evmod.mixin;

import net.evmodder.evmod.Configs;
import net.evmodder.evmod.apis.MapClickMoveNeighbors;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenHandler.class)
abstract class MixinScreenHandler{
	@Inject(method="internalOnSlotClick", at=@At("TAIL"))
	private final void clickMoveNeighborTriggerDetector(int slotIndex, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci){
		if(!Configs.Hotkeys.MAP_CLICK_MOVE_NEIGHBORS.getBooleanValue()) return;
		if(button != 0 || actionType != SlotActionType.PICKUP) return;
		if(!player.currentScreenHandler.getCursorStack().isEmpty()) return;
		if(slotIndex < 0 || slotIndex >= player.currentScreenHandler.slots.size()) return;
//		if(!Screen.hasShiftDown() && !Screen.hasControlDown() && !Screen.hasAltDown()) return;
		if(!Configs.Hotkeys.MAP_CLICK_MOVE_NEIGHBORS_KEY.getKeybind().isKeybindHeld()) return;

		MapClickMoveNeighbors.moveNeighbors(player, slotIndex, player.currentScreenHandler.getSlot(slotIndex).getStack());
	}
}