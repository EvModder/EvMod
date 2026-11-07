package net.evmodder.evmod.apis;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;

/** Best-effort, read-only circuit trace; this does not simulate redstone timing or pushed block chains. */
public final class PearlTriggerLookup{
	private static final int MAX_DEPTH = 32, MAX_VISITS = 256, CHAMBER_DEPTH = 8;
	private record Input(BlockPos pos, Direction from, boolean strong, boolean fromWire){}
	private record Signal(Input input, int strength, int depth){}

	/** Returns loaded pearls affected by traced collision changes, never a nearest-pearl guess. */
	public static List<ThrownEnderpearl> findAffectedPearls(final ClientLevel level, final BlockPos trigger){
		final Trace trace = new Trace(level);
		trace.changed(trigger, trace.state(trigger), 0);
		trace.run();
		final Set<ThrownEnderpearl> pearls = new HashSet<>();
		for(BlockPos door : trace.trapdoors){
			int bottom = door.getY();
			for(int n=1; n<=CHAMBER_DEPTH; ++n){
				final BlockPos below = door.below(n);
				if(!trace.state(below).getCollisionShape(level, below).isEmpty()) break;
				bottom = below.getY();
			}
			pearls.addAll(level.getEntitiesOfClass(ThrownEnderpearl.class,
					new AABB(door.getX(), bottom, door.getZ(), door.getX()+1, door.getY()+1, door.getZ()+1), pearl -> !pearl.isRemoved()));
		}
		for(BlockPos piston : trace.pistons){
			final BlockPos front = piston.relative(trace.state(piston).getValue(BlockStateProperties.FACING));
			// Only direct head contact: a colliding block would require resolving its own movement first.
			if(!trace.state(front).getCollisionShape(level, front).isEmpty()) continue;
			pearls.addAll(level.getEntitiesOfClass(ThrownEnderpearl.class, new AABB(front), pearl -> !pearl.isRemoved()));
		}
		for(BlockPos barrier : trace.barriers){
			final BlockState state = trace.state(barrier);
			// Include either collision position: opening can release a pearl; closing can hit it.
			for(AABB box : Shapes.or(state.getCollisionShape(level, barrier),
					state.cycle(BlockStateProperties.OPEN).getCollisionShape(level, barrier)).toAabbs()){
				pearls.addAll(level.getEntitiesOfClass(ThrownEnderpearl.class, box.move(barrier).inflate(1e-7), pearl -> !pearl.isRemoved()));
			}
		}
		// A partial/unloaded/over-budget trace cannot establish which chambers are affected.
		return trace.incomplete ? List.of() : new ArrayList<>(pearls);
	}

	private static RedstoneSide wireSide(final BlockState state, final Direction direction){
		return state.getValue(switch(direction){
			case NORTH -> BlockStateProperties.NORTH_REDSTONE;
			case SOUTH -> BlockStateProperties.SOUTH_REDSTONE;
			case EAST -> BlockStateProperties.EAST_REDSTONE;
			case WEST -> BlockStateProperties.WEST_REDSTONE;
			default -> throw new IllegalArgumentException("Not horizontal: "+direction);
		});
	}

	private static final class Trace{
		private final ClientLevel level;
		private final ArrayDeque<Signal> pending = new ArrayDeque<>();
		private final HashMap<Input, Integer> seen = new HashMap<>();
		private final Set<BlockPos> changes = new HashSet<>(), trapdoors = new HashSet<>(), pistons = new HashSet<>(), barriers = new HashSet<>();
		private int visits;
		private boolean incomplete;
		private Trace(final ClientLevel level){this.level = level;}

