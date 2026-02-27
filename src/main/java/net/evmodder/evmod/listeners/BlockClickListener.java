package net.evmodder.evmod.listeners;

import java.nio.ByteBuffer;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class BlockClickListener{
	private BlockClickListener(){}

//	public static BlockPos lastClickedBlock;
	public static UUID lastClickedBlockHash; // TODO: Ewwww public static :(

	private static final UUID getIdForBlockPos(World world, BlockPos pos){
		final byte dim;
		if(world == null) dim = -1;
		else if(world.getRegistryKey() == World.OVERWORLD) dim = 0;
		else if(world.getRegistryKey() == World.NETHER) dim = 1;
		else if(world.getRegistryKey() == World.END) dim = 2;
		else dim = 3;
		final byte[] bytes = ByteBuffer.allocate(13).put(dim).putInt(pos.getX()).putInt(pos.getY()).putInt(pos.getZ()).array();
		return UUID.nameUUIDFromBytes(bytes);
	}

	public static final void register(){
		// TODO: add later phase, after ActionResult is determined to be PASS
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
//			lastClickedBlock = hitResult.getBlockPos();
			lastClickedBlockHash = getIdForBlockPos(world, hitResult.getBlockPos());
			return ActionResult.PASS;
		});
	}
}