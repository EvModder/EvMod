package net.evmodder.evmod.mixin;

import net.evmodder.evmod.Configs;
import net.evmodder.evmod.apis.MapStateCacher;
import net.evmodder.evmod.config.OptionMapStateCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientPacketListener.class)
abstract class MixinClientPacketListenerMapCache{
	// Saw this in https://github.com/red-stoned/client_maps/, and realized it's probably good to incorporate
	@Redirect(method="handleMapItemData", at=@At(value="INVOKE",
			target="Lnet/minecraft/client/multiplayer/ClientLevel;getMapData(Lnet/minecraft/world/level/saveddata/maps/MapId;)Lnet/minecraft/world/level/saveddata/maps/MapItemSavedData;"))
	private final MapItemSavedData replaceIfClientMaps(ClientLevel instance, MapId id){
		final MapItemSavedData s = instance.getMapData(id);
		if(Configs.Database.MAP_CACHE.getOptionListValue() != OptionMapStateCache.MEMORY_AND_DISK) return s;
		return s == null || MapStateCacher.hasCacheMarker(s) ? null : s;
	}
}