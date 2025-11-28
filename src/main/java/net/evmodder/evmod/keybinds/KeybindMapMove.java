package net.evmodder.evmod.keybinds;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.TreeSet;
import java.util.stream.IntStream;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.Main;
import net.evmodder.evmod.apis.MapColorUtils;
import net.evmodder.evmod.apis.MapRelationUtils;
import net.evmodder.evmod.apis.ClickUtils.ActionType;
import net.evmodder.evmod.apis.ClickUtils.InvAction;
import net.evmodder.evmod.apis.MapRelationUtils.RelatedMapsData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapState;
import net.minecraft.text.Text;
import net.minecraft.world.World;

//TODO: Maybe preserve relative position of maps (eg., in a 3x3, keep them in a 3x3 in result GUI)?

public final class KeybindMapMove{
	static final boolean isFillerMap(ItemStack[] slots, ItemStack stack, World world){
		if(!Configs.Generic.SKIP_TRANSPARENT_MAPS.getBooleanValue()) return false;
		final MapState state = FilledMapItem.getMapState(stack, world);
		if(state == null || !MapColorUtils.isFullyTransparent(state.colors)) return false;
		if(stack.getCustomName() == null) return true;
		final RelatedMapsData data = MapRelationUtils.getRelatedMapsByName(
				Arrays.asList(slots), stack.getCustomName().getString(), stack.getCount(), state.locked, world);
		return data.slots().stream().map(i -> slots[i].getCustomName().getLiteralString()).distinct().count() <= 1;
	}

