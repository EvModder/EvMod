package net.evmodder.evmod.mixin;

import static net.evmodder.evmod.compat.MinecraftCompat.isHudHidden;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import net.evmodder.evmod.render.StaticEntityRenderCache;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;

@Mixin(EntityRenderer.class)
abstract class MixinEntityRendererStatic{
	@ModifyExpressionValue(method="shouldRender", at=@At(value="INVOKE", target="Lnet/minecraft/world/entity/Entity;shouldRender(DDD)Z"))
	private boolean applyConfiguredStaticEntityRenderRange(final boolean original, final Entity entity, final Frustum frustum,
			//? >=26.3 {
			final double cameraX, final double cameraY, final double cameraZ, final float partialTick){
			//?} else {
			/*final double cameraX, final double cameraY, final double cameraZ){
			*/  //?}
		return StaticEntityRenderCache.useConfiguredRenderDistance(original, entity, cameraX, cameraY, cameraZ);
	}
}