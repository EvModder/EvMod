package net.evmodder.KeyBound.keybinds;

import java.util.ArrayList;
import java.util.Comparator;
import net.evmodder.KeyBound.Main;
import net.evmodder.KeyBound.config.Configs;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
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
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
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
import net.minecraft.world.World;

public final class KeybindEbounceTravelHelper{
	private boolean isEnabled;
	private long enabledTs, targetY;
	private final long ENABLE_DELAY = 1500l;
	private MinecraftClient client;
	private KeybindEjectJunk ejectJunk;

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
			client.player.sendMessage(Text.literal("(!) No blocks in hotbar"), true);
			return false;
		}
		client.player.getInventory().setSelectedSlot(i);
		client.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(i));
		client.player.sendMessage(Text.literal("Selected hotbar blocks"), true);
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
			client.player.sendMessage(Text.literal("(!) Missing block: "+useBlock), true);
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

	private boolean selectPickaxeInHotbar(BlockState bs){
//		if(client.player.getMainHandStack().getItem() instanceof PickaxeItem) return false;
		int bestI=client.player.getInventory().selectedSlot;
		float bestSpeed=0;
		for(int i=0; i<9; ++i){
			float speed = client.player.getInventory().getStack(i).getMiningSpeedMultiplier(bs);
			if(speed > bestSpeed){bestSpeed=speed; bestI=i;}
		}
		if(bestSpeed == 0){
			client.player.sendMessage(Text.literal("(!) No matching tool in hotbar"), true);
			return false;
		}
		if(bestI == client.player.getInventory().selectedSlot){
			client.player.sendMessage(Text.literal("Best tool in hotbar already selected"), true);
			return false;
		}
		client.player.getInventory().setSelectedSlot(bestI);
		client.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(bestI));
		client.player.sendMessage(Text.literal("Selected hotbar pickaxe"), true);
