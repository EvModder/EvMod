package net.evmodder.mixin;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.evmodder.KeyBound;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityAttachmentType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.util.Colors;
import net.minecraft.util.math.Vec3d;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin{
	@Final @Shadow protected EntityRenderDispatcher dispatcher;
	@Shadow public abstract TextRenderer getTextRenderer();

	private static final MinecraftClient client = MinecraftClient.getInstance();

	private boolean isLookngAt(Entity entity){
		Vec3d vec3d = client.player.getRotationVec(1.0F).normalize();
		Vec3d vec3d2 = new Vec3d(entity.getX() - client.player.getX(), entity.getEyeY() - client.player.getEyeY(), entity.getZ() - client.player.getZ());
		double d = vec3d2.length();
		vec3d2 = new Vec3d(vec3d2.x / d, vec3d2.y / d, vec3d2.z / d);//normalize
		double e = vec3d.dotProduct(vec3d2);
		return e > 1.0D - 0.03D / d ? /*client.player.canSee(entity)*/true : false;
	}

	//@Inject(method = "renderLabelIfPresent", at = @At("TAIL"))
	//@Inject(method = "hasLabel", at = @At("TAIL"))
	@Inject(method = "render", at = @At("HEAD"))
	public void render(Entity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci){
		if(KeyBound.epearlLookup == null) return; // Feature is disabled
		if(client.options.hudHidden) return; // HUD is hidden
		if(entity instanceof ProjectileEntity == false) return;
		if(entity.getType() != EntityType.ENDER_PEARL) return;
		//if(!isLookngAt(entity)) return;
		String name = KeyBound.epearlLookup.getOwnerName((ProjectileEntity)entity);
		if(!isLookngAt(entity)) return;

		//((EntityRendererInvoker)(Object)this).renderLabelIfPresent(entity, Text.literal(name).formatted(Formatting.GRAY), matrices, vertexConsumers, light);

		Vec3d vec3d = entity.getAttachments().getPointNullable(EntityAttachmentType.NAME_TAG, 0, entity.getYaw(tickDelta));
//		if(vec3d == null){
//			vec3d = pe.getPos();
//			KeyBound.LOGGER.warn("BigFatTest no attachement for epearl");
//		}
//		else KeyBound.LOGGER.warn("BigFatTest all good in the hood");
		matrices.push();
		matrices.translate(vec3d.x, vec3d.y + 0.5, vec3d.z);
		matrices.multiply(dispatcher.getRotation());//TODO: how is dispatcher not null at this poin?!! lol
		matrices.scale(0.025F, -0.025F, 0.025F);
		Matrix4f matrix4f = matrices.peek().getPositionMatrix();
		TextRenderer textRenderer = this.getTextRenderer();
		float x = (float) (-textRenderer.getWidth(name) / 2);

		float backgroundOpacity = client.options.getTextBackgroundOpacity(0.25F);
		int backgroundColor = (int) (backgroundOpacity * 255.0F) << 24;

		textRenderer.draw(name, x, 10f, 553648127, false, matrix4f, vertexConsumers, TextRenderer.TextLayerType.SEE_THROUGH, backgroundColor, light);
		textRenderer.draw(name, x, 10f, Colors.WHITE, false, matrix4f, vertexConsumers, TextRenderer.TextLayerType.NORMAL, 0, light);
		matrices.pop();
	}
}