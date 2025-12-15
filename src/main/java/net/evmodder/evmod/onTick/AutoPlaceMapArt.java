package net.evmodder.evmod.onTick;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.Main;
import net.evmodder.evmod.Configs.Generic;
import net.evmodder.evmod.apis.ClickUtils;
import net.evmodder.evmod.apis.ClickUtils.ActionType;
import net.evmodder.evmod.apis.ClickUtils.InvAction;
import net.evmodder.evmod.apis.MapRelationUtils.RelatedMapsData;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.evmodder.evmod.apis.MapRelationUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class AutoPlaceMapArt/* extends MapLayoutFinder*/{

	private Direction dir;
	private World world;
	private ItemStack currStack;
	private String currPosStr;
	private int constAxis;
	private int varAxis1Origin, varAxis2Origin;
	private Boolean varAxis1Neg, varAxis2Neg, axisMatch;
	private RelatedMapsData currentData;
	private final ArrayList<ItemStack> allMapItems;
	private final ArrayList<Integer> stacksHashesForCurrentData;

	private final int[] recentPlaceAttempts;
	private int attemptIdx, lastAttemptIdx;
	private int ticksSinceInvAction;

	public AutoPlaceMapArt(){
		allMapItems = new ArrayList<>();
		stacksHashesForCurrentData = new ArrayList<>();

		recentPlaceAttempts = new int[20];

		ClientTickEvents.END_CLIENT_TICK.register(client->{synchronized(stacksHashesForCurrentData){placeNearestMap(client == null ? null : client.player);}});
	}

	private final record AxisData(int constAxis, int varAxis1, int varAxis2){}
	private final AxisData getAxisData(ItemFrameEntity ife){
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

	private final record Pos2DPair(int a1, int a2, int b1, int b2){}
	private final int intFromPos(String pos){
		assert pos.matches("[A-Z]+|-?[0-9]+") : "Invalid 2d pos str part! "+pos;
		if(pos.charAt(0) < 'A' || pos.charAt(0) > 'Z') return Integer.parseInt(pos);

		int res = pos.charAt(0) - 'A';
		for(int i=1; i<pos.length(); ++i){
			res *= 26;
			res += pos.charAt(i) - 'A';
		}
		return res;
	}
	private final int intFromTLBR(char c, boolean hasM){
		switch(c){
			case 'T': case 'L': return 0;
			case 'M': return 1;
			case 'B': case 'R': return hasM ? 2 : 1;
			default: throw new IllegalArgumentException();
		}
	}
	private final Pos2DPair getRelativePosPair(final String posA, final String posB){
		if(posA.matches("[TMB][LMR]") && posB.matches("[TMB][LMR]")){
			assert currentData.slots().stream().allMatch(i -> getPosStrFromName(allMapItems.get(i)).matches("[TMB][LMR]"));
			final boolean hasM1 =  currentData.slots().stream().anyMatch(i -> getPosStrFromName(allMapItems.get(i)).charAt(0) == 'M');
			final boolean hasM2 =  currentData.slots().stream().anyMatch(i -> getPosStrFromName(allMapItems.get(i)).charAt(1) == 'M');
//			final boolean hasM1 = posA.charAt(0) == 'M' || posB.charAt(0) == 'M';
//			final boolean hasM2 = posA.charAt(1) == 'M' || posB.charAt(1) == 'M';
			return new Pos2DPair(
					intFromTLBR(posA.charAt(0), hasM1), intFromTLBR(posA.charAt(1), hasM2),
					intFromTLBR(posB.charAt(0), hasM1), intFromTLBR(posB.charAt(1), hasM2)
			);
		}
		if(posA.matches("[1-9][0-9]*") && posB.matches("[1-9][0-9]*")){
			//TODO: support 1d posStr (e.g., 1/6 for a 3x2)
		}
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

		final String posA1 = posA.substring(0, cutA), posA2 = posA.substring(cutA+cutSpaceA);
		final String posB1 = posB.substring(0, cutB), posB2 = posB.substring(cutB+cutSpaceB);

//		Main.LOGGER.info("posA:"+posA+",posB:"+posB+",posA1:"+posA1+",posA2:"+posA2+",posB1:"+posB1+",posB2:"+posB2);
		return new Pos2DPair(intFromPos(posA1), intFromPos(posA2), intFromPos(posB1), intFromPos(posB2));
	}

	public final void disableAndReset(){
		if(dir != null){
			dir = null;
			world = null;
			currStack = null;
			currPosStr = null;
			constAxis = varAxis1Origin = varAxis2Origin = 0;
			varAxis1Neg = varAxis2Neg = axisMatch = null;
			currentData = null;
			allMapItems.clear();
			stacksHashesForCurrentData.clear();
		}
	}

	private final String getPosStrFromName(final ItemStack stack){
		final String name = stack.getCustomName().getString();
		final String nameWithoutArtist = MapRelationUtils.removeByArtist(name);
		return MapRelationUtils.simplifyPosStr(nameWithoutArtist.substring(currentData.prefixLen(), nameWithoutArtist.length()-currentData.suffixLen()));
	}

	private ItemFrameEntity lastIfe;
	private ItemStack lastStack;
	public final boolean recalcLayout(PlayerEntity player, ItemFrameEntity currIfe, ItemStack currStack){
		synchronized(stacksHashesForCurrentData){
		try{
		if(!Generic.MAPART_AUTOPLACE.getBooleanValue()
			|| currIfe == null || currStack == null || currStack.getCount() != 1)
		{
			disableAndReset();
			return false;
		}
		if(lastIfe == null) return false;

		if((dir=currIfe.getFacing()) != lastIfe.getFacing()){
			Main.LOGGER.info("AutoPlaceMapArt: currIfe and lastIfe are not facing the same dir");
			disableAndReset(); return false;
		}
		if((world=currIfe.getWorld()) != lastIfe.getWorld()){
			Main.LOGGER.info("AutoPlaceMapArt: currIfe and lastIfe are not in the same world!");
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

		RelatedMapsData data = MapRelationUtils.getRelatedMapsByName0(List.of(this.currStack=currStack, lastStack), world);
		if(data.slots().size() != 2){ // TODO: support maps w/o custom name (related by edge detection)
			Main.LOGGER.info("AutoPlaceMapArt: currIfe and lastIfe are not related");
			disableAndReset(); return false;
		}
		if(data.prefixLen() == -1){ // TODO: support related maps without pos data (same name, no pos data)
			Main.LOGGER.info("AutoPlaceMapArt: unable to predict placement for map names lacking pos data");
			disableAndReset(); return false;
		}
		// Parse 2d pos (and cache for other maps items, if necessary)
		if(currentData == null){
			assert allMapItems.isEmpty();
			allMapItems.add(currStack); allMapItems.add(lastStack);
			MapRelationUtils.getAllNestedItems(player.getInventory().main.stream()).filter(s -> s.getItem() == Items.FILLED_MAP).forEach(allMapItems::add);
//			Main.LOGGER.info("AutoPlaceMapArt: all maps in inv: "+(allMapItems.size()-2));

			currentData = MapRelationUtils.getRelatedMapsByName0(allMapItems, player.getWorld());
			if(currentData.slots().size() <= 3){
				Main.LOGGER.info("AutoPlaceMapArt: not enough remaining maps in inv to justify enabling AutoPlace");
				disableAndReset(); return false;
			}
//			Main.LOGGER.info("AutoPlaceMapArt: related maps in inv: "+(currentData.slots().size()-2));
		}
		final String currPosStr = this.currPosStr=getPosStrFromName(currStack), lastPosStr = getPosStrFromName(lastStack);
		final Pos2DPair pos2dPair = getRelativePosPair(currPosStr, lastPosStr);
		if(pos2dPair == null){ // TODO: Support non-standard pos data (e.g., TL,TR,BL,BR)
			Main.LOGGER.info("AutoPlaceMapArt: unable to parse pos2dPair from pos strs ("+currPosStr+","+lastPosStr+")");
			disableAndReset(); return false;
		}

		final int posOffset1 = pos2dPair.a1 - pos2dPair.b1, posOffset2 = pos2dPair.a2 - pos2dPair.b2;
		Main.LOGGER.info("AutoPlaceMapArt: posOffset1="+posOffset1+",posOffset2="+posOffset2);

//		assert(
//			(Math.abs(posOffset1) == Math.abs(ifeOffset1) && Math.abs(posOffset2) == Math.abs(ifeOffset2)) ||
//			(Math.abs(posOffset1) == Math.abs(ifeOffset2) && Math.abs(posOffset2) == Math.abs(ifeOffset1))
//		);
		if(Math.abs(posOffset1) != Math.abs(ifeOffset1) && Math.abs(posOffset1) != Math.abs(ifeOffset2)){
			Main.LOGGER.warn("AutoPlaceMapArt: user appears to have placed mapart in invalid spot! abs(axisDiff1), "+posOffset1);
			disableAndReset(); return false;
		}
		if(Math.abs(posOffset2) != Math.abs(ifeOffset1) && Math.abs(posOffset2) != Math.abs(ifeOffset2)){
			Main.LOGGER.warn("AutoPlaceMapArt: user appears to have placed mapart in invalid spot! abs(axisDiff2), "+posOffset2);
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
			final boolean sameSign = ((ifeOffset1 == posOffset1) == (ifeOffset1 == posOffset2)) && ((ifeOffset2 == posOffset1) == (ifeOffset2 == posOffset2));
			if(sameSign){
				final boolean isNeg = ifeOffset1 != posOffset1;
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

		if(ifeOffset1 != 0){
			final int posOffset = (axisMatch ? posOffset1 : posOffset2);
			final boolean isNeg = ifeOffset1 != posOffset;
//			assert ifeOffset1 == posOffset*(isNeg ? -1 : +1) : "?? "+axisMatch+","+posOffset+","+isNeg;
			if(ifeOffset1 != posOffset*(isNeg ? -1 : +1)){
				Main.LOGGER.info("AutoPlaceMapArt: error ??1 "+axisMatch+","+posOffset+","+isNeg);
				disableAndReset(); return false;
			}
			if(varAxis1Neg == null) varAxis1Neg = isNeg;
			else if(varAxis1Neg != isNeg){
				Main.LOGGER.warn("AutoPlaceMapArt: user appears to have placed mapart in invalid spot! +-axis1");
				disableAndReset(); return false;
			}
		}
		if(ifeOffset2 != 0){
			final int posOffset = (axisMatch ? posOffset2 : posOffset1);
			final boolean isNeg = ifeOffset2 != posOffset;
//			assert ifeOffset2 == posOffset*(isNeg ? -1 : +1);
			if(ifeOffset2 != posOffset*(isNeg ? -1 : +1)){
				Main.LOGGER.info("AutoPlaceMapArt: error ??2 "+axisMatch+","+posOffset+","+isNeg);
				disableAndReset(); return false;
			}
			if(varAxis2Neg == null) varAxis2Neg = isNeg;
			else if(varAxis2Neg != isNeg){
				Main.LOGGER.warn("AutoPlaceMapArt: user appears to have placed mapart in invalid spot! +-axis2");
				disableAndReset(); return false;
			}
		}

		if(!stacksHashesForCurrentData.isEmpty()) return true; // Already ongoing and all values are defined

		if(varAxis1Neg == null || varAxis2Neg == null){
			boolean foundAxis1 = varAxis1Neg != null;
			Main.LOGGER.warn("AutoPlaceMapArt: determined axisMatch="+axisMatch+" and 1 of 2 axis offsets"
					+" (axis"+(foundAxis1?"1="+(varAxis1Neg?"-":"+"):"2="+(varAxis2Neg?"-":"+"))
					+"), just need to get the other offset before enabling");
			return false;
		}

		varAxis1Origin = currAxisData.varAxis1-(axisMatch ? pos2dPair.a1 : pos2dPair.a2)*(varAxis1Neg?-1:+1);
		varAxis2Origin = currAxisData.varAxis2-(axisMatch ? pos2dPair.a2 : pos2dPair.a1)*(varAxis2Neg?-1:+1);

		assert !allMapItems.isEmpty();
		assert stacksHashesForCurrentData.isEmpty();
		stacksHashesForCurrentData.ensureCapacity(currentData.slots().size());
		currentData.slots().stream().map(i -> ItemStack.hashCode(allMapItems.get(i))).forEach(stacksHashesForCurrentData::add);
		assert !stacksHashesForCurrentData.isEmpty();

		Main.LOGGER.info("AutoPlaceMapArt: activated! varAxis1o="+varAxis1Origin+",varAxis2o="+varAxis2Origin+",varAxis1Neg="+varAxis1Neg+",varAxis2Neg="+varAxis2Neg);
		return true;
		}
		finally{
			lastIfe = currIfe;
			lastStack = currStack;
		}
		}
	}

	public final BlockPos getPlacement(ItemStack stack){
		synchronized(stacksHashesForCurrentData){
			if(!stacksHashesForCurrentData.contains(ItemStack.hashCode(stack))){
				RelatedMapsData data = MapRelationUtils.getRelatedMapsByName0(List.of(currStack, stack), world);
				if(data.slots().size() != 2 || data.prefixLen() == -1) return null; // Not part of the map being autoplaced
				Main.LOGGER.info("AutoPlaceMapArt: Added map itemstack to currentData"+(stack.getCustomName()==null?"":", name="+stack.getCustomName().getString()));
				stacksHashesForCurrentData.add(ItemStack.hashCode(stack));
			}
			final Pos2DPair pos2dPair = getRelativePosPair(currPosStr, getPosStrFromName(stack));
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
	}

	private final double distFromPlane(BlockPos bp){
		switch(dir){
			case UP: case DOWN: return Math.abs(bp.getY() - constAxis);
			case EAST: case WEST: return Math.abs(bp.getX() - constAxis);
			case NORTH: case SOUTH: return Math.abs(bp.getZ() - constAxis);

			default: assert(false) : "Unreachable"; return -1;
		}
	}
	public final boolean ifePosFilter(ItemFrameEntity ife){return ife.getFacing() == dir && distFromPlane(ife.getBlockPos()) == 0;}

	public final boolean hasKnownLayout(){return !stacksHashesForCurrentData.isEmpty();}

	// Functions NOT from MapLayoutFinder:

	private final void placeMapInFrame(ClientPlayerEntity player, ItemFrameEntity ife){
		assert player.getInventory().getMainHandStack().equals(player.getInventory().main.get(player.getInventory().selectedSlot));

		Main.LOGGER.info("AutoPlaceMapArt: right-clicking target iFrame"
//				+ " ("+ife.getBlockPos().toShortString()+")"
				+ " with map: "+player.getInventory().getMainHandStack().getName().getString());

//		UpdateInventoryHighlights.setCurrentlyBeingPlacedMapArt(null, stack);
		recentPlaceAttempts[attemptIdx] = ife.getId();
		lastAttemptIdx = attemptIdx;

//		client.player.networkHandler.sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
		player.networkHandler.sendPacket(PlayerInteractEntityC2SPacket.interactAt(ife, player.isSneaking(), Hand.MAIN_HAND, ife.getEyePos()));
		MinecraftClient.getInstance().interactionManager.interactEntity(player, ife, Hand.MAIN_HAND);
//		nearestIfe.interactAt(client.player, ife.getEyePos(), Hand.MAIN_HAND);
//		client.player.interact(ife, Hand.MAIN_HAND);
	}

	boolean foundValidPosOnLastAttempt;
	record MapPlacementData(int slot, int bundleSlot, ItemFrameEntity ife){}
	private final MapPlacementData getNearestMapPlacement(PlayerEntity player){
		final List<ItemStack> slots = player.playerScreenHandler.slots.stream().map(Slot::getStack).collect(Collectors.toList());

		final double MAX_REACH = Configs.Generic.PLACEMENT_HELPER_MAPART_REACH.getDoubleValue();
		final double SCAN_DIST = MAX_REACH+2;

		Box box = player.getBoundingBox().expand(SCAN_DIST, SCAN_DIST, SCAN_DIST);
		Predicate<ItemFrameEntity> filter = ife -> ifePosFilter(ife) && ife.getHeldItemStack().isEmpty();
		List<ItemFrameEntity> ifes = player.getWorld().getEntitiesByClass(ItemFrameEntity.class, box, filter);
		if(ifes.isEmpty()){
//			Main.LOGGER.warn("AutoPlaceMapArt: no nearby iframes");
			return null;
		}
		
		double nearestDistSq = Double.MAX_VALUE;
//		BlockPos nearestBp;
		ItemFrameEntity nearestIfe = null;
		int nearestSlot = -1, bundleSlot = 0;
		int numMaps = 0, numRelated = 0, numRelatedInRange = 0, numRelatedInRangeStrict = 0;
		boolean nearestIsInHotbar = false;
		invloop: for(int i=slots.size()-1; i>=0; --i){
			final boolean isInHotbar = i >= 36 && i < 45 && slots.get(i).getItem() == Items.FILLED_MAP;
			if(nearestIsInHotbar && !isInHotbar) continue;
//			ItemStack mapItem = slots.get(i);
			BundleContentsComponent contents = slots.get(i).get(DataComponentTypes.BUNDLE_CONTENTS);
			if(bundleSlot == -1 && contents != null) continue; // Prefer to avoid bundles when we have an alterantive itemstack
			final int bundleSz = contents != null ? contents.size() : 0;
//			if(contents != null && !contents.isEmpty()) mapItem = contents.get(contents.size()-1);

			final boolean ALLOW_ONLY_TOP_SLOT = !Configs.Generic.USE_BUNDLE_PACKET.getBooleanValue();
			final int TOP_SLOT = Configs.Generic.BUNDLES_ARE_REVERSED.getBooleanValue() ? bundleSz-1 : 0;
			for(int j=-1; j<bundleSz; ++j){
				final ItemStack mapStack;
				if(j == -1) mapStack = slots.get(i);
				else if(ALLOW_ONLY_TOP_SLOT && j != TOP_SLOT) continue;
				else mapStack = contents.get(j);
				if(mapStack.getItem() != Items.FILLED_MAP) continue;
				++numMaps;
				BlockPos bp = getPlacement(mapStack);
				if(bp == null) continue;
				++numRelated;
				if(bp.getSquaredDistance(player.getEyePos()) > SCAN_DIST*SCAN_DIST) continue;
				++numRelatedInRange;
				Optional<ItemFrameEntity> optionalIfe = ifes.stream().filter(ife -> ife.getBlockPos().equals(bp)).findAny();
				if(optionalIfe.isEmpty()){
					Main.LOGGER.warn("AutoPlaceMapArt: Missing iFrame at pos! "+bp.toShortString());
					continue;
				}
				ItemFrameEntity ife = optionalIfe.get();
				final double distSq = ife.getEyePos().squaredDistanceTo(player.getEyePos());//TODO: ife.getNearestCornerToPlayer
				if(distSq > MAX_REACH*MAX_REACH) continue;
				++numRelatedInRangeStrict;

				if(Arrays.stream(recentPlaceAttempts).anyMatch(id -> id == ife.getId())){
					Main.LOGGER.warn("AutoPlaceMapArt: Cannot place into the same iFrame twice! "+bp.toShortString());
					continue;
				}
				final boolean isSelected = isInHotbar && i-36 == player.getInventory().selectedSlot;
				if(isSelected || distSq < nearestDistSq || (isInHotbar && !nearestIsInHotbar)){
					nearestIsInHotbar = isInHotbar;
					nearestDistSq = distSq;
					nearestSlot = i;
					bundleSlot = j;
					nearestIfe = ife;
					if(isSelected){
						Main.LOGGER.info("AutoPlaceMapArt: Stack in hand is a valid candidate, using it!");
						break invloop;
					}
				}
			}
		}
//		Main.LOGGER.info("AutoPlaceMapArt: distance to place-loc for itemstack in slot"+nearestSlot+": "+Math.sqrt(nearestDistSq));
//		return nearestSlot == -1 ? null : new MapPlacementData(nearestSlot, bundleSlot, nearestStack, nearestIfe);
		if(nearestSlot == -1){
			if(foundValidPosOnLastAttempt) Main.LOGGER.info("AutoPlaceMapArt: No viable itemstack->iframe found. #nearby_ifes="+ifes.size()
				+",#maps="+numMaps+",#related="+numRelated+",#in_range="+numRelatedInRange+",#in_range_strict(ife)="+numRelatedInRangeStrict);
			foundValidPosOnLastAttempt = false;
			return null;
		}
		foundValidPosOnLastAttempt = true;
		return new MapPlacementData(nearestSlot, bundleSlot, nearestIfe);
	}

	private final void getMapIntoMainHand(ClientPlayerEntity player, int slot, int bundleSlot){
//		assert slot != client.player.getInventory().selectedSlot+36 || bundleSlot != -1;

		int TICKS_BETWEEN_INV_ACTIONS = Configs.Generic.MAPART_AUTOPLACE_INV_DELAY.getIntegerValue();
		if(ticksSinceInvAction < TICKS_BETWEEN_INV_ACTIONS){
			Main.LOGGER.info("AutoPlaceMapArt: waiting for inv action cooldown ("+ticksSinceInvAction+"ticks)");
			return;
		}
		final Runnable onDone = TICKS_BETWEEN_INV_ACTIONS == 0 ? ()->placeNearestMap(player) : ()->ticksSinceInvAction=0;
		final int selectedSlot = player.getInventory().selectedSlot;
		if(bundleSlot == -1){
			if(slot >= 36 && slot < 45){
				player.getInventory().setSelectedSlot(slot - 36);
				ticksSinceInvAction = 0;
				Main.LOGGER.info("AutoPlaceMapArt: Changed selected hotbar slot to nearestMap: hb="+(slot-36));
			}
			else{
				// Swap from upper inv to main hand
				ClickUtils.executeClicks(_0->true, onDone, new InvAction(slot, selectedSlot, ActionType.HOTBAR_SWAP));
				Main.LOGGER.info("AutoPlaceMapArt: Swapped nextMap to inv.selectedSlot: s="+slot+"->hb="+(selectedSlot));
			}
		}
		else{ // bundleSlot != -1
			if(slot == selectedSlot+36 || !player.getMainHandStack().isEmpty()){
				Main.LOGGER.info("AutoPlaceMapArt: Main hand is not empty! Unable to extract from bundle");
//				disableAndReset();
//				return;
				int hbSlot = 0;
				while(hbSlot < 9 && !player.getInventory().main.get(hbSlot).isEmpty()) ++hbSlot;
				if(hbSlot != 9){
					player.getInventory().setSelectedSlot(hbSlot);
					ticksSinceInvAction = 0;
					Main.LOGGER.info("AutoPlaceMapArt: Changed selected hotbar slot to empty slot: hb="+hbSlot);
				}
				else{
					// Try to move item out of main hand
					ClickUtils.executeClicks(_0->true, onDone, new InvAction(selectedSlot+36, 0, ActionType.SHIFT_CLICK));
					Main.LOGGER.info("AutoPlaceMapArt: Shift-clicking item out of mainhand (to upper inv), hb="+selectedSlot);
				}
				return;
			}
			BundleContentsComponent contents = player.playerScreenHandler.slots.get(slot).getStack().get(DataComponentTypes.BUNDLE_CONTENTS);
			assert contents != null && contents.size() > bundleSlot;
			ArrayDeque<InvAction> clicks = new ArrayDeque<>();
			if(bundleSlot != contents.size()-1){
				int bundleSlotUsed = Configs.Generic.BUNDLES_ARE_REVERSED.getBooleanValue() ? contents.size()-(bundleSlot+1) : bundleSlot;
				clicks.add(new InvAction(slot, bundleSlotUsed, ActionType.BUNDLE_SELECT)); // Select bundle slot
			}
			clicks.add(new InvAction(slot, 1, ActionType.CLICK)); // Take from bundle
			clicks.add(new InvAction(selectedSlot+36, 0, ActionType.CLICK)); // Place in main hand
			ClickUtils.executeClicks(_0->true, onDone, clicks);
			Main.LOGGER.info("AutoPlaceMapArt: Extracted map from bundle into mainhand");
		}
	}

	private boolean isMovingTooFast(Vec3d velocity){
		double xzLengthSq = velocity.x*velocity.x + velocity.z*velocity.z;
		return xzLengthSq > 0.0001 || Math.abs(velocity.y) > 0.08;
	}

	private final void placeNearestMap(ClientPlayerEntity player){
		if(!hasKnownLayout()) return;
		if(player == null || player.getWorld() == null){
			Main.LOGGER.info("AutoPlaceMapArt: player disconnected mid-op");
			disableAndReset();
			return;
		}
		if(!Configs.Generic.MAPART_AUTOPLACE.getBooleanValue()){
			Main.LOGGER.info("AutoPlaceMapArt: disabled mid-op");
			disableAndReset();
			return;
		}

		if(ClickUtils.hasOngoingClicks()){
			Main.LOGGER.info("AutoPlaceMapArt: waiting for inv action to complete");
			return;
		}
		++ticksSinceInvAction;
//		if(ticksSinceInvAction++ < INV_DELAY_TICKS){
//			Main.LOGGER.info("AutoPlaceMapArt: waiting for inv action cooldown ("+ticksSinceInvAction+"ticks)");
//			return;
//		}

		if(player.currentScreenHandler != null && player.currentScreenHandler.syncId != 0){
//			Main.LOGGER.info("AutoPlaceMapArt: paused, currently in container gui");
			return;
		}

		// Sadly this doesn't work after the last manual map, since UseEntityCallback.EVENT isn't triggered for some reason.
		// And yeah, I tried setting it manually, but since the code can't guarantee a map gets placed, it can get it stuck.
		if(!player.isInCreativeMode() && UpdateInventoryHighlights.hasCurrentlyBeingPlaceMapArt()){
			Main.LOGGER.info("AutoPlaceMapArt: waiting for last manually-placed mapart to vanish from mainhand");
			return;
		}

		// Don't spam-place in the same blockpos, give iframe entity a chance to load
		if(++attemptIdx >= recentPlaceAttempts.length) attemptIdx = 0;
		recentPlaceAttempts[attemptIdx] = 0;

		{
			Entity e = player.getWorld().getEntityById(recentPlaceAttempts[lastAttemptIdx]);
			if(e != null && e instanceof ItemFrameEntity ife && ItemStack.areEqual(player.getMainHandStack(), ife.getHeldItemStack())){
				final int waited = lastAttemptIdx < attemptIdx ? attemptIdx-lastAttemptIdx : recentPlaceAttempts.length+attemptIdx-lastAttemptIdx;
				Main.LOGGER.info("AutoPlaceMapArt: waiting for current map to vanish from mainhand ("+waited+"ticks)");
				return;
			}
		}

		{
//			assert 0 <= attemptIdx < recentPlaceAttempts.length;
			int i = (attemptIdx + 1) % recentPlaceAttempts.length;
			while(i != attemptIdx){
				Entity e = player.getWorld().getEntityById(recentPlaceAttempts[i]);
				if(e != null && e instanceof ItemFrameEntity ife && ife.getHeldItemStack().isEmpty()){
//					final int rem = attemptIdx < i ? i-attemptIdx : recentPlaceAttempts.length+i-attemptIdx;
					final int waited = i < attemptIdx ? attemptIdx-i : recentPlaceAttempts.length+attemptIdx-i;
					Main.LOGGER.info("AutoPlaceMapArt: waiting for current map to appear in iFrame ("+waited+"ticks)");
					return;
				}
				if(++i == recentPlaceAttempts.length) i = 0;
			}
		}

		if(isMovingTooFast(player.getVelocity())) return; // Pause while player is moving

		MapPlacementData data = getNearestMapPlacement(player);
		if(data == null) return;

		if(player.playerScreenHandler != null && !player.playerScreenHandler.getCursorStack().isEmpty()){
			Main.LOGGER.warn("AutoPlaceMapArt: item stuck on cursor! attempting to place into empty slot");
			for(int i=44; i>=0; --i) if(!player.playerScreenHandler.slots.get(i).hasStack()){
				// Place stack on cursor
				ClickUtils.executeClicks(_0->true, ()->{}, new InvAction(i, 0, ActionType.CLICK));
				return;
			}
			disableAndReset();
			return;
		}

		if(data.slot != player.getInventory().selectedSlot+36 || data.bundleSlot != -1){
//			Main.LOGGER.warn("AutoPlaceMapArt: calling getMapIntoMainHand(), data.slot="+data.slot+",hb="+client.player.getInventory().selectedSlot);
			getMapIntoMainHand(player, data.slot, data.bundleSlot);
			return;
		}
		placeMapInFrame(player, data.ife);
	}
}