package net.evmodder.evmod.keybinds;

import static net.evmodder.evmod.compat.MinecraftCompat.screen;

import java.util.List;
import java.util.stream.IntStream;
import net.evmodder.evmod.Main;
import net.evmodder.evmod.mixin.AccessorAnvilScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.ItemCombinerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class KeybindCraftingRestock{
	record SlotAndItem(int slot, ItemStack stack){
		SlotAndItem(AbstractContainerMenu sh, int slot){this(slot, sh.getSlot(slot).getItem().copy());}
	}
	private List<SlotAndItem> inputItems;
	private String anvilName;
	private long THREAD_START;
	private final long THREAD_TIMEOUT = 2000;
	private Class<?> lastScreen;

	private boolean willCraftItem(ContainerInput action){
		switch(action){
			case PICKUP:
			case QUICK_MOVE:
			case SWAP:
				return true;
			default:
				return false;
		}
	}
	public void checkIfCraftAction(AbstractContainerMenu sh, int slot, int button, ContainerInput action){
		if(sh == null || !willCraftItem(action)) return;
		if(sh instanceof AnvilMenu && slot == AnvilMenu.RESULT_SLOT){
			lastScreen = AnvilMenu.class;
			inputItems = List.of(new SlotAndItem(sh, AnvilMenu.INPUT_SLOT), new SlotAndItem(sh, AnvilMenu.ADDITIONAL_SLOT));
//			anvilText = as.newItemName;
			Component anvilText = sh.getSlot(AnvilMenu.RESULT_SLOT).getItem().getCustomName();
			anvilName = anvilText == null ? null : anvilText.tryCollapseToString();
			THREAD_START = 0; // Cancel current anvil name thread - anvil craft event occured
//			try{anvilNameThread.join();}catch(InterruptedException e){e.printStackTrace();}

			Main.LOGGER.info("CraftRestock: Storing 2 anvil slots"+(anvilName==null?"": " and a custom name"));
		}
		if(sh instanceof InventoryMenu && slot == InventoryMenu.RESULT_SLOT){
			lastScreen = InventoryMenu.class;
			inputItems = IntStream.range(InventoryMenu.CRAFT_SLOT_START, InventoryMenu.CRAFT_SLOT_END)
					.mapToObj(i -> new SlotAndItem(sh, i)).toList();
//			Main.LOGGER.info("CraftRestock: Storing 2x2 player inv slots");
		}
		if(sh instanceof CraftingMenu && slot == CraftingMenu.RESULT_SLOT){
			lastScreen = CraftingMenu.class;
			inputItems = IntStream.range(1/*CraftingScreenHandler.INPUT_START*/, 10/*CraftingScreenHandler.INPUT_END*/)
					.mapToObj(i -> new SlotAndItem(sh, i)).toList();
			Main.LOGGER.info("CraftRestock: Storing 3x3 crafting table slots");
		}
//		if(sh instanceof EnchantmentScreenHandler esh/* && clicked ench table button?? not tracked as a slot-click event i think*/){
//			inputItems = List.of(esh.getSlot(0).getStack().copy(), esh.getSlot(1).getStack().copy());
//		}
		// TODO: EnchantTable, Grindstone, Stonecutter,
	}

	private void updateAnvilName(AnvilScreen as){
		EditBox nameField = ((AccessorAnvilScreen)as).getNameField();
		nameField.setValue(anvilName);
		nameField.moveCursorToStart(false);
		nameField.moveCursorToEnd(false);
	}

	public void restockInputSlots(){
//		Main.LOGGER.info("CraftRestock: restockInputSlots() called");
		Minecraft client = Minecraft.getInstance();
		if(lastScreen == null || !(screen(client) instanceof AbstractContainerScreen hs) || !lastScreen.isInstance(hs.getMenu())) return;
		assert inputItems != null;
		final List<Slot> slots = hs.getMenu().slots;
		final int[] restockFrom = new int[inputItems.size()];
//		Main.LOGGER.info("CraftRestock: Looking for available items");
		for(int i=0; i<inputItems.size(); ++i){
			SlotAndItem needed = inputItems.get(i);
			restockFrom[i] = -1;
			if(slots.get(needed.slot).hasItem()){
				if(ItemStack.isSameItemSameComponents(slots.get(needed.slot).getItem(), needed.stack)) continue;
				else{
					Main.LOGGER.info("CraftRestock: Non-matching item in input");
					return;
				}
			}
			if(needed.stack.isEmpty()) continue;
			// TODO: currently assumes player inv is always the last 36 slots
			for(int j=slots.size()-36; j<slots.size(); ++j){
				if(ItemStack.isSameItemSameComponents(needed.stack, slots.get(j).getItem())){restockFrom[i] = j; break;}
			}
			if(restockFrom[i] == -1){
				Main.LOGGER.info("CraftRestock: Unable to find restock item: "+needed.stack.getHoverName().getString());
				return; // Unable to find matching item to restock a non-empty input slot
			}
		}

		final int syncId = hs.getMenu().containerId;
		if(screen(client) instanceof AnvilScreen as && THREAD_START == 0 && restockFrom[0] != -1){
			Main.LOGGER.info("CraftRestock: Restocking for anvil ("+restockFrom[0]+"->INPUT_1"+")");
			client.gameMode.handleContainerInput(syncId, restockFrom[0], 0, ContainerInput.QUICK_MOVE, client.player);
			if(restockFrom[1] != -1) client.gameMode.handleContainerInput(syncId, restockFrom[1], 0, ContainerInput.QUICK_MOVE, client.player);

			ItemStack input0 = slots.get(restockFrom[0]).getItem();
			final Component input0NameText = input0.getCustomName();
			final String input0Name = input0NameText == null ? null : input0NameText.tryCollapseToString();
			if(anvilName != null && !anvilName.equals(input0Name)){
				updateAnvilName(as);
//				Main.LOGGER.info("assigned name!");
				// TODO: Omg please figure out some event-driven alternative
				THREAD_START = System.currentTimeMillis();
				new Thread(){@Override public void run(){
//					int attempts = 0;
					boolean lastWasGood = false;
					while(true){
						if(!(screen(client) instanceof AnvilScreen as)){
							Main.LOGGER.info("not in anvilscreen! is forgingscreen:"+(screen(client) instanceof ItemCombinerScreen));
							if(screen(client) instanceof ItemCombinerScreen){Thread.yield(); continue;}
							break;
						}
						if(System.currentTimeMillis() - THREAD_START >= THREAD_TIMEOUT){
							Main.LOGGER.info("thread timeout, start="+THREAD_START+",lastWasGood="+lastWasGood);
							break;
						}

						ItemStack result = as.getMenu().getSlot(AnvilMenu.RESULT_SLOT).getItem();
						final Component resultNameText = result.getCustomName();
						String resultName = resultNameText == null ? null : resultNameText.tryCollapseToString();
						if(!anvilName.equals(resultName) && (input0Name == null ? resultName == null : input0Name.equals(resultName))){
//							Main.LOGGER.info("assigned name in thread! attempt="+attempts);
							updateAnvilName(as);
							try{Thread.sleep(10);} catch(InterruptedException e){e.printStackTrace();}
//							if(++attempts == 1000) break;
							lastWasGood = false;
						}
						else{
							lastWasGood = true;
//							Main.LOGGER.info("name looks correct, for now");
						}
						Thread.yield();
					}
					THREAD_START = 0;
				}}.start();
			}
		}
	}
}