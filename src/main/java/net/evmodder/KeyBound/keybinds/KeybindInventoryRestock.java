package net.evmodder.KeyBound.keybinds;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.evmodder.KeyBound.Main;
import net.evmodder.KeyBound.config.Configs;
import net.evmodder.KeyBound.config.OptionInventoryRestockLimit;
import net.evmodder.KeyBound.keybinds.ClickUtils.ClickEvent;
import net.evmodder.KeyBound.keybinds.KeybindInventoryOrganize.SlotAndItemName;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import net.minecraft.client.gui.screen.ingame.CartographyTableScreen;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Identifier;

//TODO: Shift-click (only 2 clicks intead of 3) when possible

public final class KeybindInventoryRestock{
	private boolean IS_WHITELIST;
	private Set<Item> itemList;

//	private final void orEqualsArray(boolean[] source, boolean[] input){
//		assert source.length == input.length;
//		for(int i=0; i<source.length; ++i) source[i] |= input[i];
//	}

	public final void doRestock(List<KeybindInventoryOrganize> organizationLayouts){
		if(Main.clickUtils.hasOngoingClicks()){Main.LOGGER.warn("InvRestock cancelled: Already ongoing"); return;}
		//
		MinecraftClient client = MinecraftClient.getInstance();
		if(client.player == null || client.world == null || !client.player.isAlive()) return;
		if(client.currentScreen == null || !(client.currentScreen instanceof HandledScreen hs)) return;
		if(hs instanceof AnvilScreen || hs instanceof CraftingScreen || hs instanceof CartographyTableScreen) return;
		//
		final ItemStack[] slots = hs.getScreenHandler().slots.stream().map(s -> s.getStack().copy()).toArray(ItemStack[]::new);

		ArrayDeque<ClickEvent> clicks = new ArrayDeque<>();
		HashMap<Item, Integer> supply = new HashMap<>();


		OptionInventoryRestockLimit limits = (OptionInventoryRestockLimit)Configs.Hotkeys.INV_RESTOCK_LIMITS.getOptionListValue();
		Main.LOGGER.info("InvRestock: restock limits: "+limits.name());
		final boolean LEAVE_ONE = limits == OptionInventoryRestockLimit.LEAVE_ONE_ITEM || limits == OptionInventoryRestockLimit.LEAVE_ONE_STACK;
		HashSet<String> itemNamesInInv;
		if(limits == OptionInventoryRestockLimit.LEAVE_UNLESS_ALL_RESUPPLY){
			itemNamesInInv = new HashSet<>();
			organizationLayouts.stream().forEach(kio -> kio.layoutMap.stream().map(SlotAndItemName::name).forEach(itemNamesInInv::add));
		}
		else itemNamesInInv = null;
		// TODO: hardcoded assumption that slots < len-36 are from the currently viewed container
		for(int i=slots.length-37; i>=0; --i){
			if(slots[i].isEmpty()) continue;
			if(limits == OptionInventoryRestockLimit.LEAVE_UNLESS_ALL_RESUPPLY){
				final String itemName = Registries.ITEM.getId(slots[i].getItem()).getPath();
				if(itemNamesInInv.contains(itemName)){
					Main.LOGGER.info("InvRestock: not a valid source (LEAVE_UNLESS_ALL_RESUPPLY: container has unlisted item type '"+itemName+"')");
					return;
				}
			}
			else{
				final int amt = limits == OptionInventoryRestockLimit.LEAVE_ONE_STACK ? 1 : slots[i].getCount();
				supply.put(slots[i].getItem(), supply.getOrDefault(slots[i].getItem(), 0) + amt);
			}
		}
		if(supply.size() > 1 && limits == OptionInventoryRestockLimit.LEAVE_UNLESS_ONE_TYPE){
			Main.LOGGER.info("InvRestock: not a valid source (LEAVE_UNLESS_ONE_TYPE: container has multiple item types)");
			return;
		}

		final boolean[] doneSlots = new boolean[slots.length];
//		final boolean[] plannedSlots = new boolean[slots.length];
		if(organizationLayouts == null || organizationLayouts.isEmpty()) Arrays.fill(doneSlots, true);
		else for(KeybindInventoryOrganize kio : organizationLayouts)
			/* orEqualsArray(plannedSlots, */kio.checkDoneSlots(slots, doneSlots, /*isInvScreen=*/false)/*)*/;

		for(int i=slots.length-36; i<slots.length; ++i){
			if(slots[i].isEmpty() || !doneSlots[i]) continue;
			final int maxCount = slots[i].getMaxCount();
			if(slots[i].getCount() >= maxCount) continue;
			if(IS_WHITELIST != itemList.contains(slots[i].getItem())) continue;
			Integer totalInContainer = supply.get(slots[i].getItem());
			if(totalInContainer == null || (LEAVE_ONE && totalInContainer == 1)) continue;

			for(int j=slots.length-37; j>=0; --j){
				if(!ItemStack.areItemsAndComponentsEqual(slots[i], slots[j])) continue;
//				Main.LOGGER.info("Adding clicks to restock "+slots[i].getItem().getName().getString()+" from slot "+j+" -> "+i);

				int combinedCount = slots[i].getCount() + slots[j].getCount();
				final boolean needToLeave1 = combinedCount <= maxCount && (totalInContainer -= slots[j].getCount()) == 0
						&& (limits == OptionInventoryRestockLimit.LEAVE_ONE_ITEM);

				if(needToLeave1 || combinedCount != maxCount) clicks.add(new ClickEvent(j, 0, SlotActionType.PICKUP)); // Pickup all
				else{
					clicks.add(new ClickEvent(j, 0, SlotActionType.QUICK_MOVE)); // Shift-click
					totalInContainer -= slots[j].getCount();
					slots[i].setCount(maxCount);
					slots[j] = ItemStack.EMPTY;
					break;
				}
				if(needToLeave1){
					clicks.add(new ClickEvent(j, 1, SlotActionType.PICKUP)); // Leave 1
					--combinedCount;
				}
				clicks.add(new ClickEvent(i, 0, SlotActionType.PICKUP)); // Place as many as possible
				if(combinedCount <= maxCount){
					totalInContainer -= (combinedCount - slots[i].getCount());
					slots[i].setCount(combinedCount);
					slots[j] = ItemStack.EMPTY;
				}
				else{
					clicks.add(new ClickEvent(j, 0, SlotActionType.PICKUP)); // Put back extras
					totalInContainer -= (maxCount - slots[i].getCount());
					slots[i].setCount(maxCount);
					slots[j].setCount(combinedCount - maxCount);
				}
				if(combinedCount >= maxCount) break;
			}
			supply.put(slots[i].getItem(), totalInContainer);
		}

//		if(organizationLayouts != null) for(KeybindInventoryOrganize kio : organizationLayouts) kio.organizeInventory(/*RESTOCK_ONLY=*/true);
//		Main.LOGGER.info("InvRestock: clicks="+clicks.size()+", layouts="+(organizationLayouts==null ? 0 : organizationLayouts.length));

		if(clicks.isEmpty()) return;

		Main.LOGGER.info("InvRestock: Scheduled with "+clicks.size()+" clicks");
		Main.clickUtils.executeClicks(clicks, _0->true, ()->Main.LOGGER.info("InvRestock: DONE!"));
	}

