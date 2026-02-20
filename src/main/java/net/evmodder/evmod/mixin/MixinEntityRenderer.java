package net.evmodder.evmod.mixin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Timer;
import java.util.TimerTask;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.evmodder.evmod.apis.EpearlLookup.XYZ;
import net.evmodder.evmod.apis.EpearlLookupFabric;
import net.evmodder.evmod.apis.MiscUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.text.Text;

@Mixin(EntityRenderer.class)
abstract class MixinEntityRenderer{
	private final MinecraftClient client = MinecraftClient.getInstance();
	private final HashMap<XYZ, HashMap<String, HashSet<Integer>>> pearlsAtXYZ = new HashMap<>();
	private long renderedOnTick = 0;
	private long lastRenderedId;
	private long lastClear = 0;

	// TODO: mixin onTick instead of hasLabel, or setName somehow
	@Inject(method="hasLabel", at=@At("HEAD"), cancellable=true)
	private final void fetchPearlOwnerNameInHasLabel_shouldDoThisInOnTickTBH(Entity e, double _distSqToCamera, CallbackInfoReturnable<Boolean> cir){
		if(client.options.hudHidden) return; // HUD is hidden
		if(e instanceof EnderPearlEntity == false) return;
		final EpearlLookupFabric eplf = AccessorMain.getInstance().epearlLookup;
		if(eplf == null || eplf.isDisabled()) return; // Feature is disabled

		String name = eplf.getOwnerName((EnderPearlEntity)e);
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
					//Main.LOGGER.info("Couldn't find pearl set at XZ: "+xyz.x()+","+xyz.z());
				}
				//else Main.LOGGER.info("Found pearl set at XZ: "+xyz.x()+","+xyz.z());
			}
		}
		final HashSet<Integer> pearlsForName = pearls.computeIfAbsent(name, _k->new HashSet<>(1));
		pearlsForName.add(e.getId());
		final boolean alreadyRenderedThisTick = renderedOnTick == client.world.getTime();
		if(alreadyRenderedThisTick && e.getId() != lastRenderedId) return;
		if(!MiscUtils.isLookingAt(e, client.player)) return;
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
		if(!MiscUtils.isLookingAt(e, client.player)) return;
		cir.setReturnValue(true);
//		cir.cancel();
	}
}