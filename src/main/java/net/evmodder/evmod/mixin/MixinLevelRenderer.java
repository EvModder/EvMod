package net.evmodder.evmod.mixin;

//? <26.1 {
/*import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import net.evmodder.evmod.render.StaticEntityRenderCache;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.Entity;

@Mixin(LevelRenderer.class)
abstract class MixinLevelRenderer{
	//? 1.21.11 {
	@ModifyExpressionValue(method="extractVisibleEntities",
			at=@At(value="INVOKE", target="Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;shouldRender(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z"))
	//?} else {
	/^@ModifyExpressionValue(method="collectVisibleEntities",
			at=@At(value="INVOKE", target="Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;shouldRender(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z"))
	^///?}
	private boolean includeStaticEntitiesOnExtendedTerrain(final boolean original, @Local final Entity entity, @Local(argsOnly=true) final Frustum frustum){
		return original || StaticEntityRenderCache.shouldRenderInExtendedTerrain(entity, frustum, frustum.getCamX(), frustum.getCamY(), frustum.getCamZ());
	}

	//? 1.21.11 {
	@ModifyExpressionValue(method="extractVisibleEntities",
			at=@At(value="INVOKE", target="Lnet/minecraft/client/renderer/LevelRenderer;isSectionCompiledAndVisible(Lnet/minecraft/core/BlockPos;)Z"))
	//?} else {
	/^@ModifyExpressionValue(method="collectVisibleEntities",
			at=@At(value="INVOKE", target="Lnet/minecraft/client/renderer/LevelRenderer;isSectionCompiled(Lnet/minecraft/core/BlockPos;)Z"))
	^///?}
	private boolean includeExtendedTerrainSection(final boolean original, @Local final Entity entity, @Local(argsOnly=true) final Frustum frustum){
		return original || StaticEntityRenderCache.hasExtendedTerrainAt(entity, frustum.getCamX(), frustum.getCamZ());
	}
}
*///?}