//		Main.LOGGER.info("Selected hotbar pickaxe");
		return true;
	}
	private int isMining;
	private boolean putOutFireAndMineObstacles(){
		BlockPos bp = client.player.getBlockPos();
		if(bp.getY() == targetY+1) bp = bp.offset(Direction.DOWN);

		Vec3d dir = Vec3d.fromPolar(0, client.player.getYaw());
		int dx = (int)Math.round(dir.x);
		int dz = (int)Math.round(dir.z);
		final boolean diag = dx != 0 && dz != 0;
		for(int i=0; i<(diag ? 1 : 3); ++i){
			BlockPos aheadPos = bp.add(i*dx, 0, i*dz);
			if(client.world.getBlockState(aheadPos).getBlock() instanceof AbstractFireBlock){
				client.interactionManager.updateBlockBreakingProgress(aheadPos, getBlockBreakingSide(aheadPos));
				client.player.sendMessage(Text.literal("Put out a fire"), true);
				return true;
			}
		}
		// Don't try to mine blocks if the player isn't stuck
		if(client.player.prevX != client.player.getX() || client.player.prevZ != client.player.getZ()) return false;

		ArrayList<BlockPos> mineSpots = new ArrayList<>();
		Vec3d pos = client.player.getPos();
		if(pos.getY() > targetY) pos = new Vec3d(pos.x, targetY, pos.z);
		if(dx != 0){
			mineSpots.add(bp.add(dx, 0, 0));
			if(diag) mineSpots.add(bp.add(dx, 0, Math.round(pos.getZ()) > pos.getZ() ? 1 : -1));
			else if(pos.getZ()+.3 > Math.floor(pos.getZ())+1) mineSpots.add(bp.add(dx, 0, 1));
			else if(pos.getZ()-.3 < Math.floor(pos.getZ())) mineSpots.add(bp.add(dx, 0, -1));
		}
		if(dz != 0){
			mineSpots.add(bp.add(0, 0, dz));
			if(diag) mineSpots.add(bp.add(Math.round(pos.getZ()) > pos.getZ() ? 1 : -1, 0, dz));
			else if(pos.getX()+.3 > Math.floor(pos.getX())+1) mineSpots.add(bp.add(1, 0, dz));
			else if(pos.getX()-.3 < Math.floor(pos.getX())) mineSpots.add(bp.add(-1, 0, -dz));
		}
		mineSpots.add(bp);
		if(diag) mineSpots.add(bp.add(dx, 0, dz));

		BlockState bs = null;
		for(BlockPos bpDig : mineSpots){
			boolean foundDig = false;
			for(int i=0; i<2; ++i){
				if(i==1) bpDig = bpDig.add(0, 2, 0);
				else if(i==2) bpDig = bpDig.add(0, -1, 0);
				bs = client.world.getBlockState(bpDig);
				if(bs.getBlock() != Blocks.BEDROCK && !bs.getCollisionShape(client.world, bpDig).isEmpty()){foundDig = true; break;}
			}
			if(foundDig == false) continue;
			// Same as above, but goes 0->1->2 instead of 0->2->1
//			int i=0;
//			for(; i<3 && client.world.getBlockState(bpDig).getCollisionShape(client.world, client.player.getBlockPos()).isEmpty(); ++i)
//				bpDig = bpDig.add(0, 1, 0);
//			if(i == 3) continue;

			if(selectPickaxeInHotbar(bs)) return true;
			if(isMining == 0) isMining = 4;
			//if(client.player.getMainHandStack().getItem() instanceof PickaxeItem) return false;
			client.interactionManager.updateBlockBreakingProgress(bpDig, getBlockBreakingSide(bpDig));
			client.player.sendMessage(Text.literal("Mining: ").copy().append(bs.getBlock().getName())
//					.append(" yaw:"+client.player.getYaw()+", dirX:"+dir.x+",dirZ:"+dir.z+", xyz: ")
//					.append(bp.getX()+","+bp.getY()+","+bp.getZ())
					, true);
//			Main.LOGGER.info("Mining block: "+client.world.getBlockState(bp).getBlock().getName().getLiteralString());
			return true;
		}
		if(isMining > 0){
			if(--isMining == 0){
				selectBlocksInHotbar(null);
				client.player.sendMessage(Text.literal("Mined: ").copy().append(bs.getBlock().getName()), true);
			}
			return true; // Wait a bit before declaring it done
		}
		return false;
	}

	private boolean barfTrash(KeybindEjectJunk ejectJunk){
		if(client.currentScreen instanceof HandledScreen) return false;

		boolean didBarf = false;
		for(int i=9; i<36; ++i) if(ejectJunk.shouldEject(client.player.getInventory().getStack(i))){
//			if(!didBarf){
//				client.player.sendMessage(Text.literal("Tossed item: ").copy().append(client.player.getInventory().getStack(i).getName()), true);
//				Main.LOGGER.info("Tossed item: "+client.player.getInventory().getStack(i).getName().getLiteralString());
//			}
			client.interactionManager.clickSlot(0, i, 1, SlotActionType.THROW, client.player);
			didBarf = true;
		}
		return didBarf;
	}

	private void registerClientTickListener(){
		ClientTickEvents.START_CLIENT_TICK.register(_0 -> {
			if(client.player == null || client.world == null){isEnabled = false; enabledTs = 0; return;}
			if(enabledTs != 0){
				final long timeSinceEnabled = System.currentTimeMillis() - enabledTs;
				if(timeSinceEnabled > ENABLE_DELAY){
					isEnabled = true;
//					Configs.Hotkeys.EBOUNCE_TRAVEL_HELPER.setBooleanValue(true); // May already be true
					targetY = Long.MIN_VALUE;
					enabledTs = 0;
					client.player.sendMessage(Text.literal("eBounce Helper: enabled"), true);
					client.player.sendMessage(Text.literal("eBounce Helper: enabled"), false);
				}
				else{
					client.player.sendMessage(Text.literal("Enabling in "+String.format("%.2f", ((ENABLE_DELAY-timeSinceEnabled)/1000d))+"s..."), true);
				}
			}
			if(!isEnabled) return;
			if(client.player.getEquippedStack(EquipmentSlot.CHEST).getItem() != Items.ELYTRA){
				client.player.sendMessage(Text.literal("Not wearing elytra"), true);
				return;
			}
			final int y = client.player.getBlockY();
			if(targetY == Long.MIN_VALUE) targetY = y;
			if(targetY != y){
				client.player.sendMessage(Text.literal("Y-height has changed!"), true);
				return;
			}
//			if(y == 118){
//				client.player.jump();
//				//TODO: temporarily turn off meteor efly, then turn it back on
//			}
//			if(y != 119 && y != 120) return;

			if(fillHighwayHole(y == 119 ? null : "obsidian")){
				client.player.sendMessage(Text.literal("Filled a hole"), true);
//				Main.LOGGER.info("Filled a hole");
				return;
			}
			if(putOutFireAndMineObstacles()) return;

			if(ejectJunk != null && barfTrash(ejectJunk)){
//				client.player.sendMessage(Text.literal("Tossed trashed"), true);
//				Main.LOGGER.info("Tossed trash");
			}
		});
	}

	public void toggle(boolean setEnabled){
//		Main.LOGGER.info("ebounce_travel_helper key pressed");
		if(client == null){
			Main.LOGGER.info("ebounce_travel_helper registered");
			client = MinecraftClient.getInstance();
			registerClientTickListener();
		}
//		client.player.sendMessage(Text.literal("eBounceHelper: key pressed, setEnabled="+setEnabled), true);
//		client.player.sendMessage(Text.literal("eBounceHelper: key pressed, setEnabled="+setEnabled), false);

		if(setEnabled == isEnabled) return;
		if(!setEnabled || enabledTs != 0){
			isEnabled = false;
			enabledTs = 0;
//			client.player.sendMessage(Text.literal("eBounceHelper: disabled"), true);
			client.player.sendMessage(Text.literal("eBounceHelper: disabled"), false);
			return;
		}
//		ItemStack chestStack = client.player.getInventory().getArmorStack(2);
//		if(chestStack.getItem() != Items.ELYTRA){
////			client.player.sendMessage(Text.literal("eBounceHelper: Must be wearing elytra first"), true);
//			client.player.sendMessage(Text.literal("eBounceHelper: Must be wearing elytra first"), false);
//			enabledTs = 0;
//			Configs.Hotkeys.EBOUNCE_TRAVEL_HELPER.setBooleanValue(false);
//			return;
//		}
//		if(enabledTs != 0){
////			client.player.sendMessage(Text.literal("eBounceHelper: ENABLE pressed again while in enable cooldown"), true);
//			client.player.sendMessage(Text.literal("eBounceHelper: ENABLE pressed again while in enable cooldown"), false);
//			Main.LOGGER.info("eBounceHelper: ENABLE pressed again while in enable countdown");
//			return;
//		}

//		client.player.sendMessage(Text.literal("eBounce Helper: enabling..."), true);
		client.player.sendMessage(Text.literal("eBounce Helper: enabling..."), false);
		enabledTs = System.currentTimeMillis();
	}

	public KeybindEbounceTravelHelper(KeybindEjectJunk ejectJunk){
		this.ejectJunk = ejectJunk;
//		new Keybind("ebounce_travel_helper", this::toggle, null, GLFW.GLFW_KEY_A);
	}
}