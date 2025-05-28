package net.evmodder.KeyBound.Keybinds;

import net.evmodder.KeyBound.Main;
import net.evmodder.KeyBound.Keybinds.ClickUtils.ClickEvent;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.OptionalInt;
import java.util.stream.IntStream;

public final class KeybindMapCopy{
	private static boolean ongoingCopy;
	private static long lastCopy;
	private static final long copyCooldown = 250l;
	private final boolean PRESERVE_MAP_POS = true;
	private final ItemStack EMPTY_ITEM = new ItemStack(Items.AIR);

	// Shift-click results:
	// Shift-click in crafting input -> TL of inv
	// Shift-click in crafting output -> BR of inv
	// Shift-click in InventoryScreen -> TL hotbar <-> TL inv
	// Shift-click in CraftingScreen -> TL input
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

	@SuppressWarnings("unused")
	private void copyMapArtInInventory(){
		if(ongoingCopy){Main.LOGGER.warn("MapCopy: Already ongoing"); return;}
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
			clicks.add(new ClickEvent(syncId, emptySlot.getAsInt(), 0, SlotActionType.PICKUP)); // Place stack from cursor
		}

		//PlayerScreenHandler.CRAFTING_INPUT_START=1, CraftingScreenHandler.INPUT_START=1
		//PlayerScreenHandler.CRAFTING_INPUT_END=5, CraftingScreenHandler.INPUT_END=10
		//PlayerScreenHandler.HOTBAR_START=36, CraftingScreenHandler.HOTBAR_START=37
		final int INPUT_START = 1, INPUT_END = isCrafter ? 10 : 5;
		final int INV_START = isCrafter ? 10 : 9, INV_END = isCrafter ? 37 : 36;
		final int HOTBAR_START = isCrafter ? 37 : 36, HOTBAR_END = isCrafter ? 46 : 45;

		// Ensure crafting 2x2 (or 3x3) is clear or has 1 slot with blank_maps
		int numEmptyMapsInGrid = 0;
		for(int i=INPUT_START; i<INPUT_END; ++i){
			if(slots[i].isEmpty()) continue;
			if(slots[i].getItem() != Items.MAP){
				Main.LOGGER.warn("MapCopy: Non-empty-map item in crafting grid");
				if(!simShiftClick(clicks, slots, i, isCrafter)) return;
				clicks.add(new ClickEvent(syncId, i, 0, SlotActionType.QUICK_MOVE));
			}
			else if(i != INPUT_START){
				Main.LOGGER.warn("MapCopy: Empty map already in crafting grid, and isn't in 1st slot");
				if(!simShiftClick(clicks, slots, i, isCrafter)) return;
				clicks.add(new ClickEvent(syncId, i, 0, SlotActionType.QUICK_MOVE));
			}
			else numEmptyMapsInGrid = slots[i].getCount();
		}

		// Decide which maps to copy (the ones with fewest copies) and how many (to match the next fewest)
		int minMapCount = 64, secondMinMapCount = 64, firstSlotToCopy = -1;
		for(int i=INV_START; i<HOTBAR_END; ++i){
			if(slots[i].getItem() != Items.FILLED_MAP) continue;
			if(slots[i].getCount() < minMapCount){
				secondMinMapCount = minMapCount;
				minMapCount = slots[i].getCount();
				firstSlotToCopy = i;
			}
		}
		if(minMapCount == 64){
			Main.LOGGER.warn("MapCopy: No maps found which need copying!");
			return;
		}
		secondMinMapCount = Math.min(secondMinMapCount, minMapCount*2);

		// Figure out how many maps we need to copy
//		final long numSlotsToCopy = IntStream.range(INV_START, HOTBAR_END)
//				.filter(i -> slots[i].getItem() == Items.FILLED_MAP && slots[i].getCount() == minMapCount).count();
		int numSlotsToCopy = 0;
		for(int i=INV_START; i<INV_END; ++i) if(slots[i].getItem() == Items.FILLED_MAP && slots[i].getCount() == minMapCount) ++numSlotsToCopy;

		// Verify we have at least SOME empty maps for copying
		int lastEmptyMapSlot = -1;
		for(int i=slots.length-1; i>=INV_START; --i) if(slots[i].getItem() == Items.MAP){lastEmptyMapSlot = i; break;}
		if(lastEmptyMapSlot == -1){Main.LOGGER.warn("MapCopy: No empty maps found"); return;}

