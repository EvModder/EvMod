package net.evmodder.evmod.keybinds;

import static net.evmodder.evmod.compat.MinecraftCompat.screen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.Main;
import net.evmodder.evmod.apis.ClickUtils;
import net.evmodder.evmod.apis.ClickUtils.ActionType;
import net.evmodder.evmod.apis.ClickUtils.InvAction;
import net.evmodder.evmod.apis.InventoryTransferPlanner;
import net.evmodder.evmod.apis.InventoryTransferPlanner.HotbarSlot;
import net.evmodder.evmod.config.OptionInventoryRestockIf;
import net.evmodder.evmod.config.OptionInventoryRestockLeave;
import net.evmodder.evmod.keybinds.KeybindInventoryOrganize.SlotAndItemName;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.CartographyTableScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

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
		if(ClickUtils.hasOngoingClicks()){Main.LOGGER.warn("InvRestock cancelled: Already ongoing"); return;}
		//
		Minecraft client = Minecraft.getInstance();
		if(client.player == null || client.level == null || !client.player.isAlive()) return;
		if(screen(client) == null || !(screen(client) instanceof AbstractContainerScreen hs)) return;
		if(hs instanceof AnvilScreen || hs instanceof CraftingScreen || hs instanceof CartographyTableScreen) return;
		if(!hs.getMenu().getCarried().isEmpty()) return;
		final Class<?> menuClass = hs.getMenu().getClass();
		final boolean reverseQuickMove = menuClass == ChestMenu.class || menuClass == ShulkerBoxMenu.class
				|| menuClass == HopperMenu.class || menuClass == DispenserMenu.class;
		//
		final ItemStack[] slots = hs.getMenu().slots.stream().map(s -> s.getItem().copy()).toArray(ItemStack[]::new);

		ArrayDeque<InvAction> clicks = new ArrayDeque<>();
		// if leave.ONE_STACK: map of item->#slots
		// else map of item->sum(stack sz)
		HashMap<Item, Integer> supply = new HashMap<>();


		OptionInventoryRestockIf limits = (OptionInventoryRestockIf)Configs.Hotkeys.INV_RESTOCK_IF.getOptionListValue();
		OptionInventoryRestockLeave leave = (OptionInventoryRestockLeave)Configs.Hotkeys.INV_RESTOCK_LEAVE.getOptionListValue();
//		Main.LOGGER.info("InvRestock: restock limits: "+limits.name());
		final boolean LEAVE_ONE = leave == OptionInventoryRestockLeave.ONE_ITEM || leave == OptionInventoryRestockLeave.ONE_STACK;
		HashSet<String> itemNamesInLayout;
		if(limits == OptionInventoryRestockIf.RESUPPLY){
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
			if(limits == OptionInventoryRestockIf.RESUPPLY){
				final String itemName = BuiltInRegistries.ITEM.getKey(slots[i].getItem()).getPath();
				if(!itemNamesInLayout.contains(itemName)){
//					Main.LOGGER.info("InvRestock: not a valid source (LEAVE_UNLESS_ALL_RESUPPLY: container has unlisted item type '"+itemName+"')");
					return;
				}
			}
			final int amt = leave == OptionInventoryRestockLeave.ONE_STACK ? 1 : slots[i].getCount();
			supply.put(slots[i].getItem(), supply.getOrDefault(slots[i].getItem(), 0) + amt);
		}
		if(supply.size() > 1 && limits == OptionInventoryRestockIf.ONE_TYPE){
//			Main.LOGGER.info("InvRestock: not a valid source (LEAVE_UNLESS_ONE_TYPE: container has multiple item types)");
			return;
		}

		final boolean[] doneSlots = new boolean[slots.length];
