package net.evmodder.evmod.mixin;

import net.evmodder.evmod.Main;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.entity.EntityTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
abstract class MixinClientPacketListenerPlayerPosition{
	@Inject(method="handleAddEntity", at=@At("HEAD"))
	private final void onSpawn(final ClientboundAddEntityPacket packet, final CallbackInfo _ci){
		// If the incoming entity is a player and matches your target's UUID
		if(packet.getType() == EntityTypes.PLAYER &&
				AccessorMain.getSyncPlayerPos().removeFakePlayer(packet.getUUID())){
			Main.LOGGER.info("[EvMod] Removed dummy player (real player spawned): "+packet.getUUID());
		}
	}
}