package net.evmodder.evmod.keybinds;

import static net.evmodder.evmod.compat.MinecraftCompat.screen;
import static net.evmodder.evmod.compat.MinecraftCompat.sendOverlay;
import static net.evmodder.evmod.compat.MinecraftCompat.sendSystem;
import static net.evmodder.evmod.compat.MinecraftCompat.selectSlot;

import java.util.ArrayList;
import java.util.Comparator;
import net.evmodder.evmod.Main;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CartographyTableBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.GrindstoneBlock;
import net.minecraft.world.level.block.LoomBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.StonecutterBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class KeybindEbounceTravelHelper{
	private boolean isEnabled;
	private long enabledTs, targetY;
	private final long ENABLE_DELAY = 1500l;
	private Minecraft client;
	private KeybindEjectJunk ejectJunk;

	private boolean hasRightClickFunction(Block block) {
		return block instanceof CraftingTableBlock
				|| block instanceof AnvilBlock
				|| block instanceof LoomBlock
				|| block instanceof CartographyTableBlock
				|| block instanceof GrindstoneBlock
				|| block instanceof StonecutterBlock
				|| block instanceof ButtonBlock
				|| block instanceof BasePressurePlateBlock
				|| block instanceof BaseEntityBlock
				|| block instanceof BedBlock
				|| block instanceof FenceGateBlock
				|| block instanceof DoorBlock
				|| block instanceof NoteBlock
				|| block instanceof TrapDoorBlock;
	}

	private Direction getPlaceSide(BlockPos blockPos) {
		Vec3 lookVec = Vec3.atCenterOf(blockPos).subtract(client.player.getEyePosition());
		double bestRelevancy = -Double.MAX_VALUE;
		Direction bestSide = null;

		for(Direction side : Direction.values()){
			BlockPos neighbor = blockPos.relative(side);
			BlockState state = client.level.getBlockState(neighbor);

			// Check if neighbour isn't empty
			if(state.isAir() || (!client.player.isShiftKeyDown() && hasRightClickFunction(state.getBlock()))) continue;

			// Check if neighbour is a fluid
			if(!state.getFluidState().isEmpty()) continue;

			double relevancy = side.getAxis().choose(lookVec.x(), lookVec.y(), lookVec.z()) * side.getAxisDirection().getStep();
			if(relevancy > bestRelevancy){
				bestRelevancy = relevancy;
				bestSide = side;
			}
		}

		return bestSide;
	}

	private boolean placeBlock(BlockPos bp, InteractionHand hand){
		Vec3 hitPos = Vec3.atCenterOf(bp);

		Direction side = getPlaceSide(bp);
		if(side == null) return false;
		BlockPos neighbour = bp.relative(side);
		hitPos = hitPos.add(side.getStepX() * 0.5, side.getStepY() * 0.5, side.getStepZ() * 0.5);
		BlockHitResult bhr = new BlockHitResult(hitPos, side.getOpposite(), neighbour, false);

		InteractionResult result = client.gameMode.useItemOn(client.player, hand, bhr);
		if(!result.consumesAction()) return false;

		return true;
	}

	private boolean canPlaceBlock(BlockPos blockPos){
		if (blockPos == null) return false;

		// Check y level
		if(!Level.isInSpawnableBounds(blockPos)) return false;

		// Check if current block is replaceable
		if (!client.level.getBlockState(blockPos).canBeReplaced()) return false;

		// Check if intersects entities
		return /*!checkEntities || */client.level.isUnobstructed(Blocks.NETHERRACK.defaultBlockState(), blockPos, CollisionContext.empty());
	}

	private boolean selectBlocksInHotbar(String path){
		if(client.player.getMainHandItem().getItem() instanceof BlockItem) return false;
		int i=0;
		for(; i<9; ++i){
			Item item = client.player.getInventory().getItem(i).getItem();
			if(client.player.getInventory().getSelectedSlot() == i || item instanceof BlockItem == false) continue;
			if(path == null || BuiltInRegistries.ITEM.getKey(item).getPath().equals(path)) break;
		}
		if(i == 9){
			sendOverlay(client.player, Component.literal("(!) No blocks in hotbar"));
			return false;
		}
		selectSlot(client.player.getInventory(), i);
		client.getConnection().send(new ServerboundSetCarriedItemPacket(i));
		sendOverlay(client.player, Component.literal("Selected hotbar blocks"));
//		Main.LOGGER.info("Selected hotbar blocks");
		return true;
	}

	private final double aheadDist = 0.9, placeRange=2;
	private boolean fillHighwayHole(String useBlock){
		boolean holdingBlock = true;
		Item mainHandItem = client.player.getMainHandItem().getItem();
		Item offHandItem = client.player.getOffhandItem().getItem();
		InteractionHand hand = (mainHandItem == null || mainHandItem instanceof BlockItem == false) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
		if(hand == InteractionHand.OFF_HAND && (offHandItem == null || offHandItem instanceof BlockItem == false)) holdingBlock = false;
		//param: Class<? extends Block> useBlock
//		Block block = ((BlockItem)client.player.getStackInHand(hand).getItem()).getBlock();
//		if(!useBlock.isInstance(block)) return false;

		Vec3 vec = client.player.position().add(client.player.getDeltaMovement()).add(0, -0.75, 0);

		Vec3 pos = client.player.position();
		if(aheadDist != 0 && !client.level.getBlockState(client.player.blockPosition().below())
				.getCollisionShape(client.level, client.player.blockPosition()).isEmpty()) {
			Vec3 dir = Vec3.directionFromRotation(0, client.player.getYRot()).multiply(aheadDist, 0, aheadDist);
			pos = pos.add(dir.x, 0, dir.z);
		}
		BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos();
		bp.set(pos.x, vec.y, pos.z);
		if(client.options.keyShift.isDown() && !client.options.keyJump.isDown() && client.player.getY() + vec.y > -1){
			bp.setY(bp.getY() - 1);
		}
		if(bp.getY() >= client.player.blockPosition().getY()){
			bp.setY(client.player.blockPosition().getY() - 1);
		}
		BlockPos targetBlock = bp.immutable();

		if(getPlaceSide(bp) == null){
			pos = client.player.position();
			pos = pos.add(0, -0.98f, 0);
			pos.add(client.player.getDeltaMovement());

			ArrayList<BlockPos> blockPosArray = new ArrayList<>();
			for(int x = (int)(client.player.getX() - placeRange); x < client.player.getX() + placeRange; ++x){
				for (int z = (int)(client.player.getZ() - placeRange); z < client.player.getZ() + placeRange; ++z){
					for (int y = (int)Math.max(client.level.getMinY(), client.player.getY() - placeRange);
							y < Math.min(client.level.getHeight(), client.player.getY() + placeRange); ++y)
					{
						bp.set(x, y, z);
						if(getPlaceSide(bp) == null) continue;
						if(!canPlaceBlock(bp)) continue;
						//if(client.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(bp.offset(getClosestPlaceSide(bp)))) > 36) continue;
						blockPosArray.add(bp.immutable());
					}
				}
			}
			if(blockPosArray.isEmpty()) return false;
			blockPosArray.sort(Comparator.comparingDouble((blockPos) -> blockPos.distSqr(targetBlock)));
			bp.set(blockPosArray.getFirst());
		}
		if(!client.level.getBlockState(bp).canBeReplaced()) return false;
		if(!holdingBlock) selectBlocksInHotbar(useBlock);
		String path = BuiltInRegistries.ITEM.getKey(client.player.getItemInHand(hand).getItem()).getPath();
		if(useBlock != null && !path.equals(useBlock)){
			sendOverlay(client.player, Component.literal("(!) Missing block: "+useBlock));
			//return false;
		}
		return placeBlock(bp, hand);
	}

	private Vec3 getEyesPos(){
		float eyeHeight = client.player.getEyeHeight(client.player.getPose());
		return client.player.position().add(0, eyeHeight, 0);
	}
	private Direction getBlockBreakingSide(BlockPos bp){
		Vec3 eyes = getEyesPos();
		Direction[] sides = Direction.values();

		BlockState state = client.level.getBlockState(bp);
		VoxelShape shape = state.getShape(client.level, bp);
		if(shape.isEmpty()) return null;

		AABB box = shape.bounds();
		Vec3 halfSize = new Vec3(box.maxX - box.minX, box.maxY - box.minY, box.maxZ - box.minZ).scale(0.5);
		Vec3 center = Vec3.atLowerCornerOf(bp).add(box.getCenter());

		Vec3[] hitVecs = new Vec3[sides.length];
		for(int i=0; i<sides.length; ++i){
			Vec3i dirVec = sides[i].getUnitVec3i();
			Vec3 relHitVec = new Vec3(halfSize.x * dirVec.getX(), halfSize.y * dirVec.getY(), halfSize.z * dirVec.getZ());
			hitVecs[i] = center.add(relHitVec);
		}
		double distanceSqToCenter = eyes.distanceToSqr(center);
		double[] distancesSq = new double[sides.length];
		boolean[] linesOfSight = new boolean[sides.length];

		for(int i=0; i<sides.length; ++i){
			distancesSq[i] = eyes.distanceToSqr(hitVecs[i]);
			if(distancesSq[i] >= distanceSqToCenter) continue;
			ClipContext context = new ClipContext(eyes, hitVecs[i], ClipContext.Block.COLLIDER,
					ClipContext.Fluid.NONE, client.player);
			linesOfSight[i] = client.level.clip(context).getType() == HitResult.Type.MISS;
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
		int bestI=client.player.getInventory().getSelectedSlot();
		float bestSpeed=0;
		for(int i=0; i<9; ++i){
			float speed = client.player.getInventory().getItem(i).getDestroySpeed(bs);
			if(speed > bestSpeed){bestSpeed=speed; bestI=i;}
		}
		if(bestSpeed == 0){
			sendOverlay(client.player, Component.literal("(!) No matching tool in hotbar"));
			return false;
		}
		if(bestI == client.player.getInventory().getSelectedSlot()){
			sendOverlay(client.player, Component.literal("Best tool in hotbar already selected"));
			return false;
		}
		selectSlot(client.player.getInventory(), bestI);
		client.getConnection().send(new ServerboundSetCarriedItemPacket(bestI));
		sendOverlay(client.player, Component.literal("Selected hotbar pickaxe"));
//		Main.LOGGER.info("Selected hotbar pickaxe");
		return true;
	}
	private int isMining;
	private boolean putOutFireAndMineObstacles(){
		BlockPos bp = client.player.blockPosition();
		if(bp.getY() == targetY+1) bp = bp.relative(Direction.DOWN);

		Vec3 dir = Vec3.directionFromRotation(0, client.player.getYRot());
		int dx = (int)Math.round(dir.x);
		int dz = (int)Math.round(dir.z);
		final boolean diag = dx != 0 && dz != 0;
		for(int i=0; i<(diag ? 1 : 3); ++i){
			BlockPos aheadPos = bp.offset(i*dx, 0, i*dz);
			if(client.level.getBlockState(aheadPos).getBlock() instanceof BaseFireBlock){
				client.gameMode.continueDestroyBlock(aheadPos, getBlockBreakingSide(aheadPos));
				sendOverlay(client.player, Component.literal("Put out a fire"));
				return true;
			}
		}
		// Don't try to mine blocks if the player isn't stuck
		if(client.player.xo != client.player.getX() || client.player.zo != client.player.getZ()) return false;

		ArrayList<BlockPos> mineSpots = new ArrayList<>();
		Vec3 pos = client.player.position();
		if(pos.y() > targetY) pos = new Vec3(pos.x, targetY, pos.z);
		if(dx != 0){
			mineSpots.add(bp.offset(dx, 0, 0));
			if(diag) mineSpots.add(bp.offset(dx, 0, Math.round(pos.z()) > pos.z() ? 1 : -1));
			else if(pos.z()+.3 > Math.floor(pos.z())+1) mineSpots.add(bp.offset(dx, 0, 1));
			else if(pos.z()-.3 < Math.floor(pos.z())) mineSpots.add(bp.offset(dx, 0, -1));
		}
		if(dz != 0){
			mineSpots.add(bp.offset(0, 0, dz));
			if(diag) mineSpots.add(bp.offset(Math.round(pos.z()) > pos.z() ? 1 : -1, 0, dz));
			else if(pos.x()+.3 > Math.floor(pos.x())+1) mineSpots.add(bp.offset(1, 0, dz));
			else if(pos.x()-.3 < Math.floor(pos.x())) mineSpots.add(bp.offset(-1, 0, -dz));
		}
		mineSpots.add(bp);
		if(diag) mineSpots.add(bp.offset(dx, 0, dz));

		BlockState bs = null;
		for(BlockPos bpDig : mineSpots){
			boolean foundDig = false;
			for(int i=0; i<2; ++i){
				if(i==1) bpDig = bpDig.offset(0, 2, 0);
				else if(i==2) bpDig = bpDig.offset(0, -1, 0);
				bs = client.level.getBlockState(bpDig);
				if(bs.getBlock() != Blocks.BEDROCK && !bs.getCollisionShape(client.level, bpDig).isEmpty()){foundDig = true; break;}
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
			client.gameMode.continueDestroyBlock(bpDig, getBlockBreakingSide(bpDig));
			sendOverlay(client.player, Component.literal("Mining: ").copy().append(bs.getBlock().getName())
//					.append(" yaw:"+client.player.getYaw()+", dirX:"+dir.x+",dirZ:"+dir.z+", xyz: ")
//					.append(bp.getX()+","+bp.getY()+","+bp.getZ())
					);
//			Main.LOGGER.info("Mining block: "+client.world.getBlockState(bp).getBlock().getName().getLiteralString());
			return true;
		}
		if(isMining > 0){
			if(--isMining == 0){
				selectBlocksInHotbar(null);
				sendOverlay(client.player, Component.literal("Mined: ").copy().append(bs.getBlock().getName()));
			}
			return true; // Wait a bit before declaring it done
		}
		return false;
	}

	private boolean barfTrash(KeybindEjectJunk ejectJunk){
		if(screen(client) instanceof AbstractContainerScreen) return false;

		boolean didBarf = false;
		for(int i=9; i<36; ++i) if(ejectJunk.shouldEject(client.player.getInventory().getItem(i))){
//			if(!didBarf){
//				client.player.sendMessage(Text.literal("Tossed item: ").copy().append(client.player.getInventory().getStack(i).getName()), true);
//				Main.LOGGER.info("Tossed item: "+client.player.getInventory().getStack(i).getName().getLiteralString());
//			}
			client.gameMode.handleContainerInput(0, i, 1, ContainerInput.THROW, client.player);
			didBarf = true;
		}
		return didBarf;
	}

	private void registerClientTickListener(){
		ClientTickEvents.START_CLIENT_TICK.register((Minecraft _) -> {
			if(client.player == null || client.level == null){isEnabled = false; enabledTs = 0; return;}
			if(enabledTs != 0){
				final long timeSinceEnabled = System.currentTimeMillis() - enabledTs;
				if(timeSinceEnabled > ENABLE_DELAY){
					isEnabled = true;
//					Configs.Hotkeys.EBOUNCE_TRAVEL_HELPER.setBooleanValue(true); // May already be true
					targetY = Long.MIN_VALUE;
					enabledTs = 0;
					sendOverlay(client.player, Component.literal("eBounce Helper: enabled"));
					sendSystem(client.player, Component.literal("eBounce Helper: enabled"));
				}
				else{
					sendOverlay(client.player, Component.literal("Enabling in "+String.format("%.2f", ((ENABLE_DELAY-timeSinceEnabled)/1000d))+"s..."));
				}
			}
			if(!isEnabled) return;
			if(client.player.getItemBySlot(EquipmentSlot.CHEST).getItem() != Items.ELYTRA){
				sendOverlay(client.player, Component.literal("Not wearing elytra"));
				return;
			}
			final int y = client.player.getBlockY();
			if(targetY == Long.MIN_VALUE) targetY = y;
			if(targetY != y){
				sendOverlay(client.player, Component.literal("Y-height has changed!"));
				return;
			}
//			if(y == 118){
//				client.player.jump();
//				// temporarily turn off meteor efly, then turn it back on (after getting out of the hole)
//			}
//			if(y != 119 && y != 120) return;

			if(fillHighwayHole(y == 119 ? null : "obsidian")){
				sendOverlay(client.player, Component.literal("Filled a hole"));
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

	public void updateEnabled(boolean enable){
//		Main.LOGGER.info("ebounce_travel_helper key pressed");
		if(client == null){
			Main.LOGGER.info("ebounce_travel_helper registered");
			client = Minecraft.getInstance();
			registerClientTickListener();
		}
//		client.player.sendMessage(Text.literal("eBounceHelper: key pressed, setEnabled="+enable), true);
//		client.player.sendMessage(Text.literal("eBounceHelper: key pressed, setEnabled="+enable), false);

		if(enable == isEnabled) return;
		if(!enable || enabledTs != 0){
			isEnabled = false;
			enabledTs = 0;
//			client.player.sendMessage(Text.literal("eBounceHelper: disabled"), true);
			sendSystem(client.player, Component.literal("eBounceHelper: disabled"));
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
		sendSystem(client.player, Component.literal("eBounce Helper: enabling..."));
		enabledTs = System.currentTimeMillis();
	}

	public KeybindEbounceTravelHelper(KeybindEjectJunk ejectJunk){
		this.ejectJunk = ejectJunk;
//		new Keybind("ebounce_travel_helper", this::toggle, null, GLFW.GLFW_KEY_A);
	}
}