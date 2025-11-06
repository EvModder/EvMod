package net.evmodder.KeyBound.keybinds;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.IntStream;
import net.evmodder.KeyBound.Main;
import net.evmodder.KeyBound.keybinds.ClickUtils.ClickEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapState;
import net.minecraft.registry.Registries;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;

public final class KeybindMapLoad{
	private boolean isUnloadedMapArt(World world, ItemStack stack){
//		if(stack == null || stack.isEmpty()) return false;
//		if(!Registries.ITEM.getId(stack.getItem()).getPath().equals("filled_map")) return false;
//		return FilledMapItem.getMapState(stack, world) == null;
		if(stack.getItem() != Items.FILLED_MAP) return false;
		MapState state = FilledMapItem.getMapState(stack, world);
		return state == null || state.colors == null || state.colors.length != 128*128;
	}
	private boolean isLoadedMapArt(World world, ItemStack stack){
		if(stack.getItem() != Items.FILLED_MAP) return false;
		MapState state = FilledMapItem.getMapState(stack, world);
		return state != null && state.colors != null && state.colors.length == 128*128;
	}

	private boolean isShulkerBox(ItemStack stack){
		return !stack.isEmpty() && Registries.ITEM.getId(stack.getItem()).getPath().endsWith("shulker_box");
	}

//	private int getNextUsableHotbarButton(MinecraftClient client, int hb){
//		while(++hb < 9 && client.currentScreen instanceof ShulkerBoxScreen && isShulkerBox(client.player.getInventory().getStack(hb)));
//		return hb;
//	}

	private boolean isUsable(ItemStack stack){
		return !isShulkerBox(stack) && stack.get(DataComponentTypes.BUNDLE_CONTENTS) == null;
	}

	private int[] getUsableHotbarButtons(MinecraftClient client){
		if(client.currentScreen instanceof ShulkerBoxScreen == false) IntStream.range(0, 9).toArray();
		return IntStream.range(0, 9).filter(hb -> isUsable(client.player.getInventory().getStack(hb))).toArray();
	}

