package net.evmodder.evmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.evmodder.evmod.render.StaticEntityRenderCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;

@Mixin(ClientLevel.class)
abstract class MixinClientLevel{
	@Inject(method="entitiesForRendering", at=@At("RETURN"), cancellable=true)
	private void includeCachedStaticEntities(final CallbackInfoReturnable<Iterable<Entity>> cir){
		cir.setReturnValue(StaticEntityRenderCache.includeCachedEntities((ClientLevel)(Object)this, cir.getReturnValue()));
	}
}