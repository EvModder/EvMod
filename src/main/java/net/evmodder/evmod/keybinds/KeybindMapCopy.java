package net.evmodder.evmod.keybinds;

import net.evmodder.evmod.Main;
import net.evmodder.evmod.keybinds.ClickUtils.ClickEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.CartographyTableScreen;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import org.apache.commons.lang3.math.Fraction;

public final class KeybindMapCopy{
	private long lastCopy;
	private final long copyCooldown = 250l;
	private final boolean PRESERVE_EXACT_POS = true;

	record ConstFields(int RESULT, int INPUT_START, int INPUT_END, int INV_START, int INV_END, int HOTBAR_START, int HOTBAR_END){}
	final ConstFields INV = new ConstFields(0, 1, 5, 9, 36, 36, 45); // PlayerScreenHandler.class
	final ConstFields CRAFTER = new ConstFields(0, 1, 10, 10, 37, 37, 46); // CraftingScreenHandler.class
	final ConstFields CARTO = new ConstFields(2, 0, 1, 3, 30, 30, 39); // CartographyTableScreenHandler.class

	// Shift-click results:
	// Shift-click in crafting input -> TL of inv
	// Shift-click in crafting output -> BR of inv
	// Shift-click in InventoryScreen -> TL hotbar <-> TL inv
	// Shift-click in CraftingScreen -> TL input
	//TODO: remove these two functions (move to some utils file and comment out)
	private final void swap(final ItemStack[] slots, final int i, final int j){
		ItemStack t = slots[i];
		slots[i] = slots[j];
		slots[j] = t;
	}
	private final boolean simShiftClick(final ArrayDeque<ClickEvent> clicks, final ItemStack[] slots, final int i, final ConstFields f){
		if(slots[i].isEmpty()){Main.LOGGER.warn("MapCopy: simShiftClick() called for an empty slot"); return true;}

		// Goes to BR
		if(i == f.RESULT){
			for(int j=f.HOTBAR_END-1; j>=f.INV_START; --j) if(slots[j].isEmpty()){swap(slots, i, j); return true;} // BR inv + hotbar
			return false;
		}
		// Goes to TL
		int START = -1, END = -1;
		if(i >= f.INPUT_START && i < f.INPUT_END){START = f.INV_START; END = f.HOTBAR_END;} // -> TL inv + hotbar
		if(f==CRAFTER && i >= f.INV_START && i < f.HOTBAR_END){START = f.INPUT_START; END = f.INPUT_END;} // -> TL crafter
		if(f==CARTO && i >= f.INV_START && i < f.HOTBAR_END){ // -> matching carto input
			if(slots[i].getItem() == Items.FILLED_MAP){START = 0; END = 1;}
			else if(slots[i].getItem() == Items.MAP || slots[i].getItem() == Items.GLASS_PANE){START = 1; END = 2;}
		}
		if(f!=CRAFTER && START==-1 && i >= f.INV_START && i < f.INV_END){START = f.HOTBAR_START; END = f.HOTBAR_END;} // -> TL hotbar
		if(f!=CRAFTER && START==-1 && i >= f.HOTBAR_START && i < f.HOTBAR_END){START = f.INV_START; END = f.INV_END;} // -> TL inv
		if(START == -1){
			Main.LOGGER.error("MapCopy: simShiftClick() with an unsupported slot index! "+i);
			return false;
		}
		for(int j=START; j<END; ++j){
			if(slots[j].isEmpty()){swap(slots, i, j); return true;}
			if(f==CARTO && ItemStack.areItemsAndComponentsEqual(slots[i], slots[j])){
				int sumCount = slots[j].getCount() + slots[i].getCount();
				if(sumCount <= slots[j].getMaxCount()){
					slots[j].setCount(sumCount);
					slots[i] = ItemStack.EMPTY;
				}
				else{
					slots[j].setCount(slots[j].getMaxCount());
					slots[i].setCount(sumCount - slots[j].getMaxCount());
				}
				return true;
			}
		}

		Main.LOGGER.error("MapCopy: simShiftClick() failed due to no available destination slots! "+i);
		return false;
	}

