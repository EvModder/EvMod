package net.evmodder.KeyBound.Keybinds;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import net.evmodder.EvLib.Pair;
import net.evmodder.KeyBound.Main;
import net.evmodder.KeyBound.Keybinds.InventoryUtils.ClickEvent;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Identifier;

public final class KeybindInventoryOrganize{
	final boolean CLEAN_UNUSED_HOTBAR_SLOTS = true;
	final List<Pair<Integer, Identifier>> layoutMap;
	private String getName(ItemStack stack){
		return stack == null || stack.isEmpty() || stack.getCount() == 0 ? null : Registries.ITEM.getId(stack.getItem()).getPath();
	}
	private int findSlotWithItem(PlayerScreenHandler psh, String itemName, boolean[] skipSlots){
		for(int slot=1; slot<=45; ++slot){
			if(itemName.equals(getName(psh.getSlot(slot).getStack())) && !skipSlots[slot]) return slot;
		}
		return -1;
	}

	// NOTE: BREAKS for various unsupported shift-click cases (current support: armorslot->inv, inv-hotbar->inv-upper)
	private int simulateShiftClick(PlayerScreenHandler psh, boolean[] emptySlots, boolean fromArmor, int i){
		final ItemStack stackToMove = psh.getSlot(i).getStack();
		for(int j=9; j<(fromArmor? 45 : 36); ++j){
			if(j == i) continue;
			if(emptySlots[j]){
				psh.getSlot(j).setStack(stackToMove.copy());
				stackToMove.setCount(0);
				emptySlots[j] = false;
				emptySlots[i] = true;
				return 0;
			}
			if(!stackToMove.isStackable()) continue;
			final ItemStack stackToFill = psh.getSlot(j).getStack();
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

	private void organizeInventory(){
		Main.LOGGER.info("InvOrganize: keybind pressed");
		MinecraftClient client = MinecraftClient.getInstance();
		if(!(client.currentScreen instanceof InventoryScreen is)){Main.LOGGER.warn("InvOrganize: not in InventoryScreen"); return;}

		PlayerScreenHandler psh = is.getScreenHandler();
		boolean[] emptySlots = new boolean[46];
		boolean[] doneSlots = new boolean[46];
		for(int i=0; i<46; ++i) emptySlots[i] = psh.getSlot(i).getStack() == null || psh.getSlot(i).getStack().isEmpty();

		for(Pair<Integer, Identifier> p : layoutMap){
			final int dstSlot = p.a == -106 ? 45 : p.a;
			if(doneSlots[dstSlot]) continue;
			if(p.b.getPath().equals(getName(psh.getSlot(dstSlot).getStack()))) doneSlots[dstSlot]=true;
		}

		ArrayDeque<ClickEvent> clicks = new ArrayDeque<>();

		// Equip armor
		for(Pair<Integer, Identifier> p : layoutMap){
			if(p.a < 5 || p.a > 8) continue; // Skip non-armor slots
			if(doneSlots[p.a]) continue;
			if(p.b.getPath().equals(getName(psh.getSlot(p.a).getStack()))){doneSlots[p.a]=true; continue;}
			final int srcSlot = findSlotWithItem(psh, p.b.getPath(), doneSlots);
			if(srcSlot == -1) continue;
			if(!psh.getSlot(p.a).getStack().isEmpty()){
				if(simulateShiftClick(psh, emptySlots, /*fromArmor=*/true, p.a) != 0){
					Main.LOGGER.warn("InvOrganize: Unable to equip incorrect armor - no room in inventory!");
					return;
				}
				clicks.add(new ClickEvent(p.a, 0, SlotActionType.QUICK_MOVE));
			}
			clicks.add(new ClickEvent(srcSlot, 0, SlotActionType.QUICK_MOVE));
			emptySlots[p.a] = false;
			emptySlots[srcSlot] = true;
			doneSlots[p.a] = true;
		}
		// Free up hotbar slots
		for(Pair<Integer, Identifier> p : layoutMap){
			final int dstSlot = p.a == -106 ? 45 : p.a;
			if(doneSlots[dstSlot]) continue;
			if(p.b.getPath().equals(getName(psh.getSlot(p.a).getStack()))){doneSlots[p.a]=true; continue;}
			if(dstSlot >= 36) continue; // Sort items going INTO hotbar/offhand last
			final int srcSlot = findSlotWithItem(psh, p.b.getPath(), doneSlots);
			//if(srcSlot == -1) continue;
			if(srcSlot < 36) continue; // We want items coming FROM hotbar/offhand
			if(srcSlot == 45) clicks.add(new ClickEvent(dstSlot, 40, SlotActionType.SWAP));
			else clicks.add(new ClickEvent(dstSlot, srcSlot-36, SlotActionType.SWAP));

			final ItemStack tempStack = psh.getSlot(srcSlot).getStack().copy();
			psh.getSlot(srcSlot).setStack(psh.getSlot(dstSlot).getStack());
			psh.getSlot(dstSlot).setStack(tempStack);
			final boolean tempEmpty = emptySlots[srcSlot];
			emptySlots[srcSlot] = emptySlots[dstSlot];
			emptySlots[dstSlot] = tempEmpty;
			doneSlots[dstSlot] = true;
		}
		// Remove junk from hotbar slots
		if(CLEAN_UNUSED_HOTBAR_SLOTS){
			for(int i=36; i<45; ++i) if(!doneSlots[i] && !emptySlots[i]){
				final int originalCount = psh.getSlot(i).getStack().getCount();
				if(simulateShiftClick(psh, emptySlots, /*fromArmor=*/false, i) < originalCount){
					clicks.add(new ClickEvent(i, 0, SlotActionType.QUICK_MOVE));
				}
			}
		}
		// Sort upper-inventory items
		for(Pair<Integer, Identifier> p : layoutMap){
			final int dstSlot = p.a == -106 ? 45 : p.a;
			if(doneSlots[dstSlot]) continue;
			if(p.b.getPath().equals(getName(psh.getSlot(p.a).getStack()))){doneSlots[p.a]=true; continue;}
			if(dstSlot >= 36) continue; // Sort items going INTO hotbar/offhand last
			final int srcSlot = findSlotWithItem(psh, p.b.getPath(), doneSlots);
			if(srcSlot == -1) continue;

			int hb = 40;
			// if dstSlot is currently empty, attempt to pick an empty hotbar/offhand slot to swap with
			if(emptySlots[dstSlot] && !emptySlots[45]) for(int i=0; i<9; ++i) if(emptySlots[i+36]){hb = i; break;}
			clicks.add(new ClickEvent(srcSlot, hb, SlotActionType.SWAP));
			clicks.add(new ClickEvent(dstSlot, hb, SlotActionType.SWAP));
			if(!emptySlots[dstSlot] || !emptySlots[hb == 40 ? 45 : hb+36]){
				//Main.LOGGER.info("putting back original displaced item");
				// Put back the displaced hotbar/offhand item
				clicks.add(new ClickEvent(srcSlot, hb, SlotActionType.SWAP));
			}
			final ItemStack tempStack = psh.getSlot(srcSlot).getStack().copy();
			psh.getSlot(srcSlot).setStack(psh.getSlot(dstSlot).getStack());
			psh.getSlot(dstSlot).setStack(tempStack);
			final boolean tempEmpty = emptySlots[srcSlot];
			emptySlots[srcSlot] = emptySlots[dstSlot];
			emptySlots[dstSlot] = tempEmpty;
			doneSlots[dstSlot] = true;
		}
		// Fill hotbar slots
		for(Pair<Integer, Identifier> p : layoutMap){
			final int dstSlot = p.a == -106 ? 45 : p.a;
			if(doneSlots[dstSlot]) continue;
			if(p.b.getPath().equals(getName(psh.getSlot(p.a).getStack()))){doneSlots[p.a]=true; continue;}
			if(dstSlot < 36) continue; // items going INTO hotbar/offhand
			final int srcSlot = findSlotWithItem(psh, p.b.getPath(), doneSlots);
			if(srcSlot == -1) continue;
			if(dstSlot == 45) clicks.add(new ClickEvent(srcSlot, 40, SlotActionType.SWAP));
			else clicks.add(new ClickEvent(srcSlot, dstSlot-36, SlotActionType.SWAP));

			final ItemStack tempStack = psh.getSlot(srcSlot).getStack().copy();
			psh.getSlot(srcSlot).setStack(psh.getSlot(dstSlot).getStack());
			psh.getSlot(dstSlot).setStack(tempStack);
			final boolean tempEmpty = emptySlots[srcSlot];
			emptySlots[srcSlot] = emptySlots[dstSlot];
			emptySlots[dstSlot] = tempEmpty;
			doneSlots[dstSlot] = true;
		}
		final int numClicks = clicks.size();
		InventoryUtils.executeClicks(client, clicks, /*MILLIS_BETWEEN_CLICKS=*/100, /*MAX_CLICKS_PER_SECOND=*/5,
				_->true,
				()->{
					Main.LOGGER.info("InvOrganize: done! clicks required: "+numClicks);
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
		KeyBindingHelper.registerKeyBinding(new EvKeybind(keybind_name, this::organizeInventory, InventoryScreen.class::isInstance));
	}
}