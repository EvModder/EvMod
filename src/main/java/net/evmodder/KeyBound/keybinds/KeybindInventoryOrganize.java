package net.evmodder.KeyBound.keybinds;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.IntStream;
import net.evmodder.KeyBound.Main;
import net.evmodder.KeyBound.config.Configs;
import net.evmodder.KeyBound.config.OptionInventoryRestockLimit;
import net.evmodder.KeyBound.keybinds.ClickUtils.ClickEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Identifier;

public final class KeybindInventoryOrganize{
	final boolean CLEAN_UNUSED_HOTBAR_SLOTS = true, RESTOCK_ONLY_1_SLOT_PER_TYPE = true, DONT_RESTOCK_IF_ALREADY_IN_INV = true;
	record SlotAndItemName(int slot, String name){
		@Override public boolean equals(Object o){
			return o != null && o instanceof SlotAndItemName s && slot == s.slot && /*id==null?s.id==null:*/name.equals(s.name);
		}
		@Override public int hashCode(){
			return name.hashCode() + slot;
		}
	}
	List<SlotAndItemName> layoutMap;

	private String getName(ItemStack stack){
		return stack == null || stack.isEmpty() ? null : Registries.ITEM.getId(stack.getItem()).getPath();
	}
	private int findSlotWithItem(ItemStack[] slots, String itemName, boolean[] skipSlots){
//		for(int slot=1; slot<=45; ++slot){
		for(int slot=slots.length-1; slot>=0; --slot){
			if(!skipSlots[slot] && itemName.equals(getName(slots[slot]))) return slot;
		}
		return -1;
	}

	private void swapSlots(ItemStack[] slots, boolean[] emptySlots, int i, int j){
		final boolean tmpEmpty = emptySlots[i];
		emptySlots[i] = emptySlots[j];
		emptySlots[j] = tmpEmpty;
		final ItemStack tempStack = slots[i].copy();
		slots[i] = slots[j];
		slots[j] = tempStack;
	}

	// NOTE: BREAKS for various unsupported shift-click cases (current support: armorslot->inv, inv-hotbar->inv-upper)
	private int simulateShiftClick(ItemStack[] simSlots, boolean[] emptySlots, boolean fromArmor, int i){
		final ItemStack stackToMove = simSlots[i];
		for(int j=9; j<(fromArmor? 45 : 36); ++j){
			if(j == i) continue;
			if(emptySlots[j]){
				simSlots[j] = stackToMove.copy();
				simSlots[i] = new ItemStack(Items.AIR);
				emptySlots[j] = false;
				emptySlots[i] = true;
				return 0;
			}
			if(!stackToMove.isStackable()) continue;
			final ItemStack stackToFill = simSlots[j];
			if(stackToFill.getCount() == stackToFill.getMaxCount()) continue;
			if(!ItemStack.areItemsAndComponentsEqual(stackToFill, stackToMove)) continue;
			final int amtToMove = Math.min(stackToFill.getMaxCount()-stackToFill.getCount(), stackToMove.getCount());
			stackToFill.setCount(stackToFill.getCount() + amtToMove);
			stackToMove.setCount(stackToMove.getCount() - amtToMove);
			if(stackToMove.getCount() == 0){
				emptySlots[i] = true;
				return 0;
			}
		}
		return stackToMove.getCount();
	}