	private final int getEmptyMapsIntoInput(final ArrayDeque<ClickEvent> clicks, final ItemStack[] slots, final ConstFields f,
			final int amtNeeded, int amtInGrid, final int dontLeaveEmptySlotsAfterThisSlot){

		// Restock empty maps as needed
		for(int j=f.INV_START; j<f.HOTBAR_END && amtInGrid < amtNeeded; ++j){
			if(slots[j].getItem() != Items.MAP) continue;
			final boolean leaveOne = j > dontLeaveEmptySlotsAfterThisSlot;
			if(leaveOne && slots[j].getCount() == 1) continue;

			final int combinedCnt = slots[j].getCount() + amtInGrid;
			final int combinedHalfCnt = (slots[j].getCount()+1)/2 + amtInGrid;

			if(j >= f.HOTBAR_START && (!leaveOne || amtInGrid > 0) && slots[j].getCount() >= amtNeeded){
				clicks.add(new ClickEvent(f.INPUT_START+1, j-f.HOTBAR_START, SlotActionType.SWAP));
				amtInGrid = slots[j].getCount();
				slots[j].setCount(combinedCnt-amtInGrid); // Effectively swap the counts
				break;
			}
			else if(f==CRAFTER && !leaveOne && combinedCnt <= 64){
				clicks.add(new ClickEvent(j, 0, SlotActionType.QUICK_MOVE)); // Move all to input
				slots[j] = ItemStack.EMPTY;
				amtInGrid = combinedCnt;
			}
			else if(f==CARTO && (!leaveOne || combinedCnt > 64)){
				clicks.add(new ClickEvent(j, 0, SlotActionType.QUICK_MOVE)); // Move all to input
				if(combinedCnt <= 64) slots[j] = ItemStack.EMPTY;
				else slots[j].setCount(combinedCnt - 64);
				amtInGrid = Math.min(combinedCnt, 64);
			}
			else if(!leaveOne && combinedCnt <= 64){
				clicks.add(new ClickEvent(j, 0, SlotActionType.PICKUP)); // Pickup all
				clicks.add(new ClickEvent(f.INPUT_START+1, 0, SlotActionType.PICKUP)); // Place all in input
				slots[j] = ItemStack.EMPTY;
				amtInGrid = combinedCnt;
			}
			else if(slots[j].getCount() > 1 && combinedHalfCnt >= amtNeeded && combinedHalfCnt <= 64){
				clicks.add(new ClickEvent(j, 1, SlotActionType.PICKUP)); // Pickup half
				clicks.add(new ClickEvent(f.INPUT_START+1, 0, SlotActionType.PICKUP)); // Place all in input
				slots[j].setCount(slots[j].getCount()/2);
				amtInGrid = combinedHalfCnt;
			}
			else if(!leaveOne || combinedCnt > 64){
//				clicks.add(new ClickEvent(j, 0, SlotActionType.QUICK_MOVE)); // Move all to input + overflow
//				clicks.add(new ClickEvent(INPUT_START+1, 0, SlotActionType.QUICK_MOVE)); // Move back overflow
//				ItemStack temp = slots[j];
//				slots[j] = EMPTY_ITEM;
//				temp.setCount(combinedCnt - 64);
//				slots[lastEmptySlot(slots, HOTBAR_END, INV_START)] = temp;
//				amtInGrid = 64;
				clicks.add(new ClickEvent(j, 0, SlotActionType.PICKUP)); // Pickup all
				clicks.add(new ClickEvent(f.INPUT_START+1, 0, SlotActionType.PICKUP)); // Place in input
				clicks.add(new ClickEvent(j, 0, SlotActionType.PICKUP)); // Putback leftovers
				slots[j].setCount(combinedCnt - 64);
				amtInGrid = 64;
			}
			else{
				clicks.add(new ClickEvent(j, 0, SlotActionType.PICKUP)); // Pickup all
				clicks.add(new ClickEvent(j, 1, SlotActionType.PICKUP)); // Putback one
				clicks.add(new ClickEvent(f.INPUT_START+1, 0, SlotActionType.PICKUP)); // Place all in input
				slots[j].setCount(1);
				amtInGrid = combinedCnt - 1;
			}
		}
		return amtInGrid;
	}

	private final int lastEmptySlot(ItemStack[] slots, final int END, final int START){
		for(int i=END-1; i>=START; --i) if(slots[i].isEmpty()) return i;
		return -1;
	}

	private final int getNumStored(Fraction fraction){
		assert 64 % fraction.getDenominator() == 0;
		return (64/fraction.getDenominator())*fraction.getNumerator();
	}
	private final String getCustomNameOrNull(ItemStack stack){
		return stack.getCustomName() == null ? null : stack.getCustomName().getString();
	}

