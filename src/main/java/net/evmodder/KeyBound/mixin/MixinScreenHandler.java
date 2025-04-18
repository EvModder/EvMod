package net.evmodder.KeyBound.mixin;

import net.evmodder.KeyBound.Main;
import net.evmodder.KeyBound.MapClickMoveNeighbors;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenHandler.class)
public abstract class MixinScreenHandler{

	@Inject(method = "internalOnSlotClick", at = @At("TAIL"))
	private void add_logic_for_bulk_move_maparts(int slotIndex, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci){
		//Main.LOGGER.info("MapMoveClick: click evt");
		if(button != 0 || actionType != SlotActionType.PICKUP) return;
		//Main.LOGGER.info("MapMoveClick: PICKUP");
		if(!Screen.hasShiftDown()) return;
		//Main.LOGGER.info("MapMoveClick: shift-click");
		if(player.currentScreenHandler.getSlot(slotIndex).hasStack()) return;
		//Main.LOGGER.info("MapMoveClick: clicked an empty slot");
		ItemStack itemPlaced = player.currentScreenHandler.getCursorStack();
		if(!Registries.ITEM.getId(itemPlaced.getItem()).getPath().equals("filled_map")) return;
		Main.LOGGER.info("MapMoveClick: placed a filled map");
		MapClickMoveNeighbors.moveNeighbors();
	}
}