	private final long WAIT_FOR_STATE_UPDATE = 71; // 50 = 1 tick
	private long stateWaitStart;
	private final void loadMapArtFromBundles(){
//		Main.LOGGER.warn("MapLoadBundle: in InventoryScreen");
		final MinecraftClient client = MinecraftClient.getInstance();
		final InventoryScreen is = (InventoryScreen)client.currentScreen;
		final ItemStack[] slots = is.getScreenHandler().slots.stream().map(s -> s.getStack()).toArray(ItemStack[]::new);
		final int[] slotsWithBundles = IntStream.range(9, 45).filter(i -> slots[i].get(DataComponentTypes.BUNDLE_CONTENTS) != null).toArray();
		if(slotsWithBundles.length == 0){
			Main.LOGGER.warn("MapLoadBundle: No bundles in inventory");
			return;
		}
		final OptionalInt emptyBundleSlotOpt = Arrays.stream(slotsWithBundles)
				.filter(i -> slots[i].get(DataComponentTypes.BUNDLE_CONTENTS).getOccupancy().getNumerator() == 0).findAny();
		if(emptyBundleSlotOpt.isEmpty()){Main.LOGGER.warn("MapLoadBundle: Empty bundle not found"); return;}
		final int emptyBundleSlot = emptyBundleSlotOpt.getAsInt();
//		final OptionalInt emptySlotOpt = IntStream.range(9, 45).filter(i -> slots[i].isEmpty()).min(Comparator.comparingInt(i -> Math.abs(i-emptyBundleSlot)));
		Optional<Integer> emptySlotOpt = IntStream.range(9, 45).filter(i -> slots[i].isEmpty()).boxed()
				.min(Comparator.comparingInt(i -> Math.abs(i-emptyBundleSlot)));
		if(emptySlotOpt.isEmpty()){Main.LOGGER.warn("MapLoadBundle: Empty slot not found"); return;}
		final int emptySlot = emptySlotOpt.get();

		final ArrayDeque<ClickEvent> clicks = new ArrayDeque<>();
		final IdentityHashMap<ClickEvent, Integer> ableToSkipClicks = new IdentityHashMap<>();
		for(int i : slotsWithBundles){
			BundleContentsComponent contents = slots[i].get(DataComponentTypes.BUNDLE_CONTENTS);
			if(contents.isEmpty()) continue;
			if(contents.stream().anyMatch(s -> s.getItem() != Items.FILLED_MAP)) continue; // Skip bundles with non-mapart contents
			if(contents.stream().allMatch(s -> isLoadedMapArt(client.world, s))) continue; // Skip bundles with already-loaded mapart
//			Main.LOGGER.info("MapLoadBundle: found bundle with "+contents.size()+" maps");
			for(int j=0; j<contents.size(); ++j){
				ClickEvent c;
				clicks.add(c=new ClickEvent(i, 1, SlotActionType.PICKUP)); // Take last map from bundle
				clicks.add(new ClickEvent(emptySlot, 0, SlotActionType.PICKUP)); // Place map in empty slot
				// Wait for map state to load
				clicks.add(new ClickEvent(emptySlot, 0, SlotActionType.PICKUP)); // Take map from empty slot
				clicks.add(new ClickEvent(emptyBundleSlot, 0, SlotActionType.PICKUP)); // Place map in empty bundle

				ableToSkipClicks.put(c, 6*(contents.size()-j));
			}
			for(int j=0; j<contents.size(); ++j){
				clicks.add(new ClickEvent(emptyBundleSlot, 1, SlotActionType.PICKUP)); // Take last map from empty bundle
				clicks.add(new ClickEvent(i, 0, SlotActionType.PICKUP)); // Place map back in original bundle
			}
		}
		if(clicks.isEmpty()){
			Main.LOGGER.warn("MapLoadBundle: No bundles containing mapart in inventory");
			return; // No bundles with maps
		}
//		client.player.sendMessage(Text.literal("Scheduling clicks: "+clicks.size()), true);
		Main.LOGGER.info("MapLoadBundle: STARTED");
		Main.clickUtils.executeClicks(clicks,
			c->{
				if(client.player == null || client.world == null) return true;
				Integer skipIfLoaded = ableToSkipClicks.get(c);
//				Main.LOGGER.info("click for slot: "+c.slotId()+", clicksLeft: "+clicks.size());
				if(skipIfLoaded != null){
					//Main.LOGGER.info("MapLoadBundle: potentially skippable");
					BundleContentsComponent contents = client.player.currentScreenHandler.slots.get(c.slotId()).getStack().get(DataComponentTypes.BUNDLE_CONTENTS);
					if(contents != null && contents.stream().allMatch(s -> isLoadedMapArt(client.world, s))){
//						Main.LOGGER.info("MapLoadBundle: skippable! whoop whoop: "+(skipIfLoaded));
						for(int i=0; i<skipIfLoaded; ++i) clicks.remove();
						return false;
					}
				}
				if(c.slotId() != emptySlot) return true;
				if(stateWaitStart == 0){stateWaitStart = -1; return true;}
				ItemStack item = client.player.currentScreenHandler.slots.get(emptySlot).getStack();
				if(!isLoadedMapArt(client.world, item)) return false;
				// Wait a bit even aft map state is loaded, to ensure it REALLY gets loaded
				else if(stateWaitStart <= 0){stateWaitStart = System.currentTimeMillis(); return false;}
				else if(System.currentTimeMillis() - stateWaitStart < WAIT_FOR_STATE_UPDATE) return false;
				else{stateWaitStart = 0; return true;}
			},
			()->Main.LOGGER.info("MapLoadBundle: DONE!")
		);
	}

