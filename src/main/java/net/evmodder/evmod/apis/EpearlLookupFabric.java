package net.evmodder.evmod.apis;

import static net.evmodder.evmod.apis.MojangProfileLookupConstants.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.UUID;
import net.evmodder.EvLib.util.TextUtils_New;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.Main;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.minecraft.client.Minecraft;
//? <1.21.11 {
/*import net.minecraft.client.gui.screens.ReceivingLevelScreen;*/
//?}
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

public final class EpearlLookupFabric extends EpearlLookup{
	private final long CHUNK_LOAD_WAIT = 60*1000;

	private final HashMap<ChunkPos, Long> recentlyLoadedChunks = new HashMap<>();
	private final HashSet<ChunkPos> loadedChunks = new HashSet<>();
	private ClientLevel world;
	private long nextCleanup;

	@Override protected boolean enableKeyUUID(){return Configs.Database.EPEARL_OWNERS_BY_UUID.getBooleanValue();}
	@Override protected boolean enableKeyXZ(){return Configs.Database.EPEARL_OWNERS_BY_XZ.getBooleanValue();}
	@Override protected boolean enableRemoteDbUUID(){return enableKeyUUID() && Configs.Database.SHARE_EPEARL_OWNERS.getBooleanValue() && !Configs.Database.ADDRESS.getStringValue().isBlank();}
	@Override protected boolean enableRemoteDbXZ(){return enableKeyXZ() && Configs.Database.SHARE_EPEARL_OWNERS.getBooleanValue() && !Configs.Database.ADDRESS.getStringValue().isBlank();}
	public final boolean isDisabled(){return !enableKeyUUID() && !enableKeyXZ();} // Only accessor: MixinEntityRenderer

	private final UUID toKeyXZ(final Entity epearl){
		return toKeyXZ(epearl.getUUID(), epearl.getX(), epearl.getY(), epearl.getZ(), this::anchorBlock);
	}
	private PearlPositionKey.Block anchorBlock(int x, int y, int z){
		final var pos = new BlockPos(x, y, z);
		if(world.isOutsideBuildHeight(pos)) return PearlPositionKey.Block.SOLID;
		if(!world.hasChunkAt(pos)) return PearlPositionKey.Block.UNLOADED;
		final var state = world.getBlockState(pos);
		if(state.is(Blocks.BUBBLE_COLUMN)) return state.getValue(BubbleColumnBlock.DRAG_DOWN)
				? PearlPositionKey.Block.SOLID : PearlPositionKey.Block.BUBBLE;
		if(state.is(Blocks.SLIME_BLOCK)) return PearlPositionKey.Block.SLIME;
		if(state.is(Blocks.MOVING_PISTON)) return PearlPositionKey.Block.MOVING;
		if(state.is(Blocks.PISTON) || state.is(Blocks.STICKY_PISTON) || state.is(Blocks.PISTON_HEAD)){
			if(state.getValue(BlockStateProperties.FACING) != Direction.UP)
				return PearlPositionKey.Block.SOLID;
			return state.is(Blocks.PISTON_HEAD) ? PearlPositionKey.Block.UP_HEAD : PearlPositionKey.Block.UP_PISTON;
		}
		return state.getCollisionShape(world, pos).isEmpty() ? PearlPositionKey.Block.PASSABLE : PearlPositionKey.Block.SOLID;
	}
	private final ChunkPos toChunkPos(final PearlDataClient pdc){
		return new ChunkPos(pdc.x()>>4, pdc.z()>>4);
	}
	private final void switchWorld(final ClientLevel level){
		resetConnectionReadiness();
		world = level;
		recentlyLoadedChunks.clear();
		loadedChunks.clear();
		nextCleanup = 0;
		final Minecraft client = Minecraft.getInstance();
		final var server = level == null ? null : MiscUtils.getRemoteServerDescriptor();
		if(server == null){setWorldScope(null, null, null); return;}
		final String identity = server.singleplayer() ? "save:"+client.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize()
				: "server:"+server.address().toLowerCase(Locale.ROOT);
		//? >=1.21.11 {
		final String dimension = level.dimension().identifier().toString();
		//?} else {
		/*final String dimension = level.dimension().location().toString();*/
		//?}
		setWorldScope(server, identity, dimension);
	}

