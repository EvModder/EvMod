package net.evmodder.evmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.Main;
import net.evmodder.evmod.apis.ClickUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

@Mixin(ClientPlayerInteractionManager.class)
abstract class MixinClientPlayerInteractionManager{

//	public static final class Friend{private Friend(){}}
//	private static final Friend friend = new Friend();

	@Inject(method="clickSlot", at=@At("HEAD"), cancellable=true)
	private final void avoidSendingTooManyClicks(int syncId, int slot, int button, SlotActionType action, PlayerEntity player, CallbackInfo ci){
//		MinecraftClient.getInstance().player.sendMessage(Text.literal("clickSlot: syncId="+syncId+",slot="+slot+",button="+button+",action="+action.name()), false);
		if(player.isCreative()) return;
//		if(action == SlotActionType.CLONE/* || action == SlotActionType.THROW || action == SlotActionType.QUICK_CRAFT*/) return;
		if(slot == -999) return; // comment this out to test things
		final boolean isBotted = ClickUtils.isThisClickBotted(/*friend*/);
		if(Configs.Generic.CLICK_FILTER_USER_INPUT.getBooleanValue() && !isBotted && ClickUtils.hasOngoingClicks()){
			ci.cancel();
			if(syncId == 0 && slot == 0 && button == 0 && action == SlotActionType.QUICK_MOVE) return; // QUICK_CRAFT sometimes sends duplicate fake QUICK_MOVE?
			MinecraftClient.getInstance().player.sendMessage(Text.literal("Discarding user click to protect an ongoing ClickOp").withColor(/*&c=*/16733525), false);
//			MinecraftClient.getInstance().player.sendMessage(Text.literal("syncId="+syncId+",slot="+slot+",button="+button+",action="+action.name()), false);
			return;
		}
		if(ClickUtils.addClick()){
			if(Configs.Hotkeys.CRAFT_RESTOCK.getKeybind().isValid())
				AccessorMain.getInstance().kbCraftRestock.checkIfCraftAction(player.currentScreenHandler, slot, button, action);
		}
		else{
			if(isBotted){
				String err = "Botted click somehow triggered click limit! VERY BAD!!";
				Main.LOGGER.error(err);
				MinecraftClient.getInstance().player.sendMessage(Text.literal(err), false);
			}
			else if(!Configs.Generic.CLICK_LIMIT_USER_INPUT.getBooleanValue()) return;
			else ci.cancel(); // Throw out clicks that exceed the limit!!

			if(syncId == 0 && slot == 0 && button == 0 && action == SlotActionType.QUICK_MOVE) return; // QUICK_CRAFT sometimes sends duplicate fake QUICK_MOVE?
			Main.LOGGER.error("Discarded click in clickSlot() due to exceeding limit!"
					+ " slot:"+slot+",button:"+button+",action:"+action.name()+",isShiftClick:"+Screen.hasShiftDown());
//			MinecraftClient.getInstance().player.sendMessage(Text.literal("syncId="+syncId+",slot="+slot+",button="+button+",action="+action.name()), false);
			MinecraftClient.getInstance().player.sendMessage(
					Text.literal("Discarding unsafe click!"
							+ " | limit:"+Configs.Generic.CLICK_LIMIT_COUNT.getIntegerValue()
							+", duration:"+Configs.Generic.CLICK_LIMIT_WINDOW.getIntegerValue()+"gt").withColor(/*&c=*/16733525), false);
		}
	}
}