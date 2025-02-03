package net.evmodder.KeyBound;

import java.util.Timer;
import java.util.TimerTask;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.world.World;
import java.util.ArrayList;

final public class MapArtKeybinds{
	//private static final boolean DYNAMIC_HOTBAR_SWAP_SLOT = true;
	private static final boolean BARF_CLOGS_FOR_MAP_COPY = false, COPY_USE_HOTBAR = true;
	//private static final int HOTBAR_SWAP_SLOT = 1; // button >= 0 && button < 9 || button == 40

	private final static boolean isMapArt(ItemStack stack){
		if(stack == null || stack.isEmpty()) return false;
		return Registries.ITEM.getId(stack.getItem()).getPath().equals("filled_map");
	}
	private final static boolean isUnloadedMapArt(World world, ItemStack stack){
		if(stack == null || stack.isEmpty()) return false;
		if(!Registries.ITEM.getId(stack.getItem()).getPath().equals("filled_map")) return false;
		return FilledMapItem.getMapState(stack, world) == null;
	}
	private final static boolean isBlankMap(ItemStack stack){
		//stack.getItem().getClass() == EmptyMapItem.class
		if(stack == null || stack.isEmpty()) return false;
		return Registries.ITEM.getId(stack.getItem()).getPath().equals("map");
	}