		// Figure out how many usable empty maps we have
		int availableEmptyMaps = 0;
		if(PRESERVE_MAP_POS) availableEmptyMaps = IntStream.rangeClosed(INV_START, lastEmptyMapSlot)
				.filter(i -> slots[i].getItem() == Items.MAP).map(i -> slots[i].getCount()).sum();
		else for(int i=INV_START; i<=lastEmptyMapSlot; ++i){
			if(slots[i].getItem() != Items.MAP) continue;
			// Due to use of shift-clicks when PRESERVE_POS=false, we need to leave 1 empty map in the slot to keep stuff in order during copy
			availableEmptyMaps += slots[i].getCount() - (i > firstSlotToCopy ? 1 : 0); 
		}
		// Ensure we have enough empty maps to copy everything
		final int emptyMapsPerCopy = secondMinMapCount - minMapCount;
		if(availableEmptyMaps < numSlotsToCopy*emptyMapsPerCopy){
			Main.LOGGER.warn("MapCopy: Insufficient empty maps");
			client.player.sendMessage(Text.of("Insufficient empty maps"), true);
			return;
		}

		// Little trick:
		// If we only care about relative positions, and we can fit them all into one input slot, and we do it BEFORE we start copying,
		// we can take full slot(s) of empty maps from indices AFTER firstSlotToCopy
		if(!PRESERVE_MAP_POS && numEmptyMapsInGrid < 64)
		for(int i=firstSlotToCopy+1; i<lastEmptyMapSlot; ++i){
			if(slots[i].getItem() != Items.MAP) continue;
			if(numEmptyMapsInGrid + slots[i].getCount() > 64) continue;
			numEmptyMapsInGrid += slots[i].getCount();
			slots[i] = EMPTY_ITEM;
		}

		// Necessary knowledge to determine if/when we can use Shift-clicks despite being in PRESERVE_POS mode 
		int lastEmptySlot = -1;
		if(PRESERVE_MAP_POS) for(int i=HOTBAR_END-1; i>=INV_START; --i) if(slots[i].isEmpty()){
			Main.LOGGER.info("MapCopy: last empty slot: "+i);
			lastEmptySlot = i;
			break;
		}

