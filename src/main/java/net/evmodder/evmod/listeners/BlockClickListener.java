package net.evmodder.evmod.listeners;

import java.nio.ByteBuffer;
import java.util.UUID;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.apis.MiscUtils;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

public final class BlockClickListener{
//	public static BlockPos lastClickedBlock;
	public static UUID lastClickedBlockHash; // TODO: Ewwww public static :(
	private final ByteBuffer posData;

	private final UUID getIdForBlockPos(final Level world, final BlockPos pos){
		final byte dim = MiscUtils.getDimensionId(world);
		posData.rewind();
		posData.put(dim).putInt(pos.getX()).putInt(pos.getY()).putInt(pos.getZ()).array();
		return UUID.nameUUIDFromBytes(posData.array());
	}

	public BlockClickListener(){ // TODO: currently called by ContainerOpenCloseListener
		posData = ByteBuffer.allocate(13);
		// TODO: add later phase, after ActionResult is determined to be PASS
		UseBlockCallback.EVENT.register((Player _, Level world, InteractionHand _, BlockHitResult hitResult) -> {
			if(Configs.Generic.MAP_CACHE_BY_CONTAINER_POS.getBooleanValue()){
//				lastClickedBlock = hitResult.getBlockPos();
				lastClickedBlockHash = getIdForBlockPos(world, hitResult.getBlockPos());
			}
			return InteractionResult.PASS;
		});
	}
}