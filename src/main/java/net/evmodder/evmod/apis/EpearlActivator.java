package net.evmodder.evmod.apis;

//? <26.3 {
/*import java.util.Arrays;*/
//?}
import java.util.Objects;
import com.google.common.collect.Streams;
import net.evmodder.evmod.Main;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
//? >=26.3 {
import net.minecraft.world.level.block.entity.SignTextSlot;
//?}
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class EpearlActivator{
	private final EpearlLookupFabric epearlLookup;
	private final int REACH = 10;
	private final boolean msgFailureFeedback = true;
	private final long msgCooldown = 1000*5;
	private long lastMsgTs;

	public EpearlActivator(EpearlLookupFabric epl){epearlLookup = epl;}

	private final boolean hasLineOfSight(Minecraft client, Vec3 from, Vec3 to){
		return client.level.clip(
				new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, client.player))
				.getType() == HitResult.Type.MISS;
	}

	/**
	 * Returns everything you need to break a block at the given position, such
	 * as which side to face, the exact hit vector to face that side, the
	 * squared distance to that hit vector, and whether or not there is line of
	 * sight to that hit vector.
	 */
	private final BlockHitResult getHitResult(Minecraft client, BlockPos pos){
		Vec3 eyes = client.player.getEyePosition();
		Direction[] sides = Direction.values();

		BlockState state = client.level.getBlockState(pos);
		VoxelShape shape = state.getShape(client.level, pos);
		if(shape.isEmpty()){Main.LOGGER.error("AutoPearlActivator: shape.isEmpty()!"); return null;}

		AABB box = shape.bounds();
		Vec3 halfSize = new Vec3(box.maxX - box.minX, box.maxY - box.minY, box.maxZ - box.minZ).scale(0.5);
		Vec3 center = Vec3.atLowerCornerOf(pos).add(box.getCenter());

		Vec3[] hitVecs = new Vec3[sides.length];
		for(int i=0; i<sides.length; ++i){
			Vec3i dirVec = sides[i].getUnitVec3i();
			Vec3 relHitVec = new Vec3(halfSize.x * dirVec.getX(), halfSize.y * dirVec.getY(), halfSize.z * dirVec.getZ());
			hitVecs[i] = center.add(relHitVec);
		}

		double distSqToCenter = eyes.distanceToSqr(center);

		int bestSide = 0;
		double bestDistSq = eyes.distanceToSqr(hitVecs[0]);
		boolean bestHasLoS = bestDistSq < distSqToCenter && hasLineOfSight(client, eyes, hitVecs[0]);
		for(int i=1; i<sides.length; ++i){
			final double distSq = eyes.distanceToSqr(hitVecs[0]);
			final boolean hasLoS = distSq < distSqToCenter && hasLineOfSight(client, eyes, hitVecs[0]);
			if(!hasLoS && bestHasLoS) continue;
			if(hasLoS && !bestHasLoS){bestSide = i; bestDistSq = distSq; bestHasLoS = true;} // Prefer LoS
			else if(distSq < bestDistSq){bestSide = i; bestDistSq = distSq;} // Prefer closest side
		}

		return new BlockHitResult(hitVecs[bestSide], sides[bestSide], pos, /*insideBlock=*/false);
	}

	private final BlockPos findNearestPearlWithOwnerName(Minecraft client, String name){
		final Vec3 playerPos = client.player.position();
		double closestDistSq = Double.MAX_VALUE;
		Vec3 closestPos = null;
		for(ThrownEnderpearl pearl : client.level.getEntitiesOfClass(ThrownEnderpearl.class,
				client.player.getBoundingBox().inflate(REACH, REACH, REACH),
				pearl->name.equalsIgnoreCase(epearlLookup.getOwnerName(pearl)))
		){
			final double distSq = pearl.position().distanceToSqr(playerPos);
			if(distSq < closestDistSq){closestDistSq = distSq; closestPos = pearl.position();}
		}
		return BlockPos.containing(closestPos);
	}
	private final BlockPos findSignWithName(Minecraft client, String name){
		final BlockPos playerPos = client.player.blockPosition();
		for(BlockPos pos : BlockPos.withinBoxByManhattanDistance(playerPos, REACH, REACH, REACH)){
			if(client.level.getBlockEntity(pos) instanceof SignBlockEntity sbe &&
					//? >=26.3 {
					Streams.concat(sbe.getText(SignTextSlot.FRONT).getMessages(false).stream(), sbe.getText(SignTextSlot.BACK).getMessages(false).stream()
					//?} else {
					/*Streams.concat(Arrays.stream(sbe.getFrontText().getMessages(/^filtered=^/false)), Arrays.stream(sbe.getBackText().getMessages(false))
					*///?}
					).map(Component::tryCollapseToString)
					.filter(Objects::nonNull)
					.map(s -> s.replaceAll("[^a-zA-Z0-9_]+", ""))
					.anyMatch(s -> s.equalsIgnoreCase(name)))
				{
					return pos;
				}
		}
		return null;
	}
	private final boolean isClickableTrigger(final BlockState bs){
		return bs.getBlock() instanceof NoteBlock || bs.is(BlockTags.BUTTONS) || bs.is(BlockTags.WOODEN_TRAPDOORS);
	}
	private final BlockPos findNearestTrigger(Minecraft client, BlockPos startPos){
		double closestDistSq = Double.MAX_VALUE;
		BlockPos buttonPos = null;
		final Vec3 centerPos = Vec3.atCenterOf(startPos);
		for(BlockPos pos : BlockPos.withinBoxByManhattanDistance(startPos, REACH, REACH, REACH)){
			BlockState bs = client.level.getBlockState(pos);
			if(isClickableTrigger(bs)){
				Vec3 closestPoint = bs.getShape(client.level, pos).closestPointTo(centerPos).get();
				final double distSq = closestPoint.distanceToSqr(centerPos);
				if(distSq < closestDistSq){closestDistSq = distSq; buttonPos = pos.mutable();}
			}
		}
		return buttonPos;
	}

	private final void sendFeedback(Minecraft client, final String who, final String msg){
		if(!msgFailureFeedback) return;
		if(msgCooldown > 0){
			final long currTs = System.currentTimeMillis();
			if(currTs-lastMsgTs < msgCooldown) return;
			lastMsgTs = currTs;
		}
		client.getConnection().sendCommand("w "+who+" "+msg);
	}

	public final void triggerPearl(final String who){
		Minecraft client = Minecraft.getInstance();
		BlockPos signPos = findSignWithName(client, who);
		if(signPos == null && (signPos=findNearestPearlWithOwnerName(client, who)) == null){
//			sendFeedback(client, who, "[AutoPearl] I do not recognize any pearl of yours nearby");
			Main.LOGGER.warn("[AutoPearl] no nearby pearl found for requester: "+who);
			return;
		}
//		Main.LOGGER.info("[AutoPearl]: found sign/pearl");// at "+signPos.toShortString());

		BlockPos buttonPos = findNearestTrigger(client, signPos);
		if(buttonPos == null){
			sendFeedback(client, who, "[AutoPearl] I see your pearl, but not how to activate it");
			Main.LOGGER.warn("[AutoPearl] pearl found, but activation not found");
			return;
		}
		Main.LOGGER.info("[AutoPearl]: found button at "+buttonPos.toShortString());

//		assert hitResult != null;
//		if(hitResult == null) return;
		client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, getHitResult(client, buttonPos));
//		Main.LOGGER.info("AutoPearlActivator: button pressed!");
	}
}