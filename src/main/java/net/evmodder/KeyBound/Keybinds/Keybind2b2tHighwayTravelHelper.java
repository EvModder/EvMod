package net.evmodder.KeyBound.Keybinds;

import java.util.ArrayList;
import java.util.Comparator;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.AbstractPressurePlateBlock;
import net.minecraft.block.AnvilBlock;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.Blocks;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.CartographyTableBlock;
import net.minecraft.block.CraftingTableBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.GrindstoneBlock;
import net.minecraft.block.LoomBlock;
import net.minecraft.block.NoteBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.StonecutterBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.PickaxeItem;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

public final class Keybind2b2tHighwayTravelHelper{
	private boolean isEnabled;
	private final MinecraftClient client;

	private boolean hasRightClickFunction(Block block) {
		return block instanceof CraftingTableBlock
				|| block instanceof AnvilBlock
				|| block instanceof LoomBlock
				|| block instanceof CartographyTableBlock
				|| block instanceof GrindstoneBlock
				|| block instanceof StonecutterBlock
				|| block instanceof ButtonBlock
				|| block instanceof AbstractPressurePlateBlock
				|| block instanceof BlockWithEntity
				|| block instanceof BedBlock
				|| block instanceof FenceGateBlock
				|| block instanceof DoorBlock
				|| block instanceof NoteBlock
				|| block instanceof TrapdoorBlock;
	}

	private Direction getPlaceSide(BlockPos blockPos) {
		Vec3d lookVec = blockPos.toCenterPos().subtract(client.player.getEyePos());
		double bestRelevancy = -Double.MAX_VALUE;
		Direction bestSide = null;

		for(Direction side : Direction.values()){
			BlockPos neighbor = blockPos.offset(side);
			BlockState state = client.world.getBlockState(neighbor);

			// Check if neighbour isn't empty
			if(state.isAir() || (!client.player.isSneaking() && hasRightClickFunction(state.getBlock()))) continue;

			// Check if neighbour is a fluid
			if(!state.getFluidState().isEmpty()) continue;

			double relevancy = side.getAxis().choose(lookVec.getX(), lookVec.getY(), lookVec.getZ()) * side.getDirection().offset();
			if(relevancy > bestRelevancy){
				bestRelevancy = relevancy;
				bestSide = side;
			}
		}

		return bestSide;
	}

	private boolean placeBlock(BlockPos bp, Hand hand){
		Vec3d hitPos = Vec3d.ofCenter(bp);

		Direction side = getPlaceSide(bp);
		if(side == null) return false;
		BlockPos neighbour = bp.offset(side);
		hitPos = hitPos.add(side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5);
		BlockHitResult bhr = new BlockHitResult(hitPos, side.getOpposite(), neighbour, false);

		ActionResult result = client.interactionManager.interactBlock(client.player, hand, bhr);
		if(!result.isAccepted()) return false;

		client.getNetworkHandler().sendPacket(new HandSwingC2SPacket(hand));

		return true;
	}

	private boolean canPlaceBlock(BlockPos blockPos){
		if (blockPos == null) return false;

		// Check y level
		if(!World.isValid(blockPos)) return false;

		// Check if current block is replaceable
		if (!client.world.getBlockState(blockPos).isReplaceable()) return false;

		// Check if intersects entities
		return /*!checkEntities || */client.world.canPlace(Blocks.NETHERRACK.getDefaultState(), blockPos, ShapeContext.absent());
	}

