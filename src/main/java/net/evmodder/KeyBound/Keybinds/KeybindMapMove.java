package net.evmodder.KeyBound.Keybinds;

import java.util.ArrayDeque;
import java.util.HashMap;
import net.evmodder.KeyBound.Main;
import net.evmodder.KeyBound.Keybinds.ClickUtils.ClickEvent;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;

//TODO: Maybe preserve relative position of maps (eg., in a 3x3, keep them in a 3x3 in result GUI)?

public final class KeybindMapMove{
	private final boolean isMapArt(ItemStack stack){
		if(stack == null || stack.isEmpty()) return false;
		return Registries.ITEM.getId(stack.getItem()).getPath().equals("filled_map");
	}

//	private MapState getMapState(World world, ItemStack stack){
//		if(stack == null || stack.isEmpty()) return null;
//		if(!Registries.ITEM.getId(stack.getItem()).getPath().equals("filled_map")) return null;
//		return FilledMapItem.getMapState(stack, world);
//	}

	private boolean ongoingStealStore;
	private long lastStealStore = 0;
	private final long stealStoreCooldown = 250l;
	private final void moveMapArtToFromShulker(){
		if(ongoingStealStore){Main.LOGGER.warn("MapMove cancelled: Already ongoing"); return;}
		//
		MinecraftClient client = MinecraftClient.getInstance();
		if(!(client.currentScreen instanceof ShulkerBoxScreen sbs)){/*Main.LOGGER.warn("MapMove cancelled: Not in ShulkerBoxScreen"); */return;}
		//
		final long ts = System.currentTimeMillis();
		if(ts - lastStealStore < stealStoreCooldown){/*Main.LOGGER.warn("MapMove cancelled: Cooldown, "+ts+","+lastStealStore+","+stealStoreCooldown); */return;}
		lastStealStore = ts;
		//
		final DefaultedList<Slot> slots = sbs.getScreenHandler().slots;
		int numInInv = 0, emptySlotsInv = 0, sameCountInv = 0;
		HashMap<Integer, Integer> mapsInPlayerInv = new HashMap<>();// id -> available stackable space
		for(int i=0; i<36; ++i){
			ItemStack stack = client.player.getInventory().getStack(i);
			if(stack == null || stack.isEmpty()) ++emptySlotsInv;
			else if(isMapArt(stack)){
				MapIdComponent id = stack.get(DataComponentTypes.MAP_ID);
				if(id == null){Main.LOGGER.error("MapMove cancelled: Unloaded map in player inventory (!!!)"); return;}
				int space = mapsInPlayerInv.getOrDefault(id.id(), 0);
				mapsInPlayerInv.put(id.id(), space+(stack.getMaxCount()-stack.getCount()));
				++numInInv;
				if(sameCountInv == 0) sameCountInv = stack.getCount();
				else if(sameCountInv != stack.getCount()) sameCountInv = -1; // Different map slots with different counts => -1
			}
		}
		boolean cantMergeMapsIntoShulker = false;
		int numInShulk = 0, emptySlotsShulk = 0, sameCountShulk = 0;
		for(int i=0; i<27; ++i){
			ItemStack stack = slots.get(i).getStack();
			if(stack == null || stack.isEmpty()) ++emptySlotsShulk;
			else if(isMapArt(stack)){
				++numInShulk;
				if(sameCountShulk == 0) sameCountShulk = stack.getCount();
				else if(sameCountShulk != stack.getCount()) sameCountShulk = -1; // Different map slots with different counts => -1

				if(cantMergeMapsIntoShulker) continue;
				MapIdComponent id = stack.get(DataComponentTypes.MAP_ID);
				if(id == null){cantMergeMapsIntoShulker = true; continue;}
				Integer space = mapsInPlayerInv.get(id.id());
				if(space == null || space - stack.getCount() < 0) cantMergeMapsIntoShulker = true;
				else mapsInPlayerInv.put(space, space - stack.getCount());
			}
		}
		boolean moveToShulk = true;
		if(numInShulk == 0 && numInInv == 0){Main.LOGGER.warn("MapMove cancelled: No mapart found"); return;}
		if(numInShulk != 0 && numInInv != 0 && (numInInv != numInShulk || cantMergeMapsIntoShulker
				|| sameCountShulk == -1 || sameCountInv == -1 || sameCountShulk+sameCountInv > 64)){
			Main.LOGGER.warn("MapMove cancelled: Unique mapart found in both inventory AND shulker");
			return;
		}
		if(numInInv == 0){
			moveToShulk = false;
			if(numInShulk > emptySlotsInv){Main.LOGGER.warn("MapMove cancelled: Not enough empty slots in inventory"); return;}
		}
		else if(numInInv > emptySlotsShulk && cantMergeMapsIntoShulker){Main.LOGGER.warn("MapMove cancelled: Not enough empty slots in shulker"); return;}
		//
		final boolean isShiftClick = Screen.hasShiftDown();
		ArrayDeque<ClickEvent> clicks = new ArrayDeque<>();
		HashMap<ClickEvent, Integer> reserveClicks = new HashMap<>();
		if(moveToShulk) for(int i=27; i<63; ++i){
			if(!isMapArt(slots.get(i).getStack())) continue;
			if((sameCountInv == 2 || sameCountInv == 3) && !isShiftClick){
				clicks.add(new ClickEvent(i, 1, SlotActionType.PICKUP)); // right-click: pickup half
				if(Main.inventoryUtils.MAX_CLICKS >= 3) reserveClicks.put(clicks.peekLast(), 3);
			}
			clicks.add(new ClickEvent(i, 0, SlotActionType.QUICK_MOVE)); // shift-click: all in slot to shulker
			if((sameCountInv == 2 || sameCountInv == 3) && !isShiftClick){
				clicks.add(new ClickEvent(i, 0, SlotActionType.PICKUP)); // left-click: put back all on cursor
			}
		}
		else for(int i=26; i>=0; --i){
//			if(isMapArt(sh.getSlot(i).getStack())) clicks.add(new ClickEvent(sh.syncId, i, 0, SlotActionType.QUICK_MOVE));
			if(!isMapArt(slots.get(i).getStack())) continue;
			if(sameCountShulk > 1 && !isShiftClick){
				if(sameCountShulk <= 3){
					clicks.add(new ClickEvent(i, 1, SlotActionType.PICKUP)); // right-click: pickup half
					if(Main.inventoryUtils.MAX_CLICKS >= 3) reserveClicks.put(clicks.peekLast(), 3);
				}
				else{
					clicks.add(new ClickEvent(i, 0, SlotActionType.PICKUP)); // left-click: pickup all
					if(Main.inventoryUtils.MAX_CLICKS >= 4) reserveClicks.put(clicks.peekLast(), 4);
					clicks.add(new ClickEvent(i, 1, SlotActionType.PICKUP)); // right-click: place one
				}
			}
			clicks.add(new ClickEvent(i, 0, SlotActionType.QUICK_MOVE));
			if(sameCountShulk > 1 && !isShiftClick){
				clicks.add(new ClickEvent(i, 0, SlotActionType.PICKUP)); // left-click: place all
			}
		}

		//Main.LOGGER.info("MapMove: STARTED");
		ongoingStealStore = true;
		Main.inventoryUtils.executeClicks(clicks,
				c->{
					// Don't start cursor-pickup move operation if we can't complete it in 1 go
					final Integer clicksNeeded = reserveClicks.get(c);
					if(clicksNeeded == null || clicksNeeded <= Main.inventoryUtils.MAX_CLICKS - Main.inventoryUtils.addClick(null)) return true;
					client.player.sendMessage(Text.literal("MapMove: Waiting for clicks...").withColor(15764490), true);
					return false;
				},
				()->{
					Main.LOGGER.info("MapMove: DONE!");
					ongoingStealStore = false;
				});
	}

	public KeybindMapMove(){
		KeyBindingHelper.registerKeyBinding(new EvKeybind("mapart_take_place", ()->moveMapArtToFromShulker(), s->s instanceof ShulkerBoxScreen));
	}
}