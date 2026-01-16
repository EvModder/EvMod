package net.evmodder.evmod.apis;

import static net.evmodder.evmod.apis.MojangProfileLookupConstants.NAME_U_404;
import static net.evmodder.evmod.apis.MojangProfileLookupConstants.NAME_U_LOADING;
import static net.evmodder.evmod.apis.MojangProfileLookupConstants.UUID_404;
import static net.evmodder.evmod.apis.MojangProfileLookupConstants.UUID_LOADING;
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
import net.minecraft.world.World;

public final class EpearlLookupFabric extends EpearlLookup{
	private final long CHUNK_LOAD_WAIT = 60*1000;
	private final long CHUNK_LOAD_WAIT_AFTER_EPEARL = 5*1000; // Shortcuts chunk wait if we see any epearls already loaded

	private final HashMap<ChunkPos, Long> recentlyLoadedChunks = new HashMap<>();
	private final HashSet<ChunkPos> loadedChunks = new HashSet<>();
	private World currWorld;
	private List<EnderPearlEntity> loadedEpearls;
	private int lastEpearlCount;

	@Override protected boolean useRemoteDB(){return Configs.Database.SHARE_EPEARL_OWNERS.getBooleanValue();}
	@Override protected boolean enableKeyUUID(){return Configs.Database.EPEARL_OWNERS_BY_UUID.getBooleanValue();}
	@Override protected boolean enableKeyXZ(){return Configs.Database.EPEARL_OWNERS_BY_XZ.getBooleanValue();}
	private boolean isDisabled(){return !enableKeyUUID() && !enableKeyXZ();}

	private final double DIST_XZ = 64, DIST_Y = 128; // Max dist for which to track/remove pearls
	private final double DIST_XZ_SQ = DIST_XZ*DIST_XZ, DIST_Y_SQ = DIST_Y*DIST_Y;
	private boolean isWithinDist(PlayerEntity player, PearlDataClient pdc){
		final double dx = pdc.x()-player.getBlockX(), dy = pdc.y()-player.getBlockY(), dz = pdc.z()-player.getBlockZ();
		return dx*dx + dz*dz < DIST_XZ_SQ && dy*dy < DIST_Y_SQ;
	}

	private UUID toKeyXZ(Entity epearl){
		return new UUID(Double.doubleToRawLongBits(epearl.getX()), Double.doubleToRawLongBits(epearl.getZ()));
	}

	public EpearlLookupFabric(RemoteServerSender rms){
		super(rms, Main.LOGGER);
		ClientChunkEvents.CHUNK_LOAD.register((phase, listener)->{
			if(isDisabled()) return;
			synchronized(loadedChunks){
				recentlyLoadedChunks.put(listener.getPos(), System.currentTimeMillis()+CHUNK_LOAD_WAIT);
				final boolean added = loadedChunks.add(listener.getPos());
				assert added;
			}
		});
		ClientChunkEvents.CHUNK_UNLOAD.register((phase, listener)->{
			if(isDisabled()) return;
			synchronized(loadedChunks){
				recentlyLoadedChunks.remove(listener.getPos());
				final boolean removed = loadedChunks.remove(listener.getPos());
				assert removed;
			}
		});

		TickListener.register(new TickListener(){
			@Override public void onTickStart(MinecraftClient client){
				if(isDisabled()) return;
				synchronized(loadedChunks){
					if(client == null || client.player == null || currWorld != client.world || client.world == null){
						currWorld = client.world;
						recentlyLoadedChunks.clear();
						loadedChunks.clear();
						return;
					}
					final long now = System.currentTimeMillis();
					final boolean fullyLoadedChunk = recentlyLoadedChunks.entrySet().removeIf(entry -> now > entry.getValue());

					final Box box = client.player.getBoundingBox().expand(DIST_XZ, DIST_Y, DIST_XZ);
					loadedEpearls = client.world.getEntitiesByType(EntityType.ENDER_PEARL, box, _0->true);

					final long epearlLoadShortcut = now+CHUNK_LOAD_WAIT_AFTER_EPEARL;
					loadedEpearls.stream().map(ep -> client.world.getChunk(ep.getBlockPos()).getPos()).distinct().forEach(chunkPos -> {
						final Long wait = recentlyLoadedChunks.get(chunkPos);
						if(wait != null && epearlLoadShortcut < wait) recentlyLoadedChunks.put(chunkPos, epearlLoadShortcut);
					});

					if(lastEpearlCount != loadedEpearls.size() || fullyLoadedChunk){
						lastEpearlCount = loadedEpearls.size();
						Main.LOGGER.info("Change to chunks/epearls loaded, calling runRemovalCheck()");
						if(Configs.Database.EPEARL_OWNERS_BY_UUID.getBooleanValue()){
							final HashSet<UUID> seenKeyUUIDs = new HashSet<>(lastEpearlCount);
							loadedEpearls.stream().map(EnderPearlEntity::getUuid).forEach(seenKeyUUIDs::add);
							runRemovalCheckUUID(e -> isWithinDist(client.player, e.getValue()) && !seenKeyUUIDs.contains(e.getKey()));
						}
						if(Configs.Database.EPEARL_OWNERS_BY_XZ.getBooleanValue()){
							final HashSet<UUID> seenKeyXZs = new HashSet<>(lastEpearlCount);
							loadedEpearls.stream().map(EpearlLookupFabric.this::toKeyXZ).forEach(seenKeyXZs::add);
							runRemovalCheckXZ(e -> isWithinDist(client.player, e.getValue()) && !seenKeyXZs.contains(e.getKey()));
						}
					}
				}
			}
		});
	}

