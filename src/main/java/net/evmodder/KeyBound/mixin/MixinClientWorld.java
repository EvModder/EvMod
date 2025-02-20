package net.evmodder.KeyBound.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.evmodder.KeyBound.Main;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.item.map.MapState;

@Mixin(ClientWorld.class)
public abstract class MixinClientWorld{

	@Inject(method="putClientsideMapState", at=@At("HEAD"))
	private void e(MapIdComponent id, MapState state, CallbackInfo ci){
//		Main.LOGGER.info("MapDatabase: storing mapstate for id "+id.id()
//				+": xz:"+state.centerX+","+state.centerZ+" scale:"+state.scale
//				+" hasDecor:"+state.getDecorations().iterator().hasNext()
//				+" locked:"+state.locked
//				+" dim:"+state.dimension.getValue().getPath()
//				+" colors: "+new String(state.colors));
		//TODO: send to database (tie to clientId)
		//TODO: see if possible to pre-load MapState when joining 2b2t
		//if(MapHandRestock.isEnabled) MapHandRestock.onProcessRightClickPre(player, hand);
	}
}