package net.evmodder.KeyBound.onTick;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.evmodder.KeyBound.Main;
import net.evmodder.KeyBound.apis.MapRelationUtils;
import net.evmodder.KeyBound.apis.MapRelationUtils.RelatedMapsData;
import net.evmodder.KeyBound.config.Configs;
import net.evmodder.KeyBound.config.Configs.Generic;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

public class AutoPlaceMapArt{
	private Direction dir; public boolean isActive(){return dir != null;}
	private ItemStack currStack; private String currPosStr;
	private int constAxis;
	private int varAxis1Origin, varAxis2Origin;
	private Boolean varAxis1Neg, varAxis2Neg, axisMatch;
	private RelatedMapsData currentData;
	private final ArrayList<ItemStack> allMapItems;
	private final ArrayList<Integer> stacksHashesForCurrentData;
//	private long lastPlaceTs;
	private final int[] recentPlaceAttempts;
//	private ItemStack lastPlacedMapStack;
	private int attemptIdx, lastAttemptIdx;
	private final int INV_DELAY_TICKS = 1;
	private int ticksSinceInvAction;

	public AutoPlaceMapArt(){
		allMapItems = new ArrayList<>();
		stacksHashesForCurrentData = new ArrayList<>();
		recentPlaceAttempts = new int[20];
	}

	private double distFromPlane(BlockPos bp){
		switch(dir){
			case UP: case DOWN: return Math.abs(bp.getY() - constAxis);
			case EAST: case WEST: return Math.abs(bp.getX() - constAxis);
			case NORTH: case SOUTH: return Math.abs(bp.getZ() - constAxis);

			default: assert(false) : "Unreachable"; return -1;
		}
	}

	/*Vec3d getIframeCenter(BlockPos bp){
		Vec3d blockHit = bp.toCenterPos();
		switch(dir){
			case UP: return blockHit.add(0, -.5, 0);
			case DOWN: return blockHit.add(0, .5, 0);
			case EAST: return blockHit.add(-.5, 0, 0);
			case WEST: return blockHit.add(.5, 0, 0);
			case NORTH: return blockHit.add(0, 0, .5);
			case SOUTH: return blockHit.add(0, 0, -.5);

			default: assert(false) : "Unreachable"; return null;
		}
	}*/

	record AxisData(int constAxis, int varAxis1, int varAxis2){}
	private AxisData getAxisData(ItemFrameEntity ife){
		final BlockPos bp = ife.getBlockPos();
		switch(/*dir*/ife.getFacing()){
			case UP: case DOWN: return new AxisData(bp.getY(), bp.getX(), bp.getZ());
			case EAST: case WEST: return new AxisData(bp.getX(), bp.getY(), bp.getZ());
			case NORTH: case SOUTH: return new AxisData(bp.getZ(), bp.getX(), bp.getY());
		}
		Main.LOGGER.info("AutoPlaceMapArt: Unreachable!!!");
		assert false;
		return null;
	}
	/*private int getConstantAxis(ItemFrameEntity ife){
		int axis;
		switch(ife.getFacing()){
			case UP: case DOWN: axis = ife.getBlockPos().getY(); break;
			case EAST: case WEST: axis = ife.getBlockPos().getX(); break;
			case NORTH: case SOUTH: axis = ife.getBlockPos().getZ(); break;
		}
		Main.LOGGER.info("AutoPlaceMapArt: Unreachable!!!");
		assert false;
		return -1;
	}*/