	public boolean[] checkDoneSlots(ItemStack[] slots, boolean[] doneSlots, boolean isInvScreen){
		//HashSet<Integer> plannedSlots = new HashSet<>();
		final boolean[] plannedSlots = Arrays.copyOf(doneSlots, doneSlots.length);
		for(SlotAndItemName p : layoutMap){
			if(!isInvScreen && (p.slot == -106 || p.slot == 45)) continue;
			final int dstSlot = p.slot == -106 ? 45 : p.slot + (isInvScreen ? 0 : slots.length-45);
//			Main.LOGGER.info("checkDoneSlots(): checking dstSlot: "+dstSlot+" (item: "+p.b().getPath()+")");
			if(plannedSlots[dstSlot]) continue;
			final String dstName;
			if(isInvScreen || dstSlot >= slots.length-36) dstName = getName(slots[dstSlot]);
			else if(dstSlot < slots.length-40){Main.LOGGER.warn("InvOrganize: Unable to restock Container->CraftingGrid");continue;}
			else dstName = getName(MinecraftClient.getInstance().player.getInventory().getArmorStack(p.slot-5));
			if(p.name.equals(dstName)){
				plannedSlots[dstSlot] = doneSlots[dstSlot] = true;
//				Main.LOGGER.info("checkDoneSlots(): done slot: "+dstSlot+" (item: "+p.b().getPath()+")");
			}
			else{
				final int srcSlot = findSlotWithItem(slots, p.name, plannedSlots);
				if(srcSlot == -1){
//					Main.LOGGER.info("checkDoneSlots(): cant find "+p.b().getPath()+" for slot "+dstSlot);
					continue;
				}
				plannedSlots[dstSlot] = plannedSlots[srcSlot] = true;
//				Main.LOGGER.info("checkDoneSlots(): planned slots: "+srcSlot+"->"+dstSlot+" (item: "+p.b().getPath()+")");
				//fakeSwapSlots(srcSlot, dstSlot);
			}
		}
		return plannedSlots;
	}

