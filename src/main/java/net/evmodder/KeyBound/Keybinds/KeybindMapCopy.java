package net.evmodder.KeyBound.Keybinds;

import java.util.Timer;
import java.util.TimerTask;
import net.evmodder.KeyBound.Main;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import java.util.ArrayList;

public final class KeybindMapCopy{
	//private static final boolean DYNAMIC_HOTBAR_SWAP_SLOT = true;
	private static final boolean BARF_CLOGS_FOR_MAP_COPY = false, PREFER_HOTBAR_SWAPS = true, FORCE_HOTBAR_SWAPS = false, COPY_PRECISE_64 = true;
	//private static final int HOTBAR_SWAP_SLOT = 1; // button >= 0 && button < 9 || button == 40

	private final static boolean isMapArt(ItemStack stack){
		if(stack == null || stack.isEmpty()) return false;
		return Registries.ITEM.getId(stack.getItem()).getPath().equals("filled_map");
	}
	private final static boolean isBlankMap(ItemStack stack){
		//stack.getItem().getClass() == EmptyMapItem.class
		if(stack == null || stack.isEmpty()) return false;
		return Registries.ITEM.getId(stack.getItem()).getPath().equals("map");
	}


	private static boolean ongoingCopy;
	private static long lastCopy;
	private static final long copyCooldown = 250L;