	record Pos2DPair(int a1, int a2, int b1, int b2){}
	private int intFromPos(String pos){
		assert pos.matches("[A-Z]+|[0-9]+") : "Invalid 2d pos str part! "+pos;
		if('0' <= pos.charAt(0) && pos.charAt(0) <= '9') return Integer.parseInt(pos);

		int res = pos.charAt(0) - 'A';
		for(int i=1; i<pos.length(); ++i){
			res *= 26;
			res += pos.charAt(i) - 'A';
		}
		return res;
	}
	private final Pos2DPair getRelativePosPair(final String posA, final String posB){
//		Main.LOGGER.info("posA:"+posA+",posB:"+posB);

		int cutA, cutB, cutSpaceA, cutSpaceB;
		if(posA.length() == posB.length() && posA.length() == 2){cutA = cutB = 1; cutSpaceA = cutSpaceB = 0;}
		else{cutA = posA.indexOf(' '); cutB = posB.indexOf(' '); cutSpaceA = cutSpaceB = 1;}
		//assert (cutA==-1) == (cutB==-1);
		if(cutA == -1 && cutB == -1) return null;
		if((cutA == -1) != (cutB == -1)){
			if(cutA != -1 && posA.length() == posB.length()+1){cutB = cutA; cutSpaceB = 0;}
			else if(cutB != -1 && posB.length() == posA.length()+1){cutA = cutB; cutSpaceA = 0;}
			else return null;
		}
//		assert posA.replaceFirst(" ", "").matches("[0-9A-Z]+") && posB.replaceFirst(" ", "").matches("[0-9A-Z]+") : "Invalid posStrs: a="+posA+" b="+posB;

//		final StringBuilder builderPosA = new StringBuilder(posA.length());
//		final StringBuilder builderPosB = new StringBuilder(posB.length());
//		for(char c : posA.toCharArray()) builderPosA.append('A' <= c && c <= 'Z' ? ""+((int)(c-'A')) : c);
//		for(char c : posB.toCharArray()) builderPosB.append('A' <= c && c <= 'Z' ? ""+((int)(c-'A')) : c);
//		final String posA1 = builderPosA.substring(0, cutA), posA2 = builderPosA.substring(cutA+cutSpaceA);
//		final String posB1 = builderPosB.substring(0, cutB), posB2 = builderPosB.substring(cutB+cutSpaceB);
		
		final String posA1 = posA.substring(0, cutA), posA2 = posA.substring(cutA+cutSpaceA);
		final String posB1 = posB.substring(0, cutB), posB2 = posB.substring(cutB+cutSpaceB);

//		Main.LOGGER.info("posA:"+posA+",posB:"+posB+",posA1:"+posA1+",posA2:"+posA2+",posB1:"+posB1+",posB2:"+posB2);
		return new Pos2DPair(intFromPos(posA1), intFromPos(posA2), intFromPos(posB1), intFromPos(posB2));
	}

	private void disableAndReset(){
		if(dir != null){
			dir = null;
			currStack = null;
			currPosStr = null;
			constAxis = varAxis1Origin = varAxis2Origin = 0;
			varAxis1Neg = varAxis2Neg = axisMatch = null;
			currentData = null;
			allMapItems.clear();
			stacksHashesForCurrentData.clear();
		}
	}

