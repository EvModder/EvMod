package net.evmodder.evmod.onTick;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.Main;
import net.evmodder.evmod.Configs.Generic;
import net.evmodder.evmod.apis.ClickUtils;
import net.evmodder.evmod.apis.ClickUtils.ActionType;
import net.evmodder.evmod.apis.ClickUtils.InvAction;
import net.evmodder.evmod.apis.InvUtils;
import net.evmodder.evmod.apis.MapRelationUtils.RelatedMapsData;
import net.evmodder.evmod.apis.TickListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.evmodder.evmod.apis.MapRelationUtils;

public final class AutoPlaceMapArt/* extends MapLayoutFinder*/{
	private final int MANUAL_CLICK_WAIT_TIMEOUT = 60;
	private final Pattern pOfSize = Pattern.compile("^\\s*(?:of|/)\\s*(\\d+).*$");

	private Direction dir;
	private Level world;
	private ItemFrame lastIfe, lastIfeAuto;
	private ItemStack lastStack, lastStackAuto;
	private String lastPosStr;
	private Boolean varAxis1Neg, varAxis2Neg, axisMatch;
	private RelatedMapsData currentData;
	private Integer ofSize, rowWidth;
	private final ArrayList<ItemStack> allMapItems = new ArrayList<>();
	private final ArrayList<Integer> stacksHashesForCurrentData = new ArrayList<>();

	private final int[] recentPlaceAttempts = new int[20];
	private int attemptIdx, lastAttemptIdx;
	private int ticksSinceInvAction, ticksWaitingForManualClick;
	private boolean hasWarnedMissingIfe;
	private final Function<ItemStack, ItemStack> handRestockFallback; // lastPlacedStack -> restockedStack (or null)
	private ItemStack lastHandRestockFallbackStack;
	private boolean calledRecalcLayout, handRestockFailed, warnedNoValidPos;

	private boolean extraInfoLogs = false;

	public AutoPlaceMapArt(Function<ItemStack, ItemStack> moveNextMapToMainHand){
		handRestockFallback = moveNextMapToMainHand;

		TickListener.register(new TickListener(){
			@Override public void onTickEnd(Minecraft client){
				synchronized(stacksHashesForCurrentData){
					placeNearestMap(client == null ? null : client.player);
				}
			}
		});
	}

