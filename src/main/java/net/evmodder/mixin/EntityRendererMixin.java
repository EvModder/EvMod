package net.evmodder.mixin;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.mojang.authlib.GameProfile;
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
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin{
	@Final @Shadow protected EntityRenderDispatcher dispatcher;

	@Shadow public abstract TextRenderer getTextRenderer();

	private static final LoadingCache<UUID, String> usernameCache =
			CacheBuilder.newBuilder()
			.expireAfterWrite(6, TimeUnit.HOURS)
			.build(new CacheLoader<>(){@Override public String load(UUID key){
					new Thread(() -> {
						GameProfile playerProfile;
						try{
							playerProfile = MinecraftClient.getInstance().getSessionService().fetchProfile(key, false).profile();
							if(playerProfile == null) usernameCache.put(key, "[404]");
							usernameCache.put(key, playerProfile.getName());
						}
						catch(NullPointerException e){usernameCache.put(key, "[404]");}
					}).start();
					return "Loading...";
			}});


	//@Inject(method = "renderLabelIfPresent", at = @At("TAIL"))
	//@Inject(method = "hasLabel", at = @At("TAIL"))

	MinecraftClient client = MinecraftClient.getInstance();

	@Inject(method = "render", at = @At("HEAD"))
	public void render(Entity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci){
		if(client.options.hudHidden) return; //If HUD is hidden
		if(entity instanceof ProjectileEntity == false) return;
		final ProjectileEntity pe = (ProjectileEntity)entity;
		if(pe.getType() != EntityType.ENDER_PEARL) return;
		KeyBound.LOGGER.warn("target entity:" +(client.targetedEntity != null ? client.targetedEntity.getUuidAsString() : "null"));
		KeyBound.LOGGER.warn("crosshair target:" +client.crosshairTarget.getType().name());
		if(client.crosshairTarget.squaredDistanceTo(entity) > 1d) return;
		final UUID uuid = ((ProjectileEntityAccessor)pe).getOwnerUUID();
		final String name = usernameCache.getUnchecked(uuid);

		//((EntityRendererInvoker)(Object)this).renderLabelIfPresent(entity, Text.literal(name).formatted(Formatting.GRAY), matrices, vertexConsumers, light);

		Vec3d vec3d = pe.getAttachments().getPointNullable(EntityAttachmentType.NAME_TAG, 0, entity.getYaw(tickDelta));
//		if(vec3d == null){
//			vec3d = pe.getPos();
//			KeyBound.LOGGER.warn("BigFatTest no attachement for epearl");
//		}
//		else KeyBound.LOGGER.warn("BigFatTest all good in the hood");
		matrices.push();
		matrices.translate(vec3d.x, vec3d.y + 0.5, vec3d.z);
		matrices.multiply(dispatcher.getRotation());
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