	private String getPosStrFromName(final ItemStack stack, final RelatedMapsData data){
		final String name = stack.getCustomName().getString();
		return MapRelationUtils.simplifyPosStr(name.substring(data.prefixLen(), name.length()-data.suffixLen()));
	}
	public final boolean recalcIsActive(PlayerEntity player, ItemFrameEntity lastIfe, ItemStack lastStack, ItemFrameEntity currIfe, ItemStack currStack){
		if(!Generic.PLACEMENT_HELPER_MAPART_AUTOPLACE.getBooleanValue()
			|| currIfe == null || currStack == null || lastIfe == null || lastStack == null || currStack.getCount() != 1 || lastStack.getCount() != 1)
		{
			disableAndReset();
			return false;
		}

		if((dir=currIfe.getFacing()) != lastIfe.getFacing()){
			Main.LOGGER.info("AutoPlaceMapArt: currIfe and lastIfe are not facing the same dir");
			disableAndReset(); return false;
		}
		AxisData currAxisData = getAxisData(currIfe), lastAxisData = getAxisData(lastIfe);
		if((constAxis=currAxisData.constAxis) != lastAxisData.constAxis){
			Main.LOGGER.info("AutoPlaceMapArt: currIfe and lastIfe are not on the same const axis");
			disableAndReset(); return false;
		}

		final int ifeOffset1 = currAxisData.varAxis1 - lastAxisData.varAxis1, ifeOffset2 = currAxisData.varAxis2 - lastAxisData.varAxis2;
//		Main.LOGGER.info("AutoPlaceMapArt: ifeOffset1="+ifeOffset1+",ifeOffset2="+ifeOffset2);
		if(ifeOffset1 == 0 && ifeOffset2 == 0){
			Main.LOGGER.error("AutoPlaceMapArt: Placed maps appear to have the same pos! (shouldn't be possible!)");
			disableAndReset(); return false;
		}

		RelatedMapsData data = MapRelationUtils.getRelatedMapsByName0(List.of(this.currStack=currStack, lastStack), currIfe.getWorld());
		if(data.slots().size() != 2){ // TODO: support maps w/o custom name (related by edge detection)
			Main.LOGGER.info("AutoPlaceMapArt: currIfe and lastIfe are not related");
			disableAndReset(); return false;
		}
		if(data.prefixLen() == -1){ // TODO: support related maps without pos data (same name, no pos data)
			Main.LOGGER.info("AutoPlaceMapArt: currIfe and lastIfe are not facing the same dir");
			disableAndReset(); return false;
		}
		// Parse 2d pos (and cache for other maps items, if necessary)
		if(currentData == null){
			assert allMapItems.isEmpty();
			allMapItems.add(currStack); allMapItems.add(lastStack);
			MapRelationUtils.getAllNestedItems(player.getInventory().main.stream()).filter(s -> s.getItem() == Items.FILLED_MAP).forEach(allMapItems::add);
			Main.LOGGER.info("AutoPlaceMapArt: all maps in inv: "+(allMapItems.size()-2));

			currentData = MapRelationUtils.getRelatedMapsByName0(allMapItems, player.getWorld());
			if(currentData.slots().size() <= 3){
				Main.LOGGER.info("AutoPlaceMapArt: not enough remaining maps in inv to justify enabling AutoPlace");
				disableAndReset(); return false;
			}
			Main.LOGGER.info("AutoPlaceMapArt: related maps in inv: "+(currentData.slots().size()-2));
		}
		final String currPosStr = this.currPosStr=getPosStrFromName(currStack, currentData), lastPosStr = getPosStrFromName(lastStack, currentData);
		final Pos2DPair pos2dPair = getRelativePosPair(currPosStr, lastPosStr);
		if(pos2dPair == null){ // TODO: Support non-standard pos data (e.g., TL,TR,BL,BR)
			Main.LOGGER.info("AutoPlaceMapArt: unable to parse pos2dPair from pos strs ("+currPosStr+","+lastPosStr+")");
			disableAndReset(); return false;
		}

		final int posOffset1 = pos2dPair.a1 - pos2dPair.b1, posOffset2 = pos2dPair.a2 - pos2dPair.b2;
//		Main.LOGGER.info("AutoPlaceMapArt: posOffset1="+posOffset1+",posOffset2="+posOffset2);

//		assert(
//			(Math.abs(posOffset1) == Math.abs(ifeOffset1) && Math.abs(posOffset2) == Math.abs(ifeOffset2)) ||
//			(Math.abs(posOffset1) == Math.abs(ifeOffset2) && Math.abs(posOffset2) == Math.abs(ifeOffset1))
//		);
		if(Math.abs(posOffset1) != Math.abs(ifeOffset1) && Math.abs(posOffset1) != Math.abs(ifeOffset2)){
			Main.LOGGER.warn("AutoPlaceMapArt: user appears to have placed mapart in invalid spot! abs(axisDiff1)");
			disableAndReset(); return false;
		}
		if(Math.abs(posOffset2) != Math.abs(ifeOffset1) && Math.abs(posOffset2) != Math.abs(ifeOffset2)){
			Main.LOGGER.warn("AutoPlaceMapArt: user appears to have placed mapart in invalid spot! abs(axisDiff2)");
			disableAndReset(); return false;
		}

		final boolean sameAbsPosOffsets = Math.abs(posOffset1) == Math.abs(posOffset2);
		if(!sameAbsPosOffsets){
			final boolean axisMatches = Math.abs(posOffset1) == Math.abs(ifeOffset1);
			if(axisMatch != null && axisMatch != axisMatches){
				Main.LOGGER.warn("AutoPlaceMapArt: user appears to have placed mapart in invalid spot! axis swap");
				disableAndReset(); return false;
			}
			axisMatch = axisMatches;
		}
		if(axisMatch == null){
			// At this point, we know abs(posOffset1) == abs(posOffset2) == abs(ifeOffset1) == abs(ifeOffset2);
			final boolean sameSign = ((posOffset1 == ifeOffset1) == (posOffset1 == ifeOffset2)) && ((posOffset2 == ifeOffset1) == (posOffset2 == ifeOffset2));
			if(sameSign){
				final boolean isNeg = posOffset1 != ifeOffset1;
				if(varAxis1Neg == null) varAxis1Neg = isNeg;
				else if(varAxis1Neg != isNeg){
					Main.LOGGER.warn("AutoPlaceMapArt: user appears to have placed mapart in invalid spot! +-axisDiff1");
					disableAndReset(); return false;
				}
				if(varAxis2Neg == null) varAxis2Neg = isNeg;
				else if(varAxis2Neg != isNeg){
					Main.LOGGER.warn("AutoPlaceMapArt: user appears to have placed mapart in invalid spot! +-axisDiff1");
					disableAndReset(); return false;
				}
//				Main.LOGGER.info("AutoPlaceMapArt: determined both axes offsets are "+(isNeg?"-":"+"));
			}
			Main.LOGGER.info("AutoPlaceMapArt: unable to distinguish the 2 variable axes from eachother");
			return false;
		}

		if(posOffset1 != 0){
			final boolean isNeg = axisMatch ? posOffset1 != ifeOffset1 : posOffset1 != ifeOffset2;
			assert posOffset1 == (axisMatch ? ifeOffset1 : ifeOffset2)*(isNeg ? -1 : +1);
			if(varAxis1Neg == null) varAxis1Neg = isNeg;
			else if(varAxis1Neg != isNeg){
				Main.LOGGER.warn("AutoPlaceMapArt: user appears to have placed mapart in invalid spot! +-axis1");
				disableAndReset(); return false;
			}
		}
		if(posOffset2 != 0){
			final boolean isNeg = axisMatch ? posOffset2 != ifeOffset2 : posOffset2 != ifeOffset1;
			assert posOffset2 == (axisMatch ? ifeOffset2 : ifeOffset1)*(isNeg ? -1 : +1);
			if(varAxis2Neg == null) varAxis2Neg = isNeg;
			else if(varAxis2Neg != isNeg){
				Main.LOGGER.warn("AutoPlaceMapArt: user appears to have placed mapart in invalid spot! +-axis2");
				disableAndReset(); return false;
			}
		}

		if(!stacksHashesForCurrentData.isEmpty()) return true; // Already ongoing and all values are defined

		if(varAxis1Neg == null || varAxis2Neg == null){
			Main.LOGGER.warn("AutoPlaceMapArt: determined 1 of 2 axis offsets, just need to get the other offset before enabling");
			return false;
		}

		varAxis1Origin = currAxisData.varAxis1-(axisMatch ? pos2dPair.a1 : pos2dPair.a2)*(varAxis1Neg?-1:+1);
		varAxis2Origin = currAxisData.varAxis2-(axisMatch ? pos2dPair.a2 : pos2dPair.a1)*(varAxis2Neg?-1:+1);

		assert !allMapItems.isEmpty();
		assert stacksHashesForCurrentData.isEmpty();
		stacksHashesForCurrentData.ensureCapacity(currentData.slots().size());
		currentData.slots().stream().map(i -> ItemStack.hashCode(allMapItems.get(i))).forEach(stacksHashesForCurrentData::add);
		assert !stacksHashesForCurrentData.isEmpty();

//		Main.LOGGER.info("AutoPlaceMapArt: varAxis1o="+varAxis1Origin+",varAxis2o="+varAxis2Origin+",varAxis1Neg="+varAxis1Neg+",varAxis2Neg="+varAxis2Neg);
		return true;
	}