	private int depth; // TODO: instead of running up to 3 times, just sort things properly the first time
	public void organizeInventory(final boolean RESTOCK_ONLY, Runnable onComplete){
		//Main.LOGGER.info("InvOrganize: keybind pressed");
		if(Main.clickUtils.hasOngoingClicks()) return;

		MinecraftClient client = MinecraftClient.getInstance();
		if(!(client.currentScreen instanceof HandledScreen hs)){
			Main.LOGGER.warn("InvOrganize: not in InventoryScreen");
			return;
		}
		if(hs.getScreenHandler().slots.size() < 46){
			Main.LOGGER.warn("InvOrganize: Inventory too small to restock from (due to limitations in current code)");
			return;
		}
		final boolean isInvScreen = client.currentScreen instanceof InventoryScreen;

		ItemStack[] simSlots = new ItemStack[hs.getScreenHandler().slots.size()];
		boolean[] emptySlots = new boolean[simSlots.length];
		boolean[] doneSlots = new boolean[simSlots.length];
		for(int i=0; i<simSlots.length; ++i){
			simSlots[i] = hs.getScreenHandler().getSlot(i).getStack().copy();
			emptySlots[i] = simSlots[i].isEmpty();
		}
		final int HOTBAR_START = isInvScreen ? 36 : simSlots.length-9;
		final int MAIN_INV_START = isInvScreen ? 9 : simSlots.length-36;

		// In restock mode, don't bother sorting anything in the player's inventory
//		if(RESTOCK_MODE) for(int i=MAIN_INV_START; i<simSlots.length; ++i) doneSlots[i] = true;

//		Main.LOGGER.info("InvOrganize: "+layoutMap.size()+" mappings, isInvScreen="+isInvScreen+", numSlots="+simSlots.length
//				+", hotbarStart="+HOTBAR_START+", invStart="+MAIN_INV_START);

		ArrayDeque<ClickEvent> clicks = new ArrayDeque<>();
		checkDoneSlots(simSlots, doneSlots, isInvScreen);
		boolean[] plannedSlots;
		if(isInvScreen){
			plannedSlots = Arrays.copyOf(doneSlots, doneSlots.length);
			// Equip armor
			for(SlotAndItemName p : layoutMap){
				final int dstSlot = p.slot == -106 ? 45 : p.slot;
				if(plannedSlots[dstSlot]) continue;
				if(p.name.equals(getName(simSlots[dstSlot]))){
					plannedSlots[dstSlot] = doneSlots[dstSlot] = true;
					continue;
				}
				else{
					final int srcSlot = findSlotWithItem(simSlots, p.name, plannedSlots);
					if(srcSlot == -1) continue;
					plannedSlots[dstSlot] = plannedSlots[srcSlot] = true;
				}

				if(dstSlot < 5 || dstSlot > 8) continue; // Skip non-armor slots
				final int srcSlot = findSlotWithItem(simSlots, p.name, doneSlots);
				if(srcSlot == -1) continue;
				if(!simSlots[dstSlot].isEmpty()){
					if(simulateShiftClick(simSlots, emptySlots, /*fromArmor=*/true, p.slot) != 0){
						Main.LOGGER.warn("InvOrganize: Unable to unequip incorrect armor - no room in inventory!");
						return;
					}
					//client.player.sendMessage(Text.literal("Click: Unequipping armor"), false);
					clicks.add(new ClickEvent(p.slot, 0, SlotActionType.QUICK_MOVE));
				}
				//client.player.sendMessage(Text.literal("Click: "+dstSlot+" Equipping armor"), false);
				clicks.add(new ClickEvent(srcSlot, 0, SlotActionType.QUICK_MOVE));
				swapSlots(simSlots, emptySlots, srcSlot, dstSlot);
				doneSlots[dstSlot] = true;
				plannedSlots[srcSlot] = false;
			}
			checkDoneSlots(simSlots, doneSlots, isInvScreen);
		}

		if(!RESTOCK_ONLY){
			// Sort items which are starting FROM the hotbar
			plannedSlots = Arrays.copyOf(doneSlots, doneSlots.length);
			for(SlotAndItemName p : layoutMap){
				if(!isInvScreen && (p.slot == -106 || p.slot == 45)) continue;
				final int dstSlot = p.slot == -106 ? 45 : p.slot + MAIN_INV_START-9;
//				if(dstSlot >= 36) usedHotbarAndOffhandSlots[dstSlot-36] = true;
				if(plannedSlots[dstSlot]) continue;
				if(p.name.equals(getName(simSlots[dstSlot]))){
					plannedSlots[dstSlot] = doneSlots[dstSlot] = true;
					continue;
				}
				else{
					final int srcSlot = findSlotWithItem(simSlots, p.name, plannedSlots);
					if(srcSlot == -1) continue;
					plannedSlots[dstSlot] = plannedSlots[srcSlot] = true;
				}

				final int srcSlot = findSlotWithItem(simSlots, p.name, doneSlots);
				//if(srcSlot == -1) continue;
				if(srcSlot < HOTBAR_START) continue; // We want items coming FROM hotbar/offhand
//				client.player.sendMessage(Text.literal("Click: "+srcSlot+"->"+dstSlot+", Hotbar->Anywhere (sorting)"), false);
				if(isInvScreen && srcSlot == 45) clicks.add(new ClickEvent(dstSlot, 40, SlotActionType.SWAP));
				else clicks.add(new ClickEvent(dstSlot, srcSlot-HOTBAR_START, SlotActionType.SWAP));

				swapSlots(simSlots, emptySlots, srcSlot, dstSlot);
				doneSlots[dstSlot] = true;
				plannedSlots[srcSlot] = false;
			}
			checkDoneSlots(simSlots, doneSlots, isInvScreen);
		}

		// Remove junk from hotbar slots
		if(CLEAN_UNUSED_HOTBAR_SLOTS && isInvScreen){
			for(int i=HOTBAR_START; i<simSlots.length; ++i) if(!doneSlots[i] && !emptySlots[i]/* && !usedHotbarAndOffhandSlots[i-HOTBAR_START]*/){
				final int originalCount = simSlots[i].getCount();
				if(simulateShiftClick(simSlots, emptySlots, /*fromArmor=*/false, i) < originalCount){
//					client.player.sendMessage(Text.literal("Click: "+i+" Hotbar->UpperInv (cleaning)"), false);
					clicks.add(new ClickEvent(i, 0, SlotActionType.QUICK_MOVE));
				}
			}
			checkDoneSlots(simSlots, doneSlots, isInvScreen);
		}

		// Stuff for Container->Inventory restock logic:
		final boolean CAN_RESTOCK_FROM_CONTAINER;
		final HashSet<Item> alreadyRestockedItems;
		final HashMap<Item, Integer> countainerCounts;
		final HashSet<Item> itemsInInv;
		if(isInvScreen){alreadyRestockedItems = null; countainerCounts = null; itemsInInv = null; CAN_RESTOCK_FROM_CONTAINER = true;}
		else{
			if(!RESTOCK_ONLY_1_SLOT_PER_TYPE) alreadyRestockedItems = null;
			else{
				alreadyRestockedItems = new HashSet<>();
				for(SlotAndItemName p : layoutMap){
					if(p.slot == -106 || p.slot == 45 || p.slot < 9) continue;
					final ItemStack stack = simSlots[p.slot + simSlots.length-45];
					if(p.name.equals(getName(stack))) alreadyRestockedItems.add(stack.getItem());
				}
			}
			final OptionInventoryRestockLimit limits = (OptionInventoryRestockLimit)Configs.Hotkeys.INV_RESTOCK_LIMITS.getOptionListValue();
			final boolean LEAVE_ONE = limits == OptionInventoryRestockLimit.LEAVE_ONE_ITEM || limits == OptionInventoryRestockLimit.LEAVE_ONE_STACK;
			if(LEAVE_ONE){
				CAN_RESTOCK_FROM_CONTAINER = true;
				countainerCounts = new HashMap<>();
				for(int i=0; i<MAIN_INV_START; ++i){
					Integer occ = countainerCounts.get(simSlots[i].getItem());
					countainerCounts.put(simSlots[i].getItem(), occ == null ? 1 : occ+1);
				}
//				Main.LOGGER.info("InvOrganize: countainerCounts="+countainerCounts);
			}
			else/* if(limits == InventoryRestockLimits.LEAVE_UNLESS_ALL_RESUPPLY)*/{
				HashSet<String> itemNamesInInv = new HashSet<>();
				layoutMap.stream().map(SlotAndItemName::name).forEach(itemNamesInInv::add);
				CAN_RESTOCK_FROM_CONTAINER = IntStream.range(0, MAIN_INV_START).allMatch(i -> itemNamesInInv.contains(getName(simSlots[i])));
				countainerCounts = null;
			}
			if(!DONT_RESTOCK_IF_ALREADY_IN_INV) itemsInInv = null;
			else{
				itemsInInv = new HashSet<>();
				for(int i=MAIN_INV_START; i<simSlots.length; ++i) itemsInInv.add(simSlots[i].getItem());
//				Main.LOGGER.info("InvOrganize: itemsInInv="+itemsInInv);
			}
		}

		// Sort upper-inventory items
		plannedSlots = Arrays.copyOf(doneSlots, doneSlots.length);
		int hb = -1; for(int i=0; i<9; ++i) if(emptySlots[i+HOTBAR_START]){hb = i; break;} // Find usable hb slot
		if(hb == -1 && isInvScreen) hb = 40;
		if(hb != -1) for(SlotAndItemName p : layoutMap){
			if(!isInvScreen && (p.slot == -106 || p.slot == 45)) continue;
			final int dstSlot = p.slot== -106 ? 45 : p.slot + MAIN_INV_START-9;
			if(plannedSlots[dstSlot]) continue;
			if(RESTOCK_ONLY && !simSlots[dstSlot].isEmpty()) continue;
			if(p.name.equals(getName(simSlots[dstSlot]))){
				plannedSlots[dstSlot] = doneSlots[dstSlot] = true;
				continue;
			}
			else{
				final int srcSlot = findSlotWithItem(simSlots, p.name, plannedSlots);
				if(srcSlot == -1) continue;
				plannedSlots[dstSlot] = plannedSlots[srcSlot] = true;
			}

			if(dstSlot >= HOTBAR_START) continue; // Sort items going INTO hotbar/offhand last
			if(dstSlot < MAIN_INV_START){ // Armor slot that got missed during armor sort section! Likely not in an invScreen
				assert !isInvScreen;
				continue;
			}
			final int srcSlot = findSlotWithItem(simSlots, p.name, doneSlots);
			if(srcSlot == -1 || doneSlots[srcSlot]) continue;
			if(RESTOCK_ONLY && srcSlot >= MAIN_INV_START) continue;
			if(!CAN_RESTOCK_FROM_CONTAINER && srcSlot < MAIN_INV_START) continue;
			if(RESTOCK_ONLY_1_SLOT_PER_TYPE && srcSlot < MAIN_INV_START && alreadyRestockedItems.contains(simSlots[srcSlot].getItem())) continue;
			if(DONT_RESTOCK_IF_ALREADY_IN_INV && srcSlot < MAIN_INV_START && itemsInInv.contains(simSlots[srcSlot].getItem())) continue;
			if(srcSlot < MAIN_INV_START && countainerCounts != null){ // Avoid taking 100% of any item type from src container
				Integer occ = countainerCounts.get(simSlots[srcSlot].getItem());
				assert occ != null && occ > 0;
				if(occ > 1) countainerCounts.put(simSlots[srcSlot].getItem(), occ-1);
				else{
//					final int count = simSlots[srcSlot].getCount();
//					if(count < 2) continue;
//					clicks.add(new ClickEvent(srcSlot, count <= 3 ? 1 : 0, SlotActionType.PICKUP));
//					if(count > 3) clicks.add(new ClickEvent(srcSlot, 1, SlotActionType.PICKUP));
//					clicks.add(new ClickEvent(dstSlot, 0, SlotActionType.PICKUP));
//					simSlots[srcSlot].setCount(count/2);
//					doneSlots[dstSlot] = true;
					continue;
				}
			}
//			client.player.sendMessage(Text.literal("Click: "+srcSlot+"->"+dstSlot+" UpperInv->Hotbar->UpperInv"), false);
			clicks.add(new ClickEvent(srcSlot, hb, SlotActionType.SWAP));
			clicks.add(new ClickEvent(dstSlot, hb, SlotActionType.SWAP));
			if(!emptySlots[dstSlot] || !emptySlots[hb == 40 ? 45 : hb+36]){
				//Main.LOGGER.info("putting back original displaced item");
				// Put back the displaced hotbar/offhand item
				clicks.add(new ClickEvent(srcSlot, hb, SlotActionType.SWAP));
			}
			swapSlots(simSlots, emptySlots, srcSlot, dstSlot);
			if(RESTOCK_ONLY_1_SLOT_PER_TYPE && !isInvScreen) alreadyRestockedItems.add(simSlots[dstSlot].getItem());
			doneSlots[dstSlot] = true;
			plannedSlots[srcSlot] = false;
		}

		// Fill hotbar slots
		for(SlotAndItemName p : layoutMap){
			if(!isInvScreen && (p.slot == -106 || p.slot == 45)) continue;
			final int dstSlot = p.slot == -106 ? 45 : p.slot + MAIN_INV_START-9;
			if(doneSlots[dstSlot]) continue;
			if(RESTOCK_ONLY && !simSlots[dstSlot].isEmpty()) continue;
			//if(needEarlier.contains(p.b().getPath())) continue;
			if(p.name.equals(getName(simSlots[dstSlot]))){doneSlots[dstSlot]=true; continue;}
			//needEarlier.add(p.b().getPath());

			if(dstSlot < HOTBAR_START) continue; // items going INTO hotbar/offhand
			final int srcSlot = findSlotWithItem(simSlots, p.name, doneSlots);
			if(srcSlot == -1 || doneSlots[srcSlot]) continue;
			if(RESTOCK_ONLY && srcSlot >= MAIN_INV_START) continue;
			if(!CAN_RESTOCK_FROM_CONTAINER && srcSlot < MAIN_INV_START) continue;
			if(RESTOCK_ONLY_1_SLOT_PER_TYPE && srcSlot < MAIN_INV_START && alreadyRestockedItems.contains(simSlots[srcSlot].getItem())) continue;
			if(DONT_RESTOCK_IF_ALREADY_IN_INV && srcSlot < MAIN_INV_START && itemsInInv.contains(simSlots[srcSlot].getItem())) continue;
			if(srcSlot < MAIN_INV_START && countainerCounts != null){ // Avoid taking 100% of any item type from src container
				Integer occ = countainerCounts.get(simSlots[srcSlot].getItem());
				assert occ != null && occ > 0;
				if(occ > 1) countainerCounts.put(simSlots[srcSlot].getItem(), occ-1);
				else{
//					final int count = simSlots[srcSlot].getCount();
//					if(count < 2) continue;
//					clicks.add(new ClickEvent(srcSlot, count <= 3 ? 1 : 0, SlotActionType.PICKUP));
//					if(count > 3) clicks.add(new ClickEvent(srcSlot, 1, SlotActionType.PICKUP));
//					clicks.add(new ClickEvent(dstSlot, 0, SlotActionType.PICKUP));
//					simSlots[srcSlot].setCount(count/2);
//					doneSlots[dstSlot] = true;
					continue;
				}
			}
//			client.player.sendMessage(Text.literal("Click: "+srcSlot+"->"+dstSlot+" UpperInv->Hotbar"), false);
			if(isInvScreen && dstSlot == 45) clicks.add(new ClickEvent(srcSlot, 40, SlotActionType.SWAP));
			else clicks.add(new ClickEvent(srcSlot, dstSlot-HOTBAR_START, SlotActionType.SWAP));

			swapSlots(simSlots, emptySlots, srcSlot, dstSlot);
			if(RESTOCK_ONLY_1_SLOT_PER_TYPE && !isInvScreen) alreadyRestockedItems.add(simSlots[dstSlot].getItem());
			doneSlots[dstSlot] = true;
			//needEarlier.remove(p.b().getPath());
		}

		// Handle leftover armor for container -> inventory
		if(!isInvScreen && CAN_RESTOCK_FROM_CONTAINER) for(SlotAndItemName p : layoutMap){
			if(p.slot == -106 || p.slot == 45) continue;
			if(p.slot > 8) continue;
			if(!client.player.getInventory().getArmorStack(3 - (p.slot - 5)).isEmpty()) continue; // -5 to get armor index, 3-x to reverse order
			final int srcSlot = findSlotWithItem(simSlots, p.name, doneSlots);
			if(srcSlot == -1 || srcSlot >= MAIN_INV_START) continue;
			if(srcSlot < MAIN_INV_START && countainerCounts != null){ // Avoid taking 100% of any item type from src container
				Integer occ = countainerCounts.get(simSlots[srcSlot].getItem());
				assert occ != null && occ > 0;
				if(occ > 1) countainerCounts.put(simSlots[srcSlot].getItem(), occ-1);
				else{
//					final int count = simSlots[srcSlot].getCount();
//					if(count < 2) continue; // Most armor is unstackable and will continue here, but carved_pumpkins can be handled below
//					if(count <= 3) clicks.add(new ClickEvent(srcSlot, 1, SlotActionType.PICKUP)); // Pickup half (leaving one)
//					else{
//						clicks.add(new ClickEvent(srcSlot, 0, SlotActionType.PICKUP)); // Pickup all
//						clicks.add(new ClickEvent(srcSlot, 1, SlotActionType.PICKUP)); // Put back one
//					}
//					clicks.add(new ClickEvent(srcSlot, 0, SlotActionType.QUICK_MOVE)); // Shift-click the one
//					clicks.add(new ClickEvent(srcSlot, 0, SlotActionType.PICKUP)); // Put back rest
					continue;
				}
			}
//			client.player.sendMessage(Text.literal("Click: "+srcSlot+" Container->Inventory (armor slot unavailable)"), false);
			clicks.add(new ClickEvent(srcSlot, 0, SlotActionType.QUICK_MOVE));
		}

		final int numClicks = clicks.size();
		if(numClicks == 0){
			depth = 0;
//			Main.LOGGER.info("InvOrganize: no clicks required");
			if(onComplete != null) onComplete.run();
			return;
		}
		Main.clickUtils.executeClicks(clicks,
				_0->true,
//				_0->{
//					//client.player.sendMessage(Text.literal("click "+c.slotId()+" "+c.button()+" "+c.actionType()), false);
//					return true;
//				},
				()->{
//					client.player.sendMessage(Text.literal("InvOrganize: done! clicks required: "+numClicks), false);
					Main.LOGGER.info("InvOrganize: done! clicks required: "+numClicks);
					if(++depth <= 3 && !RESTOCK_ONLY) organizeInventory(false, onComplete); // Try running again in case of straggler items
					else{
						depth = 0;
						if(onComplete != null) onComplete.run();
					}
				});
	}

	public void refreshLayout(List<String> strings){
		layoutMap =
//		Arrays.stream(layout.substring(1, layout.length()-1).split(","))
		strings.stream()
		.map(s -> s.replaceAll("\\s", ""))
		.filter(s -> !s.startsWith("#") && s.indexOf(':') != -1)
		.map(s -> {
			final int sep = s.indexOf(':');
			int slot = Integer.parseInt(s.substring(0, sep));
			Identifier id = Identifier.of(s.substring(sep+1));
			if(!Registries.ITEM.containsId(id)){
				Main.LOGGER.error("InvOrganize: Unknown item: "+s.substring(sep+1));
				return null;
			}
			return new SlotAndItemName(slot, id.getPath());
		})
		.filter(p -> p != null)
		.toList();
	}

	public KeybindInventoryOrganize(List<String> strings){
		refreshLayout(strings);
//		new Keybind(keybind_name, ()->organizeInventory(false, null), HandledScreen.class::isInstance, isFirstRegistered ? GLFW.GLFW_KEY_E : -1);
//		isFirstRegistered = false;
	}
}