	private static boolean ongoingLoad;
	private static long lastLoad;
	private static final long loadCooldown = 500L;
	private static void loadMapArtFromShulker(int fromSlot, final int clicks_left, final int clicks_per_gt){
		if(ongoingLoad){Main.LOGGER.warn("MapLoad cancelled: Already ongoing"); return;}
		//
		final long ts = System.currentTimeMillis();
		if(ts - lastLoad < loadCooldown){Main.LOGGER.warn("MapLoad cancelled: Cooldown"); return;}
		lastLoad = ts;
		//
//		if(slot >= 27){Main.LOGGER.warn("MapLoad cancelled: slot>27"); return;} // should be unreachable
		MinecraftClient client = MinecraftClient.getInstance();
		if(!(client.currentScreen instanceof ShulkerBoxScreen sbs)){Main.LOGGER.warn("MapLoad cancelled: not in ShulkerBoxScreen"); return;}
		//
		ShulkerBoxScreenHandler sh = sbs.getScreenHandler();
		int numMapArtsToLoad = 0;
		for(int i=fromSlot; i<27; ++i) if(isUnloadedMapArt(client.player.clientWorld, sh.getSlot(i).getStack())) ++numMapArtsToLoad;
		if(numMapArtsToLoad == 0){Main.LOGGER.warn("MapLoad cancelled: none to load"); return;}
		//
		ongoingLoad = true;
		int[] slots = new int[Math.min(9, clicks_left)];
		int nextHotbarSlot = 0;
		//Main.LOGGER.info("MapLoad starting slot: "+fromSlot);
		for(; fromSlot<27; ++fromSlot){
			ItemStack stack = sh.getSlot(fromSlot).getStack();
			if(!isUnloadedMapArt(client.player.clientWorld, stack)) continue;
			if(nextHotbarSlot == slots.length) break;
			slots[nextHotbarSlot] = fromSlot;
			client.interactionManager.clickSlot(sh.syncId, fromSlot, nextHotbarSlot, SlotActionType.SWAP, client.player);
			++nextHotbarSlot; // delay breaking till we encounter next map
		}
		//
		sh.updateToClient();
		sh.sendContentUpdates();
		sh.syncState();
		client.player.getInventory().markDirty();
		//
		final int lastHotbarSlot = nextHotbarSlot;
		final int fromSlotFinal = fromSlot;
		//Main.LOGGER.info("lastHotbarSlot: "+lastHotbarSlot+", fromSlotFinal: "+fromSlotFinal);
		new Timer().schedule(new TimerTask(){@Override public void run(){
			for(int i=0; i<lastHotbarSlot; ++i){
				client.interactionManager.clickSlot(sh.syncId, slots[i], i, SlotActionType.SWAP, client.player);
			}
//			sh.updateToClient();
//			sh.sendContentUpdates();
//			sh.syncState();
//			client.player.getInventory().markDirty();
			if(fromSlotFinal == 27){
				//Main.LOGGER.info("MapLoad: DONE!");
				ongoingLoad = false;
			}
			else{
				//Main.LOGGER.info("MapLoad: recursive call");
				if(clicks_left - 9 >= 9){
					ongoingLoad=false;lastLoad=0;
					loadMapArtFromShulker(fromSlotFinal, clicks_left-9, clicks_per_gt);
				}
				else new Timer().schedule(new TimerTask(){@Override public void run(){
					ongoingLoad=false;lastLoad=0; loadMapArtFromShulker(fromSlotFinal, clicks_per_gt, clicks_per_gt);
				}}, 51L);
			}
		}}, 50L);
	}
	/*private static void loadMapArtFromShulker(MinecraftClient client, int slot, final int MAX_CLICKS){
		pendingLoad = false;
		if(slot >= 27) return;
		if(!(client.currentScreen instanceof ShulkerBoxScreen sbs)) return;
		Main.LOGGER.info("max clicks: "+MAX_CLICKS);
		ShulkerBoxScreenHandler sh = sbs.getScreenHandler();
		int numMapArtsToLoad = 0;
		for(int i=slot; i<27; ++i) if(isMapArt(sh.getSlot(i).getStack())) ++numMapArtsToLoad;
		if(numMapArtsToLoad == 0) return;
		Main.LOGGER.info("current slot: "+slot+", toLoad:"+numMapArtsToLoad+", syncId: "+sh.syncId);

		final int hotbarSwapSlot = DYNAMIC_HOTBAR_SWAP_SLOT ? client.player.getInventory().getSwappableHotbarSlot() : HOTBAR_SWAP_SLOT;
		Main.LOGGER.info("hotbar slot to use:"+hotbarSwapSlot);
		int clicksTotal = 0;
		for(; slot < 27; ++slot){
			ItemStack stack = sh.getSlot(slot).getStack();
			if(!isMapArt(stack)) continue;
			clicksTotal += 2;
			if(clicksTotal > MAX_CLICKS) break;
			Main.LOGGER.info("swapping slot:"+slot+" with hotbar:"+hotbarSwapSlot);
			client.interactionManager.clickSlot(sh.syncId, slot, hotbarSwapSlot, SlotActionType.SWAP, client.player);
			sh.updateToClient();
			sh.sendContentUpdates();
			sh.syncState();
			client.player.getInventory().markDirty();
			client.interactionManager.clickSlot(sh.syncId, slot, hotbarSwapSlot, SlotActionType.SWAP, client.player);
		}
		if(slot < 27 && !pendingLoad){
			pendingLoad = true;
			final int slotFinal = slot;
			new Timer().schedule(new TimerTask(){@Override public void run(){loadMapArtFromContainer(client, slotFinal, MAX_CLICKS);}}, 100L);
		}
	}*/
	/*private static void loadMapArtFromContainer(MinecraftClient client, int slot, final int MAX_CLICKS){
		pendingLoad = false;
		if(!(client.currentScreen instanceof GenericContainerScreen gcs)) return;
		Main.LOGGER.info("max clicks: "+MAX_CLICKS);
		GenericContainerScreenHandler gcsh = gcs.getScreenHandler();
		Inventory inv = gcsh.getInventory();
		if(slot >= inv.size()) return;
		int numMapArtsToLoad = 0;
		for(int i=slot; i<inv.size(); ++i) if(isMapArt(inv.getStack(i))) ++numMapArtsToLoad;
		if(numMapArtsToLoad == 0) return;
		Main.LOGGER.info("num slots: "+inv.size()+", current slot: "+slot+", toLoad:"+numMapArtsToLoad+", syncId: "+gcsh.syncId);

		final int hotbarSwapSlot = DYNAMIC_HOTBAR_SWAP_SLOT ? client.player.getInventory().getSwappableHotbarSlot() : HOTBAR_SWAP_SLOT;
		Main.LOGGER.info("hotbar slot to use:"+hotbarSwapSlot);
		int clicksTotal = 0;
		for(; slot < inv.size(); ++slot){
			if(!isMapArt(inv.getStack(slot))) continue;
			clicksTotal += 2;
			if(clicksTotal > MAX_CLICKS) break;
			Main.LOGGER.info("swapping with hotbar:"+slot);
			client.interactionManager.clickSlot(gcsh.syncId, slot, hotbarSwapSlot, SlotActionType.SWAP, client.player);
			gcsh.updateToClient();
			gcsh.sendContentUpdates();
			gcsh.syncState();
//			client.player.getInventory().markDirty();
			client.interactionManager.clickSlot(gcsh.syncId, slot, hotbarSwapSlot, SlotActionType.SWAP, client.player);
		}
		if(slot < inv.size() && !pendingLoad){
			pendingLoad = true;
			final int fromSlotFinal = slot;
			new Timer().schedule(new TimerTask(){@Override public void run(){loadMapArtFromContainer(client, fromSlotFinal, MAX_CLICKS);}}, 100L);
		}
	}*/

//	private static long takeMapArtFromShulker(int fromSlot, final int clicks_left, final int clicks_per_gt){
//		
//	}

