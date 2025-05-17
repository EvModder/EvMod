package net.evmodder.KeyBound.mixin;

import net.evmodder.KeyBound.Main;
import net.evmodder.KeyBound.MapClickMoveNeighbors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenHandler.class)
public abstract class MixinScreenHandler{

	@Inject(method = "internalOnSlotClick", at = @At("TAIL"))
	private void add_logic_for_bulk_move_maparts(int slotIndex, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci){
		if(Main.inventoryUtils.addClick(actionType) > Main.inventoryUtils.MAX_CLICKS){
			ci.cancel(); // Throw out clicks that exceed the limit!!
			Main.LOGGER.error("Discarding click in internalOnSlotClick() due to exceeding MAX_CLICKS limit");
			MinecraftClient.getInstance().player.sendMessage(Text.literal("Discarding unsafe clicks!! > LIMIT").copy().withColor(/*&c=*/16733525), false);
		}
		//Main.LOGGER.info("MapMoveClick: click slot "+slotIndex);
		if(button != 0 || actionType != SlotActionType.PICKUP) return;
		//Main.LOGGER.info("MapMoveClick: PICKUP");
		if(!Screen.hasShiftDown()) return;
		//Main.LOGGER.info("MapMoveClick: shift-click");

		// Item is on cursor, at=@At("HEAD")
//		if(player.currentScreenHandler.getSlot(slotIndex).hasStack()) return;
//		//Main.LOGGER.info("MapMoveClick: clicked an empty slot");
//		ItemStack itemPlaced = player.currentScreenHandler.getCursorStack().copy();
//		if(itemPlaced.getCustomName() == null) return; // TODO: support unnamed maps (edge detection)
//		if(!Registries.ITEM.getId(itemPlaced.getItem()).getPath().equals("filled_map")) return;

		// Item is in slot, at=@At("TAIL")
		if(!player.currentScreenHandler.getCursorStack().isEmpty()) return;
		ItemStack itemPlaced = player.currentScreenHandler.getSlot(slotIndex).getStack();
		if(!Registries.ITEM.getId(itemPlaced.getItem()).getPath().equals("filled_map")) return;
		if(itemPlaced.getCustomName() == null || itemPlaced.getCustomName().getLiteralString() == null) return; // TODO: support unnamed maps (edge detection)

		//Main.LOGGER.info("MapMoveClick: placed a filled map");
		//new Timer().schedule(new TimerTask(){@Override public void run(){
			MapClickMoveNeighbors.moveNeighbors(player, slotIndex, itemPlaced);
		//}}, 10l);
	}
}