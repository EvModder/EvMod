package net.evmodder.KeyBound.Keybinds;

import net.evmodder.KeyBound.Main;
import net.evmodder.KeyBound.Keybinds.InventoryUtils.ClickEvent;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import java.util.ArrayDeque;

public final class KeybindMapCopy{
	private final boolean BARF_CLOGS_FOR_MAP_COPY = false, FORCE_HOTBAR_SWAPS = false, COPY_PRECISE_64 = true;
	private boolean PRESERVE_EXACT_POS = true;
	private static boolean ongoingCopy;
	private static long lastCopy;
	private static final long copyCooldown = 250l;

	private boolean isMapArt(ItemStack stack){
		if(stack.isEmpty()) return false;
		return Registries.ITEM.getId(stack.getItem()).getPath().equals("filled_map");
	}
	private boolean isBlankMap(ItemStack stack){
		//stack.getItem().getClass() == EmptyMapItem.class
		if(stack.isEmpty()) return false;
		return Registries.ITEM.getId(stack.getItem()).getPath().equals("map");
	}

	private int findEmptyHotbarSlot(ScreenHandler xsh){
		final int hotbarStart = xsh instanceof CraftingScreenHandler ? 37 : 36;
		for(int i=8; i>=0; --i){
			ItemStack stack = xsh.getSlot(hotbarStart+i).getStack();
			if(stack.isEmpty()) return i;
		}
		return -1;
	}

