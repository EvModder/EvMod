package net.evmodder.evmod.apis;

//? <26.3 {
/*import java.util.Arrays;*/
//?}
import java.util.Objects;
import java.util.UUID;
import com.google.common.collect.Streams;
import net.evmodder.evmod.Main;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
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

	private final BlockHitResult visibleHit(Minecraft client, BlockPos pos, Vec3 eyes, Vec3 point, double reach){
		final Vec3 end = eyes.add(point.subtract(eyes).normalize().scale(reach));
		final BlockHitResult hit = client.level.clip(
				new ClipContext(eyes, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, client.player));
		return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos) ? hit : null;
	}

	/** Prefer visible interaction-shape hits; otherwise use the nearest surface within normal reach. */
	private final BlockHitResult getHitResult(Minecraft client, BlockPos pos){
		final Vec3 eyes = client.player.getEyePosition();
		final double reach = client.player.blockInteractionRange();
		final VoxelShape shape = client.level.getBlockState(pos).getShape(client.level, pos);
		if(shape.isEmpty()){Main.LOGGER.error("AutoPearlActivator: shape.isEmpty()!"); return null;}
		BlockHitResult fallback = null;
		for(AABB box : shape.toAabbs()){
			final Vec3 nearest = new Vec3(pos.getX()+Math.clamp(eyes.x-pos.getX(), box.minX+1e-4, box.maxX-1e-4),
					pos.getY()+Math.clamp(eyes.y-pos.getY(), box.minY+1e-4, box.maxY-1e-4),
					pos.getZ()+Math.clamp(eyes.z-pos.getZ(), box.minZ+1e-4, box.maxZ-1e-4));
			// Raycast the actual shape, not its enclosing bounds (open trapdoors/buttons can be thin).
			final BlockHitResult nearestHit = shape.clip(eyes, eyes.add(nearest.subtract(eyes).normalize().scale(reach)), pos);
			if(nearestHit == null) continue;
			if(fallback == null || eyes.distanceToSqr(nearestHit.getLocation()) < eyes.distanceToSqr(fallback.getLocation())) fallback = nearestHit;
			final BlockHitResult visible = visibleHit(client, pos, eyes, nearest, reach);
			if(visible != null) return visible;
			// Sample faces and edges for partially exposed controls before allowing an obstructed click.
			for(int x=0; x<=4; ++x) for(int y=0; y<=4; ++y) for(int z=0; z<=4; ++z){
				if(x > 0 && x < 4 && y > 0 && y < 4 && z > 0 && z < 4) continue;
				final Vec3 point = new Vec3(pos.getX()+box.minX+1e-4+(box.maxX-box.minX-2e-4)*x/4,
						pos.getY()+box.minY+1e-4+(box.maxY-box.minY-2e-4)*y/4,
						pos.getZ()+box.minZ+1e-4+(box.maxZ-box.minZ-2e-4)*z/4);
				final BlockHitResult hit = visibleHit(client, pos, eyes, point, reach);
				if(hit != null) return hit;
			}
		}
		return fallback;
	}

	private final BlockPos findNearestPearlWithOwnerName(Minecraft client, String name, UUID owner){
		final Vec3 playerPos = client.player.position();
		double closestDistSq = Double.MAX_VALUE;
		Vec3 closestPos = null;
		for(ThrownEnderpearl pearl : client.level.getEntitiesOfClass(ThrownEnderpearl.class,
				client.player.getBoundingBox().inflate(REACH, REACH, REACH),
				pearl->{
					if(pearl.isRemoved()) return false;
					final String knownName = epearlLookup.getOwnerName(pearl);
					return owner == null ? name.equalsIgnoreCase(knownName) : owner.equals(MiscUtils.getPearlUUID(pearl));
				})
		){
			final double distSq = pearl.position().distanceToSqr(playerPos);
			if(distSq < closestDistSq){closestDistSq = distSq; closestPos = pearl.position();}
		}
		return closestPos == null ? null : BlockPos.containing(closestPos);
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
	private boolean canPullOwnPearl(final Minecraft client, final UUID sender){
		final BlockPos pearl = findNearestPearlWithOwnerName(client, "", sender);
		if(pearl == null) return false;
		final BlockPos trigger = findNearestTrigger(client, pearl);
		return trigger != null && getHitResult(client, trigger) != null;
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

	public final void triggerPearl(final String who, final PearlWhisperRequest request){
		Minecraft client = Minecraft.getInstance();
		final boolean thirdParty = !who.equalsIgnoreCase(request.target());
		final var player = client.getConnection().getOnlinePlayers().stream()
				.filter(info -> info.getProfile().name().equalsIgnoreCase(request.target())).findFirst().orElse(null);
		if(thirdParty && (player == null || player.getProfile().id().equals(client.player.getUUID()))) return;
		if(thirdParty){
			final var sender = client.getConnection().getOnlinePlayers().stream()
					.filter(info -> info.getProfile().name().equalsIgnoreCase(who)).findFirst().orElse(null);
			if(sender == null || !canPullOwnPearl(client, sender.getProfile().id())) return;
		}
		final String name = player == null ? who : player.getProfile().name();
		final UUID owner = player == null ? null : player.getProfile().id();
		// Third-party requests require an owned, loaded pearl, not just a sign bearing that name.
		BlockPos signPos = thirdParty ? findNearestPearlWithOwnerName(client, name, owner) : findSignWithName(client, name);
		final boolean fromSign = !thirdParty && signPos != null;
		if(signPos == null && !thirdParty) signPos = findNearestPearlWithOwnerName(client, name, owner);
		if(signPos == null){
//			sendFeedback(client, who, "[AutoPearl] I do not recognize any pearl of yours nearby");
			Main.LOGGER.warn("[AutoPearl] no nearby pearl found for requester: "+who);
			return;
		}
//		Main.LOGGER.info("[AutoPearl]: found sign/pearl");// at "+signPos.toShortString());

		BlockPos buttonPos = findNearestTrigger(client, signPos);
		if(buttonPos == null){
			if(!fromSign || findNearestPearlWithOwnerName(client, name, owner) != null)
				sendFeedback(client, who, "[AutoPearl] I see "+name+"'s pearl, but not how to activate it");
			Main.LOGGER.warn("[AutoPearl] pearl found, but activation not found");
			return;
		}
		Main.LOGGER.info("[AutoPearl]: found button at "+buttonPos.toShortString());

		final BlockHitResult hitResult = getHitResult(client, buttonPos);
		if(hitResult == null){
			if(!fromSign || findNearestPearlWithOwnerName(client, name, owner) != null)
				sendFeedback(client, who, "[AutoPearl] "+name+"'s pearl trigger is out of reach");
			return;
		}
		if(thirdParty && !request.confirmed()){
			sendFeedback(client, who, request.confirmationPrompt(name));
			return;
		}
		client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hitResult);
//		Main.LOGGER.info("AutoPearlActivator: button pressed!");
	}
}