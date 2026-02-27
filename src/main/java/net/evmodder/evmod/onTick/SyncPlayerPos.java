package net.evmodder.evmod.onTick;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.UUID;
import net.evmodder.evmod.apis.MiscUtils;
import net.evmodder.evmod.apis.PlayerPosIPC;
import net.evmodder.evmod.apis.TickListener;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class SyncPlayerPos implements TickListener{
	private final static boolean ONLY_SHOW_PLAYERS_IN_LOADED_CHUNKS = true;
	private final static ByteBuffer bb = ByteBuffer.allocate(PlayerPosIPC.DATA_SIZE);
	private final MinecraftClient client = MinecraftClient.getInstance();

	@Override public final void onTickEnd(final MinecraftClient client){
		if(client.player == null || client.world == null) return;
		bb.putInt(MiscUtils.getServerAddressHashCode());
		bb.putInt(MiscUtils.getDimensionId(client.world));
		bb.putLong(client.player.getUuid().getMostSignificantBits()).putLong(client.player.getUuid().getLeastSignificantBits());
		bb.putDouble(client.player.getX()).putDouble(client.player.getY()).putDouble(client.player.getZ());
		PlayerPosIPC.getInstance().postData(bb.array());
		bb.rewind();
	}

	public SyncPlayerPos(){
		final HashMap<UUID, OtherClientPlayerEntity> fakePlayers = new HashMap<>();
		WorldRenderEvents.AFTER_ENTITIES.register(context -> {
			if (client.world == null || client.getNetworkHandler() == null) return;

			final int myServer = MiscUtils.getServerAddressHashCode(), myWorld = MiscUtils.getDimensionId(client.world);
			PlayerPosIPC.getInstance().readData(b -> {
				final ByteBuffer bb = ByteBuffer.wrap(b);
				final int server = bb.getInt(), world = bb.getInt();
				if(server != myServer || world != myWorld) return;
				final UUID uuid = new UUID(bb.getLong(), bb.getLong());
				if(client.world.getPlayerByUuid(uuid) != null) return; // Already loaded
				final PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(uuid);
				if(entry == null) return; // Not online!
				final double x = bb.getDouble(), y = bb.getDouble(), z = bb.getDouble();
				final BlockPos bp = BlockPos.ofFloored(x, y, z);
				final int light;
				if(!client.world.getChunkManager().isChunkLoaded(bp.getX() >> 4, bp.getZ() >> 4)){
					if(ONLY_SHOW_PLAYERS_IN_LOADED_CHUNKS) return;
					light = 0xF000F0; // Full brightness lightmap
				}
				else light = WorldRenderer.getLightmapCoordinates(client.world, bp);

				final OtherClientPlayerEntity dummy = fakePlayers.computeIfAbsent(uuid, _0->{
					OtherClientPlayerEntity d = new OtherClientPlayerEntity(client.world, entry.getProfile());
					d.prevX = x; d.prevY = y; d.prevZ = z;
					return d;
				});
				dummy.setPos(x, y, z);

				final float tickDelta = context.tickCounter().getTickDelta(true);
				final MatrixStack matrices = context.matrixStack();
				final Vec3d cameraPos = context.camera().getPos();
				matrices.push();
				matrices.translate(x - cameraPos.x, y - cameraPos.y, z - cameraPos.z);
				client.getEntityRenderDispatcher().render(dummy, 0d, 0d, 0d, tickDelta, matrices, context.consumers(), light);
				matrices.pop();
			});
		});
	}
}