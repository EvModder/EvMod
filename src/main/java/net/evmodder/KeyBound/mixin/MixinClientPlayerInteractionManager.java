package net.evmodder.KeyBound.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.evmodder.KeyBound.Main;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class MixinClientPlayerInteractionManager{
	@Shadow @Final private MinecraftClient client;
	@Shadow private int blockBreakingCooldown;

	@Inject(method="interactItem", at=@At(value="INVOKE", target="Lnet/minecraft/client/network/ClientPlayerInteractionManager;syncSelectedSlot()V"))
	private void onProcessRightClickFirst(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir){
	//private void onProcessRightClickFirst(PlayerEntity player, Hand hand){
		Main.LOGGER.info("onProcessRightClickPre");
		//if(MapHandRestock.isEnabled) MapHandRestock.onProcessRightClickPre(player, hand);
	}

	@Inject(method="interactItem", at=@At("TAIL"))
	private void onProcessRightClickPost(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir){
		Main.LOGGER.info("onProcessRightClickPost");
		//if(MapHandRestock.isEnabled) MapHandRestock.onProcessRightClickPost(player, hand);
	}
}
