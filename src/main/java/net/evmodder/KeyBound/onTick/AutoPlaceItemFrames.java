package net.evmodder.KeyBound.onTick;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.evmodder.KeyBound.Main;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class AutoPlaceItemFrames{
//	public static boolean isActive; // Using dir!=null instead
	private Block placeAgainstBlock;
	private Item iFrameItem;
	private Direction dir;
	private int axis;
	private final int MAX_REACH = 4;
	private final boolean mustConnect;

	private double distFromPlane(BlockPos bp){
		switch(dir){
			case UP: case DOWN: return Math.abs(bp.getY() - axis);
			case EAST: case WEST: return Math.abs(bp.getX() - axis);
			case NORTH: case SOUTH: return Math.abs(bp.getZ() - axis);

			default: assert(false) : "Unreachable"; return -1;
		}
	}

	private Vec3d getPlaceAgainstSurface(BlockPos bp){
		Vec3d blockHit = bp.toCenterPos();
		switch(dir){
			case UP: return blockHit.add(0, .5, 0);
			case DOWN: return blockHit.add(0, -.5, 0);
			case EAST: return blockHit.add(.5, 0, 0);
			case WEST: return blockHit.add(-.5, 0, 0);
			case NORTH: return blockHit.add(0, 0, -.5);
			case SOUTH: return blockHit.add(0, 0, .5);

			default: assert(false) : "Unreachable"; return null;
		}
	}

	private boolean isValidIframePlacement(BlockPos bp, World world, List<ItemFrameEntity> existingIfes){
		if(distFromPlane(bp) != 0) return false;
//		Main.LOGGER.info("iFramePlacer: wall block is on the plane");
		BlockState bs = world.getBlockState(bp);
		if(placeAgainstBlock != null && bs.getBlock() != placeAgainstBlock) return false;
//		Main.LOGGER.info("iFramePlacer: wall block matches placeAgainstBlock");

		BlockPos ifeBp = bp.offset(dir);
		BlockState ifeBs = world.getBlockState(ifeBp);
		if(ifeBs.isFullCube(world, ifeBp)) return false;
		if(ifeBs.isSolidBlock(world, ifeBp)) return false; // iFrame cannot be placed inside a solid block
//		Main.LOGGER.info("iFramePlacer: ife spot is non-solid");

		if(existingIfes.stream().anyMatch(ife -> ife.getBlockPos().equals(ifeBp))) return false; // Already iFrame here
//		Main.LOGGER.info("iFramePlacer: ife spot is available");
		if(mustConnect && existingIfes.stream().noneMatch(ife -> ife.getBlockPos().getManhattanDistance(ifeBp) == 1)) return false; // No iFrame neighbor
//		Main.LOGGER.info("iFramePlacer: ife spot has neighboring iframe");
		return true;
	}

	private int tick;
	public AutoPlaceItemFrames(final boolean mustMatchBlockType, final boolean mustBeConnected){
		mustConnect = mustBeConnected;
		EndTick etl = (client) -> {
			if(dir == null/* || iFrameItem == null*/) return; // iFramePlacer is not currently active
			if((++tick)/5 == 1) tick = 0; else return; // TODO: Only run ever 5th tick, since there is some iframe placement speed limit (idk what it is yet)
			assert iFrameItem != null;

			if(client.player == null || client.world == null){ // Player offline, cancel iFramePlacer
				Main.LOGGER.info("iFramePlacer: Disabling due to player offline");
				dir = null; iFrameItem = null; placeAgainstBlock = null;
				return;
			}

			final int SCAN_DIST = MAX_REACH+2;

			BlockPos clientBp = client.player.getBlockPos();
			if(distFromPlane(clientBp) > SCAN_DIST){
//				Main.LOGGER.info("iFramePlacer: out of range of wall");
				return; // Player out of range of iFrame wall
			}

			Box box = client.player.getBoundingBox().expand(SCAN_DIST, SCAN_DIST, SCAN_DIST);
			Predicate<ItemFrameEntity> filter = ife -> ife.getFacing() == dir && distFromPlane(ife.getBlockPos().offset(dir.getOpposite())) == 0;
			List<ItemFrameEntity> ifes = client.world.getEntitiesByClass(ItemFrameEntity.class, box, filter);
//			Main.LOGGER.info("iFramePlacer: num ifes in reach dist: "+ifes.size());

//			Main.LOGGER.info("iFramePlacer: num blocks in reach dist: "+BlockPos.streamOutwards(clientBp, SCAN_DIST, SCAN_DIST, SCAN_DIST).count());
			Vec3d eyePos = client.player.getEyePos();
			Optional<BlockPos> closestValidPlacement = BlockPos.streamOutwards(clientBp, SCAN_DIST, SCAN_DIST, SCAN_DIST)
				.filter(bp -> isValidIframePlacement(bp, client.world, ifes))
				.filter(bp -> getPlaceAgainstSurface(bp).squaredDistanceTo(eyePos) <= MAX_REACH*MAX_REACH)
				.findFirst();
			if(closestValidPlacement.isEmpty()){
//				Main.LOGGER.info("iFramePlacer: no spot to place an iframe");
				return; // No valid spot in range to place an iFrame
			}

			Hand hand = Hand.MAIN_HAND;
			if(client.player.getOffHandStack().getItem() == iFrameItem) hand = Hand.OFF_HAND;
			else if(client.player.getMainHandStack().getItem() != iFrameItem){
				// TODO: swap iFrame item into main hand (or switch to hb slot)
//				Main.LOGGER.info("iFramePlacer: iframe not in hand");
				return;
			}

			// Do the clicky-clicky
			BlockPos bp = closestValidPlacement.get();
//			BlockState bs = client.world.getBlockState(bp);
			Vec3d blockHit = getPlaceAgainstSurface(bp);
//			Vec3d lookDirection = blockHit.subtract(client.player.getEyePos()).normalize();
			BlockHitResult hitResult = new BlockHitResult(blockHit, dir, bp, /*insideBlock=*/false);
			client.interactionManager.interactBlock(client.player, hand, hitResult);
//			Main.LOGGER.info("iFramePlacer: Placed iFrame");
		};
		ClientTickEvents.END_CLIENT_TICK.register(etl);

		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			Item heldItem = player.getStackInHand(hand).getItem();
			if(heldItem != Items.ITEM_FRAME && heldItem != Items.GLOW_ITEM_FRAME) return ActionResult.PASS;

			BlockPos bp = hitResult.getBlockPos();
			BlockState bs = world.getBlockState(bp);
			if(mustMatchBlockType) placeAgainstBlock = bs.getBlock();
			iFrameItem = heldItem;
			dir = hitResult.getSide();
			switch(dir){
				case UP: case DOWN: axis = bp.getY(); break;
				case EAST: case WEST: axis = bp.getX(); break;
				case NORTH: case SOUTH: axis = bp.getZ(); break;
			}
//			isActive = true;
//			Main.LOGGER.info("iFramePlacer: dir="+dir.name()+", placeAgainstBlock="+(placeAgainstBlock==null?null:placeAgainstBlock));
			return ActionResult.PASS;
		});
		ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
			if(dir != null && entity instanceof ItemFrameEntity ife && ife.getFacing() == dir && distFromPlane(ife.getBlockPos().offset(dir.getOpposite())) == 0){
				Main.LOGGER.info("iFramePlacer: Disabling due to removed ItemFrameEntity");
				dir = null; iFrameItem = null; placeAgainstBlock = null;
			}
		});
	}
}