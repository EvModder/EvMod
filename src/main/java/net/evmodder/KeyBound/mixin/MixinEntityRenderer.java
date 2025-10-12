package net.evmodder.KeyBound.mixin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Timer;
import java.util.TimerTask;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.evmodder.KeyBound.EpearlLookup.XYZ;
import net.evmodder.KeyBound.Main;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

@Mixin(EntityRenderer.class)
abstract class MixinEntityRenderer{
//	@Final @Shadow protected EntityRenderDispatcher dispatcher;
//	@Shadow public abstract TextRenderer getTextRenderer();

	private static final MinecraftClient client = MinecraftClient.getInstance();
	private static final HashMap<XYZ, HashMap<String, HashSet<Integer>>> pearlsAtXYZ = new HashMap<>();
	private static long renderedOnTick = 0;
	private static long lastRenderedId;
	private static long lastClear = 0;

	private boolean isLookngAt(Entity entity){
		Vec3d vec3d = client.player.getRotationVec(1.0F).normalize();
		Vec3d vec3d2 = new Vec3d(entity.getX() - client.player.getX(), entity.getEyeY() - client.player.getEyeY(), entity.getZ() - client.player.getZ());
		double d = vec3d2.length();
		vec3d2 = new Vec3d(vec3d2.x / d, vec3d2.y / d, vec3d2.z / d);//normalize
		double e = vec3d.dotProduct(vec3d2);
		return e > 1.0D - 0.03D / d ? /*client.player.canSee(entity)*/true : false;
	}

	// TODO: mixin onTick instead of hasLabel, or setName somehow
	@Inject(method = "hasLabel", at = @At("HEAD"), cancellable = true)
	public void test(Entity e, double squaredDistanceToCamera, CallbackInfoReturnable<Boolean> cir){
		if(Main.epearlLookup == null) return; // Feature is disabled
		if(client.options.hudHidden) return; // HUD is hidden
		if(e instanceof ProjectileEntity == false) return;
		if(e.getType() != EntityType.ENDER_PEARL) return;
		//if(!isLookngAt(entity)) return;
		String name = Main.epearlLookup.getOwnerName((ProjectileEntity)e);
		if(name == null) return;
		//----------
		XYZ xyz = new XYZ(e.getBlockX(), e.getBlockY()/4, e.getBlockZ());
		HashMap<String, HashSet<Integer>> pearls = pearlsAtXYZ.get(xyz);
		if(pearls == null){
			xyz = new XYZ(xyz.x(), xyz.y()+1, xyz.z());//try one block above
			pearls = pearlsAtXYZ.get(xyz);
			if(pearls == null){
				xyz = new XYZ(xyz.x(), xyz.y()-2, xyz.z());//try one block below
				pearls = pearlsAtXYZ.get(xyz);
				if(pearls == null){
					xyz = new XYZ(xyz.x(), xyz.y()+1, xyz.z());//restore to original Y and add pearl
					pearls = new HashMap<>(1);
					if(pearlsAtXYZ.isEmpty()){
						new Timer().scheduleAtFixedRate(new TimerTask(){@Override public void run(){
							if(client.world == null || client.world.getTime() - renderedOnTick > 100){
								pearlsAtXYZ.clear();
								//lastClear = client.world.getTime();
								cancel();
							}
						}}, 10_000L, 10_000L);
					}
					pearlsAtXYZ.put(xyz, pearls);
					//KeyBound.LOGGER.info("Couldn't find pearl set at XZ: "+xyz.x()+","+xyz.z());
				}
				//else KeyBound.LOGGER.info("Found pearl set at XZ: "+xyz.x()+","+xyz.z());
			}
		}
		HashSet<Integer> pearlsForName = pearls.get(name);
		if(pearlsForName == null){pearlsForName = new HashSet<>(1); pearls.put(name, pearlsForName);}
		pearlsForName.add(e.getId());
		final boolean alreadyRenderedThisTick = renderedOnTick == client.world.getTime();
		if(alreadyRenderedThisTick && e.getId() != lastRenderedId) return;
		if(!isLookngAt(e)) return;
		renderedOnTick = client.world.getTime();
		lastRenderedId = e.getId();
		//if(pearlsForName.iterator().next() != e.getId()) return; // Only render the name for 1 pearl in a stack
		if(pearlsForName.size() > 1){
			name += " x"+pearlsForName.size();
			// Only clear the list in sub-tick, since we can safely assume entities will be rendered (and hence readded) in the same order for the same world tick.
			if(alreadyRenderedThisTick && lastClear != client.world.getTime()){
				pearlsAtXYZ.clear();
				lastClear = client.world.getTime();
			}
		}
		//----------
		e.setCustomName(Text.literal(name));
		if(!isLookngAt(e)) return;
		cir.setReturnValue(true);
//		cir.cancel();
	}
}