	private boolean selectBlocksInHotbar(String path){
		if(client.player.getMainHandStack().getItem() instanceof BlockItem) return false;
		int i=0;
		for(; i<9; ++i){
			Item item = client.player.getInventory().getStack(i).getItem();
			if(client.player.getInventory().selectedSlot == i || item instanceof BlockItem == false) continue;
			if(path == null || Registries.ITEM.getId(item).getPath().equals(path)) break;
		}
		if(i == 9){
			client.player.sendMessage(Text.of("(!) No blocks in hotbar"), true);
			return false;
		}
		client.player.getInventory().selectedSlot = i;
		client.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(i));
		client.player.sendMessage(Text.of("Selected hotbar blocks"), true);
//		Main.LOGGER.info("Selected hotbar blocks");
		return true;
	}

	private final double aheadDist = 0.9, placeRange=2;
	private boolean fillHighwayHole(String useBlock){
		boolean holdingBlock = true;
		Item mainHandItem = client.player.getMainHandStack().getItem();
		Item offHandItem = client.player.getOffHandStack().getItem();
		Hand hand = (mainHandItem == null || mainHandItem instanceof BlockItem == false) ? Hand.OFF_HAND : Hand.MAIN_HAND;
		if(hand == Hand.OFF_HAND && (offHandItem == null || offHandItem instanceof BlockItem == false)) holdingBlock = false;
		//param: Class<? extends Block> useBlock
//		Block block = ((BlockItem)client.player.getStackInHand(hand).getItem()).getBlock();
//		if(!useBlock.isInstance(block)) return false;

		Vec3d vec = client.player.getPos().add(client.player.getVelocity()).add(0, -0.75, 0);

		Vec3d pos = client.player.getPos();
		if(aheadDist != 0 && !client.world.getBlockState(client.player.getBlockPos().down())
				.getCollisionShape(client.world, client.player.getBlockPos()).isEmpty()) {
			Vec3d dir = Vec3d.fromPolar(0, client.player.getYaw()).multiply(aheadDist, 0, aheadDist);
			pos = pos.add(dir.x, 0, dir.z);
		}
		BlockPos.Mutable bp = new BlockPos.Mutable();
		bp.set(pos.x, vec.y, pos.z);
		if(client.options.sneakKey.isPressed() && !client.options.jumpKey.isPressed() && client.player.getY() + vec.y > -1){
			bp.setY(bp.getY() - 1);
		}
		if(bp.getY() >= client.player.getBlockPos().getY()){
			bp.setY(client.player.getBlockPos().getY() - 1);
		}
		BlockPos targetBlock = bp.toImmutable();

		if(getPlaceSide(bp) == null){
			pos = client.player.getPos();
			pos = pos.add(0, -0.98f, 0);
			pos.add(client.player.getVelocity());

			ArrayList<BlockPos> blockPosArray = new ArrayList<>();
			for(int x = (int)(client.player.getX() - placeRange); x < client.player.getX() + placeRange; ++x){
				for (int z = (int)(client.player.getZ() - placeRange); z < client.player.getZ() + placeRange; ++z){
					for (int y = (int)Math.max(client.world.getBottomY(), client.player.getY() - placeRange);
							y < Math.min(client.world.getHeight(), client.player.getY() + placeRange); ++y)
					{
						bp.set(x, y, z);
						if(getPlaceSide(bp) == null) continue;
						if(!canPlaceBlock(bp)) continue;
						//if(client.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(bp.offset(getClosestPlaceSide(bp)))) > 36) continue;
						blockPosArray.add(new BlockPos(bp));
					}
				}
			}
			if(blockPosArray.isEmpty()) return false;
			blockPosArray.sort(Comparator.comparingDouble((blockPos) -> blockPos.getSquaredDistance(targetBlock)));
			bp.set(blockPosArray.getFirst());
		}
		if(!client.world.getBlockState(bp).isReplaceable()) return false;
		if(!holdingBlock) selectBlocksInHotbar(useBlock);
		String path = Registries.ITEM.getId(client.player.getStackInHand(hand).getItem()).getPath();
		if(useBlock != null && !path.equals(useBlock)){
			client.player.sendMessage(Text.of("(!) Missing block: "+useBlock), true);
			//return false;
		}
		return placeBlock(bp, hand);
	}

	private Vec3d getEyesPos(){
		float eyeHeight = client.player.getEyeHeight(client.player.getPose());
		return client.player.getPos().add(0, eyeHeight, 0);
	}
	private Direction getBlockBreakingSide(BlockPos bp){
		Vec3d eyes = getEyesPos();
		Direction[] sides = Direction.values();

		BlockState state = client.world.getBlockState(bp);
		VoxelShape shape = state.getOutlineShape(client.world, bp);
		if(shape.isEmpty()) return null;

		Box box = shape.getBoundingBox();
		Vec3d halfSize = new Vec3d(box.maxX - box.minX, box.maxY - box.minY, box.maxZ - box.minZ).multiply(0.5);
		Vec3d center = Vec3d.of(bp).add(box.getCenter());

		Vec3d[] hitVecs = new Vec3d[sides.length];
		for(int i=0; i<sides.length; ++i){
			Vec3i dirVec = sides[i].getVector();
			Vec3d relHitVec = new Vec3d(halfSize.x * dirVec.getX(), halfSize.y * dirVec.getY(), halfSize.z * dirVec.getZ());
			hitVecs[i] = center.add(relHitVec);
		}
		double distanceSqToCenter = eyes.squaredDistanceTo(center);
		double[] distancesSq = new double[sides.length];
		boolean[] linesOfSight = new boolean[sides.length];

		for(int i=0; i<sides.length; ++i){
			distancesSq[i] = eyes.squaredDistanceTo(hitVecs[i]);
			if(distancesSq[i] >= distanceSqToCenter) continue;
			RaycastContext context = new RaycastContext(eyes, hitVecs[i], RaycastContext.ShapeType.COLLIDER,
					RaycastContext.FluidHandling.NONE, client.player);
			linesOfSight[i] = client.world.raycast(context).getType() == HitResult.Type.MISS;
		}
		Direction side = sides[0];
		for(int i=1; i<sides.length; ++i){
			int bestSide = side.ordinal();
			// prefer sides with LOS
			if(!linesOfSight[bestSide] && linesOfSight[i]){
				side = sides[i];
				continue;
			}
			if(linesOfSight[bestSide] && !linesOfSight[i]) continue;

			// then pick the closest side
			if(distancesSq[i] < distancesSq[bestSide]) side = sides[i];
		}
		return side;
	}

	private boolean selectPickaxeInHotbar(){
		if(client.player.getMainHandStack().getItem() instanceof PickaxeItem) return false;
		int i=0;
		for(; i<9 && client.player.getInventory().getStack(i).getItem() instanceof PickaxeItem == false; ++i);
		if(i == 9){
			client.player.sendMessage(Text.of("(!) No pickaxe in hotbar"), true);
			return false;
		}
		client.player.getInventory().selectedSlot = i;
		client.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(i));
		client.player.sendMessage(Text.of("Selected hotbar pickaxe"), true);