	private static boolean ongoingCopy;
	private static long lastCopy;
	private static final long copyCooldown = 250L;
	/*private static void copyMapArtInInventory(int fromSlot, final int MAX_CLICKS){
		if(ongoingCopy){Main.LOGGER.warn("MapCopy: Already ongoing"); return;}
		//
		final long ts = System.currentTimeMillis();
		if(ts - lastCopy < copyCooldown){Main.LOGGER.warn("MapCopy: In cooldown"); return;}
		lastCopy = ts;
		MinecraftClient client = MinecraftClient.getInstance();
		if(!(client.currentScreen instanceof InventoryScreen is)){Main.LOGGER.warn("MapCopy: not in InventoryScreen"); return;}
		//
		PlayerScreenHandler psh = is.getScreenHandler();
		int clicksTotal = 0;
		if(!psh.getCursorStack().isEmpty()){
			Main.LOGGER.warn("MapCopy: Cursor needs to be empty");
			if(!BARF_CLOGS_FOR_MAP_COPY) return;
			++clicksTotal;
			client.interactionManager.clickSlot(0, ScreenHandler.EMPTY_SPACE_SLOT_INDEX, 0, SlotActionType.PICKUP, client.player);
		}
		boolean mapsInHotbar = false;
		// Figure out how many maps we need to copy
		int numMapArtsToCopy = 0;
		for(int i=fromSlot; i<=45; ++i) if(isMapArt(psh.getSlot(i).getStack())){
			++numMapArtsToCopy;
			if(i >= 36) mapsInHotbar = true;
		}
		if(numMapArtsToCopy == 0){Main.LOGGER.warn("MapCopy: Nothing to copy"); return;}
		//
		int hotbarButton = -1;
		if(COPY_USE_HOTBAR && !mapsInHotbar){//TODO: Offhand is slot:45, button:40
			for(int i=0; i<9; ++i){
				ItemStack stack = psh.getSlot(PlayerScreenHandler.HOTBAR_START+i).getStack();
				if(stack == null || stack.isEmpty()){hotbarButton = i; break;}
			}
		}
		if(hotbarButton != -1) Main.LOGGER.info("MapCopy: Using exclusively hotbar swaps and shift-click swaps");
		else Main.LOGGER.info("MapCopy: Unable to use hotbar swaps exclusively");
		//
		// Check if there are items already in the crafting 2x2
		int blankMapCraftingSlot = -1;
		int craftingMaps = 0;
		for(int i=PlayerScreenHandler.CRAFTING_INPUT_START; i<PlayerScreenHandler.CRAFTING_INPUT_END; ++i){
			ItemStack stack = psh.getSlot(i).getStack();
			if(stack == null || stack.isEmpty()) continue;
			if(!isBlankMap(stack)) Main.LOGGER.warn("MapCopy: !! Non-blank-map item in crafting 2x2");
			else if(blankMapCraftingSlot != -1) Main.LOGGER.warn("MapCopy: Multiple blank map slots in crafting 2x2");
			else if(hotbarButton != -1 && stack.getCount() < numMapArtsToCopy) Main.LOGGER.warn("MapCopy: Blank map count in 2x2 is insufficient");
			else{
				blankMapCraftingSlot = i;
				craftingMaps = stack.getCount();
			}
			if(!BARF_CLOGS_FOR_MAP_COPY) return;
			++clicksTotal;
			client.interactionManager.clickSlot(0, i, 0, SlotActionType.QUICK_MOVE, client.player); // shift-click back to inventory
//			client.interactionManager.clickSlot(0, i, 0, SlotActionType.THROW, client.player); // barf slot
//			client.interactionManager.clickSlot(0, i, 0, SlotActionType.PICKUP, client.player); // pickup all
//			client.interactionManager.clickSlot(0, ScreenHandler.EMPTY_SPACE_SLOT_INDEX, 0, SlotActionType.PICKUP, client.player); // barf cursor
			psh.updateToClient();
			psh.sendContentUpdates();
		}
//		Main.LOGGER.warn("MapCopy: Blank maps already in crafting 2x2: "+craftingMaps);
		if(blankMapCraftingSlot == -1) blankMapCraftingSlot = PlayerScreenHandler.CRAFTING_INPUT_START;
		// Check if we have enough blank maps
		int blankMaps = craftingMaps;
		for(int i=9; i<=45; ++i) if(isBlankMap(psh.getSlot(i).getStack())) blankMaps += psh.getSlot(i).getStack().getCount();
		if(blankMaps < numMapArtsToCopy){Main.LOGGER.warn("MapCopy: not enough blank maps, need:"+numMapArtsToCopy+", have:"+blankMaps); return;}
		//
		ongoingCopy = true;
		// Move blank maps to the crafting 2x2
		if(craftingMaps < numMapArtsToCopy){
			//Main.LOGGER.info("MapCopy: Getting blank maps into crafting slot:"+blankMapCraftingSlot);
			for(int slot=9; slot<=45; ++slot){
				ItemStack stack = psh.getSlot(slot).getStack();
				if(!isBlankMap(stack)) continue;
				if(hotbarButton != -1 && stack.getCount() < numMapArtsToCopy) continue;
				if(hotbarButton != -1){
					if(clicksTotal+2 > MAX_CLICKS) break;
					clicksTotal+=2;
					client.interactionManager.clickSlot(0, slot, hotbarButton, SlotActionType.SWAP, client.player);
					client.interactionManager.clickSlot(0, blankMapCraftingSlot, hotbarButton, SlotActionType.SWAP, client.player);
//					psh.updateToClient();
//					psh.sendContentUpdates();
				}
				else{
					boolean willHaveLeftoversOnCursor = stack.getCount() + craftingMaps > stack.getMaxCount();
					if(clicksTotal+(willHaveLeftoversOnCursor ? 3 : 2) > MAX_CLICKS) break;
					clicksTotal += willHaveLeftoversOnCursor ? 3 : 2;
					client.interactionManager.clickSlot(0, slot, 0, SlotActionType.PICKUP, client.player); // pickup all
					client.interactionManager.clickSlot(0, blankMapCraftingSlot, 0, SlotActionType.PICKUP, client.player); // place all
					if(willHaveLeftoversOnCursor){
						// What to do with extra blank maps (put back where we got them vs barf)
						if(BARF_CLOGS_FOR_MAP_COPY) client.interactionManager.clickSlot(0, ScreenHandler.EMPTY_SPACE_SLOT_INDEX, 0, SlotActionType.PICKUP, client.player);
						else client.interactionManager.clickSlot(0, slot, 0, SlotActionType.PICKUP, client.player);
					}
				}
				craftingMaps += stack.getCount();
				if(craftingMaps >= numMapArtsToCopy){
					Main.LOGGER.info("MapCopy: Put "+craftingMaps+" blank maps into 2x2 in slot:"+blankMapCraftingSlot+", clicks so far: "+clicksTotal);
					break;
				}
			}
		}
		else Main.LOGGER.info("MapCopy: Blank maps already in crafting slot"+blankMapCraftingSlot+": "+craftingMaps);
		// Copy the filled maps
		// Pick a different crafting slot for the filled maps to go into
		int filledMapCraftingSlot = blankMapCraftingSlot+1;
		if(filledMapCraftingSlot == PlayerScreenHandler.CRAFTING_INPUT_END) filledMapCraftingSlot = PlayerScreenHandler.CRAFTING_INPUT_START;
		Main.LOGGER.info("MapCopy: Filled map crafting slot:"+filledMapCraftingSlot);

		//int mapsCopied = 0;
		for(; fromSlot<=45; ++fromSlot){
			ItemStack stack = psh.getSlot(fromSlot).getStack();
			if(!isMapArt(stack)) continue;
			if(stack.getCount() == stack.getMaxCount()) continue; // Allowing this would break things (since we're putting copied maps back in the original slot)
			boolean willHaveLeftovers = stack.getCount() > 1;
			if(clicksTotal+(willHaveLeftovers ? 5 : 4) > MAX_CLICKS) break;
			//Main.LOGGER.info("MapCopy: copying slot:"+fromSlot);
			if(hotbarButton != -1){
				Main.LOGGER.info("MapCopy: copying map in slot:"+fromSlot);
				client.interactionManager.clickSlot(0, fromSlot, hotbarButton, SlotActionType.SWAP, client.player);
				client.interactionManager.clickSlot(0, filledMapCraftingSlot, hotbarButton, SlotActionType.SWAP, client.player);
//				psh.updateToClient();
//				psh.sendContentUpdates();
				client.interactionManager.clickSlot(0, PlayerScreenHandler.CRAFTING_RESULT_ID, hotbarButton, SlotActionType.SWAP, client.player);
//				psh.updateToClient();
//				psh.sendContentUpdates();
				if(willHaveLeftovers){
					client.interactionManager.clickSlot(0, filledMapCraftingSlot, 0, SlotActionType.QUICK_MOVE, client.player);
				}
				client.interactionManager.clickSlot(0, fromSlot, hotbarButton, SlotActionType.SWAP, client.player);
			}
			else{
				client.interactionManager.clickSlot(0, fromSlot, 1, SlotActionType.PICKUP, client.player); // Pickup half
				client.interactionManager.clickSlot(0, filledMapCraftingSlot, 1, SlotActionType.PICKUP, client.player); // Place one
				if(stack.getCount() > 1){
					++clicksTotal;
					client.interactionManager.clickSlot(0, fromSlot, 0, SlotActionType.PICKUP, client.player); // Place all (since we picked up too many)
				}
				//client.interactionManager.clickRecipe(0, Recipe.CRAFTING), true);
				client.interactionManager.clickSlot(0, PlayerScreenHandler.CRAFTING_RESULT_ID, 0, SlotActionType.PICKUP, client.player); // Pickup result (map x2)
				client.interactionManager.clickSlot(0, fromSlot, 0, SlotActionType.PICKUP, client.player); // Place all
				//++mapsCopied;
			}
			clicksTotal += 4;
		}
//		psh.updateToClient();
//		psh.sendContentUpdates();
		if(fromSlot > 45){
			if(craftingMaps > numMapArtsToCopy){
				++clicksTotal;
				client.interactionManager.clickSlot(0, blankMapCraftingSlot, hotbarButton, SlotActionType.SWAP, client.player); // put back leftovers
			}
			Main.LOGGER.info("MapCopy: DONE, clicks (this iter): "+clicksTotal);
			ongoingCopy = false;
		}
		else{
			Main.LOGGER.info("MapCopy: PART, clicks (this iter): "+clicksTotal);
			final int fromSlotFinal = fromSlot;
			new Timer().schedule(new TimerTask(){@Override public void run(){ongoingCopy=false;lastCopy=0; copyMapArtInInventory(fromSlotFinal, MAX_CLICKS);}}, 250L);
		}
	}*/

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
		boolean mapsInHotbar = false;
		// Figure out how many maps we need to copy
		int numMapArtsToCopy = 0;
		for(int i=9; i<=45; ++i) if(isMapArt(psh.getSlot(i).getStack())){
			++numMapArtsToCopy;
			if(i >= 36) mapsInHotbar = true;
		}
		if(numMapArtsToCopy == 0){Main.LOGGER.warn("MapCopy: Nothing to copy"); return;}
		//
		int hotbarButton = -1;
		if(COPY_USE_HOTBAR && !mapsInHotbar){//TODO: Offhand is slot:45, button:40
			for(int i=8; i>=0; --i){
				ItemStack stack = psh.getSlot(PlayerScreenHandler.HOTBAR_START+i).getStack();
				if(stack == null || stack.isEmpty()){hotbarButton = i; break;}
			}
		}
		if(hotbarButton != -1) Main.LOGGER.info("MapCopy: Using exclusively hotbar swaps and shift-click swaps");
		else Main.LOGGER.info("MapCopy: Unable to use hotbar swaps exclusively");
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
			if(hotbarButton != -1){
				int amountToCraft = bulk && stack.getCount()*2 <= stack.getMaxCount() ? stack.getCount() : 1;
				if(bulk && currentBlankMapsInCrafter < amountToCraft){
					currentBlankMapsInCrafter = getBlankMapsInto2x2(psh, -1, blankMapCraftingSlot, amountToCraft, currentBlankMapsInCrafter, clicks);
					if(currentBlankMapsInCrafter < 1){Main.LOGGER.info("MapCopyBulk(swaps): Ran out of blank maps"); break;}
					amountToCraft = Math.min(amountToCraft, currentBlankMapsInCrafter);
				}
				clicks.add(new ClickEvent(i, hotbarButton, SlotActionType.SWAP)); // Move map to hotbar
				clicks.add(new ClickEvent(filledMapCraftingSlot, hotbarButton, SlotActionType.SWAP)); // Move map to crafting 2x2
				clicks.add(new ClickEvent(PlayerScreenHandler.CRAFTING_RESULT_ID, hotbarButton, SlotActionType.SWAP)); // Craft one
				if(bulk && amountToCraft > 1){
						Main.LOGGER.info("MapCopyBulk(swaps): bulk-copying slot:"+i);
					clicks.add(new ClickEvent(PlayerScreenHandler.CRAFTING_RESULT_ID, 0, SlotActionType.QUICK_MOVE)); // Craft all
				}
				if(stack.getCount() > amountToCraft) clicks.add(new ClickEvent(filledMapCraftingSlot, 0, SlotActionType.QUICK_MOVE)); // Take leftovers
				clicks.add(new ClickEvent(i, hotbarButton, SlotActionType.SWAP)); // Put back in original slot
				currentBlankMapsInCrafter -= amountToCraft;
			}
			else{
				boolean fullBulk = bulk && stack.getCount()*2 <= stack.getMaxCount();
				boolean halfBulk = bulk && !fullBulk && stack.getCount() + stack.getCount()/2 <= stack.getMaxCount();
				int amountToCraft = fullBulk ? stack.getCount() : halfBulk ? stack.getCount()/2 : 1;
				if(currentBlankMapsInCrafter < amountToCraft){ // should only occur in bulk mode
					currentBlankMapsInCrafter = getBlankMapsInto2x2(psh, -1, blankMapCraftingSlot, amountToCraft, currentBlankMapsInCrafter, clicks);
					if(currentBlankMapsInCrafter < 1){Main.LOGGER.info("MapCopyBulk: Ran out of blank maps"); break;}
					if(currentBlankMapsInCrafter < amountToCraft){ // Implies amountToCraft > currentBlankMapsInCrafter
						if(fullBulk){amountToCraft = stack.getCount()/2; fullBulk=false; halfBulk=true;}
						if(halfBulk && currentBlankMapsInCrafter < amountToCraft){amountToCraft = 1; halfBulk=false;}
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
					clicks.add(new ClickEvent(PlayerScreenHandler.CRAFTING_RESULT_ID, 0, SlotActionType.QUICK_MOVE));
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

	public static AbstractKeybind kbLoad;
	final static void registerLoadArtKeybind(final int clicks_per_gt){
		if(clicks_per_gt < 2){Main.LOGGER.error("clicks_per_gt value is set too low, disabling MapArtLoad keybind"); return;}
		KeyBindingHelper.registerKeyBinding(kbLoad = new AbstractKeybind(
				"key."+Main.MOD_ID+".mapart_load_container", InputUtil.Type.KEYSYM, -1, Main.KEYBIND_CATEGORY)
		{
			@Override public void onPressed(){
				Main.LOGGER.info("LoadArtKeybind pressed");
				loadMapArtFromShulker(0, clicks_per_gt, clicks_per_gt);
			}
		});
	}

	public static AbstractKeybind kbCopy;
	final static void registerCopyArtKeybind(final int miliseconds_per_click){
		if(miliseconds_per_click < 1){Main.LOGGER.error("milis_between_clicks value is set too low, disabling MapArtCopy keybind"); return;}
		KeyBindingHelper.registerKeyBinding(kbCopy = new AbstractKeybind(
				"key."+Main.MOD_ID+".mapart_copy", InputUtil.Type.KEYSYM, -1, Main.KEYBIND_CATEGORY)
		{
			@Override public void onPressed(){
				Main.LOGGER.info("CopyArtKeybind pressed");
				copyMapArtInInventory(miliseconds_per_click, false);
			}
		});
	}

	public static AbstractKeybind kbCopyBulk;
	final static void registerCopyBulkArtKeybind(final int miliseconds_per_click){
		if(miliseconds_per_click < 1){Main.LOGGER.error("milis_between_clicks value is set too low, disabling MapArtCopyBulk keybind"); return;}
		KeyBindingHelper.registerKeyBinding(kbCopyBulk = new AbstractKeybind(
				"key."+Main.MOD_ID+".mapart_copy_bulk", InputUtil.Type.KEYSYM, -1, Main.KEYBIND_CATEGORY)
		{
			@Override public void onPressed(){
				Main.LOGGER.info("CopyBulkArtKeybind pressed");
				copyMapArtInInventory(miliseconds_per_click, true);
			}
		});
	}
}