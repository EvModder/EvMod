package net.evmodder.KeyBound.Keybinds;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.TreeSet;
import java.util.stream.IntStream;
import org.lwjgl.glfw.GLFW;
import net.evmodder.KeyBound.Main;
import net.evmodder.KeyBound.Keybinds.ClickUtils.ClickEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapState;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.world.World;

//TODO: Maybe preserve relative position of maps (eg., in a 3x3, keep them in a 3x3 in result GUI)?

public final class KeybindMapMove{

	private boolean isSpaceFillerMap(World world, ItemStack stack){
		if(stack.getCustomName() == null) return false;
		String name = stack.getCustomName().getLiteralString();
		if(name == null || !name.startsWith("slot")) return false;
		MapState state = FilledMapItem.getMapState(stack, world);
		if(state == null) return false;//Rather a false negative than a false positive
		byte[] colors = state.colors;
		for(int i=1; i<colors.length; ++i) if(colors[i] != colors[i-1]) return false;
		return true;
	}

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
		if(ts - lastStealStore < stealStoreCooldown){
			//Main.LOGGER.warn("MapMove cancelled: Cooldown, "+ts+","+lastStealStore+","+stealStoreCooldown); 
			return;
		}
		lastStealStore = ts;
		//
		final ItemStack[] slots = sbs.getScreenHandler().slots.stream().map(s -> s.getStack()).toArray(ItemStack[]::new);
		int numInInv = 0, emptySlotsInv = 0;
		TreeSet<Integer> countsInInv = new TreeSet<>();
		HashMap<Integer, Integer> mapIdsInInv = new HashMap<>();// id -> available stackable space
		for(int i=0; i<36; ++i){
			ItemStack stack = client.player.getInventory().getStack(i);
			if(stack == null || stack.isEmpty()) ++emptySlotsInv;
			else if(stack.getItem() == Items.FILLED_MAP){
				if(isSpaceFillerMap(client.world, stack)) continue;
				++numInInv;
				final int count = stack.getCount();
				countsInInv.add(count);
				MapIdComponent id = stack.get(DataComponentTypes.MAP_ID);
				if(id == null){Main.LOGGER.error("MapMove cancelled: Unloaded map in player inventory (!!!)"); return;}
				mapIdsInInv.put(id.id(), mapIdsInInv.getOrDefault(id.id(), 0)+(stack.getMaxCount()-count));
			}
		}
		int cantMergeIntoInv = 0;
		int numInShulk = 0, emptySlotsShulk = 0;
		TreeSet<Integer> countsInShulk = new TreeSet<>();
		HashMap<Integer, Integer> mapIdsInShulker = new HashMap<>();
		for(int i=0; i<27; ++i){
			ItemStack stack = slots[i];
			if(stack.isEmpty()) ++emptySlotsShulk;
			else if(stack.getItem() == Items.FILLED_MAP){
				if(isSpaceFillerMap(client.world, slots[i])) continue;
				++numInShulk;
				final int count = slots[i].getCount();
				countsInShulk.add(count);
				MapIdComponent id = stack.get(DataComponentTypes.MAP_ID);
				//if(id == null){Main.LOGGER.warn("MapMove cancelled: Unloaded map in shulker"); return;}
				if(id == null){++cantMergeIntoInv; continue;}
				mapIdsInShulker.put(id.id(), mapIdsInInv.getOrDefault(id.id(), 0)+(stack.getMaxCount()-count));
				Integer space = mapIdsInInv.getOrDefault(id.id(), 0);
				if(count > space) ++cantMergeIntoInv;
				else mapIdsInInv.put(id.id(), space - count);
			}
		}

		long cantMergeIntoShulk =
				IntStream.range(0, 36).mapToObj(i -> client.player.getInventory().getStack(i))
				.filter(s -> s.getItem() == Items.FILLED_MAP)
				.filter(s -> !isSpaceFillerMap(client.world, s))
				.filter(s -> {
					MapIdComponent id = s.get(DataComponentTypes.MAP_ID);
					Integer space = mapIdsInShulker.getOrDefault(id.id(), 0);
					if(s.getCount() > space) return false;
					mapIdsInShulker.put(id.id(), space - s.getCount());
					return true;
				}).count();

		if(numInShulk == 0 && numInInv == 0){Main.LOGGER.warn("MapMove cancelled: No mapart found"); return;}
		if(cantMergeIntoShulk != 0 && cantMergeIntoInv != 0){
			Main.LOGGER.warn("MapMove cancelled: Unique mapart found in both inventory AND shulker");
			return;
		}
		if(numInInv == 0 && cantMergeIntoInv > emptySlotsInv){Main.LOGGER.warn("MapMove cancelled: Not enough empty slots in inventory"); return;}
		if(numInShulk == 0 && cantMergeIntoShulk > emptySlotsShulk){Main.LOGGER.warn("MapMove cancelled: Not enough empty slots in shulker"); return;}

		boolean moveToShulk = numInShulk == 0 || cantMergeIntoInv > emptySlotsInv;
		//
		final boolean isShiftClick = Screen.hasShiftDown();
		ArrayDeque<ClickEvent> clicks = new ArrayDeque<>();
		HashMap<ClickEvent, Integer> reserveClicks = new HashMap<>();
		final boolean selectiveMove = !isShiftClick && moveToShulk
				? (countsInInv.size() == 2 && cantMergeIntoShulk == 0)
				: (countsInShulk.size() == 2 && (cantMergeIntoInv == 0 || numInInv == 0));
		if(moveToShulk) for(int i=27; i<63; ++i){
			if(slots[i].getItem() != Items.FILLED_MAP) continue;
			if(isSpaceFillerMap(client.world, slots[i])) continue;
			final int count = slots[i].getCount();
			if(selectiveMove && count != countsInInv.last()) continue;
			if((count == 2 || count == 3) && !isShiftClick){
				clicks.add(new ClickEvent(i, 1, SlotActionType.PICKUP)); // right-click: pickup half
				if(Main.inventoryUtils.MAX_CLICKS >= 3) reserveClicks.put(clicks.peekLast(), 3);
			}
			clicks.add(new ClickEvent(i, 0, SlotActionType.QUICK_MOVE)); // shift-click: all in slot to shulker
			if((count == 2 || count == 3) && !isShiftClick){
				clicks.add(new ClickEvent(i, 0, SlotActionType.PICKUP)); // left-click: put back all on cursor
			}
		}
		else for(int i=26; i>=0; --i){
//			if(isMapArt(sh.getSlot(i).getStack())) clicks.add(new ClickEvent(sh.syncId, i, 0, SlotActionType.QUICK_MOVE));
			if(slots[i].getItem() != Items.FILLED_MAP) continue;
			if(isSpaceFillerMap(client.world, slots[i])) continue;
			final int count = slots[i].getCount();
			if(selectiveMove && count != countsInShulk.last()) continue;
			if(count > 1 && !isShiftClick){
				if(count <= 3){
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
			if(count > 1 && !isShiftClick){
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
					client.player.sendMessage(Text.literal("MapMove: Waiting for clicks...").withColor(KeybindMapCopy.WAITING_FOR_CLICKS_COLOR), true);
					return false;
				},
				()->{
					Main.LOGGER.info("MapMove: DONE!");
					ongoingStealStore = false;
				});
	}

	public KeybindMapMove(){
		new Keybind("mapart_take_place", ()->moveMapArtToFromShulker(), s->s instanceof ShulkerBoxScreen, GLFW.GLFW_KEY_T);
	}
}