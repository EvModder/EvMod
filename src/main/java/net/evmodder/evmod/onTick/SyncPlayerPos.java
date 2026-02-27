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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;

public final class SyncPlayerPos implements TickListener{
	private final static boolean ONLY_SHOW_PLAYERS_IN_LOADED_CHUNKS = true;
	private final static ByteBuffer bb = ByteBuffer.allocate(PlayerPosIPC.DATA_SIZE);
	private final MinecraftClient client = MinecraftClient.getInstance();

	private boolean wasNull = true;
	@Override public final void onTickEnd(final MinecraftClient client){
		final PlayerEntity player = client.player;
		if(player == null || client.world == null){wasNull = true; return;}
		if(wasNull){wasNull = false; Main.LOGGER.info("[EvMod] Registered SyncPlayerPos for player: "+client.player.getName().getString());}
		bb.putInt(MiscUtils.getServerAddressHashCode());
		bb.putInt(MiscUtils.getDimensionId(client.world));
		bb.putLong(player.getUuid().getMostSignificantBits()).putLong(player.getUuid().getLeastSignificantBits());
		bb.putDouble(player.getX()).putDouble(player.getY()).putDouble(player.getZ());
		bb.putFloat(player.getYaw()).putFloat(player.getPitch()).putFloat(player.getHeadYaw());
		bb.putDouble(player.getVelocity().getX()).putDouble(player.getVelocity().getY()).putDouble(player.getVelocity().getZ());
		bb.putInt(player.getPose().getIndex());
//		bb.putFloat(player.getHealth());
		PlayerPosIPC.getInstance().postData(bb.array());
		bb.rewind();
	}

	private final HashMap<UUID, OtherClientPlayerEntity> fakePlayers = new HashMap<>();
	public final boolean removeFakePlayer(final UUID uuid){ // Accessor: MixinClientPlayNetworkHandler
		final OtherClientPlayerEntity dummy = fakePlayers.remove(uuid);
		if(dummy != null) dummy.discard();
		return dummy != null;
	}

	private int NEXT_DUMMY_ID = -1000; // Custom ID for the client-side entity
	public SyncPlayerPos(){
		ClientPlayConnectionEvents.DISCONNECT.register((_handler, _client) -> fakePlayers.clear());
		//WorldRenderEvents.AFTER_ENTITIES.register(context -> {
		ClientTickEvents.END_WORLD_TICK.register(world -> {
			final int myServerHash = MiscUtils.getServerAddressHashCode(), myWorldHash = MiscUtils.getDimensionId(world);
			PlayerPosIPC.getInstance().readData(b -> {
//				if(client.getNetworkHandler() == null) return;
				final ByteBuffer bb = ByteBuffer.wrap(b);
				final int serverHash = bb.getInt(), worldHash = bb.getInt();
				final UUID uuid = new UUID(bb.getLong(), bb.getLong());
				if(serverHash != myServerHash || worldHash != myWorldHash){
					if(removeFakePlayer(uuid)) Main.LOGGER.info("[EvMod] Removed dummy player (different world): "+uuid);
					return;
				}
				final PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(uuid);
				if(entry == null){ // Not online!
					if(removeFakePlayer(uuid)) Main.LOGGER.info("[EvMod] Removed dummy player (not online): "+uuid);
					return;
				}
				final PlayerEntity existingPlayer1 = world.getPlayerByUuid(uuid);
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
				if(!world.getChunkManager().isChunkLoaded(((int)x) >> 4, ((int)z) >> 4)){
					if(ONLY_SHOW_PLAYERS_IN_LOADED_CHUNKS){
						if(removeFakePlayer(uuid)) Main.LOGGER.info("[EvMod] Removed dummy player (unloaded chunks): "+entry.getProfile().getName());
						return;
					}
				}
				final float yaw = bb.getFloat(), pitch = bb.getFloat();
//				final double velX = bb.getDouble(), velY = bb.getDouble(), velZ = bb.getDouble();
				final OtherClientPlayerEntity dummy = fakePlayers.computeIfAbsent(uuid, _0->{
					final OtherClientPlayerEntity d = new OtherClientPlayerEntity(world, entry.getProfile());
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
					d.refreshPositionAndAngles(x, y, z, yaw, pitch);
//					d.resetPosition(); // Sets prev X,Y,Z,yaw,pitch - already called by refreshPositionAndAngles()
					world.addEntity(d); // Inject into world
					return d;
				});
				dummy.setHeadYaw(bb.getFloat());
				dummy.setVelocity(bb.getDouble(), bb.getDouble(), bb.getDouble());
				dummy.setPose(EntityPose.INDEX_TO_VALUE.apply(bb.getInt()));
				dummy.updateTrackedPositionAndAngles(x, y, z, yaw, pitch, 0);
				dummy.updatePositionAndAngles(x, y, z, yaw, pitch);
//				dummy.setHealth(bb.getFloat());
				dummy.tick();
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
		});
	}
}