	private final UUID getPearlOwnerUUID(Entity epearl){
		PearlDataClient pdc = getPearlOwner(epearl.getUuid(), -1,  epearl.getBlockX(), epearl.getBlockY(), epearl.getBlockZ(), /*keyIsUUID=*/true);
		return pdc == null ? null : pdc.owner();
	}
	private final UUID getPearlOwnerXZ(Entity epearl){
		PearlDataClient pdc = getPearlOwner(toKeyXZ(epearl), epearl.getId(), epearl.getBlockX(), epearl.getBlockY(), epearl.getBlockZ(), /*keyIsUUID=*/false);
		return pdc == null ? null : pdc.owner();
	}

	public final String getDynamicUsername(UUID owner, UUID key){
		if(owner == null ? UUID_404 == null : owner.equals(UUID_404)) return NAME_U_404;
		if(owner == null ? UUID_LOADING == null : owner.equals(UUID_LOADING)){
			Long startTs = requestStartTimes.get(key);
			if(startTs == null) return NAME_U_LOADING+" ERROR";
			return NAME_U_LOADING+" "+TextUtils_New.formatTime(System.currentTimeMillis()-startTs);
		}
		return MojangProfileLookup.nameLookup.get(owner, /*callback=*/null);
	}

	public final String updateOwner(Entity epearl){
		UUID ownerUUID = ((AccessorProjectileEntity)epearl).getOwnerUUID();
		String ownerName = getDynamicUsername(ownerUUID, epearl.getUuid());
		final boolean USE_DB_UUID = Configs.Database.EPEARL_OWNERS_BY_UUID.getBooleanValue();
		final boolean USE_DB_XZ = Configs.Database.EPEARL_OWNERS_BY_XZ.getBooleanValue();
		if((!USE_DB_UUID && !USE_DB_XZ) || epearl.getVelocity().x != 0d || epearl.getVelocity().z != 0d || epearl.getVelocity().y > .1d
				|| ownerUUID == MojangProfileLookupConstants.UUID_404 || ownerUUID == MojangProfileLookupConstants.UUID_LOADING
		){
			return ownerName;
		}
		if(ownerUUID != null){
			final PearlDataClient pdc = new PearlDataClient(ownerUUID, epearl.getBlockX(), epearl.getBlockY(), epearl.getBlockZ());
			if(USE_DB_UUID) putPearlOwner(epearl.getUuid(), pdc, /*keyIsUUID=*/true);
			if(USE_DB_XZ) putPearlOwner(toKeyXZ(epearl), pdc, /*keyIsUUID=*/false);
		}
		else{
			if(USE_DB_UUID){
				ownerUUID = getPearlOwnerUUID(epearl);
				if(ownerUUID != null && ownerUUID != MojangProfileLookupConstants.UUID_404 && ownerUUID != MojangProfileLookupConstants.UUID_LOADING){
					return getDynamicUsername(ownerUUID, epearl.getUuid());
				}
			}
			if(USE_DB_XZ) ownerUUID = getPearlOwnerXZ(epearl);
		}
		return getDynamicUsername(ownerUUID, epearl.getUuid());
	}
}