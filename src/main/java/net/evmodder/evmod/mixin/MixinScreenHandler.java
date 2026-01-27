package net.evmodder.evmod.mixin;

import net.evmodder.evmod.Configs;
import net.evmodder.evmod.apis.MapClickMoveNeighbors;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenHandler.class)
abstract class MixinScreenHandler{

//	// Now handled by MixinClientPlayerInteractionManager
//	@Inject(method = "internalOnSlotClick", at = @At("HEAD"), cancellable = true)
//	private void avoid_sending_too_many_clicks(int slot, int button, SlotActionType action, PlayerEntity player, CallbackInfo ci){
//		if(Main.clickUtils.addClick(action) > Main.clickUtils.MAX_CLICKS){
//			ci.cancel(); // Throw out clicks that exceed the limit!!
//			Main.LOGGER.error("MixinScreenHandler: Discarding click in internalOnSlotClick() due to exceeding MAX_CLICKS limit!"
//					+ " slot:"+slot+",button:"+button+",action:"+action.name()+",isShiftClick:"+Screen.hasShiftDown());
//			MinecraftClient.getInstance().player.sendMessage(Text.literal("Discarding unsafe clicks!! > LIMIT").copy().withColor(/*&c=*/16733525), false);
//		}
//	}

	@Inject(method="internalOnSlotClick", at=@At("TAIL"))
	private final void clickMoveNeighborTriggerDetector(int slotIndex, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci){
		if(!Configs.Hotkeys.MAP_CLICK_MOVE_NEIGHBORS.getBooleanValue()) return;
		if(button != 0 || actionType != SlotActionType.PICKUP) return;
		if(!player.currentScreenHandler.getCursorStack().isEmpty()) return;
		if(slotIndex < 0 || slotIndex >= player.currentScreenHandler.slots.size()) return;
//		if(!Screen.hasShiftDown() && !Screen.hasControlDown() && !Screen.hasAltDown()) return;
		if(!Configs.Hotkeys.MAP_CLICK_MOVE_NEIGHBORS_KEY.getKeybind().isKeybindHeld()) return;

		final ItemStack itemPlaced = player.currentScreenHandler.getSlot(slotIndex).getStack();
		// These checks have been moved to MapClickMoveNeighbors
//		if(itemPlaced.getItem() != Items.FILLED_MAP) return;
//		if(itemPlaced.getCustomName() == null || itemPlaced.getCustomName().getLiteralString() == null) return;
		MapClickMoveNeighbors.moveNeighbors(player, slotIndex, itemPlaced);
	}
}