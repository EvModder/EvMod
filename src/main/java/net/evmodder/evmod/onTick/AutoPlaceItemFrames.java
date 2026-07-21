package net.evmodder.evmod.onTick;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.Main;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.arguments.EntityAnchorArgument.Anchor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class AutoPlaceItemFrames{
	private Block placeAgainstBlock;
	private Item iFrameItem;
	private Direction dir;
	private int axis;

	private final double distFromPlane(BlockPos bp){
		return switch(dir){
			case UP, DOWN -> Math.abs(bp.getY() - axis);
			case EAST, WEST -> Math.abs(bp.getX() - axis);
			case NORTH, SOUTH -> Math.abs(bp.getZ() - axis);
			default -> {assert(false) : "Unreachable"; yield -1;}
		};
	}

	private final Vec3 getPlaceAgainstSurface(BlockPos wallBp){
		final Vec3 center = Vec3.atCenterOf(wallBp);
		switch(dir){
			case UP: return center.add(0, .5, 0);
			case DOWN: return center.add(0, -.5, 0);
			case EAST: return center.add(.5, 0, 0);
			case WEST: return center.add(-.5, 0, 0);
			case NORTH: return center.add(0, 0, -.5);
			case SOUTH: return center.add(0, 0, .5);

			default: assert(false) : "Unreachable"; return null;
		}
	}

	private final boolean isValidIframePlacement(BlockPos bp, Level world, List<ItemFrame> existingIfes){
		if(distFromPlane(bp) != 0) return false;
//		Main.LOGGER.info("iFramePlacer: wall block is on the plane");
		final BlockState bs = world.getBlockState(bp);
		if(Configs.Generic.IFRAME_AUTO_PLACER_MUST_MATCH_BLOCK.getBooleanValue() && bs.getBlock() != placeAgainstBlock) return false;
//		Main.LOGGER.info("iFramePlacer: wall block matches placeAgainstBlock");

		final BlockPos ifeBp = bp.relative(dir);
		final BlockState ifeBs = world.getBlockState(ifeBp);
		if(ifeBs.isCollisionShapeFullBlock(world, ifeBp)) return false;
		if(ifeBs.isRedstoneConductor(world, ifeBp)) return false; // iFrame cannot be placed inside a solid block
//		Main.LOGGER.info("iFramePlacer: ife spot is non-solid");

		if(existingIfes.stream().anyMatch(ife -> ife.blockPosition().equals(ifeBp))) return false; // Already iFrame here
//		Main.LOGGER.info("iFramePlacer: ife spot is available");
		if(Configs.Generic.IFRAME_AUTO_PLACER_MUST_CONNECT.getBooleanValue()
				&& existingIfes.stream().noneMatch(ife -> ife.blockPosition().distManhattan(ifeBp) == 1)) return false; // No iFrame neighbor
//		Main.LOGGER.info("iFramePlacer: ife spot has neighboring iframe");
		return true;
	}

	private final boolean isMovingTooFast(Vec3 velocity){
		double xzLengthSq = velocity.x*velocity.x + velocity.z*velocity.z;
		return xzLengthSq > 0.0001 || Math.abs(velocity.y) > 0.08;
	}

	private final void placeIframe(Minecraft client, BlockPos bp, InteractionHand hand){
		// Do the clicky-clicky
		if(Configs.Generic.IFRAME_AUTO_PLACER_RAYCAST.getBooleanValue()){
			BlockHitResult hitResult = new BlockHitResult(getPlaceAgainstSurface(bp), dir, bp, /*insideBlock=*/false);
			if(Configs.Generic.IFRAME_AUTO_PLACER_ROTATE_PLAYER.getBooleanValue()){
//				Vec3d playerPos = client.player.getPos();
//				float oldYaw = client.player.getYaw(), oldPitch = client.player.getPitch();
				client.player.lookAt(Anchor.EYES, hitResult.getLocation());
//				float grimYaw = client.player.getYaw(), grimPitch = client.player.getPitch();
//				client.player.setAngles(oldYaw, oldPitch);
//				client.getNetworkHandler().sendPacket(new PlayerInputC2SPacket(client.player.input.playerInput));
//				client.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(playerPos.x, playerPos.y, playerPos.z, grimYaw, grimPitch,
//						client.player.isOnGround(), client.player.horizontalCollision));
//				client.player.prevYaw = grimYaw;
//				client.player.prevPitch = grimPitch;
			}
			client.gameMode.useItemOn(client.player, hand, hitResult);
//			client.player.swingHand(Hand.MAIN_HAND, false);
//			client.getNetworkHandler().sendPacket(new HandSwingC2SPacket(hand));
		}
		else{
			BlockHitResult hitResult = new BlockHitResult(Vec3.atCenterOf(bp), dir, bp, /*insideBlock=*/true);
//			if(ROTATE_PLAYER) client.player.lookAt(EntityAnchor.EYES, hitResult.getPos());
			client.gameMode.useItemOn(client.player, hand, hitResult);
			// Airplace, basically
			client.player.swing(InteractionHand.MAIN_HAND, false);
			client.getConnection().send(new ServerboundSwingPacket(hand));
			client.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
		}
	}

	private final BlockPos[] recentPlaceAttempts;
	private int attemptIdx;
	public AutoPlaceItemFrames(){
		recentPlaceAttempts = new BlockPos[20];
		EndTick etl = (client) -> {
			if(dir == null) return; // iFramePlacer is not currently active
			if(!Configs.Generic.IFRAME_AUTO_PLACER.getBooleanValue()){
				dir = null; iFrameItem = null; placeAgainstBlock = null;
				return;
			}

			if(client.player == null || client.level == null){ // Player offline, cancel iFramePlacer
				Main.LOGGER.info("iFramePlacer: Disabling due to player offline");
				dir = null; iFrameItem = null; placeAgainstBlock = null;
				return;
			}
			// Don't spam-place in the same blockpos, give iframe entity a chance to load
			if(++attemptIdx >= recentPlaceAttempts.length) attemptIdx = 0;
			recentPlaceAttempts[attemptIdx] = null;

			if(isMovingTooFast(client.player.getDeltaMovement())) return; // Pause while player is moving

			final double MAX_REACH = Configs.Generic.IFRAME_AUTO_PLACER_REACH.getDoubleValue();
			final int SCAN_DIST = (int)(MAX_REACH+2);

			BlockPos clientBp = client.player.blockPosition();
			if(distFromPlane(clientBp) > SCAN_DIST) return; // Player out of range of iFrame wall

			AABB box = client.player.getBoundingBox().inflate(SCAN_DIST, SCAN_DIST, SCAN_DIST);
			Predicate<ItemFrame> filter = ife -> ife.getNearestViewDirection() == dir && distFromPlane(ife.blockPosition().relative(dir.getOpposite())) == 0;
			List<ItemFrame> ifes = client.level.getEntitiesOfClass(ItemFrame.class, box, filter);

			Vec3 eyePos = client.player.getEyePosition();
			Optional<BlockPos> closestValidPlacement = BlockPos.withinManhattanStream(clientBp, SCAN_DIST, SCAN_DIST, SCAN_DIST)
				.filter(bp -> isValidIframePlacement(bp, client.level, ifes))
				.filter(bp -> getPlaceAgainstSurface(bp).distanceToSqr(eyePos) <= MAX_REACH*MAX_REACH)
				.filter(bp -> Arrays.stream(recentPlaceAttempts).noneMatch(attempt -> attempt != null && bp.equals(attempt)))
				.findFirst();
			if(closestValidPlacement.isEmpty()) return; // No valid spot in range to place an iFrame

			final InteractionHand hand;
			if(client.player.getOffhandItem().getItem() == iFrameItem) hand = InteractionHand.OFF_HAND;
			else{
				hand = InteractionHand.MAIN_HAND;
				if(client.player.getMainHandItem().getItem() != iFrameItem){
					int hbSlot = 0;
					while(hbSlot < 9 && client.player.getInventory().getNonEquipmentItems().get(hbSlot).getItem() != iFrameItem) ++hbSlot;
					if(hbSlot == 9){
//						Main.LOGGER.info("iFramePlacer: Out of iFrames in hotbar/offhand");
						return;
					}
					client.player.getInventory().setSelectedSlot(hbSlot);
					/*if(!test)*/ return; // TODO: remove once test outcome is known (in AutoPlaceMapArt as well)
				}
			}

			BlockPos bp = closestValidPlacement.get();
			recentPlaceAttempts[attemptIdx] = bp;
			placeIframe(client, bp, hand);
		};
		ClientTickEvents.END_CLIENT_TICK.register(etl);

		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			Item heldItem = player.getItemInHand(hand).getItem();
			if(heldItem != Items.ITEM_FRAME && heldItem != Items.GLOW_ITEM_FRAME) return InteractionResult.PASS;

			BlockPos bp = hitResult.getBlockPos();
			BlockState bs = world.getBlockState(bp);
			placeAgainstBlock = bs.getBlock();
			iFrameItem = heldItem;
			dir = hitResult.getDirection();
			switch(dir){
				case UP: case DOWN: axis = bp.getY(); break;
				case EAST: case WEST: axis = bp.getX(); break;
				case NORTH: case SOUTH: axis = bp.getZ(); break;
			}
//			Main.LOGGER.info("iFramePlacer: dir="+dir.name()+", placeAgainstBlock="+placeAgainstBlock);
			return InteractionResult.PASS;
		});
		//TODO: prefer entity attack event? (like mapart autoplacer)
		ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
			if(dir != null && entity instanceof ItemFrame ife
					&& ife.getNearestViewDirection() == dir && distFromPlane(ife.blockPosition().relative(dir.getOpposite())) == 0
					// Filter out "ghost" itemframes (failed auto-place attempts that appear client-side for a tick)
					&& (ife.tickCount > 0 || Arrays.stream(recentPlaceAttempts).noneMatch(attempt -> attempt != null && ife.blockPosition().equals(attempt)))
					// Filter out itemframes that were not punched by the player (likely just unloaded due to render distance)
					&& ife.distanceToSqr(Minecraft.getInstance().player) < 32*32
			){
				Main.LOGGER.info("iFramePlacer: Disabling due to removed ItemFrameEntity");
				dir = null; iFrameItem = null; placeAgainstBlock = null;
			}
		});
	}
}