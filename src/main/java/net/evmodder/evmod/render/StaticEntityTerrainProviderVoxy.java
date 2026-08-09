package net.evmodder.evmod.render;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.evmodder.evmod.Main;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.multiplayer.ClientLevel;

final class StaticEntityTerrainProviderVoxy implements StaticEntityTerrainProvider{
	// Voxy stores its configured radius in 512-block top-level LoD sections.
	private static final double TOP_LEVEL_SECTION_SIZE = 512;

	// Voxy's exact visible-section selection is GPU-side. An active renderer plus its configured
	// radius is the closest CPU-side coverage signal; normal frustum/depth tests still cull entities.
	private final Method getRenderSystemMethod;
	private final Field configInstanceField;
	private final Field renderDistanceField;
	private boolean compatible;
	private boolean active;
	private double renderDistanceSq;

	StaticEntityTerrainProviderVoxy(){
		Method getRenderSystemMethod = null;
		Field configInstanceField = null, renderDistanceField = null;
		if(FabricLoader.getInstance().isModLoaded("voxy")){
			try{
				final ClassLoader loader = StaticEntityTerrainProviderVoxy.class.getClassLoader();
				final Class<?> holderClass = Class.forName("me.cortex.voxy.client.core.IVoxyRenderSystemHolder", false, loader);
				final Class<?> configClass = Class.forName("me.cortex.voxy.client.config.VoxyConfig", false, loader);
				getRenderSystemMethod = holderClass.getMethod("getNullable");
				configInstanceField = configClass.getField("CONFIG");
				renderDistanceField = configClass.getField("sectionRenderDistance");
				compatible = true;
			}
			catch(final ReflectiveOperationException | LinkageError ex){
				Main.LOGGER.warn("Voxy static-entity terrain integration is unavailable: {}", ex.toString());
			}
		}
		this.getRenderSystemMethod = getRenderSystemMethod;
		this.configInstanceField = configInstanceField;
		this.renderDistanceField = renderDistanceField;
	}

	@Override public final void tick(){
		if(!compatible) return;
		try{
			final Object renderSystem = getRenderSystemMethod.invoke(null);
			if(renderSystem == null){
				active = false;
				return;
			}
			final Object configInstance = configInstanceField.get(null);
			final double distance = ((Number)renderDistanceField.get(configInstance)).doubleValue()*TOP_LEVEL_SECTION_SIZE;
			active = distance > 0;
			renderDistanceSq = distance*distance;
		}
		catch(final ReflectiveOperationException | RuntimeException | LinkageError ex){
			compatible = active = false;
			Main.LOGGER.warn("Disabling Voxy static-entity terrain integration: {}", ex.toString());
		}
	}

	@Override public final boolean hasTerrainAt(
			final ClientLevel level, final int blockX, final int blockZ,
			final double cameraX, final double cameraZ){
		if(!active) return false;
		final double dx = blockX+0.5-cameraX, dz = blockZ+0.5-cameraZ;
		return dx*dx + dz*dz <= renderDistanceSq;
	}

	@Override public final boolean hasTerrainInChunk(
			final ClientLevel level, final int chunkX, final int chunkZ,
			final double cameraX, final double cameraZ){
		if(!active) return false;
		final double minX = (chunkX << 4)+0.5, minZ = (chunkZ << 4)+0.5;
		final double nearestX = Math.max(minX, Math.min(cameraX, minX+15));
		final double nearestZ = Math.max(minZ, Math.min(cameraZ, minZ+15));
		final double dx = nearestX-cameraX, dz = nearestZ-cameraZ;
		return dx*dx + dz*dz <= renderDistanceSq;
	}

	@Override public final boolean extendsVanillaSectionVisibility(){return true;}
}