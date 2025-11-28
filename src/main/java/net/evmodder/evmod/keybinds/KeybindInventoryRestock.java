package net.evmodder.evmod.keybinds;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.KeyCallbacks;
import net.evmodder.evmod.Main;
import net.evmodder.evmod.apis.ClickUtils.ActionType;
import net.evmodder.evmod.apis.ClickUtils.InvAction;
import net.evmodder.evmod.config.OptionInventoryRestockLimit;
import net.evmodder.evmod.keybinds.KeybindInventoryOrganize.SlotAndItemName;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import net.minecraft.client.gui.screen.ingame.CartographyTableScreen;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

//TODO: Shift-click (only 2 clicks intead of 3) when possible

public final class KeybindInventoryRestock{
	private boolean IS_WHITELIST;
	private Set<Item> itemList;
	private List<KeybindInventoryOrganize> organizationLayouts;

//	private final void orEqualsArray(boolean[] source, boolean[] input){
//		assert source.length == input.length;
//		for(int i=0; i<source.length; ++i) source[i] |= input[i];
//	}

	public final void doRestock(){
		if(Main.clickUtils.hasOngoingClicks()){Main.LOGGER.warn("InvRestock cancelled: Already ongoing"); return;}
		//
		MinecraftClient client = MinecraftClient.getInstance();
		if(client.player == null || client.world == null || !client.player.isAlive()) return;
		if(client.currentScreen == null || !(client.currentScreen instanceof HandledScreen hs)) return;
		if(hs instanceof AnvilScreen || hs instanceof CraftingScreen || hs instanceof CartographyTableScreen) return;
		//
		final ItemStack[] slots = hs.getScreenHandler().slots.stream().map(s -> s.getStack().copy()).toArray(ItemStack[]::new);

		ArrayDeque<InvAction> clicks = new ArrayDeque<>();
		HashMap<Item, Integer> supply = new HashMap<>();


		OptionInventoryRestockLimit limits = (OptionInventoryRestockLimit)Configs.Hotkeys.INV_RESTOCK_LIMITS.getOptionListValue();
//		Main.LOGGER.info("InvRestock: restock limits: "+limits.name());
		final boolean LEAVE_ONE = limits == OptionInventoryRestockLimit.LEAVE_ONE_ITEM || limits == OptionInventoryRestockLimit.LEAVE_ONE_STACK;
		HashSet<String> itemNamesInLayout;
		if(limits == OptionInventoryRestockLimit.LEAVE_UNLESS_ALL_RESUPPLY){
			if(organizationLayouts == null){
				Main.LOGGER.warn("InvRestock: Restriction to only take resupply items, but no items are defined! (InvOrganizeLayout is empty)");
				return;
			}
			itemNamesInLayout = new HashSet<>();
			organizationLayouts.stream().forEach(kio -> kio.layoutMap.stream().map(SlotAndItemName::name).forEach(itemNamesInLayout::add));
//			Main.LOGGER.info("supported restock items: "+itemNamesInLayout.toString());
		}
		else itemNamesInLayout = null;
		// TODO: hardcoded assumption that slots < len-36 are from the currently viewed container
		for(int i=slots.length-37; i>=0; --i){
			if(slots[i].isEmpty()) continue;
			if(limits == OptionInventoryRestockLimit.LEAVE_UNLESS_ALL_RESUPPLY){
				final String itemName = Registries.ITEM.getId(slots[i].getItem()).getPath();
				if(!itemNamesInLayout.contains(itemName)){
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
			int totalInContainer = supply.getOrDefault(slots[i].getItem(), 0);
			if(LEAVE_ONE && totalInContainer <= 1) continue;

			for(int j=slots.length-37; j>=0; --j){
				if(!ItemStack.areItemsAndComponentsEqual(slots[i], slots[j])) continue;
//				Main.LOGGER.info("Adding clicks to restock "+slots[i].getItem().getName().getString()+" from slot "+j+" -> "+i);

				int combinedCount = slots[i].getCount() + slots[j].getCount();
				final boolean needToLeave1 = limits == OptionInventoryRestockLimit.LEAVE_ONE_ITEM
						&& combinedCount <= maxCount && (totalInContainer -= slots[j].getCount()) == 0;

				if(needToLeave1 || combinedCount != maxCount) clicks.add(new InvAction(j, 0, ActionType.CLICK)); // Pickup all
				else{
					clicks.add(new InvAction(j, 0, ActionType.SHIFT_CLICK)); // Shift-click
					totalInContainer -= slots[j].getCount();
					slots[i].setCount(maxCount);
					slots[j] = ItemStack.EMPTY;
					break;
				}
				if(needToLeave1){
					clicks.add(new InvAction(j, 1, ActionType.CLICK)); // Leave 1
					--combinedCount;
				}
				clicks.add(new InvAction(i, 0, ActionType.CLICK)); // Place as many as possible
				if(combinedCount <= maxCount){
					totalInContainer -= (combinedCount - slots[i].getCount());
					slots[i].setCount(combinedCount);
					slots[j] = ItemStack.EMPTY;
				}
				else{
					clicks.add(new InvAction(j, 0, ActionType.CLICK)); // Put back extras
					totalInContainer -= (maxCount - slots[i].getCount());
					slots[i].setCount(maxCount);
					slots[j].setCount(combinedCount - maxCount);
				}
				if(combinedCount >= maxCount) break;
			}
			if(LEAVE_ONE) supply.put(slots[i].getItem(), totalInContainer);
		}

//		if(organizationLayouts != null) for(KeybindInventoryOrganize kio : organizationLayouts) kio.organizeInventory(/*RESTOCK_ONLY=*/true);
//		Main.LOGGER.info("InvRestock: clicks="+clicks.size()+", layouts="+(organizationLayouts==null ? 0 : organizationLayouts.length));

		if(clicks.isEmpty()) return;

		Main.LOGGER.info("InvRestock: Scheduled with "+clicks.size()+" clicks");
		Main.clickUtils.executeClicks(clicks, _0->true, ()->Main.LOGGER.info("InvRestock: DONE!"));
	}

	private void organizeThenRestock(int i){
		if(i == organizationLayouts.size()) doRestock();
		else organizationLayouts.get(i).organizeInventory(/*RESTOCK_ONLY=*/true, ()->organizeThenRestock(i+1));
	}
	public void organizeThenRestock(){organizeThenRestock(0);}

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
	public void refreshLayouts(){
		if(organizationLayouts == null) organizationLayouts = new ArrayList<>();
		else organizationLayouts.clear();
		// TODO: replace with ConfigOptionList, for named organization schemes
		List<String> layouts = Configs.Generic.INV_RESTOCK_AUTO_FOR_INV_ORGS.getStrings();
		if(layouts.contains("1")) organizationLayouts.add(KeyCallbacks.kbInvOrg1);
		if(layouts.contains("2")) organizationLayouts.add(KeyCallbacks.kbInvOrg2);
		if(layouts.contains("3")) organizationLayouts.add(KeyCallbacks.kbInvOrg3);
	}
	public KeybindInventoryRestock(){
		refreshLists();
		refreshLayouts();
	}
}