	private List<Item> parseItemList(List<String> list){
		return list.stream().map(
//				s -> Registries.ITEM.get(Identifier.of(s))
				s -> {
					Identifier id = Identifier.of(s);
					if(!Registries.ITEM.containsId(id)) Main.LOGGER.error("InvRestock: Unknown item: "+s);
					return Registries.ITEM.get(id);
				}
		).toList();
	}
	public void refreshLists(){
		List<String> blacklist = Configs.Hotkeys.INV_RESTOCK_BLACKLIST.getStrings();
		List<String> whitelist = Configs.Hotkeys.INV_RESTOCK_BLACKLIST.getStrings();
		if(whitelist == null || whitelist.isEmpty() || (whitelist.size() == 1 && whitelist.get(0).isBlank())){
			IS_WHITELIST = false;
			itemList = blacklist != null ? new HashSet<Item>(parseItemList(blacklist)) : Collections.emptySet();
		}
		else{
			IS_WHITELIST = true;
			itemList = new HashSet<Item>(parseItemList(whitelist));
			if(blacklist != null && !blacklist.isEmpty() && !blacklist.get(0).isBlank()){
				itemList.removeAll(parseItemList(blacklist));
				Main.LOGGER.warn("InvRestock: BOTH whitelist/blacklist were defined in the config");
			}
		}
	}
	public KeybindInventoryRestock(){
		refreshLists();
//		new Keybind("inventory_restock", ()->doRestock(null), s->s instanceof HandledScreen && s instanceof InventoryScreen == false, GLFW.GLFW_KEY_R);
	}
}