	//TODO: Consider shift-clicks instead of hotbar swaps (basically, MapMove but only for unloaded maps, and keep track of which)
	private long lastLoad;
	private final long loadCooldown = 500L;
	private int clickIndex;
	public final void loadMapArtFromContainer(){
		if(Main.clickUtils.hasOngoingClicks()){Main.LOGGER.warn("MapLoad cancelled: Already ongoing"); return;}
		//
		MinecraftClient client = MinecraftClient.getInstance();
		if(!(client.currentScreen instanceof HandledScreen hs)){Main.LOGGER.warn("MapLoad cancelled: not in HandledScreen"); return;}
		//
		final long ts = System.currentTimeMillis();
		if(ts - lastLoad < loadCooldown){Main.LOGGER.warn("MapLoad cancelled: Cooldown"); return;}
		lastLoad = ts;
		//
		if(hs instanceof InventoryScreen){loadMapArtFromBundles(); return;}
		final DefaultedList<Slot> slots = hs.getScreenHandler().slots;
		if(slots.stream().noneMatch(s -> isUnloadedMapArt(client.player.clientWorld, s.getStack()))){
			Main.LOGGER.warn("MapLoad cancelled: none to load");
			return;
		}
		int[] hbButtons = getUsableHotbarButtons(client);
		if(hbButtons.length == 0){Main.LOGGER.warn("MapLoad cancelled: in shulker, and hotbar is full of shulkers"); return;}
		//
		int[] putBackSlots = new int[hbButtons.length];
		ArrayDeque<ClickEvent> clicks = new ArrayDeque<>();
		HashSet<Integer> mapIdsToLoad = new HashSet<>();
		final int MAX_BATCH_SIZE = Math.min(hbButtons.length, Main.clickUtils.MAX_CLICKS/2);

		int hbi = 0;
		for(int i=0; i<slots.size(); ++i){
			if(!isUnloadedMapArt(client.player.clientWorld, slots.get(i).getStack())) continue;
			if(!mapIdsToLoad.add(slots.get(i).getStack().get(DataComponentTypes.MAP_ID).id())) continue;
			clicks.add(new ClickEvent(i, hbButtons[hbi], SlotActionType.SWAP));
			putBackSlots[hbi] = i;
			if(++hbi == hbButtons.length){
				hbi = 0;
				for(int j=0; j<hbButtons.length; ++j) clicks.add(new ClickEvent(putBackSlots[j], hbButtons[j], SlotActionType.SWAP));
			}
		}
		int extraPutBackIndex = clicks.size();
		for(int j=0; j<hbi; ++j) clicks.add(new ClickEvent(putBackSlots[j], hbButtons[j], SlotActionType.SWAP));

		Main.LOGGER.info("MapLoad: STARTED, clicks: "+clicks.size()+", extraPutBackIndex: "+extraPutBackIndex);
		Main.clickUtils.executeClicks(clicks,
			c->{
				if(client.player == null || client.world == null) return true;
//				if(isUnloadedMapArt(/*client.player.clientWorld*/client.world, item)) return false;
				if(clickIndex % hbButtons.length != 0 && clickIndex != extraPutBackIndex){++clickIndex; return true;}
				if(Main.clickUtils.calcAvailableClicks() < MAX_BATCH_SIZE) return false; // Wait for clicks

				if((clickIndex/hbButtons.length)%2 == 0 && clickIndex < extraPutBackIndex){++clickIndex; return true;} // Moving TO hotbar
//				ItemStack item = client.player.getInventory().getStack(c.button());
				//if(!isLoadedMapArt(client.world, item)){ // Weird issue rn with non-maps getting moved around? (bundles?)
				//if(isUnloadedMapArt(client.world, s){
				// Ugh just wait if any unloaded map in hotbar(or inv)
				if(client.player.getInventory().main.stream().anyMatch(s -> isUnloadedMapArt(client.world, s))){
//					Main.LOGGER.info("MapLoad: still waiting for map state to load from hotbar slot: "+c.button());
					return false;
				}
				else if(stateWaitStart == 0){stateWaitStart = System.currentTimeMillis(); return false;}
				else if(System.currentTimeMillis() - stateWaitStart < WAIT_FOR_STATE_UPDATE) return false;
				Main.LOGGER.info("map state load finished");
				stateWaitStart = 0;
				++clickIndex;
				return true;
			},
			()->{
				clickIndex = 0;
				Main.LOGGER.info("MapLoad: DONE!");
			}
		);
	}

//	public KeybindMapLoad(){
//		new Keybind("mapart_load", ()->loadMapArtFromContainer(), _0->true, GLFW.GLFW_KEY_E);
//	}
}