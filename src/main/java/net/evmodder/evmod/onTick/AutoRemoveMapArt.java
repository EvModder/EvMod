package net.evmodder.evmod.onTick;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.Main;
import net.evmodder.evmod.Configs.Generic;
import net.evmodder.evmod.apis.MapRelationUtils;
import net.evmodder.evmod.apis.MapRelationUtils.RelatedMapsData;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class AutoRemoveMapArt/* extends MapLayoutFinder*/{
	private Direction dir; // non-null implies autoremover is active
	private World world;
	private int constAxis;
	private int numMatchingRemoved;

	private final int[] recentRemoveAttempts;
	private int attemptIdx;

	public AutoRemoveMapArt(){
		recentRemoveAttempts = new int[20];
		ClientTickEvents.END_CLIENT_TICK.register(this::removeNearestMap);
	}

	public final void disableAndReset(){
		dir = null;
		world = null;
		constAxis = numMatchingRemoved = 0;
	}

	private ItemFrameEntity lastIfe;
	private ItemStack lastStack;
	public final boolean mapRemoved(PlayerEntity player, ItemFrameEntity ife){
		try{
			if(!Generic.MAPART_AUTOREMOVE.getBooleanValue() || ife == null){
				disableAndReset(); return false;
			}
			assert !ife.getHeldItemStack().isEmpty();

			if(lastIfe != null){
				if(ife.getFacing() != lastIfe.getFacing()){
					Main.LOGGER.info("AutoRemoveMapArt: currIfe and lastIfe are not facing the same dir");
					disableAndReset(); return false;
				}
				if(ife.getWorld() != lastIfe.getWorld()){
					Main.LOGGER.info("AutoRemoveMapArt: currIfe and lastIfe are not in the same world!");
					disableAndReset(); return false;
				}
				RelatedMapsData data = MapRelationUtils.getRelatedMapsByName0(List.of(ife.getHeldItemStack(), lastStack), world);
				if(data.slots().size() != 2){ // TODO: support maps w/o custom name (related by edge detection)
					Main.LOGGER.info("AutoRemoveMapArt: currIfe and lastIfe are not related");
					disableAndReset(); return false;
				}
			}

			// TODO: add a section here to check neighboring ifes, and if they do not contain related maps, return false

			// Values below 1 are invalid and should not be allowed BTW
			final int NUM_REQ_TO_ENABLE = Generic.MAPART_AUTOREMOVE_AFTER.getIntegerValue();
			if(++numMatchingRemoved < NUM_REQ_TO_ENABLE) return false;

			// Update autoremover settings
			dir = ife.getFacing();
			world = ife.getWorld();
			switch(dir){
				case UP: case DOWN: constAxis = ife.getBlockY(); break;
				case EAST: case WEST: constAxis = ife.getBlockX(); break;
				case NORTH: case SOUTH: constAxis = ife.getBlockZ(); break;
			}
			return true;
		}
		finally{
			lastIfe = ife;
			lastStack = ife.getHeldItemStack().copy();
		}
	}

	private final double distFromPlane(BlockPos bp){
		switch(dir){
			case UP: case DOWN: return Math.abs(bp.getY() - constAxis);
			case EAST: case WEST: return Math.abs(bp.getX() - constAxis);
			case NORTH: case SOUTH: return Math.abs(bp.getZ() - constAxis);

			default: assert(false) : "Unreachable"; return -1;
		}
	}

	private final ItemFrameEntity getNearestMapToRemove(PlayerEntity player){
		final double MAX_REACH = Configs.Generic.PLACEMENT_HELPER_MAPART_REACH.getDoubleValue();
		final double SCAN_DIST = MAX_REACH+2;

		Box box = player.getBoundingBox().expand(SCAN_DIST, SCAN_DIST, SCAN_DIST);
		Predicate<ItemFrameEntity> filter = ife -> ife.getFacing() == dir && distFromPlane(ife.getBlockPos()) == 0 && !ife.getHeldItemStack().isEmpty()
				&& ife.squaredDistanceTo(player.getEyePos()) <= MAX_REACH*MAX_REACH;
		List<ItemFrameEntity> ifes = player.getWorld().getEntitiesByClass(ItemFrameEntity.class, box, filter);
		if(ifes.isEmpty()){
//			Main.LOGGER.warn("AutoPlaceMapArt: no nearby iframes");
			return null;
		}

		// TODO: further considerations:
		// * Remove only connected/adjacent
		// * Remove only maps in same layout as maps already removed

		ifes.sort((a, b) -> Double.compare(a.squaredDistanceTo(player.getEyePos()), b.squaredDistanceTo(player.getEyePos())));
		for(ItemFrameEntity ife : ifes){
			if(Arrays.stream(recentRemoveAttempts).anyMatch(id -> id == ife.getId())){
				Main.LOGGER.warn("AutoRemoveMapArt: Cannot remove from same iFrame twice! "+ife.getBlockPos().toShortString());
				continue;
			}
			return ife;
		}
		return null;
	}

	public final void removeNearestMap(MinecraftClient client){
		if(client.player == null || client.world == null){
			Main.LOGGER.info("AutoRemoveMapArt: player disconnected mid-op");
			return;
		}
		if(!Configs.Generic.MAPART_AUTOREMOVE.getBooleanValue()){
			Main.LOGGER.info("AutoRemoveMapArt: disabled mid-op");
			return;
		}

		if(client.player.currentScreenHandler != null && client.player.currentScreenHandler.syncId != 0){
//			Main.LOGGER.info("AutoRemoveMapArt: paused, currently in container gui");
			return;
		}

		// Don't spam-place in the same blockpos, give iframe entity a chance to load
		if(++attemptIdx >= recentRemoveAttempts.length) attemptIdx = 0;
		recentRemoveAttempts[attemptIdx] = 0;

		{
//			assert 0 <= attemptIdx < recentPlaceAttempts.length;
			int i = (attemptIdx + 1) % recentRemoveAttempts.length;
			while(i != attemptIdx){
				Entity e = client.world.getEntityById(recentRemoveAttempts[i]);
				if(e != null && e instanceof ItemFrameEntity ife && !ife.getHeldItemStack().isEmpty()){
//					final int rem = attemptIdx < i ? i-attemptIdx : recentPlaceAttempts.length+i-attemptIdx;
					final int waited = i < attemptIdx ? attemptIdx-i : recentRemoveAttempts.length+attemptIdx-i;
					Main.LOGGER.info("AutoRemoveMapArt: waiting for punched ife to drop its map ("+waited+"ticks)");
					return;
				}
				if(++i == recentRemoveAttempts.length) i = 0;
			}
		}

//		if(isMovingTooFast(client.player.getVelocity())) return; // Pause while player is moving

		ItemFrameEntity ife = getNearestMapToRemove(client.player);
		if(ife == null) return;

		Main.LOGGER.info("AutoRemoveMapArt: punching iFrame with map: "+ife.getHeldItemStack().getName().getString());
		recentRemoveAttempts[attemptIdx] = ife.getId();
		client.interactionManager.attackEntity(client.player, ife);
	}
}