	private record PrioAndSlot(int p, int slot) implements Comparable<PrioAndSlot> {
		@Override public int compareTo(PrioAndSlot o){return p != o.p ? p - o.p : slot - o.slot;}
	};
	private void copyMapArtInBundles(final ArrayDeque<ClickEvent> clicks, final ItemStack[] slots, final ConstFields f,
			int numEmptyMapsInGrid, final int totalEmptyMaps){
		final int[] slotsWithBundles = IntStream.range(f.INV_START, f.HOTBAR_END).filter(i -> {
			BundleContentsComponent contents = slots[i].get(DataComponentTypes.BUNDLE_CONTENTS);
			return contents != null && contents.stream().allMatch(s -> s.getItem() == Items.FILLED_MAP);
		}).toArray();
		final BundleContentsComponent[] bundles = Arrays.stream(slotsWithBundles)
				.mapToObj(i -> slots[i].get(DataComponentTypes.BUNDLE_CONTENTS)).toArray(BundleContentsComponent[]::new);
		final int SRC_BUNDLES = (int)Arrays.stream(bundles).filter(Predicate.not(BundleContentsComponent::isEmpty)).count();
		final int emptyBundles = bundles.length - SRC_BUNDLES;
		if(emptyBundles == 1){Main.LOGGER.warn("MapCopyBundle: Could not find an auxiliary bundle"); return;}
		int LAST_EMPTY_SLOT = lastEmptySlot(slots, f.HOTBAR_END, f.INV_START);
		if(LAST_EMPTY_SLOT == -1 && Arrays.stream(bundles).anyMatch(b -> b.stream().anyMatch(s -> s.getCount() > 1))){
			Main.LOGGER.warn("MapCopyBundle: Unable to copy bundles containing maps with stackSize>1 without an empty inv slot");
			return;
		}
		final int DESTS_PER_SRC = SRC_BUNDLES >= emptyBundles ? 999 : (emptyBundles-1)/SRC_BUNDLES;
		Main.LOGGER.warn("MapCopyBundle: source bundles: "+SRC_BUNDLES+", empty bundles: "+emptyBundles
				+", dest-per-src: "+DESTS_PER_SRC+", last-empty-slot: "+LAST_EMPTY_SLOT);

		TreeMap<Integer, List<Integer>> bundlesToCopy = new TreeMap<>(); // source bundle -> destination bundles (slotsWithBundles)
		HashSet<Integer> usedDests = new HashSet<>();
		HashSet<Item> srcBundleTypes = new HashSet<>();
		HashSet<Item> dstBundleTypes = new HashSet<>();
		for(int i=0; i<slotsWithBundles.length; ++i){
			final int s1 = slotsWithBundles[i];
			if(bundles[i].isEmpty()) continue;
			srcBundleTypes.add(slots[s1].getItem()); // At this point, we already know we're copying this bundle
			ArrayList<Integer> copyDests = new ArrayList<>();
			final String name1 = getCustomNameOrNull(slots[s1]);
//			Main.LOGGER.info("looking for dest bundles for "+slots[s1].getName().getString()+" in slot "+s1);

			if(name1 != null){ // Match by name (1st priority)
				for(int j=0; j<slotsWithBundles.length && usedDests.size()+1<emptyBundles && copyDests.size()<DESTS_PER_SRC; ++j){
					if(bundles[j].isEmpty() && name1.equals(getCustomNameOrNull(slots[slotsWithBundles[j]])) && usedDests.add(j))
					{
						Main.LOGGER.info("MapCopyBundle: matching-name copy dest "+s1+"->"+slotsWithBundles[j]);
						copyDests.add(j);
					}
				}
			}
			if(copyDests.isEmpty() && slots[s1].getItem() != Items.BUNDLE){ // Match by non-default color (2nd priority)
				for(int j=0; j<slotsWithBundles.length && usedDests.size()+1<emptyBundles && copyDests.size()<DESTS_PER_SRC; ++j){
					if(bundles[j].isEmpty() && slots[s1].getItem() == slots[slotsWithBundles[j]].getItem()
						&& (name1 == null || getCustomNameOrNull(slots[slotsWithBundles[j]]) == null) && usedDests.add(j))
					{
						Main.LOGGER.info("MapCopyBundle: matching-color copy dest "+s1+"->"+slotsWithBundles[j]);
						copyDests.add(j);
					}
				}
			}
			// If the above methods failed, loosen the src-bundle requirements a bit
			if(copyDests.isEmpty()) for(int j=0; j<slotsWithBundles.length && usedDests.size()+1<emptyBundles && copyDests.size()<DESTS_PER_SRC; ++j){
				final int s2 = slotsWithBundles[j];
				if(!bundles[j].isEmpty()) continue;
				if(name1 != null && getCustomNameOrNull(slots[s2]) != null && !name1.equals(getCustomNameOrNull(slots[s2]))) continue;
				if(SRC_BUNDLES > 1 && slots[s1].getItem() != slots[s2].getItem()) continue; // Enforce at least matching color for N->M where N>1
				if(!usedDests.add(j)) continue;
				Main.LOGGER.info("MapCopyBundle: valid copy dest "+s1+"->"+s2);
				copyDests.add(j);
			}
			if(copyDests.isEmpty()){Main.LOGGER.warn("MapCopyBundle: Could not determine destination bundles"); return;}
			for(int j : copyDests) dstBundleTypes.add(slots[slotsWithBundles[j]].getItem());
			bundlesToCopy.put(i, copyDests);
		}
		final int emptyMapsNeeded = bundlesToCopy.entrySet().stream().mapToInt(
				e -> getNumStored(bundles[e.getKey()].getOccupancy())*e.getValue().size()).sum();
		if(totalEmptyMaps < emptyMapsNeeded){
			MinecraftClient.getInstance().player.sendMessage(Text.of("Insufficient empty maps"), true);
			Main.LOGGER.warn("MapCopyBundle: Insufficient empty maps");
			return;
		}
		if(bundlesToCopy.isEmpty()){Main.LOGGER.warn("MapCopyBundle: No bundles found to copy"); return;}

		HashSet<Integer> unusedBundles = new HashSet<Integer>(slotsWithBundles.length);
		for(int i=0; i<slotsWithBundles.length; ++i) unusedBundles.add(i);
		for(var e : bundlesToCopy.entrySet()){unusedBundles.remove(e.getKey()); unusedBundles.removeAll(e.getValue());}
		assert unusedBundles.size() >= 1;

		final int[] unusedBundleSlots = unusedBundles.stream().mapToInt(Integer::intValue).map(i -> slotsWithBundles[i]).toArray();
		final boolean anyUnnamedDst = bundlesToCopy.values().stream().anyMatch(d -> d.stream().anyMatch(i -> slots[slotsWithBundles[i]].getCustomName() == null));
		final PrioAndSlot pas = Arrays.stream(unusedBundleSlots).mapToObj(i ->
			// Lower score is better
			new PrioAndSlot(
					(!anyUnnamedDst && slots[i].getCustomName() != null ? 4 : 0)
					+ (srcBundleTypes.contains(slots[i].getItem()) ? 2 : 0)
					+ (dstBundleTypes.contains(slots[i].getItem()) ? 1 : 0), i)).min(Comparator.naturalOrder()).get();
		Main.LOGGER.info("MapCopyBundle: Intermediary bundle: slot="+pas.slot+", uniquelyUnnamed="+((pas.p&4)==0)
				+", uniqueFromSrcType="+((pas.p&2)==0)+", uniqueFromDstType="+((pas.p&1)==0));

		final int tempBundleSlot = pas.slot;
		for(var entry : bundlesToCopy.entrySet()){
//			Main.LOGGER.info("MapCopyBundle: Copying map bundle in slot "+k+", "+slots[k].getName().getString()+" to slots: "+bundlesToCopy.get(k));
			for(int _0=0; _0<bundles[entry.getKey()].size(); ++_0){
				clicks.add(new ClickEvent(slotsWithBundles[entry.getKey()], 1, SlotActionType.PICKUP)); // Take last map from src bundle
				clicks.add(new ClickEvent(tempBundleSlot, 0, SlotActionType.PICKUP)); // Place map in temp bundle
			}
//			Main.LOGGER.info("MapCopyBundle: Move to intermediary bundle complete, beginning copy");

			//2+4+2 vs 2+3+2
			//bundles[k].stream().mapToInt(stack -> stack.getCount()).forEach(count -> {
			for(int i=0; i<bundles[entry.getKey()].size(); ++i){
				final int count = bundles[entry.getKey()].get(i).getCount();

				clicks.add(new ClickEvent(tempBundleSlot, 1, SlotActionType.PICKUP)); // Take last map from temp bundle
				clicks.add(new ClickEvent(f.INPUT_START, 0, SlotActionType.PICKUP)); // Place in crafter
				boolean didShiftCraft = false;
				//Main.LOGGER.info("MapCopyBundle: Coping map item into "+bundlesToCopy.get(k).size()+" dest bundles");
				for(int d : entry.getValue()){
					if(numEmptyMapsInGrid < count){
//						Main.LOGGER.info("restocking empty maps at least: "+count+" (curr: "+numEmptyMapsInGrid+")");
						numEmptyMapsInGrid = getEmptyMapsIntoInput(clicks, slots, f, count, numEmptyMapsInGrid, /*dontLeaveEmptySlotsAfterThisSlot=*/99);
						LAST_EMPTY_SLOT = lastEmptySlot(slots, f.HOTBAR_END, f.INV_START);
//						Main.LOGGER.info("numEmptyMapsInGrid after restock: "+numEmptyMapsInGrid);
					}
					numEmptyMapsInGrid -= count;
					if(didShiftCraft){
						if(LAST_EMPTY_SLOT >= f.HOTBAR_START){
//							Main.LOGGER.info("swap into crafter: "+LAST_EMPTY_SLOT+"->"+(INPUT_START+1));
							clicks.add(new ClickEvent(f.INPUT_START, LAST_EMPTY_SLOT-f.HOTBAR_START, SlotActionType.SWAP)); // Swap into crafter
						}
						else{
							clicks.add(new ClickEvent(LAST_EMPTY_SLOT, 0, SlotActionType.PICKUP)); // Pickup all
							clicks.add(new ClickEvent(f.INPUT_START, 0, SlotActionType.PICKUP)); // Place in crafter
						}
					}
					if(LAST_EMPTY_SLOT != -1 && (count > 1 || d == entry.getValue().getLast())){
						didShiftCraft = true;
//						Main.LOGGER.info("MapCopyBundle: shift-craft into last-empty-slot: "+LAST_EMPTY_SLOT);
						clicks.add(new ClickEvent(f.RESULT, 0, SlotActionType.QUICK_MOVE)); // Move all from crafters
						clicks.add(new ClickEvent(LAST_EMPTY_SLOT, 1, SlotActionType.PICKUP)); // Pickup half
						clicks.add(new ClickEvent(slotsWithBundles[d], 0, SlotActionType.PICKUP)); // Put half in dest bundle
					}
					else{
						didShiftCraft = false;
//						Main.LOGGER.info("MapCopyBundle: normal-craft");
						clicks.add(new ClickEvent(f.RESULT, 0, SlotActionType.PICKUP)); // Take from crafter output
						clicks.add(new ClickEvent(f.INPUT_START, 0, SlotActionType.PICKUP)); // Place back in crafter input
						clicks.add(new ClickEvent(f.INPUT_START, 1, SlotActionType.PICKUP)); // Pickup half
						clicks.add(new ClickEvent(slotsWithBundles[d], 0, SlotActionType.PICKUP)); // Put half in dest bundle
					}
				}//for(dest bundles)
				final int fromSlot = didShiftCraft ? LAST_EMPTY_SLOT : f.INPUT_START;
//				Main.LOGGER.info("MapCopyBundle: Putting map item back into src bundle ("+fromSlot+"->"+slotsWithBundles[entry.getKey()]+")");
				clicks.add(new ClickEvent(fromSlot, 0, SlotActionType.PICKUP)); // Pickup all
				clicks.add(new ClickEvent(slotsWithBundles[entry.getKey()], 0, SlotActionType.PICKUP)); // Place back in src bundle
			}//for(item in src bundle)
		}//for(src bundle)
		if(numEmptyMapsInGrid > 0) clicks.add(new ClickEvent(f.INPUT_START+1, 0, SlotActionType.QUICK_MOVE));

		//Main.LOGGER.info("MapCopyBundle: STARTED");
		Main.clickUtils.executeClicks(clicks, _0->true, ()->Main.LOGGER.info("MapCopyBundle: DONE"));
	}

