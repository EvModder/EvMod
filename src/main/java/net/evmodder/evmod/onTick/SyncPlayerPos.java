package net.evmodder.evmod.onTick;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.UUID;
import net.evmodder.evmod.Main;
import net.evmodder.evmod.apis.MiscUtils;
import net.evmodder.evmod.apis.PlayerPosIPC;
import net.evmodder.evmod.apis.TickListener;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

public final class SyncPlayerPos implements TickListener{
	private final static boolean ONLY_SHOW_PLAYERS_IN_LOADED_CHUNKS = true;
	private final static long STALE_TIMEOUT_NS = 15_000l * 1000000l;
	private final static ByteBuffer bb = ByteBuffer.allocate(PlayerPosIPC.DATA_SIZE);
	private final Minecraft client = Minecraft.getInstance();

	private ClientLevel syncLevel;
	private int syncServerHash, syncWorldHash;

	private static final int getSyncWorldHash(final ClientLevel world){
		final int vanillaId = MiscUtils.getDimensionId(world);
		if(vanillaId != 3) return vanillaId;
		//? >=1.21.11 {
		return world.dimension().identifier().hashCode();
		//?} else {
		/*return world.dimension().location().hashCode();*/
		//?}
	}

	private final void updateSyncLevel(final ClientLevel world){
		if(syncLevel == world) return;
		clearFakePlayers();
		syncLevel = world;
		syncServerHash = MiscUtils.getServerAddressHashCode();
		syncWorldHash = getSyncWorldHash(world);
	}

	private boolean wasNull = true;
	@Override public final void onTickEnd(final Minecraft client){
		final Player player = client.player;
		if(player == null || client.level == null){wasNull = true; return;}
		if(wasNull){wasNull = false; Main.LOGGER.info("[EvMod] Registered SyncPlayerPos for player: "+client.player.getName().getString());}
		updateSyncLevel(client.level);
		bb.putInt(syncServerHash);
		bb.putInt(syncWorldHash);
		bb.putLong(player.getUUID().getMostSignificantBits()).putLong(player.getUUID().getLeastSignificantBits());
		bb.putDouble(player.getX()).putDouble(player.getY()).putDouble(player.getZ());
		bb.putFloat(player.getYRot()).putFloat(player.getXRot()).putFloat(player.getYHeadRot());
		bb.putDouble(player.getDeltaMovement().x()).putDouble(player.getDeltaMovement().y()).putDouble(player.getDeltaMovement().z());
		bb.putInt(player.getPose().id());
//		bb.putFloat(player.getHealth());
		PlayerPosIPC.getInstance().postData(bb.array());
		bb.rewind();
	}

	private static final class SyncedPlayer{
		final RemotePlayer entity;
		long lastUpdateNs;

		SyncedPlayer(final RemotePlayer entity, final long lastUpdateNs){
			this.entity = entity;
			this.lastUpdateNs = lastUpdateNs;
		}
	}

	private final HashMap<UUID, SyncedPlayer> fakePlayers = new HashMap<>();
	private final void clearFakePlayers(){
		fakePlayers.values().forEach(dummy -> dummy.entity.discard());
		fakePlayers.clear();
	}

	public final boolean removeFakePlayer(final UUID uuid){ // Accessor: MixinClientPlayNetworkHandler
		final SyncedPlayer dummy = fakePlayers.remove(uuid);
		if(dummy != null) dummy.entity.discard();
		return dummy != null;
	}

	private final void removeStaleFakePlayers(final long now){
		for(final var it = fakePlayers.entrySet().iterator(); it.hasNext();){
			final var entry = it.next();
			if(now - entry.getValue().lastUpdateNs < STALE_TIMEOUT_NS) continue;
			entry.getValue().entity.discard();
			Main.LOGGER.info("[EvMod] Removed stale dummy player: "+entry.getKey());
			it.remove();
		}
	}