	private BlockPos getPlacement(ItemStack stack, ClientWorld world){
		if(!stacksHashesForCurrentData.contains(ItemStack.hashCode(stack))){
			RelatedMapsData data = MapRelationUtils.getRelatedMapsByName0(List.of(currStack, stack), world);
			if(data.slots().size() != 2 || data.prefixLen() == -1) return null; // Not part of the map being autoplaced
			Main.LOGGER.info("AutoPlaceMapArt: Added map itemstack to currentData"+(stack.getCustomName()==null?"":", name="+stack.getCustomName().getString()));
			stacksHashesForCurrentData.add(ItemStack.hashCode(stack));
		}
		final String posStr = getPosStrFromName(stack, currentData);
		final Pos2DPair pos2dPair = getRelativePosPair(currPosStr, posStr);
		if(pos2dPair == null) return null;
		int varAxis1 = varAxis1Origin+(axisMatch ? pos2dPair.b1 : pos2dPair.b2)*(varAxis1Neg?-1:+1);
		int varAxis2 = varAxis2Origin+(axisMatch ? pos2dPair.b2 : pos2dPair.b1)*(varAxis2Neg?-1:+1);

		switch(dir){
			case UP: case DOWN: return new BlockPos(varAxis1, constAxis, varAxis2);
			case EAST: case WEST: return new BlockPos(constAxis, varAxis1, varAxis2);
			case NORTH: case SOUTH: return new BlockPos(varAxis1, varAxis2, constAxis);
		}
		Main.LOGGER.info("AutoPlaceMapArt: Unreachable!!!");
		assert false;
		return null;
	}