	private boolean isMapArtBundle(ItemStack stack){
		BundleContentsComponent contents = stack.get(DataComponentTypes.BUNDLE_CONTENTS);
		return contents != null && !contents.isEmpty() && contents.stream().allMatch(s -> s.getItem() == Items.FILLED_MAP);
	}

	@SuppressWarnings("unused")
	public void copyMapArtInInventory(){
		if(Main.clickUtils.hasOngoingClicks()){Main.LOGGER.warn("MapCopy: Already ongoing"); return;}
		//
		MinecraftClient client = MinecraftClient.getInstance();
		final boolean isCrafter = client.currentScreen instanceof CraftingScreen;
		final boolean isCartographyTable = client.currentScreen instanceof CartographyTableScreen;
		if(!(client.currentScreen instanceof InventoryScreen || isCrafter || isCartographyTable)){
			Main.LOGGER.warn("MapCopy: not in InventoryScreen/CraftingScreen/CartographyTableScreen");
			return;
		}
		final long ts = System.currentTimeMillis();
		if(ts - lastCopy < copyCooldown){Main.LOGGER.warn("MapCopy: In cooldown"); return;}
		lastCopy = ts;
		//
		final ScreenHandler xsh = ((HandledScreen<?>)client.currentScreen).getScreenHandler();
		final ItemStack[] slots = xsh.slots.stream().map(Slot::getStack).toArray(ItemStack[]::new);
		//for(int i=0; i<xsh.slots.size(); ++i) slots[i] = xsh.slots.get(i).getStack();

		// Ensure cursor is clear
		final int syncId = xsh.syncId;
		final ArrayDeque<ClickEvent> clicks = new ArrayDeque<>();
		if(!xsh.getCursorStack().isEmpty()){
			Main.LOGGER.warn("MapCopy: Cursor needs to be empty");
			//return;
			final OptionalInt emptySlot = IntStream.range(0, slots.length).filter(i -> slots[i].isEmpty()).findAny();
			if(emptySlot.isEmpty()) return;
			clicks.add(new ClickEvent(emptySlot.getAsInt(), 0, SlotActionType.PICKUP)); // Place stack from cursor
			slots[emptySlot.getAsInt()] = xsh.getCursorStack();
		}

		final ConstFields f = isCrafter ? CRAFTER : isCartographyTable ? CARTO : INV;

		// Ensure crafting 2x2 (or 3x3) is clear or has 1 slot with blank_maps
		int numEmptyMapsInGrid = 0;
		for(int i=f.INPUT_START; i<f.INPUT_END; ++i){
			if(slots[i].isEmpty()) continue;
			if(slots[i].getItem() != Items.MAP){
				Main.LOGGER.warn("MapCopy: Non-empty-map item in crafting grid");
				if(!simShiftClick(clicks, slots, i, f)) return;
				clicks.add(new ClickEvent(i, 0, SlotActionType.QUICK_MOVE));
			}
			else if(i != f.INPUT_START+1){
				Main.LOGGER.warn("MapCopy: Empty map already in crafting grid, and isn't in 2nd slot");
				if(!simShiftClick(clicks, slots, i, f)) return;
				clicks.add(new ClickEvent(i, 0, SlotActionType.QUICK_MOVE));
			}
			else numEmptyMapsInGrid = slots[i].getCount();
		}

		// Verify we have at least SOME empty maps for copying
		int lastEmptyMapSlot = -1;
		for(int i=slots.length-1; i>=f.INV_START; --i) if(slots[i].getItem() == Items.MAP){lastEmptyMapSlot = i; break;}
		if(lastEmptyMapSlot == -1){Main.LOGGER.warn("MapCopy: No empty maps found"); return;}

		// Figure out how many usable empty maps we have
		final int totalEmptyMaps = IntStream.rangeClosed(f.INV_START, lastEmptyMapSlot)
				.filter(i -> slots[i].getItem() == Items.MAP).map(i -> slots[i].getCount()).sum();

		// Decide which maps to copy (the ones with fewest copies) and how many (to match the next fewest)
		int minMapCount = 65, secondMinMapCount = 65, firstSlotToCopy = -1;
		for(int i=f.INV_START; i<f.HOTBAR_END; ++i){
			if(slots[i].getItem() != Items.FILLED_MAP) continue;
			if(slots[i].getCount() < minMapCount){
				secondMinMapCount = minMapCount;
				minMapCount = slots[i].getCount();
				firstSlotToCopy = i;
			}
		}

		// Little trick:
		// If we only care about relative positions, and we can fit them all into one input slot, and we do it BEFORE we start copying,
		// we can take full slot(s) of empty maps from indices AFTER firstSlotToCopy
		if(!PRESERVE_EXACT_POS && numEmptyMapsInGrid < 64)
		for(int i=firstSlotToCopy+1; i<lastEmptyMapSlot; ++i){
			if(slots[i].getItem() != Items.MAP) continue;
			if(numEmptyMapsInGrid + slots[i].getCount() > 64) continue;
			numEmptyMapsInGrid += slots[i].getCount();
			if(isCartographyTable) clicks.add(new ClickEvent(i, 0, SlotActionType.QUICK_MOVE));
			else if(i >= f.HOTBAR_START) clicks.add(new ClickEvent(f.INPUT_START+1, i-f.HOTBAR_START, SlotActionType.SWAP));
			else{
				clicks.add(new ClickEvent(i, 0, SlotActionType.PICKUP));
				clicks.add(new ClickEvent(f.INPUT_START+1, 0, SlotActionType.PICKUP));
			}
			slots[i] = ItemStack.EMPTY;
		}

		if(minMapCount >= 64){
			if(minMapCount == 65 && Arrays.stream(slots).anyMatch(this::isMapArtBundle)){
				copyMapArtInBundles(clicks, slots, f, numEmptyMapsInGrid, totalEmptyMaps);
				return;
			}
			Main.LOGGER.warn("MapCopy: No maps found which need copying!");
			return;
		}
		secondMinMapCount = Math.min(secondMinMapCount, minMapCount*2);

		// Figure out how many of the total empty maps we can actually use when copying
		final int availableEmptyMaps = totalEmptyMaps - (!PRESERVE_EXACT_POS ?
			(int)IntStream.rangeClosed(firstSlotToCopy+1, lastEmptyMapSlot).filter(i -> slots[i].getItem() == Items.MAP).count() : 0);

		// Figure out how many maps we need to copy
//		final long numSlotsToCopy = IntStream.range(INV_START, HOTBAR_END)
//				.filter(i -> slots[i].getItem() == Items.FILLED_MAP && slots[i].getCount() == minMapCount).count();
		int numSlotsToCopy = 0;
		for(int i=f.INV_START; i<f.HOTBAR_END; ++i) if(slots[i].getItem() == Items.FILLED_MAP && slots[i].getCount() == minMapCount) ++numSlotsToCopy;

		// Ensure we have enough empty maps to copy everything
		final int emptyMapsPerCopy = secondMinMapCount - minMapCount;
		if(availableEmptyMaps < numSlotsToCopy*emptyMapsPerCopy){
			Main.LOGGER.warn("MapCopy: Insufficient empty maps (have:"+availableEmptyMaps+",need:"+numSlotsToCopy*emptyMapsPerCopy+")");
			client.player.sendMessage(Text.of("Insufficient empty maps"), true);
			return;
		}

		final boolean copyAll = minMapCount == emptyMapsPerCopy;//=minMapCount*2 == secondMinMapCount; // Equivalent
		// minMapCount == 1 implies copyAll
		final boolean pickupHalf = minMapCount > 1 && (minMapCount+1)/2 >= emptyMapsPerCopy;
		final int amtPickedUp = pickupHalf ? (minMapCount+1)/2 : minMapCount;
		//Copy 5 of 32:
		// a) leave behind x=27, b) copy y=5
		//Copy 30 of 32:
		// a) leave behind x=2, b) copy y=30
		//Copy 16 of 32:
		// a) leave behind 16, b) copy 16
		//Clicks:
		// a) 1:pickup, x:putback, 1:to_crafter, 1:shift-craft = 3+x
		// b) 1:pickup, y:to_crafter, 1:putback, 1:shift-craft = 3+y
		final boolean moveExactToCrafter = amtPickedUp == emptyMapsPerCopy || (amtPickedUp-emptyMapsPerCopy <= emptyMapsPerCopy);
		final boolean leftoversInSlot = moveExactToCrafter && minMapCount > emptyMapsPerCopy;

		// Execute copy operations
		IdentityHashMap<ClickEvent, Integer> reserveClicks = new IdentityHashMap<>();
		Main.LOGGER.info("MapCopy: Starting copy, item.count "+minMapCount+" -> "+secondMinMapCount+". leftover: "+leftoversInSlot);
		for(int i=f.HOTBAR_END-1; i>=f.INV_START; --i){
			if(slots[i].getItem() != Items.FILLED_MAP) continue;
			if(slots[i].getCount() != minMapCount) continue;

			final int clicksAtStart = clicks.size();
			final ClickEvent firstClick;
			// Move filled map(s) to crafter input
			if(copyAll && i >= f.HOTBAR_START) clicks.add(firstClick=new ClickEvent(f.INPUT_START, i-f.HOTBAR_START, SlotActionType.SWAP));
			else if(copyAll && f != INV) clicks.add(firstClick=new ClickEvent(i, 0, SlotActionType.QUICK_MOVE));
			else{
				clicks.add(firstClick=new ClickEvent(i, pickupHalf ? 1 : 0, SlotActionType.PICKUP)); // Pickup all or half
				if(moveExactToCrafter) for(int j=emptyMapsPerCopy; j<amtPickedUp; ++j) clicks.add(new ClickEvent(i, 1, SlotActionType.PICKUP)); // Put back one
				clicks.add(new ClickEvent(f.INPUT_START, 0, SlotActionType.PICKUP)); // Place all
			}

			// Restock empty maps as needed
			if(numEmptyMapsInGrid < emptyMapsPerCopy){
				final int dontLeaveEmptySlotsAfter = PRESERVE_EXACT_POS ? f.HOTBAR_END : firstSlotToCopy;
				numEmptyMapsInGrid = getEmptyMapsIntoInput(clicks, slots, f, emptyMapsPerCopy, numEmptyMapsInGrid, dontLeaveEmptySlotsAfter);
			}
			numEmptyMapsInGrid -= emptyMapsPerCopy; // Deduct empty maps

			// Execute copy
			if(leftoversInSlot || (moveExactToCrafter && lastEmptySlot(slots, f.HOTBAR_END, f.INV_START) < i)){
//				Main.LOGGER.info("MapCopy: EzPz shift-click output");
				clicks.add(new ClickEvent(f.RESULT, 0, SlotActionType.QUICK_MOVE)); // Move ALL maps from crafter output
			}
			else if(moveExactToCrafter){
				if(i >= f.HOTBAR_START){
//					Main.LOGGER.info("MapCopy: Swap 1 from output"+(minMapCount>1?", then shift-click":"")+" (hb:"+(i>=f.HOTBAR_START)+")");
					clicks.add(new ClickEvent(f.RESULT, i-f.HOTBAR_START, SlotActionType.SWAP)); // Swap 1 map from crafter output
				}
				else{
//					Main.LOGGER.info("MapCopy: Pickup-place 1 from output"+(minMapCount>1?", then shift-click":""));
					clicks.add(new ClickEvent(f.RESULT, 0, SlotActionType.PICKUP)); // Pickup ONE map from crafter output
					clicks.add(new ClickEvent(i, 0, SlotActionType.PICKUP)); // Place back in source slot
				}
				// Equivalent: emptyMapsToCopy > 1
				if(minMapCount > 1) clicks.add(new ClickEvent(f.RESULT, 0, SlotActionType.QUICK_MOVE)); // Move ALL maps from crafter output
			}
			else{
//				Main.LOGGER.info("MapCopy: Move "+emptyMapsPerCopy+" from output, then shift-click leftover inputs");
				for(int j=0; j<emptyMapsPerCopy; ++j) clicks.add(new ClickEvent(f.RESULT, 0, SlotActionType.PICKUP)); // Pickup ONE map from crafter output
				clicks.add(new ClickEvent(i, 0, SlotActionType.PICKUP)); // Place back in source slot
				clicks.add(new ClickEvent(f.INPUT_START, 0, SlotActionType.QUICK_MOVE)); // Move back leftover input maps
			}
			final int clicksUsed = clicks.size() - clicksAtStart;
			if(clicksUsed <= Main.clickUtils.MAX_CLICKS) reserveClicks.put(firstClick, clicksUsed);
		}// copy maps

		// Seems like copy followed by bundle stow causes all empty maps in inv to get tossed?? Well, adding this final click fixes it apparently
		if(numEmptyMapsInGrid > 0) clicks.add(new ClickEvent(f.INPUT_START+1, 0, SlotActionType.QUICK_MOVE)); 

		//Main.LOGGER.info("MapCopy: STARTED");
		Main.clickUtils.executeClicks(clicks,
			c->{
				// Don't start individual copy operation unless we can fully knock it out (unless impossible to do in 1 go)
				final Integer clicksNeeded = reserveClicks.get(c);
				if(clicksNeeded == null || clicksNeeded <= Main.clickUtils.calcAvailableClicks()) return true;
				return false; // Wait for clicks
			},
			()->{
				Main.LOGGER.info("MapCopy: DONE");
			}
		);
	}

//	public KeybindMapCopy(){
//		new Keybind("mapart_copy", ()->copyMapArtInInventory(), s->s instanceof InventoryScreen || s instanceof CraftingScreen, GLFW.GLFW_KEY_T);
//	}
}