//		Main.LOGGER.info("Selected hotbar pickaxe");
		return true;
	}
	private boolean isMining;
	private boolean putOutFireAndMineObstacles(){
		BlockPos bp = client.player.getBlockPos();
		if(client.world.getBlockState(bp).getBlock() instanceof AbstractFireBlock){
			client.interactionManager.updateBlockBreakingProgress(bp, getBlockBreakingSide(bp));
			client.player.sendMessage(Text.of("Put out a fire"), true);
//			Main.LOGGER.info("Put out a fire");
			return true;
		}
		// Don't try to mine blocks if the player isn't stuck
		if(client.player.prevX != client.player.getX() || client.player.prevZ != client.player.getZ()) return false;

		Vec3d dir = Vec3d.fromPolar(0, client.player.getYaw());
		int dx = (int)Math.round(dir.x);
		int dz = (int)Math.round(dir.z);
		ArrayList<BlockPos> mineSpots = new ArrayList<>();
		Vec3d pos = client.player.getPos();
		if(dx != 0){
			mineSpots.add(bp.add(dx, 0, 0));
			int ddz = Math.round(pos.getZ()) > pos.getZ() ? 1 : -1;
			mineSpots.add(bp.add(dx, 0, ddz));
		}
		if(dz != 0){
			mineSpots.add(bp.add(0, 0, dz));
			int ddx = Math.round(pos.getX()) > pos.getX() ? 1 : -1;
			mineSpots.add(bp.add(ddx, 0, dz));
		}
		if(dx != 0 && dz != 0) mineSpots.add(bp.add(dx, 0, dz));

		for(BlockPos bpDig : mineSpots){
			if(client.world.getBlockState(bpDig).getCollisionShape(client.world, client.player.getBlockPos()).isEmpty()) continue;
			if(selectPickaxeInHotbar()) return true;
			isMining = true;
			//if(client.player.getMainHandStack().getItem() instanceof PickaxeItem) return false;
			client.interactionManager.updateBlockBreakingProgress(bpDig, getBlockBreakingSide(bpDig));
			client.player.sendMessage(Text.of("Mining block: ").copy().append(client.world.getBlockState(bpDig).getBlock().getName())
//					.append(" yaw:"+client.player.getYaw()+", dirX:"+dir.x+",dirZ:"+dir.z+", xyz: ")
//					.append(bp.getX()+","+bp.getY()+","+bp.getZ())
					, true);
//			Main.LOGGER.info("Mining block: "+client.world.getBlockState(bp).getBlock().getName().getLiteralString());
			return true;
		}
		if(isMining){
			isMining = false;
			selectBlocksInHotbar(null);
		}
		return false;
	}

	private boolean barfTrash(KeybindEjectJunk ejectJunk){
		if(client.currentScreen instanceof HandledScreen) return false;

		boolean didBarf = false;
		for(int i=9; i<36; ++i) if(ejectJunk.shouldEject(client.player.getInventory().getStack(i))){
//			if(!didBarf){
//				client.player.sendMessage(Text.of("Tossed item: ").copy().append(client.player.getInventory().getStack(i).getName()), true);
//				Main.LOGGER.info("Tossed item: "+client.player.getInventory().getStack(i).getName().getLiteralString());
//			}
			client.interactionManager.clickSlot(0, i, 1, SlotActionType.THROW, client.player);
			didBarf = true;
		}
		return didBarf;
	}

	public Keybind2b2tHighwayTravelHelper(KeybindEjectJunk ejectJunk){
		client = MinecraftClient.getInstance();
		KeyBindingHelper.registerKeyBinding(new EvKeybind("ebounce_travel_helper", ()->{
			String status = "2b2t Travel Helper: "+((isEnabled=!isEnabled) ? "enabled" : "disabled");
			client.player.sendMessage(Text.of(status), true);
		}));

		ClientTickEvents.START_CLIENT_TICK.register(_0 -> {
			if(!isEnabled) return;
			if(client.player == null || client.world == null) return;
			Item chestItem = client.player.getInventory().getArmorStack(2).getItem();
			Identifier chestItemId = Registries.ITEM.getId(chestItem);
			if(chestItemId == null || !chestItemId.getPath().equals("elytra")){
				client.player.sendMessage(Text.of("Not wearing elytra"), true);
				return;
			}
			int y = client.player.getBlockY();
			if(y == 118){
				client.player.jump();
				//TODO: temporarily turn off meteor efly, then turn it back on
			}
			if(y != 119 && y != 120) return;

			if(fillHighwayHole(y == 119 ? null : "obsidian")){
				client.player.sendMessage(Text.of("Filled a hole"), true);
//				Main.LOGGER.info("Filled a hole");
				return;
			}
			if(putOutFireAndMineObstacles()) return;

			if(ejectJunk != null && barfTrash(ejectJunk)){
//				client.player.sendMessage(Text.of("Tossed trashed"), true);
//				Main.LOGGER.info("Tossed trash");
			}
		});
	}
}