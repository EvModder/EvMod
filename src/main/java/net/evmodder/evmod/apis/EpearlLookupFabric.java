package net.evmodder.evmod.apis;

import static net.evmodder.evmod.apis.MojangProfileLookupConstants.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.evmodder.EvLib.util.TextUtils_New;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.Main;
import net.evmodder.evmod.mixin.AccessorProjectileEntity;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class EpearlLookupFabric extends EpearlLookup{
	private final long CHUNK_LOAD_WAIT = 60*1000;
	private final long CHUNK_LOAD_WAIT_AFTER_EPEARL = 5*1000; // Shortcuts chunk wait if we see any epearls already loaded

	private final HashMap<ChunkPos, Long> recentlyLoadedChunks = new HashMap<>();
	private final HashSet<ChunkPos> loadedChunks = new HashSet<>();
	private World world;
	private List<EnderPearlEntity> loadedEpearls;
	private int epearlCount;

	@Override protected boolean enableKeyUUID(){return Configs.Database.EPEARL_OWNERS_BY_UUID.getBooleanValue();}
	@Override protected boolean enableKeyXZ(){return Configs.Database.EPEARL_OWNERS_BY_XZ.getBooleanValue();}
	@Override protected boolean enableRemoteDbUUID(){return enableKeyUUID() && Configs.Database.SHARE_EPEARL_OWNERS.getBooleanValue();}
	@Override protected boolean enableRemoteDbXZ(){return enableKeyXZ() && Configs.Database.SHARE_EPEARL_OWNERS.getBooleanValue();}
	public boolean isDisabled(){return !enableKeyUUID() && !enableKeyXZ();} // Only accessor: MixinEntityRenderer

	private final double DIST_XZ = 64, DIST_Y = 128; // Max dist for which to track/remove pearls
	private final double DIST_XZ_SQ = DIST_XZ*DIST_XZ, DIST_Y_SQ = DIST_Y*DIST_Y;
	private boolean isWithinDist(PlayerEntity player, PearlDataClient pdc){
		final double dx = pdc.x()-player.getBlockX(), dy = pdc.y()-player.getBlockY(), dz = pdc.z()-player.getBlockZ();
		return dx*dx + dz*dz < DIST_XZ_SQ && dy*dy < DIST_Y_SQ;
	}

	private final UUID toKeyXZ(Entity epearl){
		return new UUID(Double.doubleToRawLongBits(epearl.getX()), Double.doubleToRawLongBits(epearl.getZ()));
	}
	private final ChunkPos toChunkPos(PearlDataClient pdc){
		return new ChunkPos(pdc.x()<<4, pdc.z()<<4);
	}

	public EpearlLookupFabric(RemoteServerSender rms){
		super(rms, Main.LOGGER);
		ClientChunkEvents.CHUNK_LOAD.register((phase, listener)->{
			if(isDisabled()) return;
			synchronized(recentlyLoadedChunks){
				recentlyLoadedChunks.put(listener.getPos(), System.currentTimeMillis()+CHUNK_LOAD_WAIT);
				final boolean added = loadedChunks.add(listener.getPos());
				if(!added) Main.LOGGER.error("EPLF: Loading chunk "+listener.getPos().toString()+" before it was unloaded!");
//				assert added;
			}
		});
		ClientChunkEvents.CHUNK_UNLOAD.register((phase, listener)->{
			if(isDisabled()) return;
			synchronized(recentlyLoadedChunks){
				recentlyLoadedChunks.remove(listener.getPos());
				final boolean removed = loadedChunks.remove(listener.getPos());
				if(!removed) Main.LOGGER.error("EPLF: Unloading chunk "+listener.getPos().toString()+" before it was loaded!");
//				assert removed;
			}
		});

		TickListener.register(new TickListener(){
			@Override public void onTickStart(MinecraftClient client){
				if(isDisabled()) return;
				synchronized(recentlyLoadedChunks){
					if(client == null || client.player == null || world != client.world || client.world == null){
						world = client.world;
						recentlyLoadedChunks.clear();
						loadedChunks.clear();
						return;
					}
					final long now = System.currentTimeMillis();
					// Update recentlyLoadedChunks
					final boolean fullyLoadedChunk = recentlyLoadedChunks.entrySet().removeIf(entry -> now > entry.getValue());

					final Box box = client.player.getBoundingBox().expand(DIST_XZ, DIST_Y, DIST_XZ);
					loadedEpearls = world.getEntitiesByType(EntityType.ENDER_PEARL, box, _0->true);

					// Schedule faster recentlyLoadedChunks updates if loaded epearls are detected
					final long epearlLoadShortcut = now + CHUNK_LOAD_WAIT_AFTER_EPEARL;
					loadedEpearls.stream().map(Entity::getChunkPos).distinct().forEach(chunk -> {
						final Long wait = recentlyLoadedChunks.get(chunk);
						if(wait != null && epearlLoadShortcut < wait) recentlyLoadedChunks.put(chunk, epearlLoadShortcut);
					});

					// If any epearl changes (or chunk is fully loaded), update owners for all loaded epearls
					if(epearlCount != loadedEpearls.size() || fullyLoadedChunk){
						epearlCount = loadedEpearls.size();
//						Main.LOGGER.info("Change to chunks/epearls loaded, calling getOwner() on all epearls and running removal check");
						if(enableKeyUUID()){
							final HashSet<UUID> seenKeyUUIDs = new HashSet<>(epearlCount);
							loadedEpearls.stream().map(Entity::getUuid).forEach(seenKeyUUIDs::add);
							runRemovalCheckUUID(e -> isWithinDist(client.player, e.getValue()) && !seenKeyUUIDs.contains(e.getKey())
//									&& loadedChunks.contains(toChunkPos(e.getValue()))
									&& !recentlyLoadedChunks.containsKey(toChunkPos(e.getValue())));
						}
						if(enableKeyXZ()){
							final HashSet<UUID> seenKeyXZs = new HashSet<>(epearlCount);
							loadedEpearls.stream().map(EpearlLookupFabric.this::toKeyXZ).forEach(seenKeyXZs::add);
							runRemovalCheckXZ(e -> isWithinDist(client.player, e.getValue()) && !seenKeyXZs.contains(e.getKey())
//									&& loadedChunks.contains(toChunkPos(e.getValue()))
									&& !recentlyLoadedChunks.containsKey(toChunkPos(e.getValue())));
						}
					}
				}
			}
		});
	}

	private final UUID getOwnerFromDb(final Entity epearl, final boolean byUUID){
		assert epearl != null;
		final UUID key = byUUID ? epearl.getUuid() : toKeyXZ(epearl);
		final PearlDataClient pdc = getPearlOwner(key, epearl.getId(), epearl.getBlockX(), epearl.getBlockY(), epearl.getBlockZ(), byUUID);
		assert pdc != null : "Expected PDC to be one of [404, LOADING, <result>]";
		return pdc.owner();
	}

	public final String getDynamicUsername(final UUID owner, final UUID key){
		if(owner == null) return "null";
		if(owner.equals(UUID_404)) return NAME_U_404;
		if(owner.equals(UUID_LOADING)){
			final Long startTs = requestStartTimes.get(key);
			if(startTs == null) return NAME_U_LOADING+" ERROR";
			return NAME_U_LOADING+" "+TextUtils_New.formatTime(System.currentTimeMillis()-startTs);
		}
		return MojangProfileLookup.nameLookup.get(owner, /*callback=*/null);
	}

	private final boolean isMoving(final EnderPearlEntity epearl){
		final Vec3d vel = epearl.getVelocity();
		return vel.x != 0d || vel.z != 0d || vel.y > .1d;
	}

	public final String getOwnerName(final EnderPearlEntity epearl){
		assert epearl != null;
		UUID ownerUUID = ((AccessorProjectileEntity)epearl).getOwnerUUID();
		if(isDisabled()) return getDynamicUsername(ownerUUID, epearl.getUuid());

		if(ownerUUID == null){
			if(enableKeyUUID()) ownerUUID = getOwnerFromDb(epearl, /*byUUID=*/true);
			if(enableKeyXZ() && (ownerUUID == null || ownerUUID == UUID_404 || ownerUUID == UUID_LOADING)){
				if(isMoving(epearl)) return getDynamicUsername(ownerUUID == null ? UUID_404 : ownerUUID, epearl.getUuid());
				ownerUUID = getOwnerFromDb(epearl, /*byUUID=*/false);
			}
			assert ownerUUID != null : "Expected at least one of [enableKeyUUID() or enableKeyXZ()] to be enabled, and return one of [404, LOADING, <result>]";
			if(ownerUUID != UUID_404 && ownerUUID != UUID_LOADING) ((AccessorProjectileEntity)epearl).setOwnerUUID(ownerUUID);
		}
		else{
			assert ownerUUID != UUID_404 && ownerUUID != UUID_LOADING;
			final PearlDataClient pdc = new PearlDataClient(ownerUUID, epearl.getBlockX(), epearl.getBlockY(), epearl.getBlockZ());
			if(enableKeyUUID()) putPearlOwner(epearl.getUuid(), pdc, /*keyIsUUID=*/true);
			if(enableKeyXZ() && !isMoving(epearl)) putPearlOwner(toKeyXZ(epearl), pdc, /*keyIsUUID=*/false);
		}
		return getDynamicUsername(ownerUUID, epearl.getUuid());
	}
}