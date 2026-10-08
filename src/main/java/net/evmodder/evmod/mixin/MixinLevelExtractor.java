package net.evmodder.evmod.mixin;

//? >=26.1 {
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import net.evmodder.evmod.render.StaticEntityRenderCache;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.entity.Entity;

@Mixin(LevelExtractor.class)
abstract class MixinLevelExtractor{
	//? >=26.3 {
	@ModifyExpressionValue(method="isEntityVisible", at=@At(value="INVOKE", target="Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;shouldRender(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDDF)Z"))
	//?} else {
	/*@ModifyExpressionValue(method="isEntityVisible", at=@At(value="INVOKE", target="Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;shouldRender(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z"))
	*///?}
	private boolean includeStaticEntitiesOnExtendedTerrain(final boolean original, final Entity entity, final Frustum frustum,
			//? >=26.3 {
			final double cameraX, final double cameraY, final double cameraZ, final float partialTick, final long frameTime){
			//?} else {
			/*final double cameraX, final double cameraY, final double cameraZ){
			*///?}
		return original || StaticEntityRenderCache.shouldRenderInExtendedTerrain(entity, frustum, cameraX, cameraY, cameraZ);
	}

	//? >=26.3 {
	@ModifyExpressionValue(method="isEntityVisible", at=@At(value="INVOKE", target="Lnet/minecraft/client/renderer/LevelRenderer;isSectionCompiledAndVisible(Lnet/minecraft/core/BlockPos;J)Z"))
	//?} else {
	/*@ModifyExpressionValue(method="isEntityVisible", at=@At(value="INVOKE", target="Lnet/minecraft/client/renderer/LevelRenderer;isSectionCompiledAndVisible(Lnet/minecraft/core/BlockPos;)Z"))
	*///?}
	private boolean includeExtendedTerrainSection(final boolean original, final Entity entity, final Frustum frustum,
			//? >=26.3 {
			final double cameraX, final double cameraY, final double cameraZ, final float partialTick, final long frameTime){
			//?} else {
			/*final double cameraX, final double cameraY, final double cameraZ){
			*///?}
		return original || StaticEntityRenderCache.hasExtendedTerrainAt(entity, cameraX, cameraZ);
	}
}
//?}