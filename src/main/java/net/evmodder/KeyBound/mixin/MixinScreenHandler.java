package net.evmodder.KeyBound.mixin;

import net.evmodder.KeyBound.EventListeners.MapClickMoveNeighbors;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenHandler.class)
public abstract class MixinScreenHandler{

//	@Inject(method = "internalOnSlotClick", at = @At("HEAD"), cancellable = true)
//	private void avoid_sending_too_many_clicks(int slot, int button, SlotActionType action, PlayerEntity player, CallbackInfo ci){
//		if(Main.inventoryUtils.addClick(action) > Main.inventoryUtils.MAX_CLICKS){
//			ci.cancel(); // Throw out clicks that exceed the limit!!
//			Main.LOGGER.error("Discarding click in internalOnSlotClick() due to exceeding MAX_CLICKS limit!"
//					+ " slot:"+slot+",button:"+button+",action:"+action.name()+",isShiftClick:"+Screen.hasShiftDown());
//			MinecraftClient.getInstance().player.sendMessage(Text.literal("Discarding unsafe clicks!! > LIMIT").copy().withColor(/*&c=*/16733525), false);
//		}
//	}

	@Inject(method = "internalOnSlotClick", at = @At("TAIL"))
	private void click_move_neighbors_caller(int slotIndex, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci){
		if(button != 0 || actionType != SlotActionType.PICKUP) return;
		if(!Screen.hasShiftDown() && !Screen.hasControlDown() && !Screen.hasAltDown()) return;
		if(!player.currentScreenHandler.getCursorStack().isEmpty()) return;
		if(slotIndex < 0 || slotIndex >= player.currentScreenHandler.slots.size()) return;
		final ItemStack itemPlaced = player.currentScreenHandler.getSlot(slotIndex).getStack();
		if(itemPlaced.getItem() != Items.FILLED_MAP) return;
		if(itemPlaced.getCustomName() == null || itemPlaced.getCustomName().getLiteralString() == null) return; // TODO: support unnamed maps

//		player.getInventory().markDirty();
//		new Timer().schedule(new TimerTask(){@Override public void run(){
//			player.getInventory().markDirty();
			MapClickMoveNeighbors.moveNeighbors(player, slotIndex, itemPlaced);
//		}}, 10l);
	}
}