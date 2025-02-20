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
	private int findSlotWithItem(PlayerScreenHandler psh, String itemName, boolean[] skipSlots){
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
			if(itemName.equals(getName(psh.getSlot(slot).getStack())) && !skipSlots[slot]) return slot;
		}
		return -1;
	}
	private void organizeInventory(){
		Main.LOGGER.info("InvOrganize: keybind pressed");
		MinecraftClient client = MinecraftClient.getInstance();
		if(!(client.currentScreen instanceof InventoryScreen is)){Main.LOGGER.warn("InvOrganize: not in InventoryScreen"); return;}

		PlayerScreenHandler psh = is.getScreenHandler();
		boolean[] emptySlots = new boolean[46];
		boolean[] doneSlots = new boolean[46];
		for(int i=0; i<46; ++i) emptySlots[i] = psh.getSlot(i).getStack() == null || psh.getSlot(i).getStack().isEmpty();

		ArrayDeque<ClickEvent> clicks = new ArrayDeque<>();

		for(Pair<Integer, Identifier> p : layoutMap){
			int dstSlot = p.a == -106 ? 45 : p.a;
			if(doneSlots[dstSlot]) continue;
			final Identifier id = p.b;
			if(id.getPath().equals(getName(psh.getSlot(dstSlot).getStack()))){doneSlots[dstSlot]=true; continue;}
			int srcSlot = findSlotWithItem(psh, id.getPath(), doneSlots);
			if(srcSlot == -1) continue;
			//Main.LOGGER.info("Moving desired item "+id.getPath()+", from->to slot: "+srcSlot+" -> "+dstSlot);
			if(dstSlot >= 5 && dstSlot < 9 & psh.getSlot(dstSlot).getStack().isEmpty()){
				// Shift-click armor from anywhere in the inventory
				//Main.LOGGER.info("armor shift-click");
				clicks.add(new ClickEvent(srcSlot, 0, SlotActionType.QUICK_MOVE));
			}
			else if(srcSlot == 45) clicks.add(new ClickEvent(dstSlot, 40, SlotActionType.SWAP));
			else if(srcSlot >= 36) clicks.add(new ClickEvent(dstSlot, srcSlot-36, SlotActionType.SWAP));
			else if(dstSlot == 45) clicks.add(new ClickEvent(srcSlot, 40, SlotActionType.SWAP));
			else if(dstSlot >= 36) clicks.add(new ClickEvent(srcSlot, dstSlot-36, SlotActionType.SWAP));
			else{
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
			}
			final ItemStack tempStack = psh.getSlot(srcSlot).getStack();
			psh.getSlot(srcSlot).setStack(psh.getSlot(dstSlot).getStack());
			psh.getSlot(dstSlot).setStack(tempStack);
			final boolean tempEmpty = emptySlots[srcSlot];
			emptySlots[srcSlot] = emptySlots[dstSlot];
			emptySlots[dstSlot] = tempEmpty;
			doneSlots[dstSlot] = true;
		}
		if(CLEAN_UNUSED_HOTBAR_SLOTS){
			for(int i=36; i<45; ++i) if(!doneSlots[i] && !emptySlots[i]) clicks.add(new ClickEvent(i, 0, SlotActionType.QUICK_MOVE));
		}
		final int numClicks = clicks.size();
		InventoryUtils.executeClicks(client, clicks, /*MILLIS_BETWEEN_CLICKS=*/0, /*MAX_CLICKS_PER_SECOND=*/4,
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