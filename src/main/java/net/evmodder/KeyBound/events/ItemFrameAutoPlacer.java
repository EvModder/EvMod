package net.evmodder.KeyBound.events;

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

public class ItemFrameAutoPlacer{
//	public static boolean isActive; // Using dir!=null instead
	private Block placeAgainstBlock;
	private Item iFrameItem;
	private Direction dir;
	private int axis1, axis2;
	private final int MAX_REACH = 4*4;
	private final boolean mustConnect;

	public static final void placeNearestItemFrame(){
//		if(!isActive) return;

		//Step1: Get Iframe in main hand
		//Step2: Face the target block face
		//Step3: Right click packet

		//If out of itemframes, isActive=false
	}

	public double distSqFromPlane(BlockPos bp){
		switch(dir){
			case UP: case DOWN: return Math.pow(bp.getX()-axis1, 2) + Math.pow(bp.getZ()-axis2, 2);
			case EAST: case WEST: return Math.pow(bp.getY()-axis1, 2) + Math.pow(bp.getZ()-axis2, 2);
			case NORTH: case SOUTH: return Math.pow(bp.getY()-axis1, 2) + Math.pow(bp.getX()-axis2, 2);

			default: assert(false) : "Unreachable"; return -1;
		}
	}
	public boolean isValidIframePlacement(BlockPos bp, World world, List<ItemFrameEntity> existingIfes){
		if(distSqFromPlane(bp) != 0) return false;
		BlockState bs = world.getBlockState(bp);
		if(placeAgainstBlock != null && bs.getBlock() != placeAgainstBlock) return false;

		BlockPos ifeBp = bp.offset(dir);
		BlockState ifeBs = world.getBlockState(ifeBp);
//		if(ifeBs.isFullCube(world, ifeBp)) return false;
		if(ifeBs.isSolidBlock(world, ifeBp)) return false; // iFrame cannot be placed inside a solid block

		if(existingIfes.stream().anyMatch(ife -> ife.getBlockPos().equals(ifeBp))) return false; // Already iFrame here
		if(mustConnect && existingIfes.stream().noneMatch(ife -> ife.getBlockPos().getManhattanDistance(ifeBp) == 1)) return false; // No iFrame neighbor
		return true;
	}

	public ItemFrameAutoPlacer(final boolean mustMatchBlockType, final boolean mustBeConnected){
		mustConnect = mustBeConnected;
		EndTick etl = (client) -> {
			if(dir == null/* || iFrameItem == null*/) return; // iFramePlacer is not currently active
			assert iFrameItem != null;

			if(client.player == null || client.world == null){ // Player offline, cancel iFramePlacer
				Main.LOGGER.info("iFramePlacer: Disabling due to player offline");
				dir = null; iFrameItem = null; placeAgainstBlock = null;
				return;
			}

			BlockPos clientBp = client.player.getBlockPos();
			if(distSqFromPlane(clientBp) > MAX_REACH*MAX_REACH) return; // Player out of range of iFrame wall

			Box box = client.player.getBoundingBox().expand(MAX_REACH+1, MAX_REACH+1, MAX_REACH+1);
			Predicate<ItemFrameEntity> filter = ife -> ife.getFacing() == dir && distSqFromPlane(ife.getBlockPos()) == 0;
			List<ItemFrameEntity> ifes = client.world.getEntitiesByClass(ItemFrameEntity.class, box, filter);

			Optional<BlockPos> closestValidPlacement = BlockPos.streamOutwards(clientBp, MAX_REACH, MAX_REACH, MAX_REACH)
				.filter(bp -> isValidIframePlacement(bp, client.world, ifes)).findFirst();
			if(closestValidPlacement.isEmpty()) return; // No valid spot in range to place an iFrame

			Hand hand = Hand.MAIN_HAND;
			if(client.player.getOffHandStack().getItem() == iFrameItem) hand = Hand.OFF_HAND;
			else if(client.player.getMainHandStack().getItem() != iFrameItem){
				// TODO: swap iFrame item into main hand (or switch to hb slot)
				return;
			}

			// Do the clicky-clicky
			BlockPos bp = closestValidPlacement.get();
			BlockState bs = client.world.getBlockState(bp);
			Vec3d blockHit = bs.getCullingFace(dir).getBoundingBox().getCenter();
//			Vec3d lookDirection = blockHit.subtract(client.player.getEyePos()).normalize();
			BlockHitResult hitResult = new BlockHitResult(blockHit, dir, bp, /*insideBlock=*/false);
			client.interactionManager.interactBlock(client.player, hand, hitResult);
			Main.LOGGER.info("iFramePlacer: Placed iFrame");
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
				case UP: case DOWN: axis1 = bp.getX(); axis2 = bp.getZ(); break;
				case EAST: case WEST: axis1 = bp.getY(); axis2 = bp.getZ(); break;
				case NORTH: case SOUTH: axis1 = bp.getY(); axis2 = bp.getX();
			}
//			isActive = true;

			return ActionResult.PASS;
		});
		ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
			if(dir != null && entity instanceof ItemFrameEntity ife && distSqFromPlane(ife.getBlockPos()) == 0 && ife.getFacing() == dir){
				Main.LOGGER.info("iFramePlacer: Disabling due to removed ItemFrameEntity");
				dir = null; iFrameItem = null; placeAgainstBlock = null;
			}
		});
	}
}