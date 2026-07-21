package net.evmodder.evmod.mixin;

import net.evmodder.evmod.Configs;
import net.evmodder.evmod.apis.MapClickMoveNeighbors;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
abstract class MixinScreenHandler{
	@Inject(method="doClick", at=@At("TAIL"))
	private final void clickMoveNeighborTriggerDetector(int slotIndex, int button, ContainerInput actionType, Player player, CallbackInfo ci){
//		if(!Configs.Hotkeys.MAP_CLICK_MOVE_NEIGHBORS.getKeybind().isValid()) return;
		if(!Configs.Hotkeys.MAP_MOVE_NEIGHBORS.getKeybind().isKeybindHeld()) return;
		if(button != 0 || actionType != ContainerInput.PICKUP) return;
		if(!player.containerMenu.getCarried().isEmpty()) return;
		if(slotIndex < 0 || slotIndex >= player.containerMenu.slots.size()) return;
//		if(!Screen.hasShiftDown() && !Screen.hasControlDown() && !Screen.hasAltDown()) return;
//		if(!Configs.Hotkeys.MAP_CLICK_MOVE_NEIGHBORS.getKeybind().isKeybindHeld()) return;

		MapClickMoveNeighbors.moveNeighbors(player, slotIndex, player.containerMenu.getSlot(slotIndex).getItem());
	}
}