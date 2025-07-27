package net.evmodder.KeyBound.Keybinds;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.lwjgl.glfw.GLFW;
import net.evmodder.KeyBound.Main;
import net.evmodder.KeyBound.Keybinds.ClickUtils.ClickEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import net.minecraft.client.gui.screen.ingame.CartographyTableScreen;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Identifier;

//TODO: Shift-click (only 2 clicks intead of 3) when possible

public final class KeybindInventoryRestock{
	private final boolean LEAVE_1 = true;
	private final boolean IS_WHITELIST;
	private final Set<Item> itemList;

	public final void doRestock(){
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
		for(int i=slots.length-37; i>=0; --i){
			if(slots[i].getMaxCount() > 1) supply.put(slots[i].getItem(), supply.getOrDefault(slots[i].getItem(), 0)+slots[i].getCount());
		}

		for(int i=slots.length-36; i<slots.length; ++i){
			if(slots[i].isEmpty()) continue;
			final int maxCount = slots[i].getMaxCount();
			if(slots[i].getCount() >= maxCount) continue;
			if(IS_WHITELIST != itemList.contains(slots[i].getItem())) continue;
			Integer totalInContainer = supply.get(slots[i].getItem());
			if(totalInContainer == null || (LEAVE_1 && totalInContainer == 1)) continue;

			for(int j=slots.length-37; j>=0; --j){
				if(!ItemStack.areItemsAndComponentsEqual(slots[i], slots[j])) continue;
//				Main.LOGGER.info("Adding clicks to restock "+slots[i].getItem().getName().getString()+" from slot "+j+" -> "+i);
				clicks.add(new ClickEvent(j, 0, SlotActionType.PICKUP)); // Pickup all

				int combinedCount = slots[i].getCount() + slots[j].getCount();
				if(combinedCount <= maxCount && (totalInContainer -= slots[j].getCount()) == 0 && LEAVE_1){
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
		if(clicks.isEmpty()) return;

		Main.LOGGER.info("InvRestock: Scheduled with "+clicks.size()+" clicks");
		Main.clickUtils.executeClicks(clicks, _0->true, ()->Main.LOGGER.info("InvRestock: DONE!"));
	}

	public List<Item> parseItemList(String[] list){
		return Arrays.stream(list).map(
//				s -> Registries.ITEM.get(Identifier.of(s))
				s -> {
					Identifier id = Identifier.of(s);
					if(!Registries.ITEM.containsId(id)) Main.LOGGER.error("InvRestock: Unknown item: "+s);
					return Registries.ITEM.get(id);
				}
		).toList();
	}
	public KeybindInventoryRestock(String[] blacklist, String[] whitelist){
		if(whitelist == null){
			IS_WHITELIST = false;
			itemList = blacklist != null ? new HashSet<Item>(parseItemList(blacklist)) : Collections.emptySet();
		}
		else{
			IS_WHITELIST = true;
			itemList = new HashSet<Item>(parseItemList(whitelist));
			if(blacklist != null){
				itemList.removeAll(parseItemList(blacklist));
				Main.LOGGER.warn("InvRestock: BOTH whitelist/blacklist were defined in the config");
			}
		}
		new Keybind("inventory_restock", this::doRestock, s->s instanceof HandledScreen && s instanceof InventoryScreen == false, GLFW.GLFW_KEY_R);
	}
}