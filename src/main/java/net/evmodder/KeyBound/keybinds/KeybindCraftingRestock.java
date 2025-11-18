package net.evmodder.KeyBound.keybinds;

import java.util.List;
import java.util.stream.IntStream;
import net.evmodder.KeyBound.Main;
import net.evmodder.KeyBound.mixin.AccessorAnvilScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import net.minecraft.client.gui.screen.ingame.ForgingScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

public final class KeybindCraftingRestock{
	record SlotAndItem(int slot, ItemStack stack){
		SlotAndItem(ScreenHandler sh, int slot){this(slot, sh.getSlot(slot).getStack().copy());}
	}
	private List<SlotAndItem> inputItems;
	private String anvilName;
	private long THREAD_START;
	private final long THREAD_TIMEOUT = 2000;
	private Class<?> lastScreen;

	private boolean willCraftItem(SlotActionType action){
		switch(action){
			case PICKUP:
			case QUICK_MOVE:
			case SWAP:
				return true;
			default:
				return false;
		}
	}
	public void checkIfCraftAction(ScreenHandler sh, int slot, int button, SlotActionType action){
		if(sh == null || !willCraftItem(action)) return;
		if(sh instanceof AnvilScreenHandler && slot == AnvilScreenHandler.OUTPUT_ID){
			lastScreen = AnvilScreenHandler.class;
			inputItems = List.of(new SlotAndItem(sh, AnvilScreenHandler.INPUT_1_ID), new SlotAndItem(sh, AnvilScreenHandler.INPUT_2_ID));
//			anvilText = as.newItemName;
			Text anvilText = sh.getSlot(AnvilScreenHandler.OUTPUT_ID).getStack().getCustomName();
			anvilName = anvilText == null ? null : anvilText.getLiteralString();
			THREAD_START = 0; // Cancel current anvil name thread - anvil craft event occured
//			try{anvilNameThread.join();}catch(InterruptedException e){e.printStackTrace();}

			Main.LOGGER.info("CraftRestock: Storing 2 anvil slots"+(anvilName==null?"": " and a custom name"));
		}
		if(sh instanceof PlayerScreenHandler && slot == PlayerScreenHandler.CRAFTING_RESULT_ID){
			lastScreen = PlayerScreenHandler.class;
			inputItems = IntStream.range(PlayerScreenHandler.CRAFTING_INPUT_START, PlayerScreenHandler.CRAFTING_INPUT_END)
					.mapToObj(i -> new SlotAndItem(sh, i)).toList();
			Main.LOGGER.info("CraftRestock: Storing 2x2 player inv slots");
		}
		if(sh instanceof CraftingScreenHandler && slot == CraftingScreenHandler.RESULT_ID){
			lastScreen = CraftingScreenHandler.class;
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
		TextFieldWidget nameField = ((AccessorAnvilScreen)as).getNameField();
		nameField.setText(anvilName);
		nameField.setCursorToStart(false);
		nameField.setCursorToEnd(false);
	}

	public void restockInputSlots(){
//		Main.LOGGER.info("CraftRestock: restockInputSlots() called");
		MinecraftClient client = MinecraftClient.getInstance();
		if(lastScreen == null || !(client.currentScreen instanceof HandledScreen hs) || !lastScreen.isInstance(hs.getScreenHandler())) return;
		assert inputItems != null;
		final List<Slot> slots = hs.getScreenHandler().slots;
		final int[] restockFrom = new int[inputItems.size()];
//		Main.LOGGER.info("CraftRestock: Looking for available items");
		for(int i=0; i<inputItems.size(); ++i){
			SlotAndItem needed = inputItems.get(i);
			restockFrom[i] = -1;
			if(slots.get(needed.slot).hasStack()){
				if(ItemStack.areItemsAndComponentsEqual(slots.get(needed.slot).getStack(), needed.stack)) continue;
				else{
					Main.LOGGER.info("CraftRestock: Non-matching item in input");
					return;
				}
			}
			if(needed.stack.isEmpty()) continue;
			// TODO: currently assumes player inv is always the last 36 slots
			for(int j=slots.size()-36; j<slots.size(); ++j){
				if(ItemStack.areItemsAndComponentsEqual(needed.stack, slots.get(j).getStack())){restockFrom[i] = j; break;}
			}
			if(restockFrom[i] == -1){
				Main.LOGGER.info("CraftRestock: Unable to find restock item: "+needed.stack.getName().getString());
				return; // Unable to find matching item to restock a non-empty input slot
			}
		}

		final int syncId = hs.getScreenHandler().syncId;
		if(client.currentScreen instanceof AnvilScreen as && THREAD_START == 0 && restockFrom[0] != -1){
			Main.LOGGER.info("CraftRestock: Restocking for anvil ("+restockFrom[0]+"->INPUT_1"+")");
			client.interactionManager.clickSlot(syncId, restockFrom[0], 0, SlotActionType.QUICK_MOVE, client.player);
			if(restockFrom[1] != -1) client.interactionManager.clickSlot(syncId, restockFrom[1], 0, SlotActionType.QUICK_MOVE, client.player);

			ItemStack input0 = slots.get(restockFrom[0]).getStack();
			final String input0Name = input0.getCustomName() == null ? null : input0.getCustomName().getLiteralString();
			if(anvilName != null && !anvilName.equals(input0Name)){
				updateAnvilName(as);
//				Main.LOGGER.info("assigned name!");
				// TODO: Omg please figure out some event-driven alternative
				THREAD_START = System.currentTimeMillis();
				new Thread(){@Override public void run(){
//					int attempts = 0;
					boolean lastWasGood = false;
					while(true){
						if(!(client.currentScreen instanceof AnvilScreen as)){
							Main.LOGGER.info("not in anvilscreen! is forgingscreen:"+(client.currentScreen instanceof ForgingScreen));
							if(client.currentScreen instanceof ForgingScreen){Thread.yield(); continue;}
							break;
						}
						if(System.currentTimeMillis() - THREAD_START >= THREAD_TIMEOUT){
							Main.LOGGER.info("thread timeout, start="+THREAD_START+",lastWasGood="+lastWasGood);
							break;
						}

						ItemStack result = as.getScreenHandler().getSlot(AnvilScreenHandler.OUTPUT_ID).getStack();
						String resultName = result.getCustomName() == null ? null : result.getCustomName().getLiteralString();
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