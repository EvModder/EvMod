package net.evmodder.evmod.mixin;

import net.evmodder.evmod.apis.PlayerUnload;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
abstract class MixinClientPacketListenerUnload{
	// Vanilla calls this first when disconnecting (via close) and when clearing the level for reconfiguration.
	@Inject(method="clearLevel", at=@At("HEAD"))
	private final void firePlayerUnload(final CallbackInfo ci){PlayerUnload.fire();}
}