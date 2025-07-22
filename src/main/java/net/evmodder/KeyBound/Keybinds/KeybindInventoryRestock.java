package net.evmodder.KeyBound.Keybinds;

import java.util.ArrayDeque;
import org.lwjgl.glfw.GLFW;
import net.evmodder.KeyBound.Main;
import net.evmodder.KeyBound.Keybinds.ClickUtils.ClickEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import net.minecraft.client.gui.screen.ingame.CartographyTableScreen;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;

//TODO: Shift-click (only 2 clicks intead of 3) when possible

public final class KeybindInventoryRestock{
	public static final void doRestock(){
		if(Main.clickUtils.hasOngoingClicks()){Main.LOGGER.warn("InvRestock cancelled: Already ongoing"); return;}
		//
		MinecraftClient client = MinecraftClient.getInstance();
		if(client.player == null || client.world == null || !client.player.isAlive()) return;
		if(client.currentScreen == null || !(client.currentScreen instanceof HandledScreen hs)) return;
		if(hs instanceof AnvilScreen || hs instanceof CraftingScreen || hs instanceof CartographyTableScreen) return;
		//
		final ItemStack[] slots = hs.getScreenHandler().slots.stream().map(s -> s.getStack().copy()).toArray(ItemStack[]::new);

		ArrayDeque<ClickEvent> clicks = new ArrayDeque<>();
		for(int i=slots.length-36; i<slots.length; ++i){
			if(slots[i].isEmpty()) continue;
			final int maxCount = slots[i].getMaxCount();
			if(slots[i].getCount() >= maxCount) continue;
			for(int j=slots.length-37; j>=0; --j){
				if(!ItemStack.areItemsAndComponentsEqual(slots[i], slots[j])) continue;
//				Main.LOGGER.info("Adding clicks to restock "+slots[i].getItem().getName().getString()+" from slot "+j+" -> "+i);
				clicks.add(new ClickEvent(j, 0, SlotActionType.PICKUP)); // Pickup all
				clicks.add(new ClickEvent(i, 0, SlotActionType.PICKUP)); // Place as many as possible
				final int combinedCount = slots[i].getCount() + slots[j].getCount();
				if(combinedCount > maxCount){
					clicks.add(new ClickEvent(j, 0, SlotActionType.PICKUP)); // Put back extras
					slots[i].setCount(maxCount);
					slots[j].setCount(combinedCount - maxCount);
				}
				else{
					slots[i].setCount(combinedCount);
					slots[j] = ItemStack.EMPTY;
				}
				if(combinedCount >= maxCount) break;
			}
		}
		if(clicks.isEmpty()) return;

		Main.LOGGER.info("InvRestock: Scheduled with "+clicks.size()+" clicks");
		Main.clickUtils.executeClicks(clicks, _0->true, ()->Main.LOGGER.info("InvRestock: DONE!")
		);
	}

	public KeybindInventoryRestock(){
		new Keybind("inventory_restock", KeybindInventoryRestock::doRestock, s->s instanceof HandledScreen && s instanceof InventoryScreen == false, GLFW.GLFW_KEY_R);
	}
}