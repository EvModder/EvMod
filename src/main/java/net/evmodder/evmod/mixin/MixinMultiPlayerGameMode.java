package net.evmodder.evmod.mixin;

import static net.evmodder.evmod.compat.MinecraftCompat.sendSystem;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.Main;
import net.evmodder.evmod.apis.ClickUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;

@Mixin(MultiPlayerGameMode.class)
abstract class MixinMultiPlayerGameMode{

	private final AtomicInteger discardedClicks = new AtomicInteger();

	@Inject(method="handleContainerInput", at=@At("HEAD"), cancellable=true)
	private final void avoidSendingTooManyClicks(int syncId, int slot, int button, ContainerInput action, Player player, CallbackInfo ci){
//		MinecraftClient.getInstance().player.sendMessage(Text.literal("clickSlot: syncId="+syncId+",slot="+slot+",button="+button+",action="+action.name()), false);
		if(player.isCreative()) return;
//		if(action == SlotActionType.CLONE/* || action == SlotActionType.THROW || action == SlotActionType.QUICK_CRAFT*/) return;
		if(syncId != player.containerMenu.containerId) return;  // Vanilla sends no packet for a mismatched menu.
		final boolean isBotted = ClickUtils.isThisClickBotted();
		if(Configs.Generic.CLICK_FILTER_USER_INPUT.getBooleanValue() && !isBotted && ClickUtils.hasOngoingClicks()){
			ci.cancel();
			if(syncId == 0 && slot == 0 && button == 0 && action == ContainerInput.QUICK_MOVE) return;  // QUICK_CRAFT sometimes sends duplicate fake QUICK_MOVE?
			sendSystem(Minecraft.getInstance().player, Component.literal("Discarding user click to protect an ongoing ClickOp").withColor(/*&c=*/16733525));
//			MinecraftClient.getInstance().player.sendMessage(Text.literal("syncId="+syncId+",slot="+slot+",button="+button+",action="+action.name()), false);
			return;
		}
		final boolean success = AccessorClickUtils.callAddClick(/*action, */isBotted || !Configs.Generic.CLICK_LIMIT_USER_INPUT.getBooleanValue());

		if(!success){
			if(isBotted){
				String err = "Botted click somehow triggered click limit! VERY BAD!!";
				Main.LOGGER.error(err);
				sendSystem(Minecraft.getInstance().player, Component.literal(err));
				return;
			}
			else if(!Configs.Generic.CLICK_LIMIT_USER_INPUT.getBooleanValue()) return;
//			else if(syncId == 0 && slot == 0 && button == 0 && action == SlotActionType.QUICK_MOVE) return; // QUICK_CRAFT sends duplicate fake QUICK_MOVE?
			else ci.cancel();  // Throw out clicks that exceed the limit!!
			Main.LOGGER.error("Discarded click in clickSlot() due to exceeding limit!"
					+ " slot:"+slot+",button:"+button+",action:"+action.name());
//			MinecraftClient.getInstance().player.sendMessage(Text.literal("syncId="+syncId+",slot="+slot+",button="+button+",action="+action.name()), false);
//			MinecraftClient.getInstance().player.sendMessage(
//					Text.literal("Discarding unsafe click!"
//							+ " | limit:"+Configs.Generic.CLICK_LIMIT_COUNT.getIntegerValue()
//							+", duration:"+Configs.Generic.CLICK_LIMIT_WINDOW.getIntegerValue()+"gt").withColor(/*&c=*/16733525), false);
			if(discardedClicks.getAndIncrement() == 0){
				CompletableFuture.delayedExecutor(ClickUtils.TICK_DURATION_NANOS, TimeUnit.NANOSECONDS).execute(() -> {
					final int clicks = discardedClicks.getAndSet(0);
					sendSystem(Minecraft.getInstance().player,
							Component.literal("Unsafe clicks! | limit:"+Configs.Generic.CLICK_LIMIT_COUNT.getIntegerValue()
									+" | window:"+Configs.Generic.CLICK_LIMIT_WINDOW.getIntegerValue()+"gt"
									+" | discarded: "+clicks
									).withColor(/*&c=*/16733525));
				});
			}
		}
	}
}