//		final boolean[] plannedSlots = new boolean[slots.length];
		if(organizationLayouts == null || organizationLayouts.isEmpty()) Arrays.fill(doneSlots, true);
		else for(KeybindInventoryOrganize kio : organizationLayouts)
			/* orEqualsArray(plannedSlots, */kio.checkDoneSlots(slots, doneSlots, /*isInvScreen=*/false)/*)*/;

		for(int i=slots.length-36; i<slots.length; ++i){
			if(slots[i].isEmpty() || !doneSlots[i]) continue;
			final int maxCount = slots[i].getMaxStackSize();
			if(slots[i].getCount() >= maxCount) continue;
			if(IS_WHITELIST != itemList.contains(slots[i].getItem())) continue;
			int totalInContainer = supply.getOrDefault(slots[i].getItem(), 0);
			if(LEAVE_ONE && totalInContainer <= 1) continue;

			for(int j=slots.length-37; j>=0; --j){
				if(!ItemStack.isSameItemSameComponents(slots[i], slots[j])) continue;
//				Main.LOGGER.info("Adding clicks to restock "+slots[i].getItem().getName().getString()+" from slot "+j+" -> "+i);

				final int sourceCount = slots[j].getCount(), destinationCount = slots[i].getCount();
				final boolean needToLeave1 = leave == OptionInventoryRestockLeave.ONE_ITEM
						&& totalInContainer == sourceCount;
				final int amount = Math.min(maxCount-destinationCount, sourceCount-(needToLeave1 ? 1 : 0));
				if(amount <= 0) continue;

				boolean quickMoveToTarget = reverseQuickMove && amount == sourceCount && destinationCount+sourceCount == maxCount;
				// These menus merge from the hotbar backwards, not in our destination-loop order.
				if(quickMoveToTarget) for(int k=slots.length-1; k>i; --k){
					if(slots[k].getCount() < maxCount && ItemStack.isSameItemSameComponents(slots[k], slots[j])){
						quickMoveToTarget = false;
						break;
					}
				}
				if(quickMoveToTarget){
					clicks.add(new InvAction(j, 0, ActionType.SHIFT_CLICK)); // Shift-click
				}
				else{
					final int destinationInventorySlot = hs.getMenu().getSlot(i).container == client.player.getInventory()
							? hs.getMenu().getSlot(i).getContainerSlot() : -1;
					final int destinationButton = Inventory.isHotbarSlot(destinationInventorySlot) ? destinationInventorySlot : -1;
					List<HotbarSlot> hotbarSlots = List.of();
					if(destinationButton < 0 && destinationCount == sourceCount-amount && amount != Math.ceilDiv(sourceCount, 2)){
						for(int k=slots.length-9; k<slots.length; ++k){
							if(slots[k].getCount() != destinationCount || !ItemStack.isSameItemSameComponents(slots[k], slots[j])) continue;
							final int button = hs.getMenu().getSlot(k).getContainerSlot();
							if(hs.getMenu().getSlot(k).container != client.player.getInventory() || !Inventory.isHotbarSlot(button)) continue;
							hotbarSlots = List.of(new HotbarSlot(k, button, destinationCount));
							break;
						}
					}
					InventoryTransferPlanner.transferAmount(clicks,
							j, i, sourceCount, destinationCount, maxCount, amount,
							/*sourceHotbarButton=*/-1,
							destinationButton, hotbarSlots);
				}

				totalInContainer -= leave == OptionInventoryRestockLeave.ONE_STACK
						? (amount == sourceCount ? 1 : 0) : amount;
				slots[i].setCount(destinationCount+amount);
				if(amount == sourceCount) slots[j] = ItemStack.EMPTY;
				else slots[j].setCount(sourceCount-amount);
				if(slots[i].getCount() == maxCount) break;
			}
			if(LEAVE_ONE) supply.put(slots[i].getItem(), totalInContainer);
		}

//		if(organizationLayouts != null) for(KeybindInventoryOrganize kio : organizationLayouts) kio.organizeInventory(/*RESTOCK_ONLY=*/true);
//		Main.LOGGER.info("InvRestock: clicks="+clicks.size()+", layouts="+(organizationLayouts==null ? 0 : organizationLayouts.length));

		if(clicks.isEmpty()) return;

		Main.LOGGER.info("InvRestock: Scheduled with "+clicks.size()+" clicks");
		ClickUtils.executeClicks(/*canProceed=*/_->true, ()->Main.LOGGER.info("InvRestock: DONE!"), clicks);
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
					Identifier id = Identifier.parse(s);
					if(!BuiltInRegistries.ITEM.containsKey(id)) Main.LOGGER.error("InvRestock: Unknown item: "+s);
					return BuiltInRegistries.ITEM.getValue(id);
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
	public void refreshLayouts(KeybindInventoryOrganize[] invOrganizations){
		if(organizationLayouts == null) organizationLayouts = new ArrayList<>();
		else organizationLayouts.clear();
		// TODO: replace with ConfigOptionList, for named organization schemes
		List<String> layouts = Configs.Generic.INV_RESTOCK_AUTO_FOR_INV_ORGS.getStrings();
		if(layouts.contains("1")) organizationLayouts.add(invOrganizations[0]);
		if(layouts.contains("2")) organizationLayouts.add(invOrganizations[1]);
		if(layouts.contains("3")) organizationLayouts.add(invOrganizations[2]);
	}
	public KeybindInventoryRestock(KeybindInventoryOrganize[] invOrganizations){
		refreshLists();
		refreshLayouts(invOrganizations);
	}
}