	private final record AxisData(int constAxis, int varAxis1, int varAxis2){}
	private final AxisData getAxisData(ItemFrame ife){
		final BlockPos bp = ife.blockPosition();
		switch(/*dir*/ife.getNearestViewDirection()){
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
	private final boolean posStrIs1D(final String posStr){return posStr.matches("[1-9][0-9]*|[A-Z]");}
	private final Pos2DPair getRelativePosPair(final String posA, final String posB){
		if(ofSize != null){
//			assert ofSize != null;
			if(!posStrIs1D(posA) || !posStrIs1D(posB)){
				Main.LOGGER.warn("AutoPlaceMapArt: error! pos strings X/SIZE are non-1d | posA:"+posA+",posB:"+posB);
				return null;
			}
			final boolean numericPosA = Character.isDigit(posA.charAt(0)), numericPosB = Character.isDigit(posB.charAt(0));
			if(numericPosA != numericPosB){
				Main.LOGGER.warn("AutoPlaceMapArt: error! pos strings X/SIZE are non-1d | posA:"+posA+",posB:"+posB);
				//disableAndReset();
				return null;
			}
			final int a, b;
			if(numericPosA){a = Integer.parseInt(posA)-1; b = Integer.parseInt(posB)-1;}
			else{a = posA.charAt(0)-'A'; b = posB.charAt(0)-'A';}
			if(rowWidth != null) return new Pos2DPair(a%rowWidth, a/rowWidth, b%rowWidth, b/rowWidth);
			else return new Pos2DPair(a, 0, b, 0);
		}
		if(posA.matches("[TMB][LMR]") && posB.matches("[TMB][LMR]")){
			assert currentData.slots().stream().allMatch(i -> getPosStrFromItem(allMapItems.get(i)).matches("[TMB][LMR]"));
			final boolean hasM1 =  currentData.slots().stream().anyMatch(i -> getPosStrFromItem(allMapItems.get(i)).charAt(0) == 'M');
			final boolean hasM2 =  currentData.slots().stream().anyMatch(i -> getPosStrFromItem(allMapItems.get(i)).charAt(1) == 'M');
//			final boolean hasM1 = posA.charAt(0) == 'M' || posB.charAt(0) == 'M';
//			final boolean hasM2 = posA.charAt(1) == 'M' || posB.charAt(1) == 'M';
			return new Pos2DPair(
					intFromTLBR(posA.charAt(0), hasM1), intFromTLBR(posA.charAt(1), hasM2),
					intFromTLBR(posB.charAt(0), hasM1), intFromTLBR(posB.charAt(1), hasM2)
			);
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
		if(Stream.of(posA1, posA2, posB1, posB2).anyMatch(p -> !p.matches("[A-Z]+|-?[0-9]+"))) return null; // TODO: this should be safe to remove, eventually

//		Main.LOGGER.info("posA:"+posA+",posB:"+posB+",posA1:"+posA1+",posA2:"+posA2+",posB1:"+posB1+",posB2:"+posB2);
		return new Pos2DPair(intFromPos(posA1), intFromPos(posA2), intFromPos(posB1), intFromPos(posB2));
	}

	public final void disableAndReset(){
		if(dir != null){
			dir = null;
			world = null;
			lastIfe = lastIfeAuto = null;
			lastStack = lastStackAuto = null;
			lastPosStr = null;
			varAxis1Neg = varAxis2Neg = axisMatch = null;
			currentData = null;
			ofSize = rowWidth = null;
			calledRecalcLayout = handRestockFailed = warnedNoValidPos = false;
			allMapItems.clear();
			stacksHashesForCurrentData.clear();
//			applicableIfes = null;
		}
	}

	private final String getPosStrFromName(final String name){
		final String nameWoArtist = MapRelationUtils.removeByArtist(name);
		if(currentData.prefixLen() == -1) return name;
		return MapRelationUtils.simplifyPosStr(nameWoArtist.substring(currentData.prefixLen(), nameWoArtist.length()-currentData.suffixLen()));
	}
	private final String getPosStrFromItem(final ItemStack stack){return getPosStrFromName(stack.getHoverName().getString());}

	private final boolean isPartOfCurrentAutoPlace(final ItemStack stack){
		final int hashCode = ItemStack.hashItemAndComponents(stack);
		if(stacksHashesForCurrentData.contains(hashCode)) return true;
		final RelatedMapsData data = MapRelationUtils.getRelatedMapsByName0(List.of(lastStack, stack), world);
		if(data.slots().size() != 2 || data.prefixLen() == -1) return false; // Not part of the map being autoplaced
		Main.LOGGER.info("AutoPlaceMapArt: Added map itemstack to currentData, name="+stack.getHoverName().getString());
		stacksHashesForCurrentData.add(hashCode);
		return true;
	}

//	public final boolean ifePosFilter(ItemFrameEntity ife){return ife.getFacing() == dir && distFromPlane(ife.getBlockPos()) == 0;}
	public final Predicate<ItemFrame> ifePosFilter(){
		return switch(dir){
			case UP, DOWN     -> ife -> ife.getNearestViewDirection() == dir && ife.getBlockY() == lastIfe.getBlockY();
			case EAST, WEST   -> ife -> ife.getNearestViewDirection() == dir && ife.getBlockX() == lastIfe.getBlockX();
			case NORTH, SOUTH -> ife -> ife.getNearestViewDirection() == dir && ife.getBlockZ() == lastIfe.getBlockZ();
			default -> throw new RuntimeException("unreachable");
		};
	}

	private final List<ItemFrame> getReachableItemFrames(final Player player, final double SCAN_DIST){
		final AABB box = player.getBoundingBox().inflate(SCAN_DIST, SCAN_DIST, SCAN_DIST);
		return player.level().getEntitiesOfClass(ItemFrame.class, box, ifePosFilter());
	}

	private final BlockPos getRelativeBp(AxisData data, boolean axis, boolean neg){
		final int offset = neg ? -1 : +1;
		return switch(dir){
			case UP, DOWN -> axis
					? new BlockPos(data.varAxis1 + offset, data.constAxis, data.varAxis2)
					: new BlockPos(data.varAxis1, data.constAxis, data.varAxis2 + offset);
			case EAST, WEST -> axis
					? new BlockPos(data.constAxis, data.varAxis1 + offset, data.varAxis2)
					: new BlockPos(data.constAxis, data.varAxis1, data.varAxis2 + offset);
			case NORTH, SOUTH -> axis
					? new BlockPos(data.varAxis1 + offset, data.varAxis2, data.constAxis)
					: new BlockPos(data.varAxis1, data.varAxis2 + offset, data.constAxis);
			default -> throw new RuntimeException("unreachable");
		};
	}
	private final boolean checkPosMatch1D(final ItemFrame ife, final int pos){
		return ife != null && isPartOfCurrentAutoPlace(ife.getItem())
				&& Integer.parseInt(getPosStrFromItem(ife.getItem())) == pos;
	}

	// Returns true if able to determine row width
	private final boolean calcWidthUsingAdjIFrames(Player player, AxisData currAxisData, int a, int b){
		assert axisMatch != null;
//		final Boolean rowOffsetNeg = axisMatch ? varAxis1Neg : varAxis2Neg;
		assert (axisMatch ? varAxis1Neg : varAxis2Neg) != null;//rowOffsetNeg != null;
//		final Boolean colOffsetNeg = axisMatch ? varAxis2Neg : varAxis1Neg;
		Main.LOGGER.info("AutoPlaceMapArt: recurRecalcUsingAdjIFrames, axisMatch="+axisMatch+", varAxis1Neg="+varAxis1Neg+", varAxis2Neg="+varAxis2Neg);

		final double SCAN_DIST = Configs.Generic.MAPART_AUTOPLACE_REACH.getDoubleValue() + 3d;
		final Map<Vec3i, ItemFrame> ifes = getReachableItemFrames(player, SCAN_DIST)
				.stream().collect(Collectors.toMap(ItemFrame::blockPosition, Function.identity()));

		final int rowOffset = a-b;
		final ItemFrame ifeExtendingRow = ifes.get(getRelativeBp(currAxisData, axisMatch, /*neg=*/rowOffset<0));
		if(ifeExtendingRow != null && isPartOfCurrentAutoPlace(ifeExtendingRow.getItem())) return false;
		final boolean emptyRowExtend = ifeExtendingRow != null && ifeExtendingRow.getItem().isEmpty();

		final int candidateWidth = Math.abs(rowOffset)+1;
		final int minPos = Math.min(a, b), maxPos = Math.max(a, b);
		final Boolean isTopOrBottomRow = maxPos < candidateWidth ? Boolean.TRUE : minPos >= ofSize-candidateWidth ? Boolean.FALSE : null;
		final Boolean colOffsetNeg = axisMatch ? varAxis2Neg : varAxis1Neg;
		if(isTopOrBottomRow != null && colOffsetNeg != null){
			assert (colOffsetNeg ^ !isTopOrBottomRow) == (isTopOrBottomRow ? colOffsetNeg : !colOffsetNeg);
			final ItemFrame ifeOnNextRow = ifes.get(getRelativeBp(currAxisData, !axisMatch, colOffsetNeg ^ !isTopOrBottomRow));
			if(emptyRowExtend){
				// If the map one row up/down is already hung (with the name we'd expect to find) then assume we've found rowWidth
				if(checkPosMatch1D(ifeOnNextRow, isTopOrBottomRow ? maxPos+1 : minPos-1)) rowWidth = candidateWidth;
//				if(ifeOnNextRow == null || !isPartOfCurrentAutoPlace(ifeOnNextRow.getHeldItemStack())
//					|| Integer.parseInt(getPosStrFromItem(ifeOnNextRow.getHeldItemStack())) != (isTopOrBottomRow ? maxPos+1 : minPos-1)) return false;
			}
			else if(ifeOnNextRow != null && ifeOnNextRow.getItem().isEmpty()) rowWidth = candidateWidth;
			return false;
		}
		final ItemFrame ifeColNeg = ifes.get(getRelativeBp(currAxisData, !axisMatch, true));
		if(ifeColNeg != null && isPartOfCurrentAutoPlace(ifeColNeg.getItem())){
			Main.LOGGER.info("AutoPlaceMapArt: sub-call to recalcLayout() with col-1");
			//TODO: current, this can trigger disableAndReset, killing the process
			final boolean result = recalcLayout(player, ifeColNeg, ifeColNeg.getItem()/*, sandbox=true*/);
			if(result) assert rowWidth != null;
			return result;
		}
		final ItemFrame ifeColPos = ifes.get(getRelativeBp(currAxisData, !axisMatch, false));
		if(ifeColPos != null && isPartOfCurrentAutoPlace(ifeColPos.getItem())){
			Main.LOGGER.info("AutoPlaceMapArt: sub-call to recalcLayout() with col+1");
			//TODO: current, this can trigger disableAndReset, killing the process
			final boolean result = recalcLayout(player, ifeColPos, ifeColPos.getItem()/*, sandbox=true*/);
			if(result) assert rowWidth != null;
			return result;
		}
		if(emptyRowExtend) return false;
		final boolean emptyColNeg = ifeColNeg != null && ifeColNeg.getItem().isEmpty();
		final boolean emptyColPos = ifeColPos != null && ifeColPos.getItem().isEmpty();
		if(!emptyColNeg && !emptyColPos) return false;
		if(emptyColNeg != emptyColPos && isTopOrBottomRow != null){//implies colOffsetNeg == null
			final boolean colIsNeg = emptyColNeg ^ !isTopOrBottomRow;
			Main.LOGGER.info("AutoPlaceMapArt: determined col isNeg="+colIsNeg+" from available ifes");
			if(axisMatch) varAxis2Neg = colIsNeg; else varAxis1Neg = colIsNeg;
		}
		if(b == 0 || b == ofSize-1){
			rowWidth = candidateWidth;
			return true;
		}
		return false;
	}

	public final boolean recalcLayout(final Player player, final ItemFrame currIfe, final ItemStack currStack){
		synchronized(stacksHashesForCurrentData){
		final Component currNameText = currStack.getCustomName();
		if(currNameText == null) return false;
		final String currName = currNameText.getString();
		String currPosStr = null;
		boolean updateLastIfe = true;
		try{
		if(!Generic.MAPART_AUTOPLACE.getBooleanValue()
			|| currIfe == null || currStack == null || currStack.getCount() != 1)
		{
			disableAndReset(); return false;
		}
		if(lastIfe == null) return false;

		if((dir=currIfe.getNearestViewDirection()) != lastIfe.getNearestViewDirection()){
			Main.LOGGER.info("AutoPlaceMapArt: currIfe and lastIfe are not facing the same dir");
			disableAndReset(); return false;
		}
		if((world=currIfe.level()) != lastIfe.level()){
			Main.LOGGER.info("AutoPlaceMapArt: currIfe and lastIfe are not in the same world!");
			disableAndReset(); return false;
		}
		final AxisData currAxisData = getAxisData(currIfe), lastAxisData = getAxisData(lastIfe);
		if(currAxisData.constAxis != lastAxisData.constAxis){
			Main.LOGGER.info("AutoPlaceMapArt: currIfe and lastIfe are not on the same const axis");
			disableAndReset(); return false;
		}

		final int ifeOffset1 = currAxisData.varAxis1 - lastAxisData.varAxis1, ifeOffset2 = currAxisData.varAxis2 - lastAxisData.varAxis2;
//		Main.LOGGER.info("AutoPlaceMapArt: ifeOffset1="+ifeOffset1+",ifeOffset2="+ifeOffset2);
		if(ifeOffset1 == 0 && ifeOffset2 == 0){
			Main.LOGGER.error("AutoPlaceMapArt: Placed maps appear to have the same pos! (shouldn't be possible!)");
			disableAndReset(); return false;
		}

		final RelatedMapsData data = MapRelationUtils.getRelatedMapsByName0(List.of(currStack, lastStack), world);
		if(data.slots().size() != 2){
			Main.LOGGER.info("AutoPlaceMapArt: currIfe and lastIfe are not related");
			disableAndReset(); return false;
		}
		if(data.prefixLen() == -1){
			Main.LOGGER.info("AutoPlaceMapArt: unable to predict placement for map names lacking pos data");
			disableAndReset(); return false;
		}
		// Parse 2d pos (and cache for other maps items, if necessary)
		final boolean fetchData = currentData == null;
		if(fetchData){
			assert allMapItems.isEmpty();
			allMapItems.add(currStack); allMapItems.add(lastStack);
			InvUtils.getAllNestedItems(player.getInventory().getNonEquipmentItems().stream()).filter(s -> s.getItem() == Items.FILLED_MAP).forEach(allMapItems::add);
//			Main.LOGGER.info("AutoPlaceMapArt: all maps in inv: "+(allMapItems.size()-2));

			currentData = MapRelationUtils.getRelatedMapsByName0(allMapItems, player.level());
			if(currentData.slots().size() <= 3){
				Main.LOGGER.info("AutoPlaceMapArt: not enough remaining maps in inv to justify enabling AutoPlace");
				disableAndReset(); return false;
			}
			getReachableItemFrames(player, Configs.Generic.MAPART_AUTOPLACE_REACH.getDoubleValue()+2)
					.stream().map(ItemFrame::getItem).filter(s -> s.getItem() == Items.FILLED_MAP).forEach(allMapItems::add);
			currentData = MapRelationUtils.getRelatedMapsByName0(allMapItems, player.level()); // More accurate prefix/suffix/etc data

//			Main.LOGGER.info("AutoPlaceMapArt: related maps in inv: "+(currentData.slots().size()-2));
			final String nameWoArtist = MapRelationUtils.removeByArtist(currName);
			final String suffixStr = nameWoArtist.substring(nameWoArtist.length()-data.suffixLen());
			Matcher m = pOfSize.matcher(suffixStr);
			if(m.find()){
				ofSize = Integer.parseInt(m.group(1));
				Main.LOGGER.info("AutoPlaceMapArt: Detected 'X/SIZE' posStr format, SIZE="+ofSize);
			}
		}
		currPosStr = getPosStrFromName(currName);
		if(lastPosStr == null) lastPosStr = getPosStrFromItem(lastStack);
		if(fetchData && ofSize == null && posStrIs1D(currPosStr)){
			if(!posStrIs1D(lastPosStr)){
				Main.LOGGER.info("AutoPlaceMapArt: currStack and lastStack have different posStr dimensionality! (1d)");
				disableAndReset(); return false;
			}
			final HashSet<Integer> hashes = new HashSet<>();
//			data.slots().stream().map(i -> ItemStack.hashCode(player.getInventory().main.get(i))).forEach(hashes::add);
//			hashes.remove(ItemStack.hashCode(currStack)); hashes.remove(ItemStack.hashCode(lastStack));
//			ofSize = hashes.size() + 2;
			allMapItems.stream().map(s -> ItemStack.hashItemAndComponents(s)).forEach(hashes::add);
			ofSize = hashes.size();
			Main.LOGGER.info("AutoPlaceMapArt: guessing ofSize="+ofSize+" (based on inventory/nearby ifes)");
		}
//		Main.LOGGER.info("AutoPlaceMapArt: currPosStr="+currPosStr+", lastPosStr="+lastPosStr);
		if(ofSize != null && rowWidth == null){
			if(!currPosStr.matches("-?\\d+")){
				Main.LOGGER.warn("AutoPlaceMapArt: Invalid 1d X/SIZE posStr! currPosStr="+currPosStr+",name="+currName);
				disableAndReset(); return false;
			}
			if(Math.abs(ifeOffset1) > ofSize || Math.abs(ifeOffset2) > ofSize){
				Main.LOGGER.warn("AutoPlaceMapArt: Invalid ife offsets ("+ifeOffset1+","+ifeOffset2+") for map X/SIZE="+ofSize);
				disableAndReset(); return false;
			}
			final int a = Integer.parseInt(currPosStr)-1, b = Integer.parseInt(lastPosStr)-1;
			if(a >= ofSize || b >= ofSize || a < 0 || b < 0 || a==b){
				Main.LOGGER.warn("AutoPlaceMapArt: Invalid 1d X/SIZE pos! a="+a+",b="+b);
				disableAndReset(); return false;
			}
			Main.LOGGER.info("AutoPlaceMapArt: for X/SIZE, size="+ofSize+", curr(a)="+a+", last(b)="+b+", ifeOffset1="+ifeOffset1+", ifeOffset2="+ifeOffset2);
			final int posOffset = a-b;
			if(ifeOffset1 == 0 || ifeOffset2 == 0){
				final int ifeOffset = ifeOffset1 + ifeOffset2; // one of them is 0
				final int ifeOffsetAbs = Math.abs(ifeOffset);
				final int posOffsetAbs = Math.abs(posOffset); // "a/SIZE", "b/SIZE" => a-b
				final boolean onSameRow = posOffsetAbs == ifeOffsetAbs;
				final boolean isAxisMatch = (ifeOffset1 != 0) == onSameRow;
				if(axisMatch == null){
					Main.LOGGER.info("AutoPlaceMapArt: (1d pos) determined axisMatch");
					axisMatch = isAxisMatch;
				}
				else if(axisMatch != isAxisMatch){
					Main.LOGGER.warn("AutoPlaceMapArt: (1d pos) user appears to have placed mapart in invalid spot! axisMatch");
					disableAndReset(); return false;
				}
				final boolean isNeg = (ifeOffset > 0 != posOffset > 0); // Equivalent: LHS == a-b < 0
				if(ifeOffset1 != 0){
					if(varAxis1Neg == null) varAxis1Neg = isNeg;
					else if(varAxis1Neg != isNeg){
						Main.LOGGER.warn("AutoPlaceMapArt: (1d pos) user appears to have placed mapart in invalid spot! varAxis1Neg");
						disableAndReset(); return false;
					}
				}
				else{
					if(varAxis2Neg == null) varAxis2Neg = isNeg;
					else if(varAxis2Neg != isNeg){
						Main.LOGGER.warn("AutoPlaceMapArt: (1d pos) user appears to have placed mapart in invalid spot! varAxis2Neg");
						disableAndReset(); return false;
					}
				}
			}
			if(axisMatch != null){
				final int colOffset = axisMatch ? ifeOffset2 : ifeOffset1;
				final int rowOffset = axisMatch ? ifeOffset1 : ifeOffset2;
//				Main.LOGGER.info("AutoPlaceMapArt: rowOffset="+rowOffset+", colOffset="+colOffset);
				if(colOffset == 0){
//					assert Math.abs(a-b) == Math.abs(rowOffset);
					if(Math.abs(a-b) != Math.abs(rowOffset)){ // Unreachable, I think?
						Main.LOGGER.error("AutoPlaceMapArt: rowOffset != posOffset (when colOffset == 0), unreachable?!");
						disableAndReset(); return false;
					}
					final int candidateRowWidth = Math.abs(rowOffset)+1;
					if(ofSize % candidateRowWidth == 0){
						//TODO: prevent calcWidthUsingAdjIFrames() from being able to trigger disableAndReset in sub-calls
						final boolean determinedWidth = calcWidthUsingAdjIFrames(player, currAxisData, a, b);
						if(determinedWidth){
							assert rowWidth != null && rowWidth != 0;
							Main.LOGGER.info("AutoPlaceMapArt: determined rowWidth="+rowWidth+" from calcWidthUsingAdjIFrames()");
						}
						if(dir == null) return false; // If the sub-call triggered disableAndReset()
					}
					// A little hack to maximize future rowOffset; might not be necessary anymore, but used to help the logic above
					if(rowWidth == null) updateLastIfe = false;
				}
				else if(rowOffset == 0){
					Main.LOGGER.info("AutoPlaceMapArt: rowOffset==0, so (a-b)/colOffset will give rowWidth: ("+a+"-"+b+")/"+colOffset);
					rowWidth = Math.abs(posOffset)/Math.abs(colOffset);
					assert rowWidth != 0;
				}
				else if(varAxis1Neg != null || varAxis2Neg != null){
					final Boolean rowNeg = axisMatch ? varAxis1Neg : varAxis2Neg;
					final Boolean colNeg = axisMatch ? varAxis2Neg : varAxis1Neg;
					if(rowNeg != null){
						final int test1 = posOffset - rowOffset*(rowNeg ? -1 : +1);
						assert test1 % colOffset == 0;
						rowWidth = Math.abs(test1/colOffset);
						Main.LOGGER.info("AutoPlaceMapArt: rowNeg="+rowNeg+", solved sys-of-eqs, test1="+test1+", posOffset="+posOffset+", rowWidth="+rowWidth);
						assert rowWidth != 0;
//						colNeg = test1/colOffset < 0;
						if(axisMatch) varAxis2Neg = test1/colOffset < 0;
						else varAxis1Neg = test1/colOffset < 0;
					}
					else if(colNeg != null){
						//Solve for: a + rowOffset*rowNeg + colOffset*colNeg*rowWidth = b;
						// a-b = rowOffset*rowNeg + colOffset*colNeg*rowWidth
						// (a-b - rowOffset*rowNeg)/(colOffset*colNeg) = rowWidth
						final int test1 = Math.abs(posOffset - rowOffset);
						final int test2 = Math.abs(posOffset + rowOffset);
						final int off = colOffset*(colNeg ? -1 : +1);
						Main.LOGGER.info("AutoPlaceMapArt: a-b="+posOffset+", rowOffset="+rowOffset+", test1="+test1+", test2="+test2+", off="+off);
						final boolean posWorks = test1 % off == 0, negWorks = test2 % off == 0;
						assert posWorks || negWorks;
						if(posWorks && negWorks){
							Main.LOGGER.info("AutoPlaceMapArt: (1d pos) unable to determine rowWidth from current offsets");
//							return false;
						}
						else if(posWorks){
							Main.LOGGER.info("AutoPlaceMapArt: (1d pos) using test1");
							rowWidth = test1/off;
							assert rowWidth != 0;
//							rowNeg = false;
						}
						else if(negWorks){
							Main.LOGGER.info("AutoPlaceMapArt: (1d pos) using test2");
							rowWidth = test2/off;
							assert rowWidth != 0;
//							colNeg = true;
						}
					}
				}
//				assert rowWidth != null;
				if(rowWidth != null && (rowWidth == 0 || ofSize % rowWidth != 0)){
					Main.LOGGER.warn("AutoPlaceMapArt: (1d pos) invalid width "+rowWidth+"! needs to be a divisor of SIZE");
					disableAndReset(); return false;
				}
			}//axisMatch != null
		}
		final Pos2DPair pos2dPair = getRelativePosPair(currPosStr, lastPosStr);
		if(pos2dPair == null){
			Main.LOGGER.warn("AutoPlaceMapArt: unable to parse pos2dPair from pos strs ("+currPosStr+","+lastPosStr+")");
			disableAndReset(); return false;
		}

		final int posOffset1 = pos2dPair.a1 - pos2dPair.b1, posOffset2 = pos2dPair.a2 - pos2dPair.b2;
//		Main.LOGGER.info("AutoPlaceMapArt: posOffset1="+posOffset1+",posOffset2="+posOffset2);

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
			Main.LOGGER.info("AutoPlaceMapArt: unable to distinguish the 2 variable axes from eachother");

			// At this point, we know abs(posOffset1) == abs(posOffset2) == abs(ifeOffset1) == abs(ifeOffset2);
			final boolean sameSign = ((ifeOffset1 == posOffset1) == (ifeOffset1 == posOffset2)) && ((ifeOffset2 == posOffset1) == (ifeOffset2 == posOffset2));
			if(!sameSign) return false;
			final boolean isNeg = ifeOffset1 != posOffset1;
//			Main.LOGGER.info("AutoPlaceMapArt: determined both axes offsets are "+(isNeg?"-":"+"));
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
		}
		else{
			if(ifeOffset1 != 0){
				final int posOffset = (axisMatch ? posOffset1 : posOffset2);
				final boolean isNeg = ifeOffset1 != posOffset;
				if(isNeg && ifeOffset1 != posOffset*-1){
					Main.LOGGER.info("AutoPlaceMapArt: error ??1 "+axisMatch+","+posOffset+","+isNeg);
					assert false;
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
				if(isNeg && ifeOffset2 != posOffset*-1){
					Main.LOGGER.info("AutoPlaceMapArt: error ??2 "+axisMatch+","+posOffset+","+isNeg);
					assert false;
					disableAndReset(); return false;
				}
				if(varAxis2Neg == null) varAxis2Neg = isNeg;
				else if(varAxis2Neg != isNeg){
					Main.LOGGER.warn("AutoPlaceMapArt: user appears to have placed mapart in invalid spot! +-axis2");
					disableAndReset(); return false;
				}
			}
			if(varAxis1Neg == null || varAxis2Neg == null){
				boolean foundAxis1 = varAxis1Neg != null;
				Main.LOGGER.warn("AutoPlaceMapArt: determined axisMatch="+axisMatch+" and 1 of 2 axis offsets"
						+" (axis"+(foundAxis1?"1="+(varAxis1Neg?"-":"+"):"2="+(varAxis2Neg?"-":"+"))
						+"), just need to get the other offset");
			}
		}

		if(!stacksHashesForCurrentData.isEmpty()) return true; // Already ongoing, and hashlist has ahready been defined

		assert !allMapItems.isEmpty();
		assert stacksHashesForCurrentData.isEmpty();
		stacksHashesForCurrentData.ensureCapacity(currentData.slots().size());
		currentData.slots().stream().map(i -> ItemStack.hashItemAndComponents(allMapItems.get(i))).forEach(stacksHashesForCurrentData::add);
		assert !stacksHashesForCurrentData.isEmpty();

		Main.LOGGER.info("AutoPlaceMapArt: activated! axisMatch="+axisMatch+",varAxis1Neg="+varAxis1Neg+",varAxis2Neg="+varAxis2Neg);
		return true;
		}
		finally{
			if(updateLastIfe){
				lastIfe = currIfe;
				lastStack = currStack;
				lastPosStr = currPosStr;
			}
		}
		}
	}

	public final BlockPos getPlacement(ItemStack stack){
		synchronized(stacksHashesForCurrentData){
			if(!isPartOfCurrentAutoPlace(stack)) return null;
			final Pos2DPair pos2dPair = getRelativePosPair(getPosStrFromName(stack.getHoverName().getString()), lastPosStr);
			if(pos2dPair == null) return null;
			final int axisOffset1, axisOffset2;
			if(axisMatch == null){
				if(pos2dPair.a1 - pos2dPair.b1 != pos2dPair.a2 - pos2dPair.b2) return null;
				axisOffset1 = axisOffset2 = pos2dPair.a1 - pos2dPair.b1;
			}
			else if(axisMatch){
				axisOffset1 = pos2dPair.a1 - pos2dPair.b1; axisOffset2 = pos2dPair.a2 - pos2dPair.b2;
			}
			else{
				axisOffset1 = pos2dPair.a2 - pos2dPair.b2; axisOffset2 = pos2dPair.a1 - pos2dPair.b1;
			}

			if(varAxis1Neg == null && axisOffset1 != 0) return null;
			if(varAxis2Neg == null && axisOffset2 != 0) return null;
			final AxisData data = getAxisData(lastIfe);
			final int varAxis1 = axisOffset1 == 0 ? data.varAxis1 : data.varAxis1+axisOffset1*(varAxis1Neg?-1:+1);
			final int varAxis2 = axisOffset2 == 0 ? data.varAxis2 : data.varAxis2+axisOffset2*(varAxis2Neg?-1:+1);
			return switch(dir){
				case UP, DOWN -> new BlockPos(varAxis1, data.constAxis, varAxis2);
				case EAST, WEST -> new BlockPos(data.constAxis, varAxis1, varAxis2);
				case NORTH, SOUTH -> new BlockPos(varAxis1, varAxis2, data.constAxis);
				default -> throw new RuntimeException("unreachable");
			};
		}
	}

	public final boolean hasKnownLayout(){return !stacksHashesForCurrentData.isEmpty();}

	// Functions NOT from MapLayoutFinder:

	private final void placeMapInFrame(LocalPlayer player, ItemFrame ife){
		assert player.getMainHandItem().equals(player.getInventory().getNonEquipmentItems().get(player.getInventory().getSelectedSlot()));

		Main.LOGGER.info("AutoPlaceMapArt: right-clicking target iFrame"
//				+ " ("+ife.getBlockPos().toShortString()+")"
				+ " with map: "+player.getMainHandItem().getHoverName().getString());

//		UpdateInventoryHighlights.setCurrentlyBeingPlacedMapArt(null, stack);
		recentPlaceAttempts[attemptIdx] = ife.getId();
		lastAttemptIdx = attemptIdx;

		lastStackAuto = player.getMainHandItem(); // TODO: is .copy() necessary here?
		lastIfeAuto = ife;

		final Vec3 interactionPos = ife.position().add(0, 0.0625, 0);
		player.connection.send(new ServerboundInteractPacket(ife.getId(), InteractionHand.MAIN_HAND, interactionPos, player.isShiftKeyDown()));
		Minecraft.getInstance().gameMode.interact(player, ife, new EntityHitResult(ife, interactionPos), InteractionHand.MAIN_HAND);
		if(Configs.Generic.MAPART_AUTOPLACE_SWING_HAND.getBooleanValue()) player.connection.send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
//		nearestIfe.interactAt(player, ife.getEyePos(), Hand.MAIN_HAND);
//		player.interact(ife, Hand.MAIN_HAND);
	}

	private final Vec3 getPlaceAgainstSurface(BlockPos ifeBp){
		Vec3 center = Vec3.atCenterOf(ifeBp);
//		switch(dir){
//			case UP: return center.add(0, -.5, 0);
//			case DOWN: return center.add(0, .5, 0);
//			case EAST: return center.add(-.5, 0, 0);
//			case WEST: return center.add(.5, 0, 0);
//			case NORTH: return center.add(0, 0, .5);
//			case SOUTH: return center.add(0, 0, -.5);
//
//			default: assert(false) : "Unreachable"; return null;
//		}
		return switch(dir){
			case UP -> center.add(0, -.5, 0);
			case DOWN -> center.add(0, .5, 0);
			case EAST -> center.add(-.5, 0, 0);
			case WEST -> center.add(.5, 0, 0);
			case NORTH -> center.add(0, 0, .5);
			case SOUTH -> center.add(0, 0, -.5);
			default -> throw new RuntimeException("unreachable");
		};
	}

	private record MapPlacementData(int slot, int bundleSlot, ItemFrame ife, BlockPos bp){}
	public final MapPlacementData getNearestMapPlacement(Player player, final boolean ALLOW_OUTSIDE_MAX_REACH, final boolean ALLOW_MAP_IN_HAND){
		final List<ItemStack> slots = player.inventoryMenu.slots.stream().map(Slot::getItem).toList();

		final double MAX_REACH = ALLOW_OUTSIDE_MAX_REACH ? 999d : Configs.Generic.MAPART_AUTOPLACE_REACH.getDoubleValue();
		final double MAX_REACH_SQ = MAX_REACH*MAX_REACH;
		final double BP_SCAN_DIST = MAX_REACH+2, BP_SCAN_DIST_SQ = BP_SCAN_DIST*BP_SCAN_DIST;

		final Map<Vec3i, ItemFrame> ifes = getReachableItemFrames(player, BP_SCAN_DIST)
				.stream().collect(Collectors.toMap(ItemFrame::blockPosition, Function.identity()));
		final boolean CAN_PLACE_IFRAMES = Configs.Generic.MAPART_AUTOPLACE_IFRAMES.getBooleanValue();
		if(!CAN_PLACE_IFRAMES && ifes.isEmpty()){
//			Main.LOGGER.warn("AutoPlaceMapArt: no nearby iframes");
			return null;
		}

		double nearestDistSq = Double.MAX_VALUE;
		ItemFrame nearestIfe = null;
		BlockPos nearestBp = null;
		int nearestSlot = -1, bundleSlot = 0;
		int numMaps = 0, numRelated = 0, numRelatedInRange = 0, numRelatedInRangeStrict = 0;
		boolean nearestIsInHotbar = false;
		invloop: for(int i=slots.size()-1; i>=0; --i){
			final boolean isInHotbar = i >= 36 && i < 45 && slots.get(i).getItem() == Items.FILLED_MAP;
			if(nearestIsInHotbar && !isInHotbar) continue;
			if(!ALLOW_MAP_IN_HAND && i-36 == player.getInventory().getSelectedSlot()) continue;
//			ItemStack mapItem = slots.get(i);
			BundleContents contents = slots.get(i).get(DataComponents.BUNDLE_CONTENTS);
			if(bundleSlot == -1 && contents != null) continue; // Prefer to avoid bundles when we have an alterantive itemstack
			final int bundleSz = contents != null ? contents.size() : 0;
//			if(contents != null && !contents.isEmpty()) mapItem = contents.get(contents.size()-1);

			final boolean ALLOW_ONLY_TOP_SLOT = !Configs.Generic.USE_BUNDLE_PACKET.getBooleanValue();
			final int TOP_SLOT = Configs.Generic.BUNDLES_ARE_REVERSED.getBooleanValue() ? bundleSz-1 : 0;
			for(int j=-1; j<bundleSz; ++j){
				final ItemStack mapStack;
				if(j == -1) mapStack = slots.get(i);
				else if(ALLOW_ONLY_TOP_SLOT && j != TOP_SLOT) continue;
				else mapStack = contents.items().get(j).create();
				if(mapStack.getItem() != Items.FILLED_MAP) continue;
				++numMaps;
				BlockPos ifeBp = getPlacement(mapStack);
				if(ifeBp == null) continue;
				++numRelated;
				if(ifeBp.distToCenterSqr(player.getEyePosition()) > BP_SCAN_DIST_SQ) continue;
				++numRelatedInRange;
				final ItemFrame ife = ifes.get(ifeBp);
				final Vec3 ifeEyePos;
				if(ife == null){
					if(!CAN_PLACE_IFRAMES){
						if(ofSize == null || rowWidth != null) // Don't show this warning for uncertain ife positions
							Main.LOGGER.warn("AutoPlaceMapArt: Missing iFrame at pos! ");//+ifeBp.toShortString());
						continue;
					}
					if(nearestIfe != null) continue; // found an iFrame to place into - so don't bother placing iFrames
					ifeEyePos = getPlaceAgainstSurface(ifeBp);
				}
				else{
					if(!ife.getItem().isEmpty()){
//						if(ofSize == null || rowWidth != null) // Don't show this warning for uncertain ife positions
//							Main.LOGGER.warn("AutoPlaceMapArt: iFrame already contains item at pos! ");//+ifeBp.toShortString());
						continue;
					}
					ifeEyePos = ife.getEyePosition(); // Consider: should I use ife.getNearestCornerToPlayer?
				}
				final double distSq = ifeEyePos.distanceToSqr(player.getEyePosition());
				if(distSq > MAX_REACH_SQ) continue;
				++numRelatedInRangeStrict;

				if(ife == null){
					int bpHash = ifeBp.hashCode()+1;
					if(Arrays.stream(recentPlaceAttempts).anyMatch(h -> h == bpHash)) continue;
				}
				else if(Arrays.stream(recentPlaceAttempts).anyMatch(id -> id == ife.getId())){
					Main.LOGGER.warn("AutoPlaceMapArt: Cannot place into the same iFrame twice! "+ifeBp.toShortString());
					continue;
				}
				final boolean justUseIt = isInHotbar && i-36 == player.getInventory().getSelectedSlot() && distSq <= MAX_REACH_SQ;
				if(justUseIt || distSq < nearestDistSq || (isInHotbar && !nearestIsInHotbar)){
					nearestIsInHotbar = isInHotbar;
					nearestDistSq = distSq;
					nearestSlot = i;
					bundleSlot = j;
					nearestIfe = ife;
					nearestBp = ifeBp;
					if(justUseIt){
//						if(!hasWarnedMissingIfe) Main.LOGGER.info("AutoPlaceMapArt: Stack in hand is a valid candidate, using it!");
						break invloop;
					}
				}
			}
		}
//		Main.LOGGER.info("AutoPlaceMapArt: distance to place-loc for itemstack in slot"+nearestSlot+": "+Math.sqrt(nearestDistSq));
//		return nearestSlot == -1 ? null : new MapPlacementData(nearestSlot, bundleSlot, nearestStack, nearestIfe);
		if(nearestBp == null){
			if(!warnedNoValidPos) Main.LOGGER.info("AutoPlaceMapArt: No viable itemstack->iframe found. #nearby_ifes="+ifes.size()
				+",#maps="+numMaps+",#related="+numRelated+",#in_range="+numRelatedInRange+",#in_range_strict(ife)="+numRelatedInRangeStrict);
			warnedNoValidPos = true;
			return null;
		}
		warnedNoValidPos = false;
		return new MapPlacementData(nearestSlot, bundleSlot, nearestIfe, nearestBp);
	}


	private boolean test2=true; // TODO: Pause while player is moving... keep or nah?
	private final boolean isMovingTooFast(Vec3 velocity){
		return !test2 && (velocity.x*velocity.x + velocity.z*velocity.z) > 0.0001 || Math.abs(velocity.y) > 0.08;
	}

	private final boolean test=true;// TODO: Test to confirm, but changing hotbar slots shouldn't count as an inv action

	private final void getMapIntoMainHand(LocalPlayer player, int slot, int bundleSlot){
		assert slot != player.getInventory().getSelectedSlot()+36 || bundleSlot != -1;
		assert player.getMainHandItem() == player.getInventory().getSelectedItem();
		assert player.getMainHandItem() == player.getInventory().getItem(player.getInventory().getSelectedSlot());

		final int TICKS_BETWEEN_INV_ACTIONS = Configs.Generic.MAPART_AUTOPLACE_INV_DELAY.getIntegerValue();
		if(ticksSinceInvAction < TICKS_BETWEEN_INV_ACTIONS){
			if(extraInfoLogs || ticksSinceInvAction == TICKS_BETWEEN_INV_ACTIONS-1)
				Main.LOGGER.info("AutoPlaceMapArt: waiting for inv action cooldown ("+ticksSinceInvAction+"ticks)");
			return;
		}
		final Runnable onDone = TICKS_BETWEEN_INV_ACTIONS == 0 ? ()->placeNearestMap(player) : ()->ticksSinceInvAction=0;
		final int selectedSlot = player.getInventory().getSelectedSlot();
		if(bundleSlot == -1){
			final int nextHbSlot;
			if(slot >= 36 && slot < 45){
				player.getInventory().setSelectedSlot(slot - 36);
				Main.LOGGER.info("AutoPlaceMapArt: Changed selected hotbar slot to nearestMap: hb="+(slot-36));
				if(test) placeNearestMap(player);
				else ticksSinceInvAction = 0;
			}
			else{
				if(isIFrame(player.getInventory().getItem(selectedSlot).getItem()) &&
					!isIFrame(player.getInventory().getItem(nextHbSlot=(selectedSlot+1)%9).getItem()))
				{
					player.getInventory().setSelectedSlot(nextHbSlot);
					Main.LOGGER.info("AutoPlaceMapArt: Changed selected hotbar slot to avoid losing iFrame stack");
					if(!test) return;
				}
				if(isMovingTooFast(player.getDeltaMovement())) return;
				// Swap from upper inv to main hand
				ClickUtils.executeClicks(/*canProceed=*/_->true, onDone, new InvAction(slot, selectedSlot, ActionType.HOTBAR_SWAP));
				Main.LOGGER.info("AutoPlaceMapArt: Swapped nextMap to inv.selectedSlot: s="+slot+"->hb="+(selectedSlot));
			}
		}
		else{ // bundleSlot != -1
			if(slot == selectedSlot+36 || !player.getMainHandItem().isEmpty()){
				Main.LOGGER.info("AutoPlaceMapArt: Main hand is not empty! Unable to extract from bundle");
//				disableAndReset(); return;
				int hbSlot = 0;
				while(hbSlot < 9 && !player.getInventory().getNonEquipmentItems().get(hbSlot).isEmpty()) ++hbSlot;
				if(hbSlot != 9){
					player.getInventory().setSelectedSlot(hbSlot);
					Main.LOGGER.info("AutoPlaceMapArt: Changed selected hotbar slot to empty slot: hb="+hbSlot);
					if(!test){ticksSinceInvAction = 0; return;}
				}
				else{
					if(isMovingTooFast(player.getDeltaMovement())) return;
					// Try to move item out of main hand
					ClickUtils.executeClicks(/*canProceed=*/_->true, onDone, new InvAction(selectedSlot+36, 0, ActionType.SHIFT_CLICK));
					Main.LOGGER.info("AutoPlaceMapArt: Shift-clicking item out of mainhand (to upper inv), hb="+selectedSlot);
					return;
				}
			}
			if(isMovingTooFast(player.getDeltaMovement())) return;
			BundleContents contents = player.inventoryMenu.slots.get(slot).getItem().get(DataComponents.BUNDLE_CONTENTS);
			assert contents != null && contents.size() > bundleSlot;
			ArrayDeque<InvAction> clicks = new ArrayDeque<>();
			if(bundleSlot != contents.size()-1){
				int bundleSlotUsed = Configs.Generic.BUNDLES_ARE_REVERSED.getBooleanValue() ? contents.size()-(bundleSlot+1) : bundleSlot;
				clicks.add(new InvAction(slot, bundleSlotUsed, ActionType.BUNDLE_SELECT)); // Select bundle slot
			}
			clicks.add(new InvAction(slot, 1, ActionType.CLICK)); // Take from bundle
			clicks.add(new InvAction(player.getInventory().getSelectedSlot()+36, 0, ActionType.CLICK)); // Place in hand (intentionally using inv.selectedSlot here)
			ClickUtils.executeClicks(/*canProceed=*/_->true, onDone, clicks);
			Main.LOGGER.info("AutoPlaceMapArt: Extracted map from bundle into mainhand");
		}
	}

	private boolean isIFrame(Item item){return item == Items.ITEM_FRAME || item == Items.GLOW_ITEM_FRAME;}

	private final void placeNearestMap(LocalPlayer player){
		if(!hasKnownLayout()) return;
		if(player == null || player.level() == null){
			Main.LOGGER.info("AutoPlaceMapArt: player disconnected mid-op");
			disableAndReset(); return;
		}
		if(!Configs.Generic.MAPART_AUTOPLACE.getBooleanValue()){
			Main.LOGGER.info("AutoPlaceMapArt: disabled mid-op");
			disableAndReset(); return;
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

		if(player.containerMenu != null && player.containerMenu.containerId != 0){
//			Main.LOGGER.info("AutoPlaceMapArt: paused, currently in container gui");
			return;
		}

		// Sadly this doesn't work after the last manual map, since UseEntityCallback.EVENT isn't triggered by AutoMapArtPlace for some reason.
		// And yeah, I tried setting it manually, but since the code can't guarantee a map gets placed, it can get it stuck.
		if(!player.hasInfiniteMaterials() && UpdateInventoryContents.hasCurrentlyBeingPlacedMapArt() && ++ticksWaitingForManualClick <= MANUAL_CLICK_WAIT_TIMEOUT){
			if(extraInfoLogs || ticksWaitingForManualClick == MANUAL_CLICK_WAIT_TIMEOUT)
				Main.LOGGER.info("AutoPlaceMapArt: waiting for last manually-placed mapart to vanish from mainhand ("+ticksWaitingForManualClick+"ticks)");
			return;
		}
		ticksWaitingForManualClick = 0;

		// Don't spam-place in the same blockpos, give iframe entity a chance to load
		if(++attemptIdx >= recentPlaceAttempts.length) attemptIdx = 0;
		recentPlaceAttempts[attemptIdx] = 0;

		{
			Entity e = player.level().getEntity(recentPlaceAttempts[lastAttemptIdx]);
			if(e != null && e instanceof ItemFrame ife && ItemStack.matches(player.getMainHandItem(), ife.getItem())){
				final int waited = lastAttemptIdx < attemptIdx ? attemptIdx-lastAttemptIdx : recentPlaceAttempts.length+attemptIdx-lastAttemptIdx;
				if(extraInfoLogs || waited == recentPlaceAttempts.length-1)
					Main.LOGGER.info("AutoPlaceMapArt: waiting for current map to vanish from mainhand ("+waited+"ticks)");
				return;
			}
		}

		{
//			assert 0 <= attemptIdx < recentPlaceAttempts.length;
			int i = (attemptIdx + 1) % recentPlaceAttempts.length;
			while(i != attemptIdx){
				Entity e = player.level().getEntity(recentPlaceAttempts[i]);
				if(e != null && e instanceof ItemFrame ife && ife.getItem().isEmpty()){
//					final int rem = attemptIdx < i ? i-attemptIdx : recentPlaceAttempts.length+i-attemptIdx;
					final int waited = i < attemptIdx ? attemptIdx-i : recentPlaceAttempts.length+attemptIdx-i;
					if(extraInfoLogs || waited == recentPlaceAttempts.length-1)
						Main.LOGGER.info("AutoPlaceMapArt: waiting for current map to appear in iFrame ("+waited+"ticks)");
					return;
				}
				if(++i == recentPlaceAttempts.length) i = 0;
			}
		}

		final MapPlacementData data = getNearestMapPlacement(player, /*allowOutsideReach=*/false, /*allowMapInHand=*/true);
		if(data == null){
			if(lastIfeAuto != null && (axisMatch == null || varAxis1Neg == null || varAxis2Neg == null) && !calledRecalcLayout){
				assert lastStackAuto != null; // in sync with lastIfeAuto
				Main.LOGGER.info("AutoPlaceMapArt: Unable to determine placement, calling recalcLayout");
				recalcLayout(player, lastIfeAuto, lastStackAuto);
				calledRecalcLayout = true; // Calling this regardless, since sometimes
			}
			else if(player.getMainHandItem().getItem() != Items.FILLED_MAP && handRestockFallback != null && !handRestockFailed){
				Main.LOGGER.info("AutoPlaceMapArt: Unable to determine placement, calling handRestockFallback");
				final ItemStack handRestock = handRestockFallback.apply(lastStackAuto != null ? lastStackAuto : lastStack);
				if(handRestock == null || handRestock == lastHandRestockFallbackStack) handRestockFailed = true;
				else lastHandRestockFallbackStack = handRestock;
			}
			return;
		}
		calledRecalcLayout = handRestockFailed = false;

		if(player.inventoryMenu != null && !player.inventoryMenu.getCarried().isEmpty()){
			Main.LOGGER.warn("AutoPlaceMapArt: item stuck on cursor! attempting to place into empty slot");
			for(int i=44; i>=0; --i) if(!player.inventoryMenu.slots.get(i).hasItem()){
				// Place stack on cursor
				ClickUtils.executeClicks(/*canProceed=*/_->true, ()->{}, new InvAction(i, 0, ActionType.CLICK));
				return;
			} 
			disableAndReset(); return;
		}

		if(data.ife == null){ // Implies placing iFrame, not map item
			if(!hasWarnedMissingIfe) Main.LOGGER.warn("AutoPlaceMapArt: no ife found"
//					+ " at "+data.bp.toShortString()
					+ ", checking for iframes in inv");
			final InteractionHand hand;
			if(isIFrame(player.getMainHandItem().getItem())) hand = InteractionHand.MAIN_HAND;
			else if(isIFrame(player.getOffhandItem().getItem())) hand = InteractionHand.OFF_HAND;
			else{
				int hbSlot = 0;
				while(hbSlot < 9 && !isIFrame(player.getInventory().getNonEquipmentItems().get(hbSlot).getItem())) ++hbSlot;
				if(hbSlot == 9){
					if(!hasWarnedMissingIfe) Main.LOGGER.warn("AutoPlaceMapArt: no iFrames found in offhand or hotbar");
					hasWarnedMissingIfe = true;
					return;
				}
				player.getInventory().setSelectedSlot(hbSlot);
				if(!test){ticksSinceInvAction = 0; return;}
				else hand = InteractionHand.MAIN_HAND;
			}
			recentPlaceAttempts[attemptIdx] = data.bp.hashCode()+1;

			BlockHitResult hitResult = new BlockHitResult(getPlaceAgainstSurface(data.bp), dir, data.bp.relative(dir.getOpposite()), /*insideBlock=*/false);
			Minecraft.getInstance().gameMode.useItemOn(player, hand, hitResult);
//			placedAnyIframe = true;
			return;
		}
		else hasWarnedMissingIfe = false;

		if(data.slot != player.getInventory().getSelectedSlot()+36 || data.bundleSlot != -1){
//			Main.LOGGER.warn("AutoPlaceMapArt: calling getMapIntoMainHand(), data.slot="+data.slot+",hb="+client.player.getInventory().selectedSlot);
			getMapIntoMainHand(player, data.slot, data.bundleSlot);
			return;
		}
		placeMapInFrame(player, data.ife);
	}
}