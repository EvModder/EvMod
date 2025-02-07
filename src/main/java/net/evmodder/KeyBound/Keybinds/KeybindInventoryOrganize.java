package net.evmodder.KeyBound.Keybinds;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import net.evmodder.EvLib.Pair;
import net.evmodder.KeyBound.Main;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public final class KeybindInventoryOrganize{
	final List<Pair<Integer, Identifier>> layoutMap;
	private String getName(ItemStack stack){
		return stack == null || stack.isEmpty() ? null : Registries.ITEM.getId(stack.getItem()).getPath();
	}
	private int findSlotWithItem(Inventory inv, String itemName, HashSet<Integer> skipSlots){
		for(int slot=0; slot<36; ++slot){ // Hotbar[0-8] + Inventory[9-35]
			if(itemName.equals(getName(inv.getStack(slot))) && !skipSlots.contains(slot)) return slot;
		}
		for(int slot=100; slot<104; ++slot){ // Armor
			if(itemName.equals(getName(inv.getStack(slot))) && !skipSlots.contains(slot)) return slot;
		}
		//if(itemName.equals(getName(inv.getStack((byte)-106))) && !skipSlots.contains((byte)-106)) return -106; // Offhand
		return -1;
	}
	private void organizeInventory(){
		Main.LOGGER.info("inv org keybind pressed");
//		//TODO: ALL BROKEN NOTHING WORKING RIPPP
//		for(Pair<Integer, Identifier> p : layoutMap){
//			final int destSlot = p.a;
//			if(doneSlots.contains(destSlot)) continue;
//			final Identifier id = p.b;
//			int srcSlot = findSlotWithItem(inv, id.getPath(), doneSlots);
//			Main.LOGGER.info("src slot: "+ srcSlot);
//			//if(srcSlot == -1 && id.getPath().equals(getName(instance.player.getOffHandStack())) && !doneSlots.contains(-106)) srcSlot = -106;
//			if(srcSlot != -1){
//				if(srcSlot != destSlot){
//					Main.LOGGER.info("swapping slots "+srcSlot+" and "+destSlot);
//					ItemStack temp = inv.getStack(srcSlot);
//					inv.setStack(srcSlot, inv.getStack(destSlot));
//					inv.setStack(destSlot, temp);
//					instance.interactionManager.clickSlot(0, destSlot, srcSlot, SlotActionType.SWAP, instance.player);
//				}
//				doneSlots.add(destSlot);
//			}
//		}
//		if(!doneSlots.isEmpty()) inv.markDirty();
	}

	public KeybindInventoryOrganize(String keybind_name, String layout){
		layoutMap = 
		Arrays.stream(layout.substring(1, layout.length()-1).split(","))
		.map(s -> {
				int idx = Integer.parseInt(s.substring(0, s.indexOf(':')));
				String iname = s.substring(s.indexOf(':')+1);
				Identifier id = Identifier.of(iname);
				if(!Registries.ITEM.containsId(id)){
					Main.LOGGER.error("Unknown item in '"+keybind_name+"': "+iname);
					return null;
				}
				return new Pair<>(idx, id);
			}
		)
		.filter(p -> p != null)
		.toList();
		KeyBindingHelper.registerKeyBinding(new EvKeybind(keybind_name, this::organizeInventory));
	}
}