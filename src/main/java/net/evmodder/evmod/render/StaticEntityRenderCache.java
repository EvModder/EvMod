package net.evmodder.evmod.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.UUID;
import com.google.common.collect.Iterables;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
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
	private static final long TERRAIN_LOSS_GRACE_TICKS = 5;
	private static final long TERRAIN_SWEEP_INTERVAL_TICKS = 20;
	private static final long UNSET_TICK = Long.MIN_VALUE;

	private static final HashMap<UUID, CachedEntity> cachedByUuid = new HashMap<>();
	private static final Long2ObjectOpenHashMap<CachedChunk> cachedByChunk = new Long2ObjectOpenHashMap<>();
	private static final HashMap<UUID, Entity> loadedByUuid = new HashMap<>();
	private static final Set<Entity> selectedLoadedEntities = Collections.newSetFromMap(new IdentityHashMap<>());
	private static final NearestEntityCollector nearestEntityCollector = new NearestEntityCollector();
	private static final ArrayList<Entity> renderSnapshot = new ArrayList<>();
	private static ClientLevel level;
	private static boolean initialized, loadedIndexInitialized, limitApplied, snapshotDirty;
	private static int renderRange, renderLimit;
	private static double renderRangeSq;
	private static long lastTerrainSweepTick = UNSET_TICK;

	private static final class CachedEntity{
		private final Entity entity;
		private final CachedChunk chunk;
		private int chunkIndex;

		private CachedEntity(final Entity entity, final CachedChunk chunk){
			this.entity = entity;
			this.chunk = chunk;
			chunkIndex = chunk.entities.size();
			chunk.entities.add(this);
		}
	}

	private static final class CachedChunk{
		private final long key;
		private final int chunkX, chunkZ;
		private final ArrayList<CachedEntity> entities = new ArrayList<>();
		private long lastTerrainCheckTick = UNSET_TICK, terrainMissingStartTick = UNSET_TICK;
		private boolean hasTerrain = true;

		private CachedChunk(final long key, final int chunkX, final int chunkZ){
			this.key = key;
			this.chunkX = chunkX;
			this.chunkZ = chunkZ;
		}

		private void remove(final CachedEntity cached){
			final int lastIndex = entities.size()-1;
			final CachedEntity moved = entities.remove(lastIndex);
			if(cached.chunkIndex != lastIndex){
				entities.set(cached.chunkIndex, moved);
				moved.chunkIndex = cached.chunkIndex;
			}
			cached.chunkIndex = -1;
		}
	}

	private static final class NearestEntityCollector{
		private Entity[] entities = new Entity[0];
		private double[] distancesSq = new double[0];
		private boolean[] cached = new boolean[0];
		private int capacity, size;

		private void reset(final int requestedCapacity){
			capacity = requestedCapacity;
			size = 0;
			if(entities.length >= capacity) return;
			entities = new Entity[capacity];
			distancesSq = new double[capacity];
			cached = new boolean[capacity];
		}

		private void offer(final Entity entity, final double distanceSq, final boolean isCached){
			if(size < capacity){
				int child = size++;
				while(child > 0){
					final int parent = (child-1) >> 1;
					if(distanceSq <= distancesSq[parent]) break;
					entities[child] = entities[parent];
					distancesSq[child] = distancesSq[parent];
					cached[child] = cached[parent];
					child = parent;
				}
				entities[child] = entity;
				distancesSq[child] = distanceSq;
				cached[child] = isCached;
				return;
			}
			if(distanceSq >= distancesSq[0]) return;

			int parent = 0;
			while(true){
				final int left = parent*2+1;
				if(left >= size) break;
				final int right = left+1;
				final int child = right < size && distancesSq[right] > distancesSq[left] ? right : left;
				if(distancesSq[child] <= distanceSq) break;
				entities[parent] = entities[child];
				distancesSq[parent] = distancesSq[child];
				cached[parent] = cached[child];
				parent = child;
			}
			entities[parent] = entity;
			distancesSq[parent] = distanceSq;
			cached[parent] = isCached;
		}

		private void appendTo(final Set<Entity> loadedEntities, final ArrayList<Entity> cachedEntities){
			for(int i=0; i<size; ++i){
				if(cached[i]) cachedEntities.add(entities[i]);
				else loadedEntities.add(entities[i]);
				entities[i] = null;
			}
		}

		private void clear(){
			entities = new Entity[0];
			distancesSq = new double[0];
			cached = new boolean[0];
			capacity = size = 0;
		}
	}

	private StaticEntityRenderCache(){}

	public static final void init(){
		if(initialized) return;
		initialized = true;
		updateConfiguredSettings();
		Configs.Visuals.STATIC_ENTITY_RENDER_RANGE.setValueChangeCallback(_->updateConfiguredSettings());
		Configs.Visuals.STATIC_ENTITY_RENDER_LIMIT.setValueChangeCallback(_->updateConfiguredSettings());
		ClientEntityEvents.ENTITY_LOAD.register(StaticEntityRenderCache::onEntityLoad);
		ClientEntityEvents.ENTITY_UNLOAD.register(StaticEntityRenderCache::onEntityUnload);
		ClientTickEvents.END_CLIENT_TICK.register(StaticEntityRenderCache::onClientTick);
	}

	private static final boolean isCacheable(final Entity entity){return entity instanceof ItemFrame;}

	private static final void updateConfiguredSettings(){
		final int configuredRange = Configs.Visuals.STATIC_ENTITY_RENDER_RANGE.getIntegerValue();
		final int configuredLimit = Configs.Visuals.STATIC_ENTITY_RENDER_LIMIT.getIntegerValue();
		if(configuredRange == renderRange && configuredLimit == renderLimit) return;
		final boolean limitWasEnabled = renderLimit > 0;
		renderRange = configuredRange;
		renderLimit = configuredLimit;
		renderRangeSq = (double)configuredRange*configuredRange;
		if(renderRange == 0) clearCachedEntityStorage();
		if(renderLimit == 0 || !limitWasEnabled) clearLoadedEntityIndex();
		clearRenderSelection();
		snapshotDirty = isActive();
		if(!snapshotDirty){
			level = null;
			renderSnapshot.trimToSize();
			nearestEntityCollector.clear();
		}
	}

	private static final boolean isActive(){return renderRange > 0 || renderLimit > 0;}

	private static final boolean isWithinConfiguredRange(final double distanceSq){return distanceSq <= renderRangeSq;}

	public static final boolean useConfiguredRenderDistance(
			final boolean original, final Entity entity, final double cameraX, final double cameraY, final double cameraZ){
		return renderRange > 0 && isCacheable(entity)
				? isWithinConfiguredRange(entity.distanceToSqr(cameraX, cameraY, cameraZ)) : original;
	}

	public static final boolean shouldRenderInExtendedTerrain(
			final Entity entity, final Frustum frustum, final double cameraX, final double cameraY, final double cameraZ){
		if(renderRange == 0 || !isCacheable(entity)
				|| !isWithinConfiguredRange(entity.distanceToSqr(cameraX, cameraY, cameraZ))) return false;
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
		return renderRange > 0 && isCacheable(entity) && client.level != null && entity.level() == client.level
				&& StaticEntityTerrainProviderRegistry.hasExtendedTerrainAt(
						client.level, entity.getBlockX(), entity.getBlockZ(), cameraX, cameraZ);
	}

	public static final Iterable<Entity> includeCachedEntities(final ClientLevel clientLevel, final Iterable<Entity> loadedEntities){
		if(!isActive()) return loadedEntities;
		if(level != clientLevel) reset(clientLevel);
		if(renderLimit > 0 && !loadedIndexInitialized) indexLoadedEntities(loadedEntities);
		if(snapshotDirty){
			final LocalPlayer player = Minecraft.getInstance().player;
			if(player != null) rebuildRenderSnapshot(clientLevel, player, clientLevel.getGameTime());
		}
		if(limitApplied){
			final Iterable<Entity> selectedLoaded = Iterables.filter(loadedEntities, entity->!isCacheable(entity) || selectedLoadedEntities.contains(entity));
			return renderSnapshot.isEmpty() ? selectedLoaded : Iterables.concat(selectedLoaded, renderSnapshot);
		}
		return renderSnapshot.isEmpty() ? loadedEntities : Iterables.concat(loadedEntities, renderSnapshot);
	}

	private static final void onEntityLoad(final Entity entity, final ClientLevel clientLevel){
		if(!isActive() || !isCacheable(entity)) return;
		if(level != clientLevel) reset(clientLevel);
		if(renderLimit > 0 && loadedIndexInitialized) loadedByUuid.put(entity.getUUID(), entity);
		if(renderRange > 0) remove(entity.getUUID());
		snapshotDirty = true;
	}

	private static final void onEntityUnload(final Entity entity, final ClientLevel clientLevel){
		if(!isActive() || !isCacheable(entity)) return;
		if(level != clientLevel) reset(clientLevel);
		if(renderLimit > 0 && loadedIndexInitialized) loadedByUuid.remove(entity.getUUID());
		snapshotDirty = true;
		if(renderRange == 0) return;

		final Minecraft client = Minecraft.getInstance();
		final LocalPlayer player = client.player;
		if(player == null) return;
		final double unloadDistanceSq = player.distanceToSqr(entity);
		if(unloadDistanceSq < MIN_CACHE_DISTANCE*MIN_CACHE_DISTANCE){
			remove(entity.getUUID());
			return;
		}

		cache(entity);
	}

	private static final void onClientTick(final Minecraft client){
		if(!isActive()) return;
		final ClientLevel clientLevel = client.level;
		final LocalPlayer player = client.player;
		if(clientLevel == null || player == null){
			if(level != null) reset(null);
			return;
		}
		if(level != clientLevel) reset(clientLevel);
		if(renderRange > 0 && !cachedByUuid.isEmpty()){
			StaticEntityTerrainProviderRegistry.tick();
			final long tick = clientLevel.getGameTime();
			if(lastTerrainSweepTick == UNSET_TICK || tick < lastTerrainSweepTick || tick-lastTerrainSweepTick >= TERRAIN_SWEEP_INTERVAL_TICKS){
				lastTerrainSweepTick = tick;
				pruneMissingTerrain(clientLevel, player.getX(), player.getZ(), tick);
			}
			snapshotDirty = true;
		}
		if(limitApplied) snapshotDirty = true;
	}

	private static final void remove(final UUID uuid){
		final CachedEntity cached = cachedByUuid.remove(uuid);
		if(cached == null) return;
		cached.chunk.remove(cached);
		if(cached.chunk.entities.isEmpty()) cachedByChunk.remove(cached.chunk.key);
		snapshotDirty = true;
	}

	private static final long chunkKey(final int chunkX, final int chunkZ){return ((long)chunkX << 32) ^ (chunkZ&0xFFFFFFFFL);}

	private static final void cache(final Entity entity){
		remove(entity.getUUID());
		final int chunkX = entity.getBlockX() >> 4, chunkZ = entity.getBlockZ() >> 4;
		final long chunkKey = chunkKey(chunkX, chunkZ);
		final CachedChunk chunk = cachedByChunk.computeIfAbsent(chunkKey, _->new CachedChunk(chunkKey, chunkX, chunkZ));
		cachedByUuid.put(entity.getUUID(), new CachedEntity(entity, chunk));
		snapshotDirty = true;
	}

	private static final void reset(final ClientLevel clientLevel){
		level = clientLevel;
		clearCachedEntityStorage();
		clearLoadedEntityIndex();
		clearRenderSelection();
		nearestEntityCollector.clear();
		snapshotDirty = isActive();
	}

	private static final void clearCachedEntityStorage(){
		cachedByUuid.clear();
		cachedByChunk.clear();
		lastTerrainSweepTick = UNSET_TICK;
	}

	private static final void clearLoadedEntityIndex(){
		loadedByUuid.clear();
		loadedIndexInitialized = false;
	}

	private static final void clearRenderSelection(){
		selectedLoadedEntities.clear();
		renderSnapshot.clear();
		limitApplied = false;
		snapshotDirty = false;
	}

	private static final void indexLoadedEntities(final Iterable<Entity> loadedEntities){
		loadedByUuid.clear();
		for(final Entity entity : loadedEntities) if(isCacheable(entity)) loadedByUuid.put(entity.getUUID(), entity);
		loadedIndexInitialized = true;
	}

	private static final boolean updateTerrainStatus(
			final CachedChunk chunk, final ClientLevel clientLevel,
			final double playerX, final double playerZ, final long tick){
		if(chunk.lastTerrainCheckTick == tick) return chunk.hasTerrain;
		chunk.lastTerrainCheckTick = tick;
		chunk.hasTerrain = StaticEntityTerrainProviderRegistry.hasTerrainInChunk(clientLevel, chunk.chunkX, chunk.chunkZ, playerX, playerZ);
		if(chunk.hasTerrain) chunk.terrainMissingStartTick = UNSET_TICK;
		else if(chunk.terrainMissingStartTick == UNSET_TICK) chunk.terrainMissingStartTick = tick;
		return chunk.hasTerrain;
	}

	private static final void pruneMissingTerrain(
			final ClientLevel clientLevel, final double playerX, final double playerZ, final long tick){
		for(final var it = cachedByChunk.values().iterator(); it.hasNext();){
			final CachedChunk chunk = it.next();
			if(updateTerrainStatus(chunk, clientLevel, playerX, playerZ, tick)
					|| tick-chunk.terrainMissingStartTick < TERRAIN_LOSS_GRACE_TICKS) continue;
			for(final CachedEntity cached : chunk.entities) cachedByUuid.remove(cached.entity.getUUID());
			it.remove();
			snapshotDirty = true;
		}
	}

	private static final boolean isChunkWithinRange(final CachedChunk chunk, final double playerX, final double playerZ){
		final double minX = chunk.chunkX*16.0, minZ = chunk.chunkZ*16.0;
		final double dx = playerX < minX ? minX-playerX : playerX > minX+16 ? playerX-minX-16 : 0;
		final double dz = playerZ < minZ ? minZ-playerZ : playerZ > minZ+16 ? playerZ-minZ-16 : 0;
		return dx*dx + dz*dz <= renderRangeSq;
	}

	private static final void collectChunkEntitiesWithinRange(
			final ArrayList<Entity> snapshot, final NearestEntityCollector nearest, final CachedChunk chunk, final ClientLevel clientLevel,
			final double playerX, final double playerY, final double playerZ, final long tick){
		if(!updateTerrainStatus(chunk, clientLevel, playerX, playerZ, tick)) return;
		for(final CachedEntity cached : chunk.entities){
			final Entity entity = cached.entity;
			final double dx = entity.getX()-playerX, dy = entity.getY()-playerY, dz = entity.getZ()-playerZ;
			final double distanceSq = dx*dx + dy*dy + dz*dz;
			if(distanceSq <= renderRangeSq){
				if(nearest == null) snapshot.add(entity);
				else nearest.offer(entity, distanceSq, true);
			}
		}
	}

	private static final void rebuildRenderSnapshot(final ClientLevel clientLevel, final LocalPlayer player, final long tick){
		selectedLoadedEntities.clear();
		renderSnapshot.clear();
		final double playerX = player.getX(), playerY = player.getY(), playerZ = player.getZ();
		final long candidateCount = (long)loadedByUuid.size()+cachedByUuid.size();
		final NearestEntityCollector nearest = renderLimit > 0 && renderLimit < candidateCount ? nearestEntityCollector : null;
		limitApplied = nearest != null;
		if(nearest != null){
			nearest.reset(renderLimit);
			for(final Entity entity : loadedByUuid.values()){
				final double dx = entity.getX()-playerX, dy = entity.getY()-playerY, dz = entity.getZ()-playerZ;
				final double distanceSq = dx*dx + dy*dy + dz*dz;
				if(renderRange == 0 || distanceSq <= renderRangeSq) nearest.offer(entity, distanceSq, false);
			}
		}
		if(renderRange > 0 && !cachedByChunk.isEmpty()){
			final int centerChunkX = player.getBlockX() >> 4, centerChunkZ = player.getBlockZ() >> 4;
			final int chunkRadius = (renderRange >> 4)+2;
			final long chunkDiameter = (long)chunkRadius*2+1, chunkLookups = chunkDiameter*chunkDiameter;
			if(chunkLookups < cachedByChunk.size()){
				for(int chunkX=centerChunkX-chunkRadius; chunkX<=centerChunkX+chunkRadius; ++chunkX){
					for(int chunkZ=centerChunkZ-chunkRadius; chunkZ<=centerChunkZ+chunkRadius; ++chunkZ){
						final CachedChunk chunk = cachedByChunk.get(chunkKey(chunkX, chunkZ));
						if(chunk != null) collectChunkEntitiesWithinRange(renderSnapshot, nearest, chunk, clientLevel, playerX, playerY, playerZ, tick);
					}
				}
			}
			else for(final CachedChunk chunk : cachedByChunk.values()){
				if(isChunkWithinRange(chunk, playerX, playerZ)) collectChunkEntitiesWithinRange(renderSnapshot, nearest, chunk, clientLevel, playerX, playerY, playerZ, tick);
			}
		}
		if(nearest != null) nearest.appendTo(selectedLoadedEntities, renderSnapshot);
		snapshotDirty = false;
	}
}