	boolean unableToFindMap;
	public final void placeNearestMap(MinecraftClient client){
		if(stacksHashesForCurrentData.isEmpty()) return; // mapartPlacer is not currently active
		if(client.player == null || client.world == null){
			Main.LOGGER.info("AutoPlaceMapArt: player disconnected mid-op");
			disableAndReset();
			return;
		}
		if(!Configs.Generic.PLACEMENT_HELPER_MAPART_AUTOPLACE.getBooleanValue()){
			Main.LOGGER.info("AutoPlaceMapArt: disabled mid-op");
			disableAndReset();
			return;
		}
//		final long ts = System.currentTimeMillis();
//		if(ts-lastPlaceTs < 100) return; // Cooldown

		// Sadly this doesn't appear to work, since UseEntityCallback.EVENT isn't triggered for some reason.
		// And yeah, I tried setting it manually, but since the code can't guarantee a map gets placed, doing so can get it stuck.
		if(!client.player.isInCreativeMode() && UpdateInventoryHighlights.hasCurrentlyBeingPlaceMapArt()){
			Main.LOGGER.info("AutoPlaceMapArt: waiting for last manually-placed mapart to vanish from mainhand");
			return;
		}

		// Same problem
//		if(lastPlacedMapStack != null && ItemStack.areEqual(lastPlacedMapStack, client.player.getMainHandStack())){
//			Main.LOGGER.info("AutoPlaceMapArt: waiting for current mapart to be placed in iFrame...");
//			return;
//		}
		{
			Entity e = client.world.getEntityById(recentPlaceAttempts[lastAttemptIdx]);
			if(e != null && e instanceof ItemFrameEntity ife && ItemStack.areEqual(client.player.getMainHandStack(), ife.getHeldItemStack())){
				final int waited = lastAttemptIdx < attemptIdx ? attemptIdx-lastAttemptIdx : recentPlaceAttempts.length+attemptIdx-lastAttemptIdx;
				Main.LOGGER.info("AutoPlaceMapArt: waiting for current map to vanish from mainhand ("+waited+"ticks)");
				return;
			}
		}

		// Don't spam-place in the same blockpos, give iframe entity a chance to load
//		final boolean lastAttemptSucceeded = recentPlaceAttempts[attemptIdx] != 0;
		if(++attemptIdx >= recentPlaceAttempts.length) attemptIdx = 0;
		recentPlaceAttempts[attemptIdx] = 0;

//		if(Arrays.stream(recentPlaceAttempts).anyMatch(id -> {
//			Entity e = client.world.getEntityById(id);
//			return e != null && e instanceof ItemFrameEntity ife && ife.getHeldItemStack().isEmpty();
//		})){
//			Main.LOGGER.warn("AutoPlaceMapArt: seems there was a failed place attempt, pausing for a moment...");
//			return;
//		}
		{
//			assert 0 <= attemptIdx < recentPlaceAttempts.length;
			int i = (attemptIdx + 1) % recentPlaceAttempts.length;
			while(i != attemptIdx){
				Entity e = client.world.getEntityById(recentPlaceAttempts[i]);
				if(e != null && e instanceof ItemFrameEntity ife && ife.getHeldItemStack().isEmpty()){
					if(ife.getHeldItemStack().isEmpty()){
	//					final int rem = attemptIdx < i ? i-attemptIdx : recentPlaceAttempts.length+i-attemptIdx;
						final int waited = i < attemptIdx ? attemptIdx-i : recentPlaceAttempts.length+attemptIdx-i;
						Main.LOGGER.info("AutoPlaceMapArt: waiting for current map to appear in iFrame ("+waited+"ticks)");
						return;
					}
				}
				if(++i == recentPlaceAttempts.length) i = 0;
			}
		}

		final List<ItemStack> slots = client.player.playerScreenHandler.slots.stream().map(Slot::getStack).collect(Collectors.toList());
		final int selectedSlot = client.player.getInventory().selectedSlot;

		final double MAX_REACH = Configs.Generic.PLACEMENT_HELPER_MAPART_REACH.getDoubleValue();
		final double SCAN_DIST = MAX_REACH+2;

		Box box = client.player.getBoundingBox().expand(SCAN_DIST, SCAN_DIST, SCAN_DIST);
		Predicate<ItemFrameEntity> filter = ife -> ife.getFacing() == dir && distFromPlane(ife.getBlockPos()) == 0 && ife.getHeldItemStack().isEmpty();
		List<ItemFrameEntity> ifes = client.world.getEntitiesByClass(ItemFrameEntity.class, box, filter);
		if(ifes.isEmpty()){
//			Main.LOGGER.warn("AutoPlaceMapArt: no nearby iframes");
			return;
		}

		double nearestDistSq = Double.MAX_VALUE;
//		BlockPos nearestBp;
		ItemFrameEntity nearestIfe = null;
		ItemStack nearestStack = null;
		int nearestSlot = -1;
		int numMaps = 0, numUsableMaps = 0, numUsableMapsInRange = 0;
		for(int i=0; i<slots.size(); ++i){
			ItemStack mapItem = slots.get(i);
			BundleContentsComponent contents = slots.get(i).get(DataComponentTypes.BUNDLE_CONTENTS);
			if(contents != null && !contents.isEmpty()) mapItem = contents.get(0);
			if(mapItem.getItem() != Items.FILLED_MAP) continue;
			++numMaps;
			BlockPos bp = getPlacement(mapItem, client.world);
			if(bp == null) continue;
			++numUsableMaps;
			final double distSq = bp.getSquaredDistance(client.player.getEyePos());
			if(distSq < MAX_REACH*MAX_REACH){
				++numUsableMapsInRange;
				Optional<ItemFrameEntity> optionalIfe = ifes.stream().filter(ife -> ife.getBlockPos().equals(bp)).findAny();
				if(optionalIfe.isEmpty()){
					Main.LOGGER.warn("AutoPlaceMapArt: Missing iFrame at pos! "+bp.toShortString());
					continue;
				}
				final int ifeId = optionalIfe.get().getId();
				if(Arrays.stream(recentPlaceAttempts).anyMatch(id -> id == ifeId)){
					Main.LOGGER.warn("AutoPlaceMapArt: Cannot place into the same iFrame twice! "+bp.toShortString());
					continue;
				}
				if(i-36 == selectedSlot){
//					Main.LOGGER.info("AutoPlaceMapArt: Stack in hand is a valid candidate, using it!");
					nearestSlot = i;
					nearestDistSq = distSq;
					nearestIfe = optionalIfe.get();
					nearestStack = mapItem;
					break;
				}
				final boolean isHbSlot = i >= 36 && i < 45;
				if(isHbSlot || distSq < nearestDistSq){
					nearestDistSq = isHbSlot ? -distSq : distSq;
					nearestSlot=i;
					nearestIfe=optionalIfe.get();
					nearestStack=mapItem;
				}
			}
//			else{
//				Main.LOGGER.info("distSq:"+distSq+",maxReachSq:"+(MAX_REACH*MAX_REACH));
//				Main.LOGGER.info("bp: "+bp.toShortString());
//				Main.LOGGER.info("eye: "+client.player.getBlockPos().toShortString());
//			}
		}
		if(nearestSlot == -1){
			if(!unableToFindMap) Main.LOGGER.info("AutoPlaceMapArt: No viable itemstack->iframe found. #nearby_ifes="+ifes.size()
				+",#map_items="+numMaps+",#usable_maps="+numUsableMaps+",#numUsableMapsInRange="+numUsableMapsInRange);
			unableToFindMap = true;
			return;
		}
		unableToFindMap = false;
		if(nearestDistSq > MAX_REACH*MAX_REACH){
			Main.LOGGER.info("AutoPlaceMapArt: Nearest placement spot is out of reach. distSq="+nearestDistSq);
			return;
		}
//		Main.LOGGER.info("AutoPlaceMapArt: distance to place-loc for itemstack in slot"+nearestSlot+": "+Math.sqrt(nearestDistSq));

		//TODO: handle bundles
		if(nearestSlot - 36 != selectedSlot){
			if(nearestSlot >= 36 && nearestSlot < 45){
				client.player.getInventory().setSelectedSlot(nearestSlot - 36);
				Main.LOGGER.info("AutoPlaceMapArt: Changed selected hotbar slot to nearestMap: hb="+(nearestSlot-36));
			}
			else{
				client.interactionManager.clickSlot(0, nearestSlot, selectedSlot, SlotActionType.SWAP, client.player);
				Main.LOGGER.info("AutoPlaceMapArt: Swapped inv.selectedSlot to nextMap: s="+nearestSlot);
			}
			ticksSinceInvAction = 0;
		}
		if(ticksSinceInvAction++ < INV_DELAY_TICKS){
			Main.LOGGER.info("AutoPlaceMapArt: waiting for inv action cooldown ("+ticksSinceInvAction+"ticks)");
			return;
		}
//		assert client.player.getInventory().getMainHandStack().equals(client.player.getInventory().main.get(client.player.getInventory().selectedSlot));

		Main.LOGGER.info("AutoPlaceMapArt: right-clicking target iFrame"
//				+ " ("+nearestIfe.getBlockPos().toShortString()+")"
				+ " with map: "+nearestStack.getName().getString());

//		UpdateInventoryHighlights.setCurrentlyBeingPlacedMapArt(null, nearestStack);
//		lastPlacedMapStack = nearestStack.copy();
		recentPlaceAttempts[attemptIdx] = nearestIfe.getId();
		lastAttemptIdx = attemptIdx;
//		lastPlaceTs = ts;

//		client.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
		client.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.interactAt(nearestIfe, client.player.isSneaking(), Hand.MAIN_HAND, nearestIfe.getEyePos()));
		client.interactionManager.interactEntity(client.player, nearestIfe, Hand.MAIN_HAND);
//		nearestIfe.interactAt(client.player, nearestIfe.getEyePos(), Hand.MAIN_HAND);
//		client.player.interact(nearestIfe, Hand.MAIN_HAND);

//		EntityHitResult ehr = new EntityHitResult(nearestIfe, nearestIfe.getEyePos());
//		ActionResult result = client.interactionManager.interactEntityAtLocation(client.player, nearestIfe, ehr, Hand.MAIN_HAND);
//		Main.LOGGER.info("AutoPlaceMapArt: interact result: "+(
//			result == ActionResult.CONSUME ? "consume" :
//			result == ActionResult.FAIL ? "fail" :
//			result == ActionResult.PASS ? "pass" :
//			result == ActionResult.PASS_TO_DEFAULT_BLOCK_ACTION ? "PASS_TO_DEFAULT_BLOCK_ACTION" :
//			result == ActionResult.SUCCESS ? "success" :
//			result == ActionResult.SUCCESS_SERVER ? "SUCCESS_SERVER" :
//				"unknown"
//		));

	}
}