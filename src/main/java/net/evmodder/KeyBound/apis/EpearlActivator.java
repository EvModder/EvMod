package net.evmodder.KeyBound.apis;

import java.util.Arrays;
import java.util.Objects;
import com.google.common.collect.Streams;
import net.evmodder.KeyBound.Main;
import net.minecraft.block.BlockState;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;

public class EpearlActivator{
	private final int REACH = 10;
	private final boolean msgFailureFeedback = true;
	private final long msgCooldown = 1000*5;
	private long lastMsgTs;

	public boolean hasLineOfSight(MinecraftClient client, Vec3d from, Vec3d to){
		return client.world.raycast(
				new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, client.player))
				.getType() == HitResult.Type.MISS;
	}

	/**
	 * Returns everything you need to break a block at the given position, such
	 * as which side to face, the exact hit vector to face that side, the
	 * squared distance to that hit vector, and whether or not there is line of
	 * sight to that hit vector.
	 */
	public BlockHitResult getHitResult(MinecraftClient client, BlockPos pos){
		Vec3d eyes = client.player.getEyePos();
		Direction[] sides = Direction.values();

		BlockState state = client.world.getBlockState(pos);
		VoxelShape shape = state.getOutlineShape(client.world, pos);
		if(shape.isEmpty()){Main.LOGGER.error("AutoPearlActivator: shape.isEmpty()!"); return null;}

		Box box = shape.getBoundingBox();
		Vec3d halfSize = new Vec3d(box.maxX - box.minX, box.maxY - box.minY, box.maxZ - box.minZ).multiply(0.5);
		Vec3d center = Vec3d.of(pos).add(box.getCenter());

		Vec3d[] hitVecs = new Vec3d[sides.length];
		for(int i=0; i<sides.length; ++i){
			Vec3i dirVec = sides[i].getVector();
			Vec3d relHitVec = new Vec3d(halfSize.x * dirVec.getX(), halfSize.y * dirVec.getY(), halfSize.z * dirVec.getZ());
			hitVecs[i] = center.add(relHitVec);
		}

		double distSqToCenter = eyes.squaredDistanceTo(center);

		int bestSide = 0;
		double bestDistSq = eyes.squaredDistanceTo(hitVecs[0]);
		boolean bestHasLoS = bestDistSq < distSqToCenter && hasLineOfSight(client, eyes, hitVecs[0]);
		for(int i=1; i<sides.length; ++i){
			final double distSq = eyes.squaredDistanceTo(hitVecs[0]);
			final boolean hasLoS = distSq < distSqToCenter && hasLineOfSight(client, eyes, hitVecs[0]);
			if(!hasLoS && bestHasLoS) continue;
			if(hasLoS && !bestHasLoS){bestSide = i; bestDistSq = distSq; bestHasLoS = true;} // Prefer LoS
			else if(distSq < bestDistSq){bestSide = i; bestDistSq = distSq;} // Prefer closest side
		}

		return new BlockHitResult(hitVecs[bestSide], sides[bestSide], pos, /*insideBlock=*/false);
	}

	private BlockPos findNearestPearlWithOwnerName(MinecraftClient client, String name){
		final Vec3d playerPos = client.player.getPos();
		double closestDistSq = Double.MAX_VALUE;
		Vec3d closestPos = null;
		for(EnderPearlEntity pearl : client.world.getEntitiesByClass(EnderPearlEntity.class,
				client.player.getBoundingBox().expand(REACH, REACH, REACH),
				pearl->name.equalsIgnoreCase(Main.epearlLookup.getOwnerName(pearl)))
		){
			final double distSq = pearl.getPos().squaredDistanceTo(playerPos);
			if(distSq < closestDistSq){closestDistSq = distSq; closestPos = pearl.getPos();}
		}
		return BlockPos.ofFloored(closestPos);
	}
	private BlockPos findSignWithName(MinecraftClient client, String name){
		final BlockPos playerPos = client.player.getBlockPos();
		for(BlockPos pos : BlockPos.iterateOutwards(playerPos, REACH, REACH, REACH)){
			if(client.world.getBlockEntity(pos) instanceof SignBlockEntity sbe &&
					Streams.concat(Arrays.stream(sbe.getFrontText().getMessages(/*filtered=*/false)), Arrays.stream(sbe.getBackText().getMessages(false))
					).map(Text::getLiteralString)
					.filter(Objects::nonNull)
					.map(s -> s.replaceAll("[^a-zA-Z0-9_]+", ""))
					.anyMatch(s -> s.equalsIgnoreCase(name)))
				{
					return pos;
				}
		}
		return null;
	}
	private BlockPos findNearestButton/*OrNoteblock*/(MinecraftClient client, BlockPos startPos){ // TODO: trapdoor, noteblock
		double closestDistSq = Double.MAX_VALUE;
		BlockPos buttonPos = null;
		for(BlockPos pos : BlockPos.iterateOutwards(startPos, REACH, REACH, REACH)){
			if(client.world.getBlockState(pos).getBlock() instanceof ButtonBlock){
				final double distSq = pos.getSquaredDistance(startPos);
				if(distSq < closestDistSq){closestDistSq = distSq; buttonPos = pos.mutableCopy();}
			}
		}
		return buttonPos;
	}

	private void sendFeedback(MinecraftClient client, final String who, final String msg){
		if(!msgFailureFeedback) return;
		if(msgCooldown > 0){
			final long currTs = System.currentTimeMillis();
			if(currTs-lastMsgTs < msgCooldown) return;
			lastMsgTs = currTs;
		}
		client.getNetworkHandler().sendChatCommand("/w "+who+" "+msg);
	}

	public void triggerPearl(final String who){
		MinecraftClient client = MinecraftClient.getInstance();
		BlockPos signPos = findSignWithName(client, who);
		if(signPos == null && (signPos=findNearestPearlWithOwnerName(client, who)) == null){
			sendFeedback(client, who, "[automated response] I do not recognize any pearl of yours nearby");
			return;
		}
//		Main.LOGGER.info("AutoPearlActivator: found sign/pearl");// at "+signPos.toShortString());

		BlockPos buttonPos = findNearestButton(client, signPos);
		if(buttonPos == null){
//			sendFeedback(client, name, "[automated response] I see your pearl, but not how to activate it");
			return;
		}
//		Main.LOGGER.info("AutoPearlActivator: found button at "+buttonPos.toShortString());

//		assert hitResult != null;
//		if(hitResult == null) return;
		client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, getHitResult(client, buttonPos));
//		Main.LOGGER.info("AutoPearlActivator: button pressed!");
	}
}