	private int NEXT_DUMMY_ID = -1000; // Custom ID for the client-side entity
	public SyncPlayerPos(){
		ClientPlayConnectionEvents.DISCONNECT.register((ClientPacketListener _, Minecraft _) ->{
			clearFakePlayers();
			syncLevel = null;
		});
		//WorldRenderEvents.AFTER_ENTITIES.register(context -> {
		ClientTickEvents.END_LEVEL_TICK.register(world -> {
			updateSyncLevel(world);
			final long now = System.nanoTime();
			PlayerPosIPC.getInstance().readData(b -> {
//				if(client.getNetworkHandler() == null) return;
				final ByteBuffer bb = ByteBuffer.wrap(b);
				final int serverHash = bb.getInt(), worldHash = bb.getInt();
				final UUID uuid = new UUID(bb.getLong(), bb.getLong());
				if(serverHash != syncServerHash || worldHash != syncWorldHash){
					if(removeFakePlayer(uuid)) Main.LOGGER.info("[EvMod] Removed dummy player (different world): "+uuid);
					return;
				}
				final PlayerInfo entry = client.getConnection().getPlayerInfo(uuid);
				if(entry == null){ // Not online!
					if(removeFakePlayer(uuid)) Main.LOGGER.info("[EvMod] Removed dummy player (not online): "+uuid);
					return;
				}
				final Player existingPlayer1 = world.getPlayerByUUID(uuid);
				if(existingPlayer1 != null && existingPlayer1.getId() >= 0){ // Already loaded 1
					if(removeFakePlayer(uuid)) Main.LOGGER.info("[EvMod] Removed dummy player (real player loaded 1): "+existingPlayer1.getName().getString());
					return;
				}
				/*final PlayerEntity existingPlayer2 = world.getPlayers().stream().filter(p -> p.getUuid().equals(uuid) && p.getId() >= 0).findAny().orElse(null);
				if(existingPlayer2 != null){ // Already loaded 2
					if(removeFakePlayer(uuid)) Main.LOGGER.info("[EvMod] Removed dummy player (real player loaded 2): "+existingPlayer2.getName().getString());
					return;
				}*/
				final double x = bb.getDouble(), y = bb.getDouble(), z = bb.getDouble();
				if(!world.getChunkSource().hasChunk(Mth.floor(x) >> 4, Mth.floor(z) >> 4)){
					if(ONLY_SHOW_PLAYERS_IN_LOADED_CHUNKS){
						if(removeFakePlayer(uuid)) Main.LOGGER.info("[EvMod] Removed dummy player (unloaded chunks): "+entry.getProfile().name());
						return;
					}
				}
				final float yaw = bb.getFloat(), pitch = bb.getFloat();
//				final double velX = bb.getDouble(), velY = bb.getDouble(), velZ = bb.getDouble();
				final SyncedPlayer syncedPlayer = fakePlayers.computeIfAbsent(uuid, (UUID _)->{
					final RemotePlayer d = new RemotePlayer(world, entry.getProfile());
					d.setId(--NEXT_DUMMY_ID);
					Main.LOGGER.info(String.format("[EvMod] Adding dummy player '%s' at %d %d %d", d.getName().getString(), (int)x, (int)y, (int)z));
//					d.getDataTracker().set(net.minecraft.entity.player.PlayerEntity.PLAYER_MODEL_PARTS, (byte)0x7F);
					d.setInvisible(false);
//					d.unsetRemoved();
//					d.revive();
//					final SkinTextures textures = entry.getSkinTextures();
//					final boolean skinHasHat = textures.secure() && textures.texture() != null; 
//					d.getSkinTextures()
//					d.getDataTracker().set(PlayerEntity., modelParts);
					//? >=1.21.11 {
					d.snapTo(x, y, z, yaw, pitch);
					//?} else {
					/*d.moveTo(x, y, z, yaw, pitch);*/
					//?}
//					d.resetPosition(); // Sets prev X,Y,Z,yaw,pitch - already called by refreshPositionAndAngles()
					world.addEntity(d); // Inject into world
					return new SyncedPlayer(d, now);
				});
				syncedPlayer.lastUpdateNs = now;
				final RemotePlayer dummy = syncedPlayer.entity;
				dummy.setYHeadRot(bb.getFloat());
				dummy.setDeltaMovement(bb.getDouble(), bb.getDouble(), bb.getDouble());
				dummy.setPose(Pose.BY_ID.apply(bb.getInt()));
				dummy.setPos(x, y, z);
				dummy.setYRot(yaw);
				dummy.setXRot(pitch);
//				dummy.setHealth(bb.getFloat());
//				final int light;
//				if(client.world.getChunkManager().isChunkLoaded(bp.getX() >> 4, bp.getZ() >> 4)){
//					light = WorldRenderer.getLightmapCoordinates(client.world, bp);
//				}
//				else light = 0xF000F0; // Full brightness lightmap
//				final float tickDelta = context.tickCounter().getTickDelta(true);
//				final MatrixStack matrices = context.matrixStack();
//				final Vec3d cameraPos = context.camera().getPos();
//				matrices.push();
//				matrices.translate(x - cameraPos.x, y - cameraPos.y, z - cameraPos.z);
//				client.getEntityRenderDispatcher().render(dummy, 0d, 0d, 0d, tickDelta, matrices, context.consumers(), light);
//				matrices.pop();
			});
			removeStaleFakePlayers(now);
		});
	}
}