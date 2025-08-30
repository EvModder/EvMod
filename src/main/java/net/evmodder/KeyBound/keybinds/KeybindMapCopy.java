package net.evmodder.KeyBound.keybinds;

import net.evmodder.KeyBound.Main;
import net.evmodder.KeyBound.keybinds.ClickUtils.ClickEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import org.apache.commons.lang3.math.Fraction;
import org.lwjgl.glfw.GLFW;

public final class KeybindMapCopy{
	private long lastCopy;
	private final long copyCooldown = 250l;
	private final boolean PRESERVE_EXACT_POS = true;
	private final ItemStack EMPTY_ITEM = new ItemStack(Items.AIR);

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
	private final boolean simShiftClick(final ArrayDeque<ClickEvent> clicks, final ItemStack[] slots, final int i, final boolean isCrafter){
		if(slots[i].isEmpty()){Main.LOGGER.warn("MapCopy: simShiftClick() called for an empty slot"); return true;}
		final int CRAFT_RESULT = 0;
		final int INPUT_START = 1, INPUT_END = isCrafter ? 10 : 5;
		final int INV_START = isCrafter ? 10 : 9, INV_END = isCrafter ? 37 : 36;
		final int HOTBAR_START = isCrafter ? 37 : 36, HOTBAR_END = isCrafter ? 46 : 45;

		// Goes to BR
		if(i == CRAFT_RESULT){
			for(int j=HOTBAR_END-1; j>=INV_START; --j) if(slots[j].isEmpty()){swap(slots, i, j); return true;} // BR inv + hotbar
			return false;
		}
		// Goes to TL
		int START = -1, END = -1;
		if(i >= INPUT_START && i < INPUT_END){START = INV_START; END = HOTBAR_END;} // -> TL inv + hotbar
		if(isCrafter && i >= INV_START && i < HOTBAR_END){START = INPUT_START; END = INPUT_END;} // -> TL crafter
		if(!isCrafter && i >= INV_START && i < INV_END){START = HOTBAR_START; END = HOTBAR_END;} // -> TL hotbar
		if(!isCrafter && i >= HOTBAR_START && i < HOTBAR_END){START = INV_START; END = INV_END;} // -> TL inv
		if(START == -1){
			Main.LOGGER.error("MapCopy: simShiftClick() with an unsupported slot index! "+i);
			return false;
		}
		for(int j=START; j<END; ++j) if(slots[j].isEmpty()){swap(slots, i, j); return true;}

		Main.LOGGER.error("MapCopy: simShiftClick() failed due to no available destination slots! "+i);
		return false;
	}

