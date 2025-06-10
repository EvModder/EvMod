package net.evmodder.KeyBound.Keybinds;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import net.evmodder.EvLib.Pair;
import net.evmodder.KeyBound.Main;
import net.evmodder.KeyBound.Keybinds.ClickUtils.ClickEvent;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Identifier;

public final class KeybindInventoryOrganize{
	final boolean CLEAN_UNUSED_HOTBAR_SLOTS = true;
	final List<Pair<Integer, Identifier>> layoutMap;

	private String getName(ItemStack stack){
		return stack == null || stack.isEmpty() || stack.getCount() == 0 ? null : Registries.ITEM.getId(stack.getItem()).getPath();
	}
	private int findSlotWithItem(ItemStack[] slots, String itemName, boolean[] skipSlots){
		for(int slot=1; slot<=45; ++slot){
			if(itemName.equals(getName(slots[slot])) && !skipSlots[slot]) return slot;
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

	public void checkDoneSlots(ItemStack[] slots, boolean[] doneSlots){
		//HashSet<Integer> plannedSlots = new HashSet<>();
		boolean[] plannedSlots = Arrays.copyOf(doneSlots, doneSlots.length);
		for(Pair<Integer, Identifier> p : layoutMap){
			final int dstSlot = p.a == -106 ? 45 : p.a;
			if(plannedSlots[dstSlot]) continue;
			final String name = getName(slots[dstSlot]);
			if(p.b.getPath().equals(name)){
				plannedSlots[dstSlot] = true;
				doneSlots[dstSlot] = true;
			}
			else{
				final int srcSlot = findSlotWithItem(slots, p.b.getPath(), plannedSlots);
				if(srcSlot == -1) continue;
				plannedSlots[dstSlot] = true;
				plannedSlots[srcSlot] = true;
				//fakeSwapSlots(srcSlot, dstSlot);
			}
		}
	}

	private boolean ongoingOrganize = false;
	private void organizeInventory(){
		//Main.LOGGER.info("InvOrganize: keybind pressed");
		MinecraftClient client = MinecraftClient.getInstance();
		if(!(client.currentScreen instanceof InventoryScreen is)){/*Main.LOGGER.warn("InvOrganize: not in InventoryScreen"); */return;}

		if(ongoingOrganize) return;

		ItemStack[] simSlots = new ItemStack[46];
		boolean[] emptySlots = new boolean[46];
		boolean[] doneSlots = new boolean[46];
		for(int i=0; i<46; ++i){
			simSlots[i] = is.getScreenHandler().getSlot(i).getStack().copy();
			emptySlots[i] = simSlots[i].isEmpty();
		}

		checkDoneSlots(simSlots, doneSlots);

		ArrayDeque<ClickEvent> clicks = new ArrayDeque<>();
		boolean[] plannedSlots = Arrays.copyOf(doneSlots, doneSlots.length);
		// Equip armor
		for(Pair<Integer, Identifier> p : layoutMap){
			final int dstSlot = p.a == -106 ? 45 : p.a;
			if(plannedSlots[dstSlot]) continue;
			if(p.b.getPath().equals(getName(simSlots[dstSlot]))){
				plannedSlots[dstSlot] = doneSlots[dstSlot] = true;
				continue;
			}
			else{
				final int srcSlot = findSlotWithItem(simSlots, p.b.getPath(), plannedSlots);
				if(srcSlot == -1) continue;
				plannedSlots[dstSlot] = plannedSlots[srcSlot] = true;
			}

			if(dstSlot < 5 || dstSlot > 8) continue; // Skip non-armor slots
			final int srcSlot = findSlotWithItem(simSlots, p.b.getPath(), doneSlots);
			if(srcSlot == -1) continue;
			if(!simSlots[dstSlot].isEmpty()){
				if(simulateShiftClick(simSlots, emptySlots, /*fromArmor=*/true, p.a) != 0){
					Main.LOGGER.warn("InvOrganize: Unable to unequip incorrect armor - no room in inventory!");
					return;
				}
				//client.player.sendMessage(Text.literal("Click: Unequipping armor"), false);
				clicks.add(new ClickEvent(p.a, 0, SlotActionType.QUICK_MOVE));
			}
			//client.player.sendMessage(Text.literal("Click: "+dstSlot+" Equipping armor"), false);
			clicks.add(new ClickEvent(srcSlot, 0, SlotActionType.QUICK_MOVE));
			swapSlots(simSlots, emptySlots, srcSlot, dstSlot);
			doneSlots[dstSlot] = true;
			plannedSlots[srcSlot] = false;
		}
		checkDoneSlots(simSlots, doneSlots);

		// Sort items which are starting FROM the hotbar
		plannedSlots = Arrays.copyOf(doneSlots, doneSlots.length);
		for(Pair<Integer, Identifier> p : layoutMap){
			final int dstSlot = p.a == -106 ? 45 : p.a;
//			if(dstSlot >= 36) usedHotbarAndOffhandSlots[dstSlot-36] = true;
			if(plannedSlots[dstSlot]) continue;
			if(p.b.getPath().equals(getName(simSlots[dstSlot]))){
				plannedSlots[dstSlot] = doneSlots[dstSlot] = true;
				continue;
			}
			else{
				final int srcSlot = findSlotWithItem(simSlots, p.b.getPath(), plannedSlots);
				if(srcSlot == -1) continue;
				plannedSlots[dstSlot] = plannedSlots[srcSlot] = true;
			}

			final int srcSlot = findSlotWithItem(simSlots, p.b.getPath(), doneSlots);
			//if(srcSlot == -1) continue;
			if(srcSlot < 36) continue; // We want items coming FROM hotbar/offhand
			//client.player.sendMessage(Text.literal("Click: "+dstSlot+" Hotbar->Anywhere (sorting"), false);
			if(srcSlot == 45) clicks.add(new ClickEvent(dstSlot, 40, SlotActionType.SWAP));
			else clicks.add(new ClickEvent(dstSlot, srcSlot-36, SlotActionType.SWAP));

			swapSlots(simSlots, emptySlots, srcSlot, dstSlot);
			doneSlots[dstSlot] = true;
			plannedSlots[srcSlot] = false;
		}
		checkDoneSlots(simSlots, doneSlots);

		// Remove junk from hotbar slots
		if(CLEAN_UNUSED_HOTBAR_SLOTS){
			for(int i=36; i<45; ++i) if(!doneSlots[i] && !emptySlots[i]/* && !usedHotbarAndOffhandSlots[i-36]*/){
				final int originalCount = simSlots[i].getCount();
				if(simulateShiftClick(simSlots, emptySlots, /*fromArmor=*/false, i) < originalCount){
					//client.player.sendMessage(Text.literal("Click: "+i+" Hotbar->UpperInv (cleaning)"), false);
					clicks.add(new ClickEvent(i, 0, SlotActionType.QUICK_MOVE));
				}
			}
		}
		checkDoneSlots(simSlots, doneSlots);

		// Sort upper-inventory items
		plannedSlots = Arrays.copyOf(doneSlots, doneSlots.length);
		for(Pair<Integer, Identifier> p : layoutMap){
			final int dstSlot = p.a == -106 ? 45 : p.a;
			if(plannedSlots[dstSlot]) continue;
			if(p.b.getPath().equals(getName(simSlots[dstSlot]))){
				plannedSlots[dstSlot] = doneSlots[dstSlot] = true;
				continue;
			}
			else{
				final int srcSlot = findSlotWithItem(simSlots, p.b.getPath(), plannedSlots);
				if(srcSlot == -1) continue;
				plannedSlots[dstSlot] = plannedSlots[srcSlot] = true;
			}

			if(dstSlot >= 36) continue; // Sort items going INTO hotbar/offhand last
			final int srcSlot = findSlotWithItem(simSlots, p.b.getPath(), doneSlots);
			if(srcSlot == -1) continue;

			// Attempt to pick an empty hotbar/offhand slot to swap with
			int hb = 40; for(int i=0; i<9; ++i) if(emptySlots[i+36]){hb = i; break;}
			//client.player.sendMessage(Text.literal("Click: "+dstSlot+" UpperInv->Hotbar->UpperInv"), false);
			clicks.add(new ClickEvent(srcSlot, hb, SlotActionType.SWAP));
			clicks.add(new ClickEvent(dstSlot, hb, SlotActionType.SWAP));
			if(!emptySlots[dstSlot] || !emptySlots[hb == 40 ? 45 : hb+36]){
				//Main.LOGGER.info("putting back original displaced item");
				// Put back the displaced hotbar/offhand item
				clicks.add(new ClickEvent(srcSlot, hb, SlotActionType.SWAP));
			}
			swapSlots(simSlots, emptySlots, srcSlot, dstSlot);
			doneSlots[dstSlot] = true;
			plannedSlots[srcSlot] = false;
		}
		checkDoneSlots(simSlots, doneSlots);

		// Fill hotbar slots
		for(Pair<Integer, Identifier> p : layoutMap){
			final int dstSlot = p.a == -106 ? 45 : p.a;
			if(doneSlots[dstSlot]) continue;
			//if(needEarlier.contains(p.b.getPath())) continue;
			if(p.b.getPath().equals(getName(simSlots[dstSlot]))){doneSlots[dstSlot]=true; continue;}
			//needEarlier.add(p.b.getPath());

			if(dstSlot < 36) continue; // items going INTO hotbar/offhand
			final int srcSlot = findSlotWithItem(simSlots, p.b.getPath(), doneSlots);
			if(srcSlot == -1) continue;
			//client.player.sendMessage(Text.literal("Click: "+dstSlot+" UpperInv->Hotbar"), false);
			if(dstSlot == 45) clicks.add(new ClickEvent(srcSlot, 40, SlotActionType.SWAP));
			else clicks.add(new ClickEvent(srcSlot, dstSlot-36, SlotActionType.SWAP));

			swapSlots(simSlots, emptySlots, srcSlot, dstSlot);
			doneSlots[dstSlot] = true;
			//needEarlier.remove(p.b.getPath());
		}
		final int numClicks = clicks.size();
		ongoingOrganize = true;
		Main.inventoryUtils.executeClicks(clicks,
				//_->true,
				_0->{
					//client.player.sendMessage(Text.literal("click "+c.slotId()+" "+c.button()+" "+c.actionType()), false);
					return true;
				},
				()->{
					//client.player.sendMessage(Text.literal("InvOrganize: done! clicks required: "+numClicks), false);
					Main.LOGGER.info("InvOrganize: done! clicks required: "+numClicks);
					ongoingOrganize = false;
				});
	}

	public KeybindInventoryOrganize(String keybind_name, String layout){
		layoutMap =
		Arrays.stream(layout.substring(1, layout.length()-1).split(","))
		.map(s -> {
				int idx = Integer.parseInt(s.substring(0, s.indexOf(':')));
				String iname = s.substring(s.indexOf(':')+1);
				Identifier id = Identifier.of(iname);
				if(!Registries.ITEM.containsId(id)){
					Main.LOGGER.error("InvOrganize: Unknown item: "+iname);
					return null;
				}
				return new Pair<>(idx, id);
			}
		)
		.filter(p -> p != null)
		.toList();
		KeyBindingHelper.registerKeyBinding(new Keybind(keybind_name, this::organizeInventory, InventoryScreen.class::isInstance));
	}
}