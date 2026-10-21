package net.evmodder.evmod.mixin;

import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
abstract class MixinClientPacketListener{
	// First point after vanilla's thread handoff, before profiles/skins/tab entries are processed.
	@Inject(method="handlePlayerInfoUpdate", at=@At(value="INVOKE",
			target="Lnet/minecraft/network/protocol/game/ClientboundPlayerInfoUpdatePacket;newEntries()Ljava/util/List;"))
	private final void pearlRescue(final ClientboundPlayerInfoUpdatePacket packet, final CallbackInfo _ci){
		AccessorCommandPearlRescue.callOnPlayerInfo((ClientPacketListener)(Object)this, packet);
	}

	@Inject(method="handleSetTime", at=@At("TAIL"))
	private final void pearlReadinessTime(final ClientboundSetTimePacket packet, final CallbackInfo ci){
		// Vanilla has already moved this packet onto the client thread, behind earlier entity updates.
		if(Minecraft.getInstance().getConnection() == (Object)this)
			((AccessorEpearlLookup)(Object)AccessorMain.getEpearlLookup()).callOnServerTime(packet.gameTime());
	}

	@Inject(method="handleMovePlayer", at=@At("TAIL"))
	private final void pearlReadinessCorrection(final ClientboundPlayerPositionPacket packet, final CallbackInfo ci){
		if(Minecraft.getInstance().getConnection() == (Object)this)
			((AccessorEpearlLookup)(Object)AccessorMain.getEpearlLookup()).callResetConnectionReadiness();
	}
}