	public final void moveMapArtToFromShulker(){
		if(Main.clickUtils.hasOngoingClicks()){Main.LOGGER.warn("MapMove cancelled: Already ongoing"); return;}
		//
		MinecraftClient client = MinecraftClient.getInstance();
		if(!(client.currentScreen instanceof HandledScreen hs)){/*Main.LOGGER.warn("MapMove cancelled: Not in ShulkerBoxScreen"); */return;}
		//
		if(hs.getScreenHandler().slots.size() != 63/*27+36*/){
			Main.LOGGER.warn("MapMove cancelled: Unexpected slot count for MapMove: "+hs.getScreenHandler().slots.size());
			return;
		}
		//
		final ItemStack[] slots = hs.getScreenHandler().slots.stream().map(s -> s.getStack()).toArray(ItemStack[]::new);
		int numInInv = 0, emptySlotsInv = 0;
		TreeSet<Integer> countsInInv = new TreeSet<>();
		HashMap<ItemStack, Integer> invCapacity = new HashMap<>();// id -> available space to merge into
		for(int i=0; i<36; ++i){
			ItemStack stack = client.player.getInventory().getStack(i);
			if(stack == null || stack.isEmpty()) ++emptySlotsInv;
			else if(stack.getItem() == Items.FILLED_MAP){
				if(isFillerMap(slots, stack, client.world)) continue;
				if(FilledMapItem.getMapState(stack, client.world) == null){
					Main.LOGGER.warn("MapMove: Unloaded map in player inventory!");
//					return;
				}
				++numInInv;
				final int count = stack.getCount();
				countsInInv.add(count);
				invCapacity.put(stack, invCapacity.getOrDefault(stack, 0)+(stack.getMaxCount()-count));
			}
		}
		int cantMergeIntoInv = 0;
		int numInShulk = 0, emptySlotsShulk = 0;
		TreeSet<Integer> countsInShulk = new TreeSet<>();
		HashMap<ItemStack, Integer> shulkCapacity = new HashMap<>();
		boolean smallerSlotsAtStart = true;
		final boolean ALLOW_AIR_POCKETS = Configs.Generic.KEYBIND_MAPART_MOVE_IGNORE_AIR_POCKETS.getBooleanValue();
		for(int i=0; i<27; ++i){
			ItemStack stack = slots[i];
			if(stack.isEmpty()) ++emptySlotsShulk;
			else if(stack.getItem() == Items.FILLED_MAP){
				if(isFillerMap(slots, stack, client.world)) continue;
				if(emptySlotsShulk != 0 && !ALLOW_AIR_POCKETS){
					client.player.sendMessage(Text.literal("MapMove: Air gap between items in shulker currently disabled"), true);
					client.player.sendMessage(Text.literal("MapMove: Air gap between items in shulker currently disabled"), false);
					return;
				}
				++numInShulk;
				final int count = slots[i].getCount();
				countsInShulk.add(count);
				if(count < countsInShulk.last()) smallerSlotsAtStart = false;
				shulkCapacity.put(stack, shulkCapacity.getOrDefault(stack, 0)+(stack.getMaxCount()-count));

				int space = invCapacity.getOrDefault(stack, 0);
				if(count > space) ++cantMergeIntoInv;
				else invCapacity.put(stack, space - count);
			}
		}

		final long cantMergeIntoShulk =
				IntStream.range(0, 36).mapToObj(i -> client.player.getInventory().getStack(i))
				.filter(s -> s.getItem() == Items.FILLED_MAP)
				.filter(s -> !isFillerMap(slots, s, client.world))
				.filter(s -> {
					int space = shulkCapacity.getOrDefault(s, 0);
					if(s.getCount() > space) return false;
					shulkCapacity.put(s, space - s.getCount());
					return true;
				}).count();

		if(numInShulk == 0 && numInInv == 0){Main.LOGGER.warn("MapMove cancelled: No mapart found"); return;}
		if(cantMergeIntoShulk != 0 && cantMergeIntoInv != 0){
			Main.LOGGER.warn("MapMove cancelled: Unique mapart found in both inventory AND shulker");
			return;
		}
		if(numInInv == 0 && cantMergeIntoInv > emptySlotsInv){Main.LOGGER.warn("MapMove cancelled: Not enough empty slots in inventory"); return;}
		if(numInShulk == 0 && cantMergeIntoShulk > emptySlotsShulk){Main.LOGGER.warn("MapMove cancelled: Not enough empty slots in shulker"); return;}

		final boolean moveToShulk = numInShulk == 0 || cantMergeIntoInv > emptySlotsInv || (numInInv == numInShulk && cantMergeIntoShulk == 0);
		final boolean isShiftClick = Screen.hasShiftDown();
		final boolean selectiveMove = !isShiftClick && (moveToShulk
				? (countsInInv.size() == 2 && cantMergeIntoShulk == 0)
				: (countsInShulk.size() == 2 && smallerSlotsAtStart && (cantMergeIntoInv == 0 || numInInv == 0)));

		Main.LOGGER.info("MapMove: moveToShulk="+moveToShulk+", isShiftClick="+isShiftClick+", selectiveMove="+selectiveMove);
//		client.player.sendMessage(Text.literal("MapMove: moveToShulk="+moveToShulk+", isShiftClick="+isShiftClick+", selectiveMove="+selectiveMove), false);

		ArrayDeque<InvAction> clicks = new ArrayDeque<>();
		IdentityHashMap<InvAction, Integer> reserveClicks = new IdentityHashMap<>();
		if(moveToShulk) for(int i=27, j=0; i<63; ++i){
			if(slots[i].getItem() != Items.FILLED_MAP) continue;
			if(isFillerMap(slots, slots[i], client.world)) continue;
			final int count = slots[i].getCount();
			if(selectiveMove && count != countsInInv.last()) continue;
			if(numInShulk == 0){
				while(j < 27 && !slots[j].isEmpty()) ++j;
				if(j >= 27){
					Main.LOGGER.warn("MapMove: ran out of slots in shulker!");
					break;
				}
			}
			if(count == 1 || isShiftClick){
				clicks.add(new InvAction(i, 0, ActionType.SHIFT_CLICK));
			}
			else{ // put 1 into shulk
				if(count == 2 || (count == 3 && numInShulk != 0)){
					clicks.add(new InvAction(i, 1, ActionType.CLICK)); // pickup half
					if(numInShulk == 0){
						if(Main.clickUtils.MAX_CLICKS >= 2) reserveClicks.put(clicks.peekLast(), 2);
						clicks.add(new InvAction(j, 0, ActionType.CLICK)); // place into next empty slot
					}
					else{
						if(Main.clickUtils.MAX_CLICKS >= 3) reserveClicks.put(clicks.peekLast(), 3);
						clicks.add(new InvAction(i, 0, ActionType.SHIFT_CLICK)); // shift-move remaining (1)
						clicks.add(new InvAction(i, 0, ActionType.CLICK)); // place back
					}
				}
				else{
					clicks.add(new InvAction(i, 0, ActionType.CLICK)); // pickup all
					if(Main.clickUtils.MAX_CLICKS >= 4) reserveClicks.put(clicks.peekLast(), 4);
					clicks.add(new InvAction(i, 1, ActionType.CLICK)); // place one
					clicks.add(new InvAction(i, 0, ActionType.SHIFT_CLICK)); // shift-move the one
					clicks.add(new InvAction(i, 0, ActionType.CLICK)); // place all (-1)
				}
			}
			if(numInShulk == 0) ++j;
		}
		else for(int i=26, j=62; i>=0; --i){
//			if(isMapArt(sh.getSlot(i).getStack())) clicks.add(new ClickEvent(sh.syncId, i, 0, ClickAction.SHIFT_CLICK));
			if(slots[i].getItem() != Items.FILLED_MAP) continue;
			if(isFillerMap(slots, slots[i], client.world)) continue;
			final int count = slots[i].getCount();
			if(selectiveMove && count != countsInShulk.last()) continue;
			if(numInInv == 0){
				while(j >= 27 && !slots[j].isEmpty()) --j;
				if(j < 27){
					Main.LOGGER.warn("MapMove: ran out of slots in inv!");
					break;
				}
			}
			if(count == 1 || isShiftClick){
				clicks.add(new InvAction(i, 0, ActionType.SHIFT_CLICK));
			}
			else{ // take 1 from shulk
				if(count == 2 || (count == 3 && numInInv != 0)){
					clicks.add(new InvAction(i, 1, ActionType.CLICK)); // pickup half
					if(numInInv == 0){
						if(Main.clickUtils.MAX_CLICKS >= 2) reserveClicks.put(clicks.peekLast(), 2);
						clicks.add(new InvAction(j, 0, ActionType.CLICK)); // place into next empty slot
					}
					else{
						if(Main.clickUtils.MAX_CLICKS >= 3) reserveClicks.put(clicks.peekLast(), 3);
						clicks.add(new InvAction(i, 0, ActionType.SHIFT_CLICK)); // shift-move remaining (1)
						clicks.add(new InvAction(i, 0, ActionType.CLICK)); // place back
					}
				}
				else{
					clicks.add(new InvAction(i, 0, ActionType.CLICK)); // pickup all
					if(Main.clickUtils.MAX_CLICKS >= 4) reserveClicks.put(clicks.peekLast(), 4);
					clicks.add(new InvAction(i, 1, ActionType.CLICK)); // place one
					clicks.add(new InvAction(i, 0, ActionType.SHIFT_CLICK)); // shift-move the one
					clicks.add(new InvAction(i, 0, ActionType.CLICK)); // place all (-1)
				}
			}
			if(numInInv == 0) --j;
		}

		//Main.LOGGER.info("MapMove: STARTED");
		Main.clickUtils.executeClicks(c->{
					// Don't start cursor-pickup move operation if we can't complete it in 1 go
					final Integer clicksNeeded = reserveClicks.get(c);
					if(clicksNeeded == null || clicksNeeded <= Main.clickUtils.calcAvailableClicks()) return true;
					return false; // Wait for clicks
				}, ()->Main.LOGGER.info("MapMove: DONE!"),
				clicks
		);
	}

//	public KeybindMapMove(){
//		new Keybind("mapart_move", ()->moveMapArtToFromShulker(), s->s instanceof HandledScreen && s instanceof InventoryScreen == false, GLFW.GLFW_KEY_T);
//	}
}