	private final int getEmptyMapsIntoInput(final ArrayDeque<ClickEvent> clicks, final ItemStack[] slots, final boolean isCrafter,
			final int amtNeeded, int amtInGrid, final int dontLeaveEmptySlotsAfterThisSlot){
		final int INPUT_START = 1;
		final int INV_START = isCrafter ? 10 : 9;
		final int HOTBAR_START = isCrafter ? 37 : 36, HOTBAR_END = isCrafter ? 46 : 45;

		// Restock empty maps as needed
		for(int j=INV_START; j<HOTBAR_END && amtInGrid < amtNeeded; ++j){
			if(slots[j].getItem() != Items.MAP) continue;
			final boolean leaveOne = j > dontLeaveEmptySlotsAfterThisSlot;
			if(leaveOne && slots[j].getCount() == 1) continue;

			final int combinedCnt = slots[j].getCount() + amtInGrid;
			final int combinedHalfCnt = (slots[j].getCount()+1)/2 + amtInGrid;

			if(j >= HOTBAR_START && (!leaveOne || amtInGrid > 0) && slots[j].getCount() >= amtNeeded){
				clicks.add(new ClickEvent(INPUT_START, j-HOTBAR_START, SlotActionType.SWAP));
				amtInGrid = slots[j].getCount();
				slots[j].setCount(combinedCnt-amtInGrid); // Effectively swap the counts
				break;
			}
			else if(isCrafter && !leaveOne && combinedCnt <= 64){
				clicks.add(new ClickEvent(j, 0, SlotActionType.QUICK_MOVE)); // Move all to input
				slots[j] = EMPTY_ITEM;
				amtInGrid = combinedCnt;
			}
			else if(!leaveOne && combinedCnt <= 64){
				clicks.add(new ClickEvent(j, 0, SlotActionType.PICKUP)); // Pickup all
				clicks.add(new ClickEvent(INPUT_START, 0, SlotActionType.PICKUP)); // Place all in input
				slots[j] = EMPTY_ITEM;
				amtInGrid = combinedCnt;
			}
			else if(slots[j].getCount() > 1 && combinedHalfCnt >= amtNeeded && combinedHalfCnt <= 64){
				clicks.add(new ClickEvent(j, 1, SlotActionType.PICKUP)); // Pickup half
				clicks.add(new ClickEvent(INPUT_START, 0, SlotActionType.PICKUP)); // Place all in input
				slots[j].setCount(slots[j].getCount()/2);
				amtInGrid = combinedHalfCnt;
			}
			else if(!leaveOne || combinedCnt > 64 ){
//				clicks.add(new ClickEvent(j, 0, SlotActionType.QUICK_MOVE)); // Move all to input + overflow
//				clicks.add(new ClickEvent(INPUT_START+1, 0, SlotActionType.QUICK_MOVE)); // Move back overflow
//				ItemStack temp = slots[j];
//				slots[j] = EMPTY_ITEM;
//				temp.setCount(combinedCnt - 64);
//				slots[lastEmptySlot(slots, HOTBAR_END, INV_START)] = temp;
//				amtInGrid = 64;
				clicks.add(new ClickEvent(j, 0, SlotActionType.PICKUP)); // Pickup all
				clicks.add(new ClickEvent(INPUT_START, 0, SlotActionType.PICKUP)); // Place in input
				clicks.add(new ClickEvent(j, 0, SlotActionType.PICKUP)); // Putback leftovers
				slots[j].setCount(combinedCnt - 64);
				amtInGrid = 64;
			}
			else{
				clicks.add(new ClickEvent(j, 0, SlotActionType.PICKUP)); // Pickup all
				clicks.add(new ClickEvent(j, 1, SlotActionType.PICKUP)); // Putback one
				clicks.add(new ClickEvent(INPUT_START, 0, SlotActionType.PICKUP)); // Place all in input
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
	private void copyMapArtInBundles(final ArrayDeque<ClickEvent> clicks, final ItemStack[] slots, final boolean isCrafter,
			int numEmptyMapsInGrid, final int totalEmptyMaps){
		final int INPUT_START = 1/*, INPUT_END = isCrafter ? 10 : 5*/;
		final int INV_START = isCrafter ? 10 : 9;
		final int /*HOTBAR_START = isCrafter ? 37 : 36, */HOTBAR_END = isCrafter ? 46 : 45;
		final int[] slotsWithBundles = IntStream.range(INV_START, HOTBAR_END).filter(i -> {
			BundleContentsComponent contents = slots[i].get(DataComponentTypes.BUNDLE_CONTENTS);
			return contents != null && contents.stream().allMatch(s -> s.getItem() == Items.FILLED_MAP);
		}).toArray();
		final BundleContentsComponent[] bundles = Arrays.stream(slotsWithBundles)
				.mapToObj(i -> slots[i].get(DataComponentTypes.BUNDLE_CONTENTS)).toArray(BundleContentsComponent[]::new);
		final int SRC_BUNDLES = (int)Arrays.stream(bundles).filter(Predicate.not(BundleContentsComponent::isEmpty)).count();
		int emptyBundles = bundles.length - SRC_BUNDLES;

		HashMap<Integer, List<Integer>> bundlesToCopy = new HashMap<>(); // source bundle -> destination bundles (slotsWithBundles)
//		int storageBundles = 0;
		int emptyMapsNeeded = 0;
//		boolean multiMapCopy = false;
		for(int i=0; i<slotsWithBundles.length; ++i){
			final int s1 = slotsWithBundles[i];
			if(bundles[i].isEmpty()) continue;
			ArrayList<Integer> copyDests = new ArrayList<>();
//			++storageBundles;
			final String name1 = slots[s1].getCustomName() == null ? null : slots[s1].getCustomName().getString();
//			Main.LOGGER.info("looking for dest bundles for "+slots[s1].getName().getString()+" in slot "+s1);
			for(int j=0; j<slotsWithBundles.length && emptyBundles>1; ++j){
				final int s2 = slotsWithBundles[j];
				if(!bundles[j].isEmpty()) continue;
//				Main.LOGGER.info("empty bundle in slot "+s2);
				if(name1 != null){
					final String name2 = slots[s2].getCustomName() == null ? null : slots[s2].getCustomName().getString();
					if(name1.equals(name2));// copyDests.add(s2);--emptyBundles;
					else continue;
				}
				else if(slots[s1].getItem() == slots[s2].getItem());// copyDests.add(s2);--emptyBundles;
				else if(SRC_BUNDLES == 1);// copyDests.add(s2);--emptyBundles;
				else continue;
				copyDests.add(j);
				--emptyBundles;
			}
			if(copyDests.isEmpty()){
				if(emptyBundles == 1) Main.LOGGER.warn("MapCopyBundle: Could not find an auxiliary bundle");
				else Main.LOGGER.warn("MapCopyBundle: Could not determine destination bundles");
				return;
			}
//			storageBundles += copyDests.size();
			bundlesToCopy.put(i, copyDests);
			emptyMapsNeeded += getNumStored(bundles[i].getOccupancy())*copyDests.size();
//			multiMapCopy |= contents.stream().anyMatch(s -> s.getCount() > 1);
		}
		if(totalEmptyMaps < emptyMapsNeeded){
			MinecraftClient.getInstance().player.sendMessage(Text.of("Insufficient empty maps"), true);
			Main.LOGGER.warn("MapCopyBundle: Insufficient empty maps");
			return;
		}
		if(bundlesToCopy.isEmpty()){Main.LOGGER.warn("MapCopyBundle: No bundles found to copy"); return;}
//		if(storageBundles == slotsWithBundles.length){Main.LOGGER.warn("MapCopyBundle: Could not find an auxiliary bundle"); return;}

		HashSet<Integer> unusedBundles = new HashSet<Integer>(slotsWithBundles.length);
		for(int i=0; i<slotsWithBundles.length; ++i) unusedBundles.add(i);
		for(var e : bundlesToCopy.entrySet()){unusedBundles.remove(e.getKey()); unusedBundles.removeAll(e.getValue());}
		assert unusedBundles.size() >= 1;

		final int tempBundleSlot = unusedBundles.stream().mapToInt(Integer::intValue).map(i -> slotsWithBundles[i]).min().getAsInt();
		Main.LOGGER.info("MapCopyBundle: Intermediary bundle slot "+tempBundleSlot+", "+slots[tempBundleSlot].getName().getString());

		for(var entry : bundlesToCopy.entrySet()){
//			Main.LOGGER.info("MapCopyBundle: Copying map bundle in slot "+k+", "+slots[k].getName().getString()+" to slots: "+bundlesToCopy.get(k));
			for(int _0=0; _0<bundles[entry.getKey()].size(); ++_0){
				clicks.add(new ClickEvent(slotsWithBundles[entry.getKey()], 1, SlotActionType.PICKUP)); // Take last map from src bundle
				clicks.add(new ClickEvent(tempBundleSlot, 0, SlotActionType.PICKUP)); // Place map in temp bundle
			}
//			Main.LOGGER.info("MapCopyBundle: Move to intermediary bundle complete, beginning copy");

			//bundles[k].stream().mapToInt(stack -> stack.getCount()).forEach(count -> {
			for(int i=0; i<bundles[entry.getKey()].size(); ++i){
				final int count = bundles[entry.getKey()].get(i).getCount();

				clicks.add(new ClickEvent(tempBundleSlot, 1, SlotActionType.PICKUP)); // Take last map from temp bundle
				clicks.add(new ClickEvent(INPUT_START+1, 0, SlotActionType.PICKUP)); // Place in crafter
				int leftoverMapSlot = INPUT_START+1;
				//Main.LOGGER.info("MapCopyBundle: Coping map item into "+bundlesToCopy.get(k).size()+" dest bundles");
				for(int d : entry.getValue()){
					if(numEmptyMapsInGrid < count){
//						Main.LOGGER.info("restocking empty maps at least: "+count+" (curr: "+numEmptyMapsInGrid+")");
						numEmptyMapsInGrid = getEmptyMapsIntoInput(clicks, slots, isCrafter, count, numEmptyMapsInGrid, HOTBAR_END);
//						Main.LOGGER.info("numEmptyMapsInGrid after restock: "+numEmptyMapsInGrid);
					}
					numEmptyMapsInGrid -= count;
					if(count > 1){
						if(leftoverMapSlot != INPUT_START+1){
							clicks.add(new ClickEvent(leftoverMapSlot, 0, SlotActionType.PICKUP)); // Pickup all
							clicks.add(new ClickEvent(INPUT_START+1, 0, SlotActionType.PICKUP)); // Place in crafter
						}
						leftoverMapSlot = lastEmptySlot(slots, HOTBAR_END, INV_START);
						clicks.add(new ClickEvent(0, 0, SlotActionType.QUICK_MOVE)); // Move all from crafters
						clicks.add(new ClickEvent(leftoverMapSlot, 1, SlotActionType.PICKUP)); // Pickup half
						clicks.add(new ClickEvent(slotsWithBundles[d], 0, SlotActionType.PICKUP)); // Put half in dest bundle
					}
					else{
						clicks.add(new ClickEvent(0, 0, SlotActionType.PICKUP)); // Take from crafter output
						clicks.add(new ClickEvent(INPUT_START+1, 0, SlotActionType.PICKUP)); // Place back in crafter input
						clicks.add(new ClickEvent(INPUT_START+1, 1, SlotActionType.PICKUP)); // Pickup half
						clicks.add(new ClickEvent(slotsWithBundles[d], 0, SlotActionType.PICKUP)); // Put half in dest bundle
					}
				}//for(dest bundles)
				//Main.LOGGER.info("MapCopyBundle: Putting map item back into src bundle");
				clicks.add(new ClickEvent(leftoverMapSlot, 0, SlotActionType.PICKUP)); // Pickup all
				clicks.add(new ClickEvent(slotsWithBundles[entry.getKey()], 0, SlotActionType.PICKUP)); // Place back in src bundle
			}//for(item in src bundle)
		}//for(src bundle)
		if(numEmptyMapsInGrid > 0) clicks.add(new ClickEvent(INPUT_START, 0, SlotActionType.QUICK_MOVE));

		//Main.LOGGER.info("MapCopyBundle: STARTED");
		Main.clickUtils.executeClicks(clicks, _0->true, ()->Main.LOGGER.info("MapCopyBundle: DONE"));
	}

	@SuppressWarnings("unused")
	private void copyMapArtInInventory(){
		if(Main.clickUtils.hasOngoingClicks()){Main.LOGGER.warn("MapCopy: Already ongoing"); return;}
		//
		MinecraftClient client = MinecraftClient.getInstance();
		final boolean isCrafter = client.currentScreen instanceof CraftingScreen;
		if(!(client.currentScreen instanceof InventoryScreen || isCrafter)){
			Main.LOGGER.warn("MapCopy: not in InventoryScreen or CraftingScreen");
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

		//PlayerScreenHandler.CRAFTING_INPUT_START=1, CraftingScreenHandler.INPUT_START=1
		//PlayerScreenHandler.CRAFTING_INPUT_END=5, CraftingScreenHandler.INPUT_END=10
		//PlayerScreenHandler.HOTBAR_START=36, CraftingScreenHandler.HOTBAR_START=37
		final int INPUT_START = 1, INPUT_END = isCrafter ? 10 : 5;
		final int INV_START = isCrafter ? 10 : 9;
		final int HOTBAR_START = isCrafter ? 37 : 36, HOTBAR_END = isCrafter ? 46 : 45;

		// Ensure crafting 2x2 (or 3x3) is clear or has 1 slot with blank_maps
		int numEmptyMapsInGrid = 0;
		for(int i=INPUT_START; i<INPUT_END; ++i){
			if(slots[i].isEmpty()) continue;
			if(slots[i].getItem() != Items.MAP){
				Main.LOGGER.warn("MapCopy: Non-empty-map item in crafting grid");
				if(!simShiftClick(clicks, slots, i, isCrafter)) return;
				clicks.add(new ClickEvent(i, 0, SlotActionType.QUICK_MOVE));
			}
			else if(i != INPUT_START){
				Main.LOGGER.warn("MapCopy: Empty map already in crafting grid, and isn't in 1st slot");
				if(!simShiftClick(clicks, slots, i, isCrafter)) return;
				clicks.add(new ClickEvent(i, 0, SlotActionType.QUICK_MOVE));
			}
			else numEmptyMapsInGrid = slots[i].getCount();
		}

		// Verify we have at least SOME empty maps for copying
		int lastEmptyMapSlot = -1;
		for(int i=slots.length-1; i>=INV_START; --i) if(slots[i].getItem() == Items.MAP){lastEmptyMapSlot = i; break;}
		if(lastEmptyMapSlot == -1){Main.LOGGER.warn("MapCopy: No empty maps found"); return;}

		// Figure out how many usable empty maps we have
		final int totalEmptyMaps = IntStream.rangeClosed(INV_START, lastEmptyMapSlot)
				.filter(i -> slots[i].getItem() == Items.MAP).map(i -> slots[i].getCount()).sum();

		// Decide which maps to copy (the ones with fewest copies) and how many (to match the next fewest)
		int minMapCount = 65, secondMinMapCount = 65, firstSlotToCopy = -1;
		for(int i=INV_START; i<HOTBAR_END; ++i){
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
			if(isCrafter) clicks.add(new ClickEvent(i, 0, SlotActionType.QUICK_MOVE));
			else if(i >= HOTBAR_START) clicks.add(new ClickEvent(INPUT_START, i-HOTBAR_START, SlotActionType.SWAP));
			else{
				clicks.add(new ClickEvent(i, 0, SlotActionType.PICKUP));
				clicks.add(new ClickEvent(INPUT_START, 0, SlotActionType.PICKUP));
			}
			slots[i] = EMPTY_ITEM;
		}

		if(minMapCount >= 64){
			if(minMapCount == 65 && Arrays.stream(slots).anyMatch(s -> s.get(DataComponentTypes.BUNDLE_CONTENTS) != null)){
				copyMapArtInBundles(clicks, slots, isCrafter, numEmptyMapsInGrid, totalEmptyMaps);
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
		for(int i=INV_START; i<HOTBAR_END; ++i) if(slots[i].getItem() == Items.FILLED_MAP && slots[i].getCount() == minMapCount) ++numSlotsToCopy;

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
		for(int i=HOTBAR_END-1; i>=INV_START; --i){
			if(slots[i].getItem() != Items.FILLED_MAP) continue;
			if(slots[i].getCount() != minMapCount) continue;

			// Restock empty maps as needed
			if(numEmptyMapsInGrid < emptyMapsPerCopy){
				final int dontLeaveEmptySlotsAfter = PRESERVE_EXACT_POS ? HOTBAR_END : firstSlotToCopy;
				numEmptyMapsInGrid = getEmptyMapsIntoInput(clicks, slots, isCrafter, emptyMapsPerCopy, numEmptyMapsInGrid, dontLeaveEmptySlotsAfter);
			}

			final int clicksAtStart = clicks.size();
			final ClickEvent firstClick;
			// Move filled map(s) to crafter input
			if(copyAll && i >= HOTBAR_START) clicks.add(firstClick=new ClickEvent(INPUT_START+1, i-HOTBAR_START, SlotActionType.SWAP));
			else if(copyAll && isCrafter) clicks.add(firstClick=new ClickEvent(i, 0, SlotActionType.QUICK_MOVE));
			else{
				clicks.add(firstClick=new ClickEvent(i, pickupHalf ? 1 : 0, SlotActionType.PICKUP)); // Pickup all or half
				if(moveExactToCrafter) for(int j=emptyMapsPerCopy; j<amtPickedUp; ++j) clicks.add(new ClickEvent(i, 1, SlotActionType.PICKUP)); // Put back one
				clicks.add(new ClickEvent(INPUT_START+1, 0, SlotActionType.PICKUP)); // Place all
			}

			numEmptyMapsInGrid -= emptyMapsPerCopy; // Deduct empty maps

			if(leftoversInSlot || (moveExactToCrafter && lastEmptySlot(slots, HOTBAR_END, INV_START) < i)){
				Main.LOGGER.info("MapCopy: EzPz shift-click output");
				clicks.add(new ClickEvent(0, 0, SlotActionType.QUICK_MOVE)); // Move ALL maps from crafter output
			}
			else if(moveExactToCrafter){
				if(i >= HOTBAR_START){
					Main.LOGGER.info("MapCopy: Swap 1 from output"+(minMapCount>1?", then shift-click":"")+" (hb:"+(i>=HOTBAR_START)+")");
					clicks.add(new ClickEvent(0, i-HOTBAR_START, SlotActionType.SWAP)); // Swap 1 map from crafter output
				}
				else{
					Main.LOGGER.info("MapCopy: Pickup-place 1 from output"+(minMapCount>1?", then shift-click":""));
					clicks.add(new ClickEvent(0, 0, SlotActionType.PICKUP)); // Pickup ONE map from crafter output
					clicks.add(new ClickEvent(i, 0, SlotActionType.PICKUP)); // Place back in source slot
				}
				// Equivalent: emptyMapsToCopy > 1
				if(minMapCount > 1) clicks.add(new ClickEvent(0, 0, SlotActionType.QUICK_MOVE)); // Move ALL maps from crafter output
			}
			else{
				Main.LOGGER.info("MapCopy: Move "+emptyMapsPerCopy+" from output, then shift-click leftover inputs");
				for(int j=0; j<emptyMapsPerCopy; ++j) clicks.add(new ClickEvent(0, 0, SlotActionType.PICKUP)); // Pickup ONE map from crafter output
				clicks.add(new ClickEvent(i, 0, SlotActionType.PICKUP)); // Place back in source slot
				clicks.add(new ClickEvent(INPUT_START+1, 0, SlotActionType.QUICK_MOVE)); // Move back leftover input maps
			}
			final int clicksUsed = clicks.size() - clicksAtStart;
			if(clicksUsed <= Main.clickUtils.MAX_CLICKS) reserveClicks.put(firstClick, clicksUsed);
		}// copy maps

		if(numEmptyMapsInGrid > 0) clicks.add(new ClickEvent(INPUT_START, 0, SlotActionType.QUICK_MOVE));

		//Main.LOGGER.info("MapCopy: STARTED");
		Main.clickUtils.executeClicks(clicks,
			c->{
				// Don't start individual copy operation unless we can fully knock it out (unless impossible to do in 1 go)
				final Integer clicksNeeded = reserveClicks.get(c);
				if(clicksNeeded == null || clicksNeeded <= Main.clickUtils.MAX_CLICKS - Main.clickUtils.addClick(null)) return true;
				return false; // Wait for clicks
			},
			()->{
				Main.LOGGER.info("MapCopy: DONE");
			}
		);
	}

	public KeybindMapCopy(){
		new Keybind("mapart_copy", ()->copyMapArtInInventory(), s->s instanceof InventoryScreen || s instanceof CraftingScreen, GLFW.GLFW_KEY_T);
	}
}