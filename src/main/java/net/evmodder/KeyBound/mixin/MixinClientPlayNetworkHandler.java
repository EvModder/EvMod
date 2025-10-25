package net.evmodder.KeyBound.mixin;

import net.evmodder.KeyBound.apis.MapStateCacher;
import net.evmodder.KeyBound.config.Configs;
import net.evmodder.KeyBound.config.MapStateCacheOption;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.item.map.MapState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientPlayNetworkHandler.class)
abstract class MixinClientPlayNetworkHandler{
	// Saw this in https://github.com/red-stoned/client_maps/, and realized it's probably good to incorporate
	@Redirect(method="onMapUpdate", at=@At(value="INVOKE", target=
			"Lnet/minecraft/client/world/ClientWorld;getMapState(Lnet/minecraft/component/type/MapIdComponent;)Lnet/minecraft/item/map/MapState;"))
	private MapState replaceIfClientMaps(ClientWorld instance, MapIdComponent id){
		final MapState s = instance.getMapState(id);
		if(Configs.Generic.MAP_STATE_CACHE.getOptionListValue() != MapStateCacheOption.MEMORY_AND_DISK) return s;
		return s == null || MapStateCacher.hasCacheMarker(s) ? null : s;
	}
}