	record ClickEvent(int slotId, int button, SlotActionType actionType){}
	private static int getBlankMapsInto2x2(PlayerScreenHandler psh,
			final int hotbarButton, final int craftingSlot, final int blankMapsNeeded, int blankMapsCurrent, ArrayList<ClickEvent> clicks){
		if(blankMapsNeeded <= blankMapsCurrent) return blankMapsCurrent;
		if(blankMapsNeeded > 64){
			Main.LOGGER.error("!!! ERROR: blankMapsNeeded > 64, this should be unreachable");
			return blankMapsCurrent;
		}
		Main.LOGGER.info("MapCopy: Getting "+blankMapsNeeded+"+ blank maps into crafting slot:"+craftingSlot);
		for(int i=9; i<=45; ++i){
			ItemStack stack = psh.getSlot(i).getStack();
			if(!isBlankMap(stack) || stack.getCount() == 0) continue;
			if(hotbarButton != -1){
				if(stack.getCount() < blankMapsNeeded) continue;
				Main.LOGGER.info("MapCopy: Found sufficient blank maps (hotbar swap)");
				clicks.add(new ClickEvent(i, hotbarButton, SlotActionType.SWAP));
				clicks.add(new ClickEvent(craftingSlot, hotbarButton, SlotActionType.SWAP));
				int countTemp = stack.getCount();
				stack.setCount(blankMapsCurrent);
				return countTemp;
			}
			else{
				boolean willHaveLeftoversOnCursor = stack.getCount() + blankMapsCurrent > stack.getMaxCount();
				clicks.add(new ClickEvent(i, 0, SlotActionType.PICKUP)); // pickup all
				clicks.add(new ClickEvent(craftingSlot, 0, SlotActionType.PICKUP)); // place all
				if(willHaveLeftoversOnCursor){
					clicks.add(new ClickEvent(i, 0, SlotActionType.PICKUP)); // putback extras
					stack.setCount(stack.getCount() + blankMapsCurrent - stack.getMaxCount());
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
	private static void copyMapArtInInventory(long miliseconds_per_click, boolean bulk){
		if(ongoingCopy){Main.LOGGER.warn("MapCopy: Already ongoing"); return;}
		//
		final long ts = System.currentTimeMillis();
		if(ts - lastCopy < copyCooldown){Main.LOGGER.warn("MapCopy: In cooldown"); return;}
		lastCopy = ts;
		MinecraftClient client = MinecraftClient.getInstance();
		if(!(client.currentScreen instanceof InventoryScreen is)){Main.LOGGER.warn("MapCopy: not in InventoryScreen"); return;}
		//
		ArrayList<ClickEvent> clicks = new ArrayList<>();
		PlayerScreenHandler psh = is.getScreenHandler();
		if(!psh.getCursorStack().isEmpty()){
			Main.LOGGER.warn("MapCopy: Cursor needs to be empty");
			if(!BARF_CLOGS_FOR_MAP_COPY) return;
			clicks.add(new ClickEvent(ScreenHandler.EMPTY_SPACE_SLOT_INDEX, 0, SlotActionType.PICKUP));
		}
		// Figure out how many maps we need to copy
		int numMapArtsToCopy = 0;
		for(int i=9; i<=45; ++i){
			ItemStack stack = psh.getSlot(i).getStack();
			if(isMapArt(stack) && stack.getCount() < stack.getMaxCount()) ++numMapArtsToCopy;
		}
		if(numMapArtsToCopy == 0){Main.LOGGER.warn("MapCopy: Nothing to copy"); return;}
		//
		int hotbarButton = -1;
		if(PREFER_HOTBAR_SWAPS){//TODO: Offhand is slot:45, button:40
			for(int i=8; i>=0; --i){
				ItemStack stack = psh.getSlot(PlayerScreenHandler.HOTBAR_START+i).getStack();
				if(stack == null || stack.isEmpty()){hotbarButton = i; break;}
			}
		}
		if(hotbarButton != -1) Main.LOGGER.info("MapCopy: Attempting to use hotbar swaps to reduce clicks");
		else{
			Main.LOGGER.info("MapCopy: Unable to use hotbar swaps exclusively");
			if(FORCE_HOTBAR_SWAPS) return;
		}
		//
		// Check if there are items already in the crafting 2x2
		int blankMapCraftingSlot = -1;
		int currentBlankMapsInCrafter = 0;
		for(int i=PlayerScreenHandler.CRAFTING_INPUT_START; i<PlayerScreenHandler.CRAFTING_INPUT_END; ++i){
			ItemStack stack = psh.getSlot(i).getStack();
			if(stack == null || stack.isEmpty()) continue;
			if(!isBlankMap(stack)) Main.LOGGER.warn("MapCopy: !! Non-blank-map item in crafting 2x2");
			else if(blankMapCraftingSlot != -1) Main.LOGGER.warn("MapCopy: Multiple blank map slots in crafting 2x2");
			else if(hotbarButton != -1 && stack.getCount() < numMapArtsToCopy) Main.LOGGER.warn("MapCopy: Blank map count in 2x2 is insufficient");
			else{
				blankMapCraftingSlot = i;
				currentBlankMapsInCrafter = stack.getCount();
			}
			if(hotbarButton == -1) clicks.add(new ClickEvent(i, 0, SlotActionType.QUICK_MOVE));
			else if(BARF_CLOGS_FOR_MAP_COPY) clicks.add(new ClickEvent(i, 0, SlotActionType.THROW));
			else return;
		}
//		Main.LOGGER.warn("MapCopy: Blank maps already in crafting 2x2: "+craftingMaps);
		if(blankMapCraftingSlot == -1) blankMapCraftingSlot = PlayerScreenHandler.CRAFTING_INPUT_START;
		//
		if(!bulk){
			// Check if we have enough blank maps to copy every map in the inventory
			int blankMaps = currentBlankMapsInCrafter;
			for(int i=9; i<=45; ++i) if(isBlankMap(psh.getSlot(i).getStack())) blankMaps += psh.getSlot(i).getStack().getCount();
			if(blankMaps < numMapArtsToCopy){Main.LOGGER.warn("MapCopy: not enough blank maps, need:"+numMapArtsToCopy+", have:"+blankMaps); return;}
		}
		//
		// Move blank maps to the crafting 2x2
		currentBlankMapsInCrafter = getBlankMapsInto2x2(psh, hotbarButton, blankMapCraftingSlot, /*needed=*/1, currentBlankMapsInCrafter, clicks);
		if(currentBlankMapsInCrafter < 1){Main.LOGGER.warn("No blank maps found in inventory"); return;}
		Main.LOGGER.info("Initial blank maps in crafter: "+currentBlankMapsInCrafter);
		//
		// Copy the filled maps
		// Pick a different crafting slot for the filled maps to go into
		int filledMapCraftingSlot = blankMapCraftingSlot+1;
		if(filledMapCraftingSlot == PlayerScreenHandler.CRAFTING_INPUT_END) filledMapCraftingSlot = PlayerScreenHandler.CRAFTING_INPUT_START;

		for(int i=45; i>=9; --i){
			ItemStack stack = psh.getSlot(i).getStack();
			if(!isMapArt(stack)) continue;
			if(stack.getCount() == stack.getMaxCount()) continue;
			//Main.LOGGER.info("MapCopy: copying slot:"+i);
			final int iHotbarButton = PREFER_HOTBAR_SWAPS && i >= 36 ? i-36 : hotbarButton;
			final boolean canBulkCopy = stack.getCount()*2 <= stack.getMaxCount();
			if(iHotbarButton != -1 && (!bulk || canBulkCopy || FORCE_HOTBAR_SWAPS)){
				int amountToCraft = bulk && canBulkCopy ? stack.getCount() : 1;
				if(bulk && currentBlankMapsInCrafter < amountToCraft){
					currentBlankMapsInCrafter = getBlankMapsInto2x2(psh, -1, blankMapCraftingSlot, amountToCraft, currentBlankMapsInCrafter, clicks);
					if(currentBlankMapsInCrafter < 1){Main.LOGGER.info("MapCopyBulk(swaps): Ran out of blank maps"); break;}
					amountToCraft = Math.min(amountToCraft, currentBlankMapsInCrafter);
				}
				if(iHotbarButton == hotbarButton) clicks.add(new ClickEvent(i, iHotbarButton, SlotActionType.SWAP)); // Move to hotbar from original slot
				clicks.add(new ClickEvent(filledMapCraftingSlot, iHotbarButton, SlotActionType.SWAP)); // Move map to crafting 2x2
				clicks.add(new ClickEvent(PlayerScreenHandler.CRAFTING_RESULT_ID, iHotbarButton, SlotActionType.SWAP)); // Craft one
				if(bulk && amountToCraft > 1){
						Main.LOGGER.info("MapCopyBulk(swaps): bulk-copying slot:"+i);
					clicks.add(new ClickEvent(PlayerScreenHandler.CRAFTING_RESULT_ID, 0, SlotActionType.QUICK_MOVE)); // Craft all
				}
				if(stack.getCount() > amountToCraft) clicks.add(new ClickEvent(filledMapCraftingSlot, 0, SlotActionType.QUICK_MOVE)); // Take leftovers
				if(iHotbarButton == hotbarButton) clicks.add(new ClickEvent(i, iHotbarButton, SlotActionType.SWAP)); // Put back in original slot
				currentBlankMapsInCrafter -= amountToCraft;
			}
			else{
				boolean fullBulk = bulk && canBulkCopy;
				boolean halfBulk = bulk && !fullBulk && stack.getCount() + stack.getCount()/2 <= stack.getMaxCount();
				boolean clickBulk = COPY_PRECISE_64 && bulk && !fullBulk && !halfBulk/* && stack.getCount() > stack.getMaxCount() - stack.getCount()/2*/;//math already implied
				int amountToCraft = fullBulk ? stack.getCount() : halfBulk ? stack.getCount()/2 : clickBulk ? stack.getMaxCount()-stack.getCount() : 1;
				if(currentBlankMapsInCrafter < amountToCraft){ // should only occur in bulk mode
					currentBlankMapsInCrafter = getBlankMapsInto2x2(psh, -1, blankMapCraftingSlot, amountToCraft, currentBlankMapsInCrafter, clicks);
					if(currentBlankMapsInCrafter < 1){Main.LOGGER.info("MapCopyBulk: Ran out of blank maps"); break;}
					if(currentBlankMapsInCrafter < amountToCraft){ // Implies amountToCraft > currentBlankMapsInCrafter
						if(fullBulk){amountToCraft = stack.getCount()/2; fullBulk=false; halfBulk=true;}
						if(halfBulk && currentBlankMapsInCrafter < amountToCraft){amountToCraft = stack.getMaxCount()-stack.getCount(); halfBulk=false; clickBulk=true;}
						if(clickBulk){
							if(!COPY_PRECISE_64){amountToCraft=1; clickBulk=false;}
							else amountToCraft = Math.min(amountToCraft, currentBlankMapsInCrafter);
						}
					}
				}
				clicks.add(new ClickEvent(i, !fullBulk ? 1 : 0, SlotActionType.PICKUP)); // pickup half (unless fullBulk)
				clicks.add(new ClickEvent(filledMapCraftingSlot, amountToCraft==1 ? 1 : 0, SlotActionType.PICKUP)); // place one (unless bulk)
				if(amountToCraft==1 && stack.getCount() > 2) clicks.add(new ClickEvent(i, 0, SlotActionType.PICKUP)); // place all - place back extras
				//client.interactionManager.clickRecipe(0, Recipe.CRAFTING), true);
				if(amountToCraft == 1 || stack.getCount() == 1 || fullBulk){ // We need to have some maps in the original slot in order to shift-click results into it
					clicks.add(new ClickEvent(PlayerScreenHandler.CRAFTING_RESULT_ID, 0, SlotActionType.PICKUP));
					clicks.add(new ClickEvent(i, 0, SlotActionType.PICKUP)); // Place result back in original slot
				}
				if(amountToCraft > 1){
					Main.LOGGER.info("MapCopyBulk: bulk-copying slot:"+i);
					if(fullBulk || halfBulk) clicks.add(new ClickEvent(PlayerScreenHandler.CRAFTING_RESULT_ID, 0, SlotActionType.QUICK_MOVE));
					else/*if(clickBulk)*/{
						for(int j=0; j<amountToCraft; ++j) clicks.add(new ClickEvent(PlayerScreenHandler.CRAFTING_RESULT_ID, 0, SlotActionType.PICKUP));
						clicks.add(new ClickEvent(i, 0, SlotActionType.PICKUP)); // Place result back in original slot
						clicks.add(new ClickEvent(filledMapCraftingSlot, 0, SlotActionType.QUICK_MOVE)); // Take back leftovers
					}
				}
				currentBlankMapsInCrafter -= amountToCraft;
			}
		}

		if(currentBlankMapsInCrafter > 0){
			clicks.add(new ClickEvent(blankMapCraftingSlot, 0, SlotActionType.QUICK_MOVE)); // put back leftover blank maps
			clicks.add(new ClickEvent(blankMapCraftingSlot, 0, SlotActionType.THROW)); // throw leftovers if quick_move fails
		}
		ongoingCopy = true;
		new Timer().scheduleAtFixedRate(new TimerTask(){@Override public void run(){
			if(clicks.isEmpty()){
				Main.LOGGER.info("MapCopy: DONE");
				cancel();
				ongoingCopy = false;
				return;
			}
			ClickEvent click = clicks.removeFirst();
			client.interactionManager.clickSlot(0, click.slotId, click.button, click.actionType, client.player);
		}}, miliseconds_per_click, miliseconds_per_click);
	}

	public static AbstractKeybind kbCopy, kbCopyBulk;
	public KeybindMapCopy(final int miliseconds_per_click){
		if(miliseconds_per_click < 1){Main.LOGGER.error("milis_between_clicks value is set too low, disabling MapArtCopy/Bulk keybind"); return;}//TODO: remove

		KeyBindingHelper.registerKeyBinding(kbCopy = new AbstractKeybind("mapart_copy", ()->copyMapArtInInventory(miliseconds_per_click, false)){});
		KeyBindingHelper.registerKeyBinding(kbCopyBulk = new AbstractKeybind("mapart_copy_bulk", ()->copyMapArtInInventory(miliseconds_per_click, true)){});
	}
}