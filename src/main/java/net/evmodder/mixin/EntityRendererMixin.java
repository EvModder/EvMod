package net.evmodder.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.evmodder.KeyBound;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.text.Text;
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

	@Inject(method = "hasLabel", at = @At("HEAD"), cancellable = true)
	public void test(Entity e, double squaredDistanceToCamera, CallbackInfoReturnable<Boolean> cir){
		if(KeyBound.epearlLookup == null) return; // Feature is disabled
		if(client.options.hudHidden) return; // HUD is hidden
		if(e instanceof ProjectileEntity == false) return;
		if(e.getType() != EntityType.ENDER_PEARL) return;
		//if(!isLookngAt(entity)) return;
		String name = KeyBound.epearlLookup.getOwnerName((ProjectileEntity)e);
		if(name == null) return;
		e.setCustomName(Text.literal(name));
		if(!isLookngAt(e)) return;
		cir.setReturnValue(true);
		cir.cancel();
	}
}