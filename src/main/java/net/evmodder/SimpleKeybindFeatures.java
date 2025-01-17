package net.evmodder;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import net.evmodder.EvLib.Pair;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerModelPart;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Identifier;

final class SimpleKeybindFeatures{
	private static final String SKIN_LAYER_CATEGORY = "key.categories."+KeyBound.MOD_ID+".skin_toggles";
	private static final String CHAT_MSG_CATEGORY = "key.categories."+KeyBound.MOD_ID+".chat_messages";

	final static void registerSkinLayerKeybinds(){
		Arrays.stream(PlayerModelPart.values())
		.map(part -> new AbstractKeybind("key."+KeyBound.MOD_ID+".skin_toggle."+part.name().toLowerCase(), InputUtil.Type.KEYSYM, -1, SKIN_LAYER_CATEGORY){
			@Override public void onPressed(){
				final MinecraftClient client = MinecraftClient.getInstance();
				client.options.setPlayerModelPart(part, !client.options.isPlayerModelPartEnabled(part));
			}
		}).forEach(KeyBindingHelper::registerKeyBinding);
	}

	final static void registerChatKeybind(String keybind_name, String chat_message){
		if(chat_message.charAt(0) == '/'){
			final String command = chat_message.substring(1);
			KeyBindingHelper.registerKeyBinding(new AbstractKeybind("key."+KeyBound.MOD_ID+"."+keybind_name, InputUtil.Type.KEYSYM, -1, CHAT_MSG_CATEGORY){
				@Override public void onPressed(){
					MinecraftClient instance = MinecraftClient.getInstance();
					instance.player.networkHandler.sendChatCommand(command);
				}
			});
		}
		else{
			KeyBindingHelper.registerKeyBinding(new AbstractKeybind("key."+KeyBound.MOD_ID+"."+keybind_name, InputUtil.Type.KEYSYM, -1, CHAT_MSG_CATEGORY){
				@Override public void onPressed(){
					MinecraftClient instance = MinecraftClient.getInstance();
					instance.player.networkHandler.sendChatMessage(chat_message);
				}
			});
		}
	}

	final static void registerInvOrganizeKeyBind(String keybind_name, String layout){
		List<Pair<Integer, Identifier>> layoutMap = 
		Arrays.stream(layout.substring(1, layout.length()-1).split(","))
		.map(s -> {
				int idx = Integer.parseInt(s.substring(0, s.indexOf(':')));
				String iname = s.substring(s.indexOf(':')+1);
				Identifier id = Identifier.of(iname);
				if(!Registries.ITEM.containsId(id)){
					KeyBound.LOGGER.error("Unknown item in '"+keybind_name+"': "+iname);
					return null;
				}
				return new Pair<>(idx, id);
			}
		)
		.filter(p -> p != null)
		.toList();
		KeyBindingHelper.registerKeyBinding(new AbstractKeybind("key."+KeyBound.MOD_ID+"."+keybind_name, InputUtil.Type.KEYSYM, -1, KeyBound.KEYBIND_CATEGORY){
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
//			private void swapSlots(int src, int dest){
//				final MinecraftClient instance = MinecraftClient.getInstance();
////				final ItemStack srcStack = src == -106 ? instance.player.getOffHandStack() : instance.player.getInventory().getStack(src);
////				i(src == -106) instance.player
//				instance.interactionManager.clickSlot(0, src, dest, SlotActionType.SWAP, instance.player);
//			}
			@Override public void onPressed(){
				final HashSet<Integer> doneSlots = new HashSet<>();
				final MinecraftClient instance = MinecraftClient.getInstance();
				Inventory inv = instance.player.getInventory();


				KeyBound.LOGGER.info("inv org keybind pressed");
				//TODO: ALL BROKEN NOTHING WORKING RIPPP
				for(Pair<Integer, Identifier> p : layoutMap){
					final int destSlot = p.a;
					if(doneSlots.contains(destSlot)) continue;
					final Identifier id = p.b;
					int srcSlot = findSlotWithItem(inv, id.getPath(), doneSlots);
					KeyBound.LOGGER.info("src slot: "+ srcSlot);
					//if(srcSlot == -1 && id.getPath().equals(getName(instance.player.getOffHandStack())) && !doneSlots.contains(-106)) srcSlot = -106;
					if(srcSlot != -1){
						if(srcSlot != destSlot){
							KeyBound.LOGGER.info("swapping slots "+srcSlot+" and "+destSlot);
							ItemStack temp = inv.getStack(srcSlot);
							inv.setStack(srcSlot, inv.getStack(destSlot));
							inv.setStack(destSlot, temp);
							instance.interactionManager.clickSlot(0, destSlot, srcSlot, SlotActionType.SWAP, instance.player);
						}
						doneSlots.add(destSlot);
					}
				}
				if(!doneSlots.isEmpty()) inv.markDirty();
			}
		});
	}
}