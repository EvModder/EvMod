package net.evmodder.evmod.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
abstract class MixinClientPacketListenerCrafting{
	@Inject(method="handleContainerSetSlot", at=@At("TAIL"))
	private final void confirmCraftResult(final ClientboundContainerSetSlotPacket packet, final CallbackInfo _ci){
		((AccessorKeybindCraftingRestock)(Object)AccessorMain.getKbCraftRestock())
				.callOnServerSlotUpdate(packet.getContainerId(), packet.getStateId(), packet.getSlot(), packet.getItem());
	}

	@Inject(method="handleContainerContent", at=@At("TAIL"))
	private final void confirmCraftContents(final ClientboundContainerSetContentPacket packet, final CallbackInfo _ci){
		((AccessorKeybindCraftingRestock)(Object)AccessorMain.getKbCraftRestock()).callOnServerContainerContent();
	}
}