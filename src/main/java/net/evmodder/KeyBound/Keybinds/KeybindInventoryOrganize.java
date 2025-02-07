package net.evmodder.KeyBound.Keybinds;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import net.evmodder.EvLib.Pair;
import net.evmodder.KeyBound.Main;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Identifier;

public final class KeybindInventoryOrganize{
	final List<Pair<Integer, Identifier>> layoutMap;
	private String getName(ItemStack stack){
		return stack == null || stack.isEmpty() || stack.getCount() == 0 ? null : Registries.ITEM.getId(stack.getItem()).getPath();
	}
	/*private ItemStack getStack(ClientPlayerEntity p, int slot){
		switch(slot){
			case 40: case -106: return p.getOffHandStack();
			case 100: return p.getEquippedStack(EquipmentSlot.FEET);
			case 101: return p.getEquippedStack(EquipmentSlot.LEGS);
			case 102: return p.getEquippedStack(EquipmentSlot.CHEST);
			case 103: return p.getEquippedStack(EquipmentSlot.HEAD);
			default:
				if(slot < 0 || slot >= 36){
					Main.LOGGER.error("Invalid slot in inv-organize: "+slot);
					return null;
				}
				return p.getInventory().getStack(slot);
		}
	}
	private void setStack(ClientPlayerEntity p, int slot, ItemStack stack){
		switch(slot){
			case 40: case -106: p.setStackInHand(Hand.OFF_HAND, stack); return;
			case 100: p.equipStack(EquipmentSlot.FEET, stack); return;
			case 101: p.equipStack(EquipmentSlot.LEGS, stack); return;
			case 102: p.equipStack(EquipmentSlot.CHEST, stack); return;
			case 103: p.equipStack(EquipmentSlot.HEAD, stack); return;
			default:
				if(slot < 0 || slot >= 36) Main.LOGGER.error("Invalid slot in inv-organize: "+slot);
				else if(stack == null) p.getInventory().getStack(slot).setCount(0);
				else p.getInventory().setStack(slot, stack);
		}
	}*/
	private int findSlotWithItem(PlayerScreenHandler psh, String itemName, HashSet<Integer> skipSlots){
//		for(int slot=PlayerScreenHandler.EQUIPMENT_START; slot<PlayerScreenHandler.EQUIPMENT_END; ++slot){ // Armor[5-8]
//			if(itemName.equals(getName(psh.getSlot(slot).getStack())) && !skipSlots.contains(slot)) return slot;
//		}
//		for(int slot=PlayerScreenHandler.INVENTORY_START; slot<PlayerScreenHandler.INVENTORY_END; ++slot){ // Inventory[9-35], Hotbar[36-44]
//			if(itemName.equals(getName(psh.getSlot(slot).getStack())) && !skipSlots.contains(slot)) return slot;
//		}
//		final int slot = PlayerScreenHandler.OFFHAND_ID;
//		if(itemName.equals(getName(psh.getSlot(slot).getStack())) && !skipSlots.contains(slot)) return slot; // Offhand[45]

		// Crafting 2x2[1-4]
		for(int slot=1; slot<=45; ++slot){
			if(itemName.equals(getName(psh.getSlot(slot).getStack())) && !skipSlots.contains(slot)) return slot;
		}
		return -1;
	}
	private void organizeInventory(){
		Main.LOGGER.info("inv org keybind pressed");
		MinecraftClient client = MinecraftClient.getInstance();
		if(!(client.currentScreen instanceof InventoryScreen is)){Main.LOGGER.warn("MapCopy: not in InventoryScreen"); return;}

		PlayerScreenHandler psh = is.getScreenHandler();
		HashSet<Integer> doneSlots = new HashSet<>();
		for(Pair<Integer, Identifier> p : layoutMap){
			int dstSlot = p.a == -106 ? 45 : p.a;
			if(doneSlots.contains(dstSlot)) continue;
			final Identifier id = p.b;
			int srcSlot = findSlotWithItem(psh, id.getPath(), doneSlots);
			Main.LOGGER.info("src slot: "+ srcSlot);
			if(srcSlot == -1) continue;
			//if(srcSlot == -1 && id.getPath().equals(getName(instance.player.getOffHandStack())) && !doneSlots.contains(-106)) srcSlot = -106;
			if(srcSlot != dstSlot){
				Main.LOGGER.info("swapping slots "+srcSlot+" and "+dstSlot);
				ItemStack temp = psh.getSlot(srcSlot).getStack();
				psh.getSlot(srcSlot).setStack(psh.getSlot(dstSlot).getStack());
				psh.getSlot(dstSlot).setStack(temp);
				if(srcSlot == 45)	   client.interactionManager.clickSlot(0, dstSlot, 40, SlotActionType.SWAP, client.player);
				else if(srcSlot >= 36) client.interactionManager.clickSlot(0, dstSlot, srcSlot-36, SlotActionType.SWAP, client.player);
				else if(dstSlot == 45) client.interactionManager.clickSlot(0, srcSlot, 40, SlotActionType.SWAP, client.player);
				else if(dstSlot >= 36) client.interactionManager.clickSlot(0, srcSlot, dstSlot-36, SlotActionType.SWAP, client.player);
				else{
					client.interactionManager.clickSlot(0, srcSlot, 1, SlotActionType.SWAP, client.player);
					client.interactionManager.clickSlot(0, destSlot, 1, SlotActionType.SWAP, client.player);
					client.interactionManager.clickSlot(0, srcSlot, 1, SlotActionType.SWAP, client.player);//TODO: not necessary if dest.isEmpty() && hotbar[1].isEmpty()
				}
			}
			doneSlots.add(dstSlot);
		}
		Main.LOGGER.info("inv organize complete, slots updated: "+doneSlots.size());
		if(!doneSlots.isEmpty()) client.player.getInventory().markDirty();
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
		KeyBindingHelper.registerKeyBinding(new EvKeybind(keybind_name, this::organizeInventory, true));
	}
}