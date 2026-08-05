package net.evmodder.evmod.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import com.google.common.collect.Iterables;
import net.evmodder.evmod.Configs;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.phys.AABB;

public final class StaticEntityRenderCache{
	private static final double MIN_CACHE_DISTANCE = 32;
	private static final double TRACKING_RANGE_MARGIN = 16;
	private static final long REVALIDATE_AFTER_TICKS = 20;
	private static final long TERRAIN_LOSS_GRACE_TICKS = 5;

	private static final HashMap<UUID, CachedEntity> cachedByUuid = new HashMap<>();
	private static List<Entity> renderSnapshot = List.of();
	private static ClientLevel level;
	private static boolean initialized;

	private static final class CachedEntity{
		private final Entity entity;
		private final double revalidationDistanceSq;
		private long revalidationStartTick = -1;
		private long terrainMissingStartTick = -1;

		private CachedEntity(final Entity entity, final double unloadDistance){
			this.entity = entity;
			final double revalidationDistance = Math.max(MIN_CACHE_DISTANCE, unloadDistance - TRACKING_RANGE_MARGIN);
			revalidationDistanceSq = revalidationDistance*revalidationDistance;
		}
	}

	private StaticEntityRenderCache(){}

	public static final void init(){
		if(initialized) return;
		initialized = true;
		ClientEntityEvents.ENTITY_LOAD.register(StaticEntityRenderCache::onEntityLoad);
		ClientEntityEvents.ENTITY_UNLOAD.register(StaticEntityRenderCache::onEntityUnload);
		ClientTickEvents.END_CLIENT_TICK.register(StaticEntityRenderCache::onClientTick);
	}

	private static final boolean isCacheable(final Entity entity){return entity instanceof ItemFrame;}

	private static final boolean isWithinConfiguredRange(final double distanceSq){
		final int configuredRange = Configs.Visuals.STATIC_ENTITY_RENDER_RANGE.getIntegerValue();
		return distanceSq <= (double)configuredRange*configuredRange;
	}

	public static final boolean useConfiguredRenderDistance(
			final boolean original, final Entity entity, final double cameraX, final double cameraY, final double cameraZ){
		return isCacheable(entity) ? isWithinConfiguredRange(entity.distanceToSqr(cameraX, cameraY, cameraZ)) : original;
	}

	public static final boolean shouldRenderInExtendedTerrain(
			final Entity entity, final Frustum frustum, final double cameraX, final double cameraY, final double cameraZ){
		if(!isCacheable(entity) || !isWithinConfiguredRange(entity.distanceToSqr(cameraX, cameraY, cameraZ))) return false;
		final Minecraft client = Minecraft.getInstance();
		if(client.level == null || entity.level() != client.level
				|| !StaticEntityTerrainProviderRegistry.hasExtendedTerrainAt(
						client.level, entity.getBlockX(), entity.getBlockZ(), cameraX, cameraZ)) return false;

		AABB boundingBox = entity.getBoundingBox().inflate(0.5);
		if(boundingBox.hasNaN() || boundingBox.getSize() == 0){
			boundingBox = new AABB(entity.getX()-2, entity.getY()-2, entity.getZ()-2, entity.getX()+2, entity.getY()+2, entity.getZ()+2);
		}
		return frustum.isVisible(boundingBox);
	}

	public static final boolean hasExtendedTerrainAt(final Entity entity, final double cameraX, final double cameraZ){
		final Minecraft client = Minecraft.getInstance();
		return isCacheable(entity) && client.level != null && entity.level() == client.level
				&& StaticEntityTerrainProviderRegistry.hasExtendedTerrainAt(
						client.level, entity.getBlockX(), entity.getBlockZ(), cameraX, cameraZ);
	}

	public static final Iterable<Entity> includeCachedEntities(final ClientLevel clientLevel, final Iterable<Entity> loadedEntities){
		if(level != clientLevel) reset(clientLevel);
		return renderSnapshot.isEmpty() ? loadedEntities : Iterables.concat(loadedEntities, renderSnapshot);
	}

	private static final void onEntityLoad(final Entity entity, final ClientLevel clientLevel){
		if(!isCacheable(entity)) return;
		if(level != clientLevel) reset(clientLevel);
		remove(entity.getUUID());
	}

	private static final void onEntityUnload(final Entity entity, final ClientLevel clientLevel){
		if(!isCacheable(entity)) return;
		if(level != clientLevel) reset(clientLevel);

		final Minecraft client = Minecraft.getInstance();
		final LocalPlayer player = client.player;
		if(player == null) return;
		final double unloadDistanceSq = player.distanceToSqr(entity);
		if(unloadDistanceSq < MIN_CACHE_DISTANCE*MIN_CACHE_DISTANCE){
			remove(entity.getUUID());
			return;
		}

		cachedByUuid.put(entity.getUUID(), new CachedEntity(entity, Math.sqrt(unloadDistanceSq)));
		refreshSnapshot();
	}

	private static final void onClientTick(final Minecraft client){
		final ClientLevel clientLevel = client.level;
		final LocalPlayer player = client.player;
		if(clientLevel == null || player == null){
			if(level != null) reset(null);
			return;
		}
		StaticEntityTerrainProviderRegistry.tick();
		if(level != clientLevel) reset(clientLevel);
		if(cachedByUuid.isEmpty()) return;

		final long tick = clientLevel.getGameTime();
		final double playerX = player.getX(), playerZ = player.getZ();
		boolean changed = false;
		for(final var it = cachedByUuid.entrySet().iterator(); it.hasNext();){
			final CachedEntity cached = it.next().getValue();
			if(!StaticEntityTerrainProviderRegistry.hasTerrainAt(
					clientLevel, cached.entity.getBlockX(), cached.entity.getBlockZ(),
					playerX, playerZ)){
				cached.revalidationStartTick = -1;
				if(cached.terrainMissingStartTick == -1) cached.terrainMissingStartTick = tick;
				else if(tick - cached.terrainMissingStartTick >= TERRAIN_LOSS_GRACE_TICKS){
					it.remove();
					changed = true;
				}
				continue;
			}
			cached.terrainMissingStartTick = -1;

			if(player.distanceToSqr(cached.entity) > cached.revalidationDistanceSq){
				cached.revalidationStartTick = -1;
				continue;
			}
			if(cached.revalidationStartTick == -1){
				cached.revalidationStartTick = tick;
				continue;
			}
			if(tick - cached.revalidationStartTick < REVALIDATE_AFTER_TICKS) continue;

			it.remove();
			changed = true;
		}
		if(changed) refreshSnapshot();
	}

	private static final void remove(final UUID uuid){
		if(cachedByUuid.remove(uuid) != null) refreshSnapshot();
	}

	private static final void reset(final ClientLevel clientLevel){
		level = clientLevel;
		cachedByUuid.clear();
		renderSnapshot = List.of();
	}

	private static final void refreshSnapshot(){
		if(cachedByUuid.isEmpty()){
			renderSnapshot = List.of();
			return;
		}
		final ArrayList<Entity> snapshot = new ArrayList<>(cachedByUuid.size());
		for(final CachedEntity cached : cachedByUuid.values()) snapshot.add(cached.entity);
		renderSnapshot = snapshot;
	}
}