package net.evmodder.evmod.onTick;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.Main;
import net.evmodder.evmod.Configs.Generic;
import net.evmodder.evmod.apis.MapRelationUtils;
import net.evmodder.evmod.apis.TickListener;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.evmodder.evmod.apis.MapRelationUtils.RelatedMapsData;

public final class AutoRemoveMapArt/* extends MapLayoutFinder*/{
	private Direction dir; // non-null implies autoremover is active
	private Level world;
	private int constAxis;
	private int numMatchingRemoved;

	private final int[] recentRemoveAttempts;
	private int attemptIdx;

	public AutoRemoveMapArt(){
		recentRemoveAttempts = new int[20];
//		ClientTickEvents.END_CLIENT_TICK.register(client->{synchronized(dir){removeNearestMap(client);}});
//		ClientTickEvents.END_CLIENT_TICK.register(this::removeNearestMap);
		TickListener.register(new TickListener(){
			@Override public void onTickEnd(Minecraft client){removeNearestMap(client);}
		});
	}

	public final void disableAndReset(){
		dir = null;
		world = null;
		constAxis = numMatchingRemoved = 0;
	}
	public final boolean isActivelyRemoving(){return dir != null;}

	private ItemFrame lastIfe;
	private ItemStack lastStack;
	public final boolean mapRemoved(ItemFrame ife){
//		synchronized(dir){
		try{
			if(!Generic.MAPART_AUTOREMOVE.getBooleanValue() || ife == null){
				disableAndReset(); return false;
			}
			assert !ife.getItem().isEmpty();

			// Values below 1 are invalid and should not be allowed BTW
			final int NUM_REQ_TO_ENABLE = Generic.MAPART_AUTOREMOVE_AFTER.getIntegerValue();

			if(NUM_REQ_TO_ENABLE > 1 && lastIfe != null){
				assert lastStack != null;
				if(ife.getNearestViewDirection() != lastIfe.getNearestViewDirection()){
					Main.LOGGER.info("AutoRemoveMapArt: currIfe and lastIfe are not facing the same dir");
					disableAndReset(); return false;
				}
				if(ife.level() != lastIfe.level()){
					Main.LOGGER.info("AutoRemoveMapArt: currIfe and lastIfe are not in the same world!");
					disableAndReset(); return false;
				}
				RelatedMapsData data = MapRelationUtils.getRelatedMapsByName0(List.of(ife.getItem(), lastStack), ife.level());
				if(data.slots().size() != 2){
					Main.LOGGER.info("AutoRemoveMapArt: currIfe and lastIfe are not related");
					disableAndReset(); return false;
				}
			}

			if(++numMatchingRemoved < NUM_REQ_TO_ENABLE) return false;

			// Update autoremover settings
			dir = ife.getNearestViewDirection();
			world = ife.level();
			switch(dir){
				case UP: case DOWN: constAxis = ife.getBlockY(); break;
				case EAST: case WEST: constAxis = ife.getBlockX(); break;
				case NORTH: case SOUTH: constAxis = ife.getBlockZ(); break;
			}

			// Final check: are there actually any maps that still need to be removed?
			AABB box = ife.getBoundingBox().inflate(2, 2, 2);
			lastStack = ife.getItem();
			Predicate<ItemFrame> filter = oIfe -> oIfe.getNearestViewDirection() == dir && distFromPlane(oIfe.blockPosition()) == 0
					&& oIfe.getItem().getItem() == Items.FILLED_MAP && isRelated(oIfe.getItem());
			if(ife.level().getEntitiesOfClass(ItemFrame.class, box, filter).isEmpty()){
				Main.LOGGER.info("AutoRemoveMapArt: appears there are no remaining (related) maps to remove");
				disableAndReset(); return false;
			}
			return true;
		}
		finally{
			recentRemoveAttempts[attemptIdx] = ife.getId();
			lastIfe = ife;
			lastStack = ife.getItem().copy();
		}
//		}
	}

	private final double distFromPlane(BlockPos bp){
		switch(dir){
			case UP: case DOWN: return Math.abs(bp.getY() - constAxis);
			case EAST: case WEST: return Math.abs(bp.getX() - constAxis);
			case NORTH: case SOUTH: return Math.abs(bp.getZ() - constAxis);

			default: assert(false) : "Unreachable"; return -1;
		}
	}

	private final boolean isRelated(ItemStack stack){
		assert lastStack != null && world != null;
//		if(dir == null) return false;
		RelatedMapsData data = MapRelationUtils.getRelatedMapsByName0(List.of(lastStack, stack), world);
		return data.slots().size() == 2;
	}
	private final ItemFrame getNearestMapToRemove(Player player){
		final double MAX_REACH = Configs.Generic.MAPART_AUTOREMOVE_REACH.getDoubleValue();
		final double SCAN_DIST = MAX_REACH+2;

		AABB box = player.getBoundingBox().inflate(SCAN_DIST, SCAN_DIST, SCAN_DIST);
		Predicate<ItemFrame> filter = ife -> ife.getNearestViewDirection() == dir && distFromPlane(ife.blockPosition()) == 0
				&& ife.getItem().getItem() == Items.FILLED_MAP
				&& ife.distanceToSqr(player.getEyePosition()) <= MAX_REACH*MAX_REACH
				&& isRelated(ife.getItem());
		List<ItemFrame> ifes = player.level().getEntitiesOfClass(ItemFrame.class, box, filter);
		if(ifes.isEmpty()){
//			Main.LOGGER.warn("AutoPlaceMapArt: no nearby iframes");
			return null;
		}

		// Further considerations:
		// * Remove only connected/adjacent
		// * with MapLayoutFinder, don't remove adjacent duplicate map if it is in a different layout/rotation from this one.

		ifes.sort((a, b) -> Double.compare(a.distanceToSqr(player.getEyePosition()), b.distanceToSqr(player.getEyePosition())));
		for(ItemFrame ife : ifes){
			if(Arrays.stream(recentRemoveAttempts).anyMatch(id -> id == ife.getId())){
				Main.LOGGER.warn("AutoRemoveMapArt: Cannot remove from same iFrame twice! "+ife.blockPosition().toShortString());
				continue;
			}
			return ife;
		}
		return null;
	}

	private final void removeNearestMap(Minecraft client){
		if(dir == null) return;
		if(client.player == null || client.level == null){
			Main.LOGGER.info("AutoRemoveMapArt: player disconnected mid-op");
			disableAndReset(); return;
		}
		if(!Configs.Generic.MAPART_AUTOREMOVE.getBooleanValue()){
			Main.LOGGER.info("AutoRemoveMapArt: disabled mid-op");
			disableAndReset(); return;
		}

		if(client.player.containerMenu != null && client.player.containerMenu.containerId != 0){
//			Main.LOGGER.info("AutoRemoveMapArt: paused, currently in container gui");
			return;
		}

		// Don't spam-place in the same blockpos, give iframe entity a chance to load
		if(++attemptIdx >= recentRemoveAttempts.length) attemptIdx = 0;
		recentRemoveAttempts[attemptIdx] = 0;

		/*{
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
		}*/

//		if(isMovingTooFast(client.player.getVelocity())) return; // Pause while player is moving

		ItemFrame ife = getNearestMapToRemove(client.player);
		if(ife == null) return;

		Main.LOGGER.info("AutoRemoveMapArt: punching iFrame with map: "+ife.getItem().getHoverName().getString());
		recentRemoveAttempts[attemptIdx] = ife.getId();
		client.gameMode.attack(client.player, ife);
	}
}