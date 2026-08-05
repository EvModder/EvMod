package net.evmodder.evmod.mixin;

//? >=26.1 {
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.evmodder.evmod.render.StaticEntityRenderCache;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.entity.Entity;

@Mixin(LevelExtractor.class)
abstract class MixinLevelExtractor{
	@Inject(method="isEntityVisible", at=@At("HEAD"), cancellable=true)
	private void includeStaticEntitiesOnExtendedTerrain(final Entity entity, final Frustum frustum,
			final double cameraX, final double cameraY, final double cameraZ,
			final CallbackInfoReturnable<Boolean> cir){
		if(StaticEntityRenderCache.shouldRenderInExtendedTerrain(entity, frustum, cameraX, cameraY, cameraZ)) cir.setReturnValue(true);
	}
}
//?}