	public EpearlLookupFabric(final RemoteServerSender rms){
		super(rms, Main.LOGGER);
		ClientChunkEvents.CHUNK_LOAD.register((ClientLevel level, LevelChunk listener)->{
			if(level != Minecraft.getInstance().level) return;
			if(world != level) switchWorld(level);
			recentlyLoadedChunks.put(listener.getPos(), System.nanoTime()/1_000_000+CHUNK_LOAD_WAIT);
			final boolean added = loadedChunks.add(listener.getPos());
			if(!added) Main.LOGGER.error("EPLF: Loading chunk "+listener.getPos().toString()+" before it was unloaded!");
//			assert added;
		});
		ClientChunkEvents.CHUNK_UNLOAD.register((ClientLevel level, LevelChunk listener)->{
			if(level != world) return; // Ignore delayed unload callbacks from the previous world.
			resetCleanup(); // Even unload/reload between two scans invalidates continuous absence.
			recentlyLoadedChunks.remove(listener.getPos());
			final boolean removed = loadedChunks.remove(listener.getPos());
			if(!removed) Main.LOGGER.error("EPLF: Unloading chunk "+listener.getPos().toString()+" before it was loaded!");
//			assert removed;
		});

		TickListener.register(new TickListener(){
			@Override public void onTickStart(final Minecraft client){
				final ClientLevel level = client == null ? null : client.level;
				if(world != level) switchWorld(level);
				if(isDisabled() || client == null || client.player == null || level == null || client.isPaused()){
					resetConnectionReadiness(); return;
				}
				final long now = System.nanoTime()/1_000_000;
				if(now < nextCleanup) return;
				if(now-nextCleanup > 2_000) resetConnectionReadiness(); // Do not count a paused/stalled client as continuous observation.
				nextCleanup = now+1_000;
				if(client.getConnection() == null || client.player.touchingUnloadedChunk()
						//? >=1.21.11 {
						|| !client.getConnection().hasClientLoaded()
						//?} else {
						/*|| client.screen instanceof ReceivingLevelScreen*/
						//?}
						|| !loadedChunks.contains(new ChunkPos(client.player.getBlockX()>>4, client.player.getBlockZ()>>4))){
					resetConnectionReadiness(); return;
				}
				// Passive heuristic, not an RTT measurement: require ongoing, normally paced world updates throughout the absence window.
				if(!isConnectionReady(now)){resetCleanup(); return;}
				recentlyLoadedChunks.entrySet().removeIf(entry -> now > entry.getValue());
				final HashSet<UUID> seenUUID = new HashSet<>(), seenXZ = new HashSet<>();
				// Include every loaded pearl, not only rendered ones or pearls inside a Y-limited box.
				for(Entity entity : level.entitiesForRendering()){
					if(!(entity instanceof ThrownEnderpearl pearl) || pearl.isRemoved()) continue;
					seenUUID.add(pearl.getUUID());
					if(enableKeyXZ()){
						final UUID key = toKeyXZ(pearl);
						if(key != null) seenXZ.add(key);
					}
					getOwnerName(pearl); // Refresh locations even when render culling hides the pearl.
				}
				cleanupPearls(seenUUID, seenXZ, pdc -> {
					final double dx = pdc.x()+.5-client.player.getX(), dz = pdc.z()+.5-client.player.getZ();
					final ChunkPos chunk = toChunkPos(pdc);
					// Vanilla tracks pearls horizontally. Keep well inside the usual range; bobbing Y is irrelevant.
					return dx*dx+dz*dz < 16*16 && loadedChunks.contains(chunk) && !recentlyLoadedChunks.containsKey(chunk);
				}, now);
			}
		});
	}

	private final UUID getOwnerFromDb(final Entity epearl, final boolean byUUID){
		assert epearl != null;
		final UUID key = byUUID ? epearl.getUUID() : toKeyXZ(epearl);
		if(key == null) return UUID_LOADING;
		final PearlDataClient pdc = getPearlOwner(key, epearl.getUUID(), epearl.getBlockX(), epearl.getBlockY(), epearl.getBlockZ(), byUUID);
		assert pdc != null : "Expected PDC to be one of [404, LOADING, <result>]";
		return pdc.owner();
	}

	public final String getDynamicUsername(final UUID owner, final UUID key){
		if(owner == null) return "null";
		if(owner == UUID_404) return NAME_U_404;
		if(owner == UUID_LOADING){
			final Long startTs = requestStartTimes.get(key);
			if(startTs == null) return NAME_U_LOADING+" ERROR";
			return NAME_U_LOADING+" "+TextUtils_New.formatTime(System.currentTimeMillis()-startTs);
		}
		return MojangProfileLookup.nameLookup.get(owner, /*callback=*/null);
	}

	private final boolean isMoving(final ThrownEnderpearl epearl){
		final Vec3 vel = epearl.getDeltaMovement();
		return vel.x != 0d || vel.z != 0d || vel.y > .1d;
	}

	public final String getOwnerName(final ThrownEnderpearl epearl){
		assert epearl != null;
		if(world != Minecraft.getInstance().level) switchWorld(Minecraft.getInstance().level);
		UUID ownerUUID = MiscUtils.getPearlUUID(epearl);
		if(isDisabled() || epearl.level() != world) return getDynamicUsername(ownerUUID, epearl.getUUID());
		UUID lookupKey = epearl.getUUID();

		if(ownerUUID == null){
			if(enableKeyUUID()) ownerUUID = getOwnerFromDb(epearl, /*byUUID=*/true);
			if(enableKeyXZ() && (ownerUUID == null || ownerUUID == UUID_404 || ownerUUID == UUID_LOADING)){
				if(isMoving(epearl)) return getDynamicUsername(ownerUUID == null ? UUID_404 : ownerUUID, epearl.getUUID());
				lookupKey = toKeyXZ(epearl);
				if(lookupKey == null) return getDynamicUsername(UUID_LOADING, epearl.getUUID());
				ownerUUID = getOwnerFromDb(epearl, /*byUUID=*/false);
			}
			assert ownerUUID != null : "Expected at least one of [enableKeyUUID() or enableKeyXZ()] to be enabled, and return one of [404, LOADING, <result>]";
			if(ownerUUID != UUID_404 && ownerUUID != UUID_LOADING) MiscUtils.setPearlUUID(epearl, ownerUUID);
		}
		else{
			assert ownerUUID != UUID_404 && ownerUUID != UUID_LOADING;
			final PearlDataClient pdc = new PearlDataClient(ownerUUID, epearl.getBlockX(), epearl.getBlockY(), epearl.getBlockZ());
			if(enableKeyUUID()) putPearlOwner(epearl.getUUID(), pdc, /*keyIsUUID=*/true);
			if(enableKeyXZ() && !isMoving(epearl)){
				final UUID key = toKeyXZ(epearl);
				if(key != null) putPearlOwner(key, pdc, /*keyIsUUID=*/false);
			}
		}
		return getDynamicUsername(ownerUUID, lookupKey);
	}
}