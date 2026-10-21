package net.evmodder.evmod.mixin;

import net.evmodder.evmod.apis.MapGroupUtils;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.Main;
import net.evmodder.evmod.apis.MapColorUtils;
import net.evmodder.evmod.apis.MapStateCacher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
abstract class MixinClientPacketListenerMaps{
	@Inject(method="handleMapItemData", at=@At("TAIL"))
	private void updateMapState(final ClientboundMapItemDataPacket packet, final CallbackInfo _ci){
		final MapItemSavedData state = Minecraft.getInstance().level.getMapData(packet.mapId());
		assert state != null && state.colors.length == 128*128;
		final int id = packet.mapId().id();
		if(AccessorMapGroupUtils.callMarkMapLoaded(id))
			if(MapColorUtils.isFullyTransparent(state.colors))
				Main.LOGGER.warn("MixinClientPacketListener: fully transparent map state! id="+id);
		MapGroupUtils.markMapAvailable(id);
		assert !MapStateCacher.hasCacheMarker(state);
		if(Configs.Database.MAP_CACHE_BY_ID.getBooleanValue()) MapStateCacher.addMapStateById(id, state);
	}
}