	private int getBlankMapsIntoCraftingArea(ScreenHandler xsh,
			final int hotbarButton, final int craftingSlot, final int blankMapsNeeded, int blankMapsCurrent,
			ArrayDeque<ClickEvent> clicks, Integer blankMapStackableCapacity // mutable
	){
		if(blankMapsNeeded <= blankMapsCurrent) return blankMapsCurrent;
		if(blankMapsNeeded > 64){
			Main.LOGGER.error("!!! ERROR: blankMapsNeeded > 64, this should be unreachable");
			return blankMapsCurrent;
		}
//		if(hotbarButton != -1 && blankMapStackableCapacity > blankMapsCurrent){
//			blankMapStackableCapacity -= blankMapsCurrent;
//			blankMapsCurrent = 0;
//			clicks.add(new ClickEvent(craftingSlot, 0, SlotActionType.QUICK_MOVE));
//		}
		Main.LOGGER.info("MapCopy: Getting "+blankMapsNeeded+"+ blank maps into crafting slot:"+craftingSlot);
		final boolean isCrafter = xsh instanceof CraftingScreenHandler;
		for(int i=isCrafter ? 10 : 9; i<=45; ++i){
			ItemStack stack = xsh.getSlot(i).getStack();
			if(!isBlankMap(stack) || stack.getCount() == 0) continue;
			blankMapStackableCapacity -= stack.getMaxCount() - stack.getCount();
			final boolean willHaveLeftoversOnCursor = blankMapsCurrent + stack.getCount() > stack.getMaxCount();
			final int hbStart = isCrafter ? 37 : 36;
			final int iHotbarButton = i<hbStart ? -1 : (!isCrafter && i==45) ? 40 : i-hbStart;
			if(isCrafter && craftingSlot == 1){
				clicks.add(new ClickEvent(xsh.syncId, i, 0, SlotActionType.QUICK_MOVE));
				if(!willHaveLeftoversOnCursor){
					blankMapsCurrent += stack.getCount();
					stack.setCount(0);
					if(blankMapsCurrent >= blankMapsNeeded){
						Main.LOGGER.info("MapCopyCrafter: Found sufficient blank maps (shift-click)");
						return blankMapsCurrent;
					}
				}
				else{
					if(iHotbarButton != -1) clicks.add(new ClickEvent(xsh.syncId, 2, iHotbarButton, SlotActionType.SWAP));
					else{
						clicks.add(new ClickEvent(xsh.syncId, 2, hotbarButton, SlotActionType.SWAP));
						clicks.add(new ClickEvent(xsh.syncId, i, hotbarButton, SlotActionType.SWAP));
					}
					stack.setCount(stack.getCount() + blankMapsCurrent - stack.getMaxCount());
					blankMapStackableCapacity += stack.getMaxCount() - stack.getCount();
					Main.LOGGER.info("MapCopyCrafter: Found sufficient blank maps (with extra)");
					return stack.getMaxCount();
				}
			}
			else if(hotbarButton != -1 && stack.getCount() >= blankMapsNeeded){
				Main.LOGGER.info("MapCopy: Found sufficient blank maps (hotbar swap)");
				if(iHotbarButton != -1) clicks.add(new ClickEvent(xsh.syncId, craftingSlot, iHotbarButton, SlotActionType.SWAP));
				else{
					clicks.add(new ClickEvent(xsh.syncId, i, hotbarButton, SlotActionType.SWAP));
					clicks.add(new ClickEvent(xsh.syncId, craftingSlot, hotbarButton, SlotActionType.SWAP));
				}
				if(blankMapsCurrent != 0) blankMapStackableCapacity += stack.getMaxCount() - blankMapsCurrent;
				int countTemp = stack.getCount();
				stack.setCount(blankMapsCurrent);
				return countTemp;
			}
			else{
				clicks.add(new ClickEvent(xsh.syncId, i, 0, SlotActionType.PICKUP)); // pickup all
				clicks.add(new ClickEvent(xsh.syncId, craftingSlot, 0, SlotActionType.PICKUP)); // place all
				if(willHaveLeftoversOnCursor){
					clicks.add(new ClickEvent(xsh.syncId, i, 0, SlotActionType.PICKUP)); // putback extras
					stack.setCount(stack.getCount() + blankMapsCurrent - stack.getMaxCount());
					blankMapStackableCapacity += stack.getMaxCount() - stack.getCount();
					Main.LOGGER.info("MapCopy: Found sufficient blank maps (with extra)");
					return stack.getMaxCount();
				}
				else{
					blankMapsCurrent += stack.getCount();
					stack.setCount(0);
					if(blankMapsCurrent >= blankMapsNeeded){
						Main.LOGGER.info("MapCopy: Found sufficient blank maps");
						return blankMapsCurrent;
					}
				}
			}
		}
		return blankMapsCurrent;
	}
	private void copyMapArtInInventory(Boolean bulk){
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
		ArrayDeque<ClickEvent> clicks = new ArrayDeque<>();
		ScreenHandler xsh = ((HandledScreen<?>)client.currentScreen).getScreenHandler();
		final int syncId = xsh.syncId;
		if(!xsh.getCursorStack().isEmpty()){
			Main.LOGGER.warn("MapCopy: Cursor needs to be empty");
			if(!BARF_CLOGS_FOR_MAP_COPY) return;
			clicks.add(new ClickEvent(syncId, ScreenHandler.EMPTY_SPACE_SLOT_INDEX, 0, SlotActionType.PICKUP));
		}
		// Decide whether to do a bulk copy
		int numMapArtSingles = 0, numMapArtMultis = 0, smallestMapStack = 63;
		for(int i=9; i<=45; ++i){
			ItemStack stack = xsh.getSlot(i).getStack();
			if(isMapArt(stack)){
				smallestMapStack = Math.min(smallestMapStack, stack.getCount());
				if(stack.getCount() == 1) ++numMapArtSingles; else ++numMapArtMultis;
			}
		}
		if(bulk == null){
			bulk = numMapArtMultis > numMapArtSingles;
			Main.LOGGER.info("Dynamic BULK decision: "+bulk);
		}

		// Figure out how many maps we need to copy
		int numMapArtsToCopy = 0, lastFilledMapSlot = 0;
		for(int i=9; i<=45; ++i){
			ItemStack stack = xsh.getSlot(i).getStack();
			if(isMapArt(stack) && stack.getCount() == smallestMapStack){
				++numMapArtsToCopy;
				lastFilledMapSlot = i;
			}
		}
		if(numMapArtsToCopy == 0){Main.LOGGER.warn("MapCopy: Nothing to copy"); return;}
		//
		int hotbarButton = -1;
		//if(PREFER_HOTBAR_SWAPS){
			for(int i=8; i>=0; --i){
				ItemStack stack = xsh.getSlot((isCrafter ? 37 : 36)+i).getStack();
				if(stack.isEmpty()){hotbarButton = i; break;}
			}
			if(hotbarButton == -1 && !isCrafter){ // use offhand (NOTE: does not work for bulk)
				ItemStack stack = xsh.getSlot(PlayerScreenHandler.OFFHAND_ID/*45*/).getStack();
				if(stack.isEmpty()) hotbarButton = 40;
			}
		//}
		if(hotbarButton != -1) Main.LOGGER.info("MapCopy: Attempting to use hotbar swaps to reduce clicks");
		else{
			Main.LOGGER.info("MapCopy: Unable to use hotbar swaps exclusively");
			if(FORCE_HOTBAR_SWAPS) return;
		}
		PRESERVE_EXACT_POS = !isCrafter; // TODO: smarter/configurable decision
		//if(isCrafter) MILLIS_BETWEEN_CLICKS /= 2; // TODO: smarter decision / configurable
		if(PRESERVE_EXACT_POS) hotbarButton = -1; // TODO: any case where we can still use the hotbar? (where it wont be filled with relocated maps)
		//
		// Check if there are items already in the crafting 2x2
		int blankMapCraftingSlot = -1;
		int currentBlankMapsInCrafter = 0;
		for(int i=1; i<(isCrafter ? 10 : 5); ++i){
			ItemStack stack = xsh.getSlot(i).getStack();
			if(stack.isEmpty()) continue;
			if(!isBlankMap(stack)) Main.LOGGER.warn("MapCopy: !! Non-blank-map item in crafting grid");
			else if(blankMapCraftingSlot != -1) Main.LOGGER.warn("MapCopy: Multiple blank map slots in crafting grid");
			else if(hotbarButton != -1 && stack.getCount() < numMapArtsToCopy) Main.LOGGER.warn("MapCopy: Blank map count in grid is insufficient");
			else if(isCrafter && i != 1){Main.LOGGER.warn("MapCopyCrafter: Blank map already in 3x3 and is not in the first slot"); return;}
			else{
				blankMapCraftingSlot = i;
				currentBlankMapsInCrafter = stack.getCount();
			}
			// handle non-map items in crafting grid
			if(hotbarButton == -1) clicks.add(new ClickEvent(syncId, i, 0, SlotActionType.QUICK_MOVE)); // no risk of clogging a non-existant hotbar slot
			else if(BARF_CLOGS_FOR_MAP_COPY) clicks.add(new ClickEvent(syncId, i, 0, SlotActionType.THROW)); // barf non-map
			else return;
		}
//		Main.LOGGER.warn("MapCopy: Blank maps already in crafting grid: "+craftingMaps);
		if(blankMapCraftingSlot == -1) blankMapCraftingSlot = 1;//= isCrafter? CraftingScreenHandler.INPUT_START : PlayerScreenHandler.CRAFTING_INPUT_START
		//
		// Check if we have enough blank maps to copy every map in the inventory
		int blankMaps = currentBlankMapsInCrafter;
		Integer blankMapStackableCapacity = 0;
		for(int i=isCrafter ? 10 : 9; i<=45; ++i){
			if(!PRESERVE_EXACT_POS && blankMaps > 0 && i > lastFilledMapSlot) break; // We don't want to lose relative ordering, even though we lose exact pos
			ItemStack stack = xsh.getSlot(i).getStack();
			if(isBlankMap(stack)){
				blankMaps += stack.getCount();
				//don't include 45 since it can't be shift-clicked to
				if(i != 45) blankMapStackableCapacity += stack.getMaxCount() - stack.getCount();
			}
		}
		if((!PRESERVE_EXACT_POS || !bulk) && blankMaps < numMapArtsToCopy){
			Main.LOGGER.warn("MapCopy: not enough blank maps, need:"+numMapArtsToCopy+", have:"+blankMaps);
			return;
		}
		//
		// Move blank maps to the crafting grid
		currentBlankMapsInCrafter =
				getBlankMapsIntoCraftingArea(xsh, hotbarButton, blankMapCraftingSlot, /*needed=*/1, currentBlankMapsInCrafter, clicks, blankMapStackableCapacity);
		if(currentBlankMapsInCrafter < 1){Main.LOGGER.warn("No blank maps found in inventory"); return;}
		Main.LOGGER.info("Initial blank maps in crafter: "+currentBlankMapsInCrafter);
		//
		// Recheck for available hotbar slot
		if(hotbarButton == -1) hotbarButton = findEmptyHotbarSlot(xsh);
		//
		// Pick a different crafting slot for the filled maps to go into
		int filledMapCraftingSlot = blankMapCraftingSlot+1;
		if(filledMapCraftingSlot == (isCrafter ? /*CraftingScreenHandler.INPUT_END*/10 : PlayerScreenHandler.CRAFTING_INPUT_END))
			filledMapCraftingSlot = 1;//csh.INPUT_START : psh.CRAFTING_INPUT_START
		//
		// Execute copy operations
		Main.LOGGER.info("MapCopy"+(isCrafter?"Crafter":"")+(bulk?"Bulk":"")+": Starting copy");
		int slotStart, slotEnd, slotIncr;
		if(PRESERVE_EXACT_POS){slotStart=isCrafter?10:9; slotEnd=46; slotIncr=1;}//asc
		else{slotStart=45; slotEnd=isCrafter?9:8; slotIncr=-1;}//desc
		//for(int i=45; i>=(isCrafter?10:9); --i){
		for(int i=slotStart; i!=slotEnd; i+=slotIncr){
			ItemStack stack = xsh.getSlot(i).getStack();
			if(!isMapArt(stack)) continue;
			if(stack.getCount() == stack.getMaxCount()) continue;
			if(!bulk && stack.getCount() != smallestMapStack) continue;
			//Main.LOGGER.info("MapCopy: copying slot:"+i);
			final int hbStart = isCrafter ? 37 : 36;
			final int isHB = i<hbStart ? -1 : (!isCrafter && i==45) ? 40 : i-hbStart;
			final boolean canBulkCopy = stack.getCount()*2 <= stack.getMaxCount();
			int useHB = isHB != -1 ? isHB : hotbarButton;
			if(useHB != -1 && (!bulk || canBulkCopy || FORCE_HOTBAR_SWAPS) &&
					(stack.getCount() == 1 || (isHB != 40 && isHB != -1) || (hotbarButton != 40 && hotbarButton != -1))){
				Main.LOGGER.info("MapCopy: using swaps for i:"+i);
				int amountToCraft = bulk && canBulkCopy ? stack.getCount() : 1;
				if(bulk && currentBlankMapsInCrafter < amountToCraft){
					currentBlankMapsInCrafter =
							getBlankMapsIntoCraftingArea(xsh, -1, blankMapCraftingSlot, amountToCraft, currentBlankMapsInCrafter, clicks, blankMapStackableCapacity);
					if(currentBlankMapsInCrafter < 1){Main.LOGGER.info("MapCopyBulk(swaps): Ran out of blank maps"); break;}
					amountToCraft = Math.min(amountToCraft, currentBlankMapsInCrafter);
					if(hotbarButton == -1) hotbarButton = findEmptyHotbarSlot(xsh);
				}
				if(isCrafter && blankMapCraftingSlot == 1 && filledMapCraftingSlot == 2){
					clicks.add(new ClickEvent(syncId, i, 0, SlotActionType.QUICK_MOVE));
				}
				else{
					if(useHB != isHB) clicks.add(new ClickEvent(syncId, i, hotbarButton, SlotActionType.SWAP)); // Move from original slot to hotbar slot
					clicks.add(new ClickEvent(syncId, filledMapCraftingSlot, useHB, SlotActionType.SWAP)); // Move from hotbar to crafting grid
				}
				if(!PRESERVE_EXACT_POS && amountToCraft >= stack.getCount() && amountToCraft > 1 && isHB != 40){
					clicks.add(new ClickEvent(syncId, 0, 0, SlotActionType.QUICK_MOVE)); // Craft all
					// mark the crafting destination slot occupied and mark the current slot as empty
					for(int j=isCrafter?45:44; j>i; --j){
						if(xsh.getSlot(j).getStack().isEmpty()){
							// Round-about way to put an item stack in a slot internally without triggering markDirty()
							xsh.getSlot(j).inventory.setStack(xsh.getSlot(j).getIndex(), stack.copyWithCount(stack.getCount() + amountToCraft));
							stack.setCount(0);
							break;
						}
					}
				}
				else{
					if(isHB == 40 && stack.getCount() > 1) useHB = hotbarButton;
					clicks.add(new ClickEvent(syncId, 0, useHB, SlotActionType.SWAP)); // Craft one
					if(stack.getCount() > amountToCraft) clicks.add(new ClickEvent(syncId, filledMapCraftingSlot, 0, SlotActionType.QUICK_MOVE)); // Take leftovers
					else if(stack.getCount() > 1) clicks.add(new ClickEvent(syncId, 0, 0, SlotActionType.QUICK_MOVE)); // Craft all
					if(useHB != isHB) clicks.add(new ClickEvent(syncId, i, hotbarButton, SlotActionType.SWAP)); // Put back in original slot
				}
				currentBlankMapsInCrafter -= amountToCraft;
			}
			else{
				Main.LOGGER.info("MapCopy: using mouse clicks for i:"+i);
				boolean fullBulk = bulk && canBulkCopy;
				boolean halfBulk = bulk && !fullBulk && stack.getCount() + stack.getCount()/2 <= stack.getMaxCount();
				boolean clickBulk = COPY_PRECISE_64 && bulk && !fullBulk && !halfBulk
						/* && stack.getCount() > stack.getMaxCount() - stack.getCount()/2*/;//math already implied
				int amountToCraft = fullBulk ? stack.getCount() : halfBulk ? stack.getCount()/2 : clickBulk ? stack.getMaxCount()-stack.getCount() : 1;
				if(currentBlankMapsInCrafter < amountToCraft){ // should only occur in bulk mode
					currentBlankMapsInCrafter =
							getBlankMapsIntoCraftingArea(xsh, -1, blankMapCraftingSlot, amountToCraft, currentBlankMapsInCrafter, clicks, blankMapStackableCapacity);
					if(currentBlankMapsInCrafter < 1){Main.LOGGER.info("MapCopyBulk: Ran out of blank maps"); break;}
					if(currentBlankMapsInCrafter < amountToCraft){ // Implies amountToCraft > currentBlankMapsInCrafter
						if(fullBulk){amountToCraft = stack.getCount()/2; fullBulk=false; halfBulk=true;}
						if(halfBulk && currentBlankMapsInCrafter < amountToCraft){amountToCraft = stack.getMaxCount()-stack.getCount(); halfBulk=false; clickBulk=true;}
						if(clickBulk){
							if(!COPY_PRECISE_64){amountToCraft=1; clickBulk=false;}
							else amountToCraft = Math.min(amountToCraft, currentBlankMapsInCrafter);
						}
					}
					if(hotbarButton == -1) hotbarButton = findEmptyHotbarSlot(xsh);
				}
				if(isCrafter && !PRESERVE_EXACT_POS && blankMapCraftingSlot == 1 && filledMapCraftingSlot == 2 && (stack.getCount() == 1 || canBulkCopy)){
					clicks.add(new ClickEvent(syncId, i, 0, SlotActionType.QUICK_MOVE));
				}
				else{
					clicks.add(new ClickEvent(syncId, i, !fullBulk ? 1 : 0, SlotActionType.PICKUP)); // Pickup half (unless fullBulk)
					clicks.add(new ClickEvent(syncId, filledMapCraftingSlot, amountToCraft==1 ? 1 : 0, SlotActionType.PICKUP)); // Place one (unless bulk)
					if(!fullBulk && amountToCraft==1 && stack.getCount() > 1) clicks.add(new ClickEvent(i, 0, SlotActionType.PICKUP)); // Place all - place back extras
				}
				if(!PRESERVE_EXACT_POS && amountToCraft >= stack.getCount() && isHB != 40){
					clicks.add(new ClickEvent(syncId, 0, 0, SlotActionType.QUICK_MOVE)); // Craft all
					// mark the crafting destination slot occupied and mark the current slot as empty
					for(int j=isCrafter?45:44; j>i; --j){
						if(xsh.getSlot(j).getStack().isEmpty()){
							// Round-about way to put an item stack in a slot internally without triggering markDirty()
							xsh.getSlot(j).inventory.setStack(xsh.getSlot(j).getIndex(), stack.copyWithCount(stack.getCount() + amountToCraft));
							stack.setCount(0);
							break;
						}
					}
				}
				else{
					if(fullBulk || stack.getCount() == 1){
						// We need to have some maps in the original slot in order to shift-click results into it
						clicks.add(new ClickEvent(syncId, 0, 0, SlotActionType.PICKUP)); // Craft 1 (to cursor)
						clicks.add(new ClickEvent(syncId, i, 0, SlotActionType.PICKUP)); // Place result back in original slot
					}
					if(amountToCraft > 1){
						Main.LOGGER.info("MapCopyBulk: bulk-copying slot:"+i);
						if((fullBulk || halfBulk) && isHB != 40) clicks.add(new ClickEvent(syncId, 0, 0, SlotActionType.QUICK_MOVE));// Craft all
						else/*if(clickBulk)*/{
							for(int j=0; j<amountToCraft; ++j) clicks.add(new ClickEvent(syncId, 0, 0, SlotActionType.PICKUP));
							clicks.add(new ClickEvent(syncId, i, 0, SlotActionType.PICKUP)); // Place result back in original slot
							clicks.add(new ClickEvent(syncId, filledMapCraftingSlot, 0, SlotActionType.QUICK_MOVE)); // Take back leftovers
						}
					}
				}
				currentBlankMapsInCrafter -= amountToCraft;
			}
		}

		if(currentBlankMapsInCrafter > 0){
			if(hotbarButton != -1 && currentBlankMapsInCrafter > blankMapStackableCapacity){
				clicks.add(new ClickEvent(syncId, blankMapCraftingSlot, hotbarButton, SlotActionType.SWAP)); // put back leftover blank maps in hotbar
			}
			else clicks.add(new ClickEvent(syncId, blankMapCraftingSlot, 0, SlotActionType.QUICK_MOVE)); // put back leftover blank maps with shift-click
			clicks.add(new ClickEvent(syncId, blankMapCraftingSlot, 0, SlotActionType.THROW)); // throw leftovers if quick_move fails
		}
		ongoingCopy = true;
		Main.inventoryUtils.executeClicks(client, clicks, /*canProceed=*/_->true, ()->{
			Main.LOGGER.info("MapCopy: DONE");
			ongoingCopy = false;
		});
	}

	public KeybindMapCopy(){
		KeyBindingHelper.registerKeyBinding(new EvKeybind("mapart_copy", ()->copyMapArtInInventory(null),
				s->s instanceof InventoryScreen || s instanceof CraftingScreen));

//		KeyBindingHelper.registerKeyBinding(new EvKeybind("mapart_copy", ()->copyMapArtInInventory(false), true));
//		KeyBindingHelper.registerKeyBinding(new EvKeybind("mapart_copy_bulk", ()->copyMapArtInInventory(true), true));
	}
}