		// Execute copy operations
		HashMap<ClickEvent, Integer> reserveClicks = new HashMap<>();
		Main.LOGGER.info("MapCopy: Starting copy");
		for(int i=HOTBAR_END-1; i>=INV_START; --i){
			if(slots[i].getItem() != Items.FILLED_MAP) continue;
			if(slots[i].getCount() != minMapCount) continue;

			// Restock empty maps as needed
			if(numEmptyMapsInGrid < emptyMapsPerCopy) for(int j=INV_START; j<HOTBAR_END; ++j){
				if(slots[j].getItem() != Items.MAP) continue;
				final boolean leaveOne = j > firstSlotToCopy && !PRESERVE_MAP_POS;
				if(leaveOne && slots[j].getCount() == 1) continue;

				int combinedCount = numEmptyMapsInGrid + slots[j].getCount();

				if(j >= HOTBAR_START && !leaveOne && combinedCount <= 64) clicks.add(new ClickEvent(syncId, INPUT_START, j-HOTBAR_START, SlotActionType.SWAP));
				else if(isCrafter && !leaveOne) clicks.add(new ClickEvent(syncId, j, 0, SlotActionType.QUICK_MOVE));
				else{
					final boolean takeHalf = leaveOne && (slots[j].getCount() <= 3 || (slots[j].getCount()+1)/2 + numEmptyMapsInGrid >= 64);
					if(takeHalf) combinedCount -= slots[j].getCount()/2;
					clicks.add(new ClickEvent(syncId, j, takeHalf ? 1 : 0, SlotActionType.PICKUP)); // Pickup all or half
					if(leaveOne && !takeHalf) clicks.add(new ClickEvent(syncId, j, 1, SlotActionType.PICKUP)); // Place one
					clicks.add(new ClickEvent(syncId, INPUT_START, 0, SlotActionType.PICKUP)); // Place as many as possible in input
				}
				if(combinedCount <= 64){
					numEmptyMapsInGrid = combinedCount;
					slots[j] = EMPTY_ITEM;
				}
				else{
					if(isCrafter && !leaveOne){
						clicks.add(new ClickEvent(syncId, INPUT_START+1, 0, SlotActionType.QUICK_MOVE));
						for(int k=INV_START; k<HOTBAR_END; ++k) if(slots[j].isEmpty()){if(j != k) swap(slots, j, k); break;}
					}
					else{
						clicks.add(new ClickEvent(syncId, j, 0, SlotActionType.PICKUP)); // Put back leftovers
					}
					numEmptyMapsInGrid = 64;
					slots[j].setCount(combinedCount - 64);
				}// combinedCnt > maxCnt
				if(numEmptyMapsInGrid == 64) break;
			}// restock

			final int clicksAtStart = clicks.size();
			final ClickEvent firstClick;
			// Move filled map(s) to crafter input
			final boolean copyAll = minMapCount == emptyMapsPerCopy;//=minMapCount*2 == secondMinMapCount; // Equivalent
			if(copyAll && i >= HOTBAR_START) clicks.add(firstClick=new ClickEvent(syncId, INPUT_START+1, i-HOTBAR_START, SlotActionType.SWAP));
			else if(copyAll && isCrafter) clicks.add(firstClick=new ClickEvent(syncId, i, 0, SlotActionType.QUICK_MOVE));
			else{
				final boolean takeHalf = (minMapCount+1)/2 >= emptyMapsPerCopy;
				final int amtTaken = takeHalf ? (minMapCount+1)/2 : minMapCount;
				clicks.add(firstClick=new ClickEvent(syncId, i, takeHalf ? 1 : 0, SlotActionType.PICKUP)); // Pickup all or half
				for(int j=emptyMapsPerCopy; j<amtTaken; ++j) clicks.add(new ClickEvent(syncId, i, 1, SlotActionType.PICKUP)); // Put back one
				clicks.add(new ClickEvent(syncId, INPUT_START+1, 0, SlotActionType.PICKUP)); // Place all
			}

			numEmptyMapsInGrid -= emptyMapsPerCopy; // Deduct empty maps

			// Take filled maps from crafter output
			clicks.add(new ClickEvent(syncId, 0, 0, SlotActionType.QUICK_MOVE));
			if(PRESERVE_MAP_POS && lastEmptySlot > i){
				if(lastEmptySlot >= HOTBAR_START){
					//Main.LOGGER.info("MapCopy: moving back to correct slot using SWAP: "+lastEmptySlot+","+(lastEmptySlot-HOTBAR_START));
					clicks.add(new ClickEvent(syncId, i, lastEmptySlot-HOTBAR_START, SlotActionType.SWAP));
				}
				else{
					//Main.LOGGER.info("MapCopy: moving back to correct slot using PICKUP");
					clicks.add(new ClickEvent(syncId, lastEmptySlot, 0, SlotActionType.PICKUP)); // Pickup all
					clicks.add(new ClickEvent(syncId, i, 0, SlotActionType.PICKUP)); // Place all
				}
			}
			final int clicksUsed = clicks.size() - clicksAtStart;
			if(clicksUsed <= Main.inventoryUtils.MAX_CLICKS) reserveClicks.put(firstClick, clicksUsed);
		}// copy maps

		if(numEmptyMapsInGrid > 0) clicks.add(new ClickEvent(syncId, INPUT_START, 0, SlotActionType.QUICK_MOVE));

		ongoingCopy = true;
		Main.inventoryUtils.executeClicks(clicks,
		c->{
			// Don't start individual copy operation unless we can fully knock it out (unless impossible to do in 1 go)
			final Integer clicksNeeded = reserveClicks.get(c);
			return clicksNeeded == null || clicksNeeded <= Main.inventoryUtils.MAX_CLICKS - Main.inventoryUtils.addClick(null);
		},
		()->{
			Main.LOGGER.info("MapCopy: DONE");
			ongoingCopy = false;
		});
	}

	public KeybindMapCopy(){
		KeyBindingHelper.registerKeyBinding(new EvKeybind("mapart_copy", ()->copyMapArtInInventory(),
				s->s instanceof InventoryScreen || s instanceof CraftingScreen));
	}
}