		private BlockState state(final BlockPos pos){
			if(!level.getChunkSource().hasChunk(pos.getX()>>4, pos.getZ()>>4)){incomplete = true; return Blocks.AIR.defaultBlockState();}
			return level.getBlockState(pos);
		}
		private void push(final BlockPos pos, final Direction from, final int strength, final boolean strong, final boolean fromWire, final int depth){
			if(depth > MAX_DEPTH){incomplete = true; return;}
			pending.addLast(new Signal(new Input(pos, from, strong, fromWire), strength, depth));
		}
		private void changed(final BlockPos pos, final BlockState block, final int depth){
			if(incomplete || !changes.add(pos)) return;
			if(depth > MAX_DEPTH || ++visits > MAX_VISITS){incomplete = true; return;}
			if(block.is(BlockTags.TRAPDOORS)) trapdoors.add(pos);
			else if(block.is(BlockTags.DOORS) || block.is(BlockTags.FENCE_GATES)){
				barriers.add(pos);
				if(block.is(BlockTags.DOORS)){
					final BlockPos other = block.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
					changed(other, state(other), depth+1);
				}
			}
			for(Direction direction : Direction.values()){
				final BlockPos observer = pos.relative(direction);
				final BlockState state = state(observer);
				if(state.is(Blocks.OBSERVER) && state.getValue(BlockStateProperties.FACING) == direction.getOpposite()){
					emit(observer, state.setValue(BlockStateProperties.POWERED, true), depth+1);
				}
			}
		}
		private void emit(final BlockPos pos, final BlockState state, final int depth){
			if(incomplete) return;
			changed(pos, state, depth);
			final boolean wire = state.is(Blocks.REDSTONE_WIRE);
			for(Direction direction : Direction.values()){
				final int strength = state.getSignal(level, pos, direction.getOpposite());
				if(strength > 0) push(pos.relative(direction), direction.getOpposite(), strength,
						state.getDirectSignal(level, pos, direction.getOpposite()) > 0, wire, depth+1);
			}
			if(wire) for(Direction direction : Direction.Plane.HORIZONTAL){
				final RedstoneSide side = wireSide(state, direction);
				if(!side.isConnected()) continue;
				final BlockPos beside = pos.relative(direction);
				final BlockPos next = side == RedstoneSide.UP ? beside.above()
						: !state(beside).isRedstoneConductor(level, beside) ? beside.below() : beside;
				if(state(next).is(Blocks.REDSTONE_WIRE)) push(next, direction.getOpposite(), state.getValue(BlockStateProperties.POWER), false, true, depth+1);
			}
		}
		private void run(){
			while(!incomplete && !pending.isEmpty()){
				final Signal signal = pending.removeFirst();
				final Input input = signal.input;
				if(seen.getOrDefault(input, 0) >= signal.strength) continue;
				seen.put(input, signal.strength);
				if(++visits > MAX_VISITS){incomplete = true; break;}
				final BlockState state = state(input.pos);
				if((state.is(Blocks.PISTON) || state.is(Blocks.STICKY_PISTON)) && !state.getValue(BlockStateProperties.EXTENDED)
						&& input.from != state.getValue(BlockStateProperties.FACING)) pistons.add(input.pos);
				if(state.is(Blocks.NOTE_BLOCK)) changed(input.pos, state, signal.depth);
				if(state.is(BlockTags.TRAPDOORS) || state.is(BlockTags.DOORS) || state.is(BlockTags.FENCE_GATES)) changed(input.pos, state, signal.depth);
				else if(state.is(Blocks.REDSTONE_WIRE)){
					final int strength = signal.strength-(input.fromWire ? 1 : 0);
					if(strength > 0) emit(input.pos, state.setValue(BlockStateProperties.POWER, strength), signal.depth);
				}
				else if(state.is(Blocks.REPEATER)){
					if(state.getValue(BlockStateProperties.HORIZONTAL_FACING) == input.from && !state.getValue(BlockStateProperties.LOCKED)){
						emit(input.pos, state.setValue(BlockStateProperties.POWERED, true), signal.depth);
					}
				}
				else if(state.is(Blocks.REDSTONE_TORCH) || state.is(Blocks.REDSTONE_WALL_TORCH)){
					final Direction support = state.is(Blocks.REDSTONE_TORCH) ? Direction.DOWN
							: state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();
					if(input.from == support) emit(input.pos, state.setValue(BlockStateProperties.LIT, true), signal.depth);
				}
				else if(input.strong && state.isRedstoneConductor(level, input.pos)){
					// Conduct one block only. Dust-powered blocks cannot feed another dust line.
					for(Direction direction : Direction.values()){
						final BlockPos next = input.pos.relative(direction);
						if(!input.fromWire || !state(next).is(Blocks.REDSTONE_WIRE)) push(next, direction.getOpposite(), signal.strength, false, false, signal.depth+1);
					}
				}
			}
		}
	}
}