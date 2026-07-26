package net.evmodder.evmod.keybinds;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.OptionalInt;
import java.util.stream.IntStream;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.Main;
import net.evmodder.evmod.apis.ClickUtils;
import net.evmodder.evmod.apis.ClickUtils.ActionType;
import net.evmodder.evmod.apis.ClickUtils.InvAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public final class KeybindMapLoad{
	private boolean isUnloadedMapArt(Level world, ItemStack stack){
		if(stack.getItem() != Items.FILLED_MAP) return false;
		MapItemSavedData state = MapItem.getSavedData(stack, world);
		return state == null || state.colors == null || state.colors.length != 128*128;
	}
	private boolean isLoadedMapArt(Level world, ItemStack stack){
		if(stack.getItem() != Items.FILLED_MAP) return false;
		MapItemSavedData state = MapItem.getSavedData(stack, world);
		return state != null && state.colors != null && state.colors.length == 128*128;
	}

	private final void requestTextureUpdate(Minecraft client, ItemStack stack){
		MapId mapId = stack.get(DataComponents.MAP_ID);
		if(mapId == null) return;
		MapItemSavedData state = client.level.getMapData(mapId);
		if(state == null) return;
		client.getMapTextureManager().update(mapId, state);
	}

	private boolean isShulkerBox(ItemStack stack){
		return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().endsWith("shulker_box");
	}

//	private int getNextUsableHotbarButton(MinecraftClient client, int hb){
//		while(++hb < 9 && client.currentScreen instanceof ShulkerBoxScreen && isShulkerBox(client.player.getInventory().getStack(hb)));
//		return hb;
//	}

	private boolean isUsable(ItemStack stack){
		return !isShulkerBox(stack) && stack.get(DataComponents.BUNDLE_CONTENTS) == null;
	}

	private int[] getUsableHotbarButtons(Minecraft client){
		if(client.gui.screen() instanceof ShulkerBoxScreen == false) IntStream.range(0, 9).toArray();
		return IntStream.range(0, 9).filter(hb -> isUsable(client.player.getInventory().getItem(hb))).toArray();
	}

	private final long WAIT_FOR_STATE_UPDATE = 101, STATE_LOAD_TIMEOUT = 5*1000; // 50 = 1 tick
	private long stateUpdateWaitStart, stateLoadWaitStart, textureUpdateRequestClickIndex;
	private final void loadMapArtFromBundles(){
		final Minecraft client = Minecraft.getInstance();
		final InventoryScreen is = (InventoryScreen)client.gui.screen();
		final ItemStack[] slots = is.getMenu().slots.stream().map(s -> s.getItem()).toArray(ItemStack[]::new);
		final int[] slotsWithMapArtBundles = IntStream.range(9, 45).filter(i -> {
			BundleContents content = slots[i].get(DataComponents.BUNDLE_CONTENTS);
			return content != null && !content.isEmpty() && content.itemCopyStream().allMatch(s -> s.getItem() == Items.FILLED_MAP);
		}).toArray();
		if(slotsWithMapArtBundles.length == 0){
			Main.LOGGER.warn("MapLoadBundle: No mapart bundles in inventory");
			return;
		}
		final int emptyBundleSlot;
		if(Configs.Generic.USE_BUNDLE_PACKET.getBooleanValue()) emptyBundleSlot = -1;
		else{
			final OptionalInt emptyBundleSlotOpt = IntStream.range(9, 45).filter(i -> slots[i].get(DataComponents.BUNDLE_CONTENTS).isEmpty()).findAny();
			if(emptyBundleSlotOpt.isEmpty()){Main.LOGGER.warn("MapLoadBundle: Empty bundle not found"); return;}
			emptyBundleSlot = emptyBundleSlotOpt.getAsInt();
		}
//		final OptionalInt emptySlotOpt = IntStream.range(9, 45).filter(i -> slots[i].isEmpty()).min(Comparator.comparingInt(i -> Math.abs(i-emptyBundleSlot)));
		final int[] emptySlots = IntStream.range(9, 44).filter(i -> slots[i].isEmpty()).toArray();
		if(emptySlots.length == 0){Main.LOGGER.warn("MapLoadBundle: Empty slot not found"); return;}

		final boolean BUNDLES_ARE_REVERSED = Configs.Generic.BUNDLES_ARE_REVERSED.getBooleanValue();
		final ArrayDeque<InvAction> clicks = new ArrayDeque<>();
		final IdentityHashMap<InvAction, Integer> ableToSkipClicks = new IdentityHashMap<>();
		final IdentityHashMap<InvAction, Object> waitForMapLoadClicks = new IdentityHashMap<InvAction, Object>();
		for(int i : slotsWithMapArtBundles){
			BundleContents contents = slots[i].get(DataComponents.BUNDLE_CONTENTS);
			if(contents.isEmpty()) continue;
//			if(contents.stream().anyMatch(s -> s.getItem() != Items.FILLED_MAP)) continue; // Skip bundles with non-mapart contents
			final int numToLoad = (int)contents.itemCopyStream().filter(s -> isUnloadedMapArt(client.level, s)).count();
			if(numToLoad == 0) continue; // Skip bundles with already-loaded mapart
//			Main.LOGGER.info("MapLoadBundle: found bundle with "+contents.size()+" maps");

			final int depthToLoad;
			{
				int j;
				for(j=0; j<contents.size() && !isUnloadedMapArt(client.level, contents.items().get(BUNDLES_ARE_REVERSED ? j : contents.size()-1-j).create()); ++j);
				depthToLoad = contents.size()-j;
//				if(j>0) Main.LOGGER.info("MapLoadBundle: Able to skip loading for bundle in slot"+i+": "+j);
			}
			assert depthToLoad > 0;
			final InvAction takeFromBundle = new InvAction(i, 1, ActionType.CLICK);
			final InvAction stowIntoBundle = new InvAction(i, 0, ActionType.CLICK);
			final ArrayList<InvAction> subClicks = new ArrayList<>();
			if(depthToLoad <= emptySlots.length){
//				Main.LOGGER.info("MapLoadBundle: More empty slots than maps to load, able to use simple method");
				subClicks.add(new InvAction(i, 0, ActionType.CLICK)); // Pick up bundle
				int j;
				for(j=0; j<depthToLoad; ++j){
//					subClicks.add(takeFromBundle);
//					subClicks.add(new InvAction(emptySlots[emptySlots.length-1-j], 0, ActionType.CLICK)); // Place map in empty slot
					subClicks.add(new InvAction(emptySlots[emptySlots.length-1-j], 1, ActionType.CLICK)); // Place map in empty slot
				}
				for(--j; j>=0; --j){
					subClicks.add(new InvAction(emptySlots[emptySlots.length-1-j], 0, ActionType.CLICK)); // Take map from empty slot
					if(j+1 == depthToLoad) waitForMapLoadClicks.put(subClicks.getLast(), null); // Wait for map states to load
//					subClicks.add(stowIntoBundle); // Place map back into bundle
				}
				subClicks.add(new InvAction(i, 0, ActionType.CLICK)); // Put back bundle
			}
			else if(emptyBundleSlot == -1){
				int j=0;
				final int magic = BUNDLES_ARE_REVERSED ? depthToLoad-1 : contents.size()-depthToLoad;
				while(j<depthToLoad){
//					Main.LOGGER.info("MapLoadBundle: load iteration, remaining to load for bundle in slot"+i+": "+(depthToLoad-j));
					int k;
					for(k=0; k<emptySlots.length && j<depthToLoad; ++k, ++j){
//						final int skipBundleSlots = contents.size()-depthToLoad;
//						final int bundleSlotJ = BUNDLES_ARE_REVERSED ? contents.size()-k-1-skipBundleSlots : skipBundleSlots;
//						subClicks.add(new InvAction(i, bundleSlotJ, ActionType.BUNDLE_SELECT)); // Select map in source bundle
						subClicks.add(new InvAction(i, BUNDLES_ARE_REVERSED ? magic-k : magic, ActionType.BUNDLE_SELECT)); // Select map in source bundle
						subClicks.add(takeFromBundle); // Take first map from bundle
						subClicks.add(new InvAction(emptySlots[k], 0, ActionType.CLICK)); // Place map in next empty slot
					}
					for(int h=0; h<k; ++h){
						subClicks.add(new InvAction(emptySlots[h], 0, ActionType.CLICK)); // Take map from empty slot
						if(h==0) waitForMapLoadClicks.put(subClicks.getLast(), null); // Wait for map states to load
						subClicks.add(stowIntoBundle); // Place map back into source bundle
					}
				}
			}
			else{
				final InvAction stowIntoTempBundle = new InvAction(emptyBundleSlot, 0, ActionType.CLICK);
				final InvAction takeFromTempBundle = new InvAction(emptyBundleSlot, 1, ActionType.CLICK);
				int j=0;
				while(j<depthToLoad){
					int k;
					for(k=0; k<emptySlots.length && j<depthToLoad; ++k, ++j){
						subClicks.add(takeFromBundle); // Take last map from bundle
						subClicks.add(new InvAction(emptySlots[emptySlots.length-1-k], 0, ActionType.CLICK)); // Place map in next empty slot
					}
					for(int h=0; h<k; ++h){
						subClicks.add(new InvAction(emptySlots[emptySlots.length-1-h], 0, ActionType.CLICK)); // Take map from empty slot
						if(h==0) waitForMapLoadClicks.put(subClicks.getLast(), null); // Wait for map states to load
						subClicks.add(stowIntoTempBundle); // Place map back into temp bundle
					}
				}
				for(j=0; j<depthToLoad; ++j){
					subClicks.add(takeFromTempBundle);
					subClicks.add(stowIntoBundle);
				}
			}
			subClicks.set(0, subClicks.get(0).clone());
			ableToSkipClicks.put(subClicks.get(0), subClicks.size());
			clicks.addAll(subClicks);
		}
		if(clicks.isEmpty()){
			Main.LOGGER.warn("MapLoadBundle: No bundles containing unloaded mapart in inventory");
			return; // No bundles with maps
		}

		Main.LOGGER.info("MapLoadBundle: STARTED");
		ClickUtils.executeClicks(c->{
			if(client.player == null || client.level == null) return true;
			final Integer skipIfLoaded = ableToSkipClicks.get(c);
//				Main.LOGGER.info("click for slot: "+c.slotId()+", clicksLeft: "+clicks.size());
			if(skipIfLoaded != null){
				//Main.LOGGER.info("MapLoadBundle: potentially skippable");
				final BundleContents contents = client.player.containerMenu.slots.get(c.slot()).getItem().get(DataComponents.BUNDLE_CONTENTS);
				if(contents != null && contents.itemCopyStream().allMatch(s -> isLoadedMapArt(client.level, s))){
//						Main.LOGGER.info("MapLoadBundle: skippable! whoop whoop: "+(skipIfLoaded));
					for(int i=0; i<skipIfLoaded; ++i) clicks.remove();
					return false;
				}
			}
			if(!waitForMapLoadClicks.containsKey(c)) return true;
			if(Arrays.stream(emptySlots).anyMatch(i -> isUnloadedMapArt(client.level, client.player.getInventory().getNonEquipmentItems().get(i-9)))){
//				Main.LOGGER.info("MapLoadBundle: still waiting for map states to load");
				if(stateLoadWaitStart == 0) stateLoadWaitStart = System.currentTimeMillis();
				if(System.currentTimeMillis() - stateLoadWaitStart < STATE_LOAD_TIMEOUT) return false;
				stateUpdateWaitStart = stateLoadWaitStart = 0;
				Main.LOGGER.warn("MapLoadBundle: Timed out while waiting for map state(s) to load!");
				return true;
			}
			else if(stateUpdateWaitStart == 0){
				Arrays.stream(emptySlots).mapToObj(i -> client.player.getInventory().getNonEquipmentItems().get(i-9)).forEach(s -> requestTextureUpdate(client, s));
				stateUpdateWaitStart = System.currentTimeMillis();
				return false;
			}
			else if(System.currentTimeMillis() - stateUpdateWaitStart < WAIT_FOR_STATE_UPDATE) return false;
//			Main.LOGGER.info("MapLoadBundle: map state(s) loaded and updated");
			stateUpdateWaitStart = stateLoadWaitStart = 0;
			return true;
		},
			()->Main.LOGGER.info("MapLoadBundle: DONE!"),
			clicks
		);
	}

	private long lastLoad;
	private final long loadCooldown = 500L;
	private int clickIndex;
	public final void loadMapArtFromContainer(){
		if(ClickUtils.hasOngoingClicks()){Main.LOGGER.warn("MapLoad cancelled: Already ongoing"); return;}

		Minecraft client = Minecraft.getInstance();
		if(!(client.gui.screen() instanceof AbstractContainerScreen hs)){Main.LOGGER.warn("MapLoad cancelled: not in HandledScreen"); return;}

		final long ts = System.currentTimeMillis();
		if(ts - lastLoad < loadCooldown){Main.LOGGER.warn("MapLoad cancelled: Cooldown"); return;}
		lastLoad = ts;

		if(hs instanceof InventoryScreen){loadMapArtFromBundles(); return;}
		final NonNullList<Slot> slots = hs.getMenu().slots;
		if(slots.stream().noneMatch(s -> isUnloadedMapArt(client.player.level(), s.getItem()))){
			Main.LOGGER.warn("MapLoad cancelled: none to load");
			return;
		}
		int[] hbButtons = getUsableHotbarButtons(client);
		if(hbButtons.length == 0){Main.LOGGER.warn("MapLoad cancelled: in shulker, and hotbar is full of shulkers"); return;}

		int[] putBackSlots = new int[hbButtons.length];
		ArrayDeque<InvAction> clicks = new ArrayDeque<>();
		HashSet<Integer> mapIdsToLoad = new HashSet<>();
		final int CLICK_BATCH_SIZE = Math.min(hbButtons.length, ClickUtils.getMaxClicks()/2);

		int hbi = 0;
		for(int i=0; i<slots.size(); ++i){
			if(!isUnloadedMapArt(client.player.level(), slots.get(i).getItem())) continue;
			if(!mapIdsToLoad.add(slots.get(i).getItem().get(DataComponents.MAP_ID).id())) continue;
			clicks.add(new InvAction(i, hbButtons[hbi], ActionType.HOTBAR_SWAP));
			putBackSlots[hbi] = i;
			if(++hbi == hbButtons.length){
				hbi = 0;
				for(int j=0; j<hbButtons.length; ++j) clicks.add(new InvAction(putBackSlots[j], hbButtons[j], ActionType.HOTBAR_SWAP));
			}
		}
		int extraPutBackIndex = clicks.size();
		for(int j=0; j<hbi; ++j) clicks.add(new InvAction(putBackSlots[j], hbButtons[j], ActionType.HOTBAR_SWAP));

		final int numFullBatches = clicks.size()/(hbButtons.length*2);
		Main.LOGGER.info("MapLoad: STARTED, clicks: "+clicks.size()+" == ("+hbButtons.length+"x"+numFullBatches+" + "+hbi+")x2");
		clickIndex = 0;
		ClickUtils.executeClicks((InvAction _)->{
			if(client.player == null || client.level == null) return true;

			// Not the start of a putback click sequence
			final int inBatch = clickIndex/hbButtons.length;
			if(((inBatch&1) == 0 || inBatch*hbButtons.length != clickIndex) && clickIndex != extraPutBackIndex){
				++clickIndex;
				return true;
			}
			if(textureUpdateRequestClickIndex != clickIndex){
				textureUpdateRequestClickIndex = clickIndex;
				Arrays.stream(hbButtons).mapToObj(i -> client.player.getInventory().getNonEquipmentItems().get(27+i)).forEach(s -> requestTextureUpdate(client, s));
			}

			if(ClickUtils.calcAvailableClicks() < CLICK_BATCH_SIZE) return false; // Wait for clicks

//			if(isUnloadedMapArt(client.world, client.player.getInventory().main.get(27+hbButtons[clickIndex % hbButtons.length]))) return false;
			if(Arrays.stream(hbButtons).anyMatch(i -> isUnloadedMapArt(client.level, client.player.getInventory().getNonEquipmentItems().get(27+i)))){
//				Main.LOGGER.info("MapLoad: still waiting for map state to load from hotbar slot: "+c.button());
				if(stateLoadWaitStart == 0) stateLoadWaitStart = System.currentTimeMillis();
				if(System.currentTimeMillis() - stateLoadWaitStart < STATE_LOAD_TIMEOUT) return false;
				stateUpdateWaitStart = stateLoadWaitStart = 0;
				Main.LOGGER.warn("MapLoad: Timed out while waiting for map state to load!");
				++clickIndex;
				return true;
			}
			else if(stateUpdateWaitStart == 0){
				Arrays.stream(hbButtons).mapToObj(i -> client.player.getInventory().getNonEquipmentItems().get(27+i)).forEach(s -> requestTextureUpdate(client, s));
				stateUpdateWaitStart = System.currentTimeMillis();
				return false;
			}
			else if(System.currentTimeMillis() - stateUpdateWaitStart < WAIT_FOR_STATE_UPDATE) return false;
			Main.LOGGER.info("MapLoad: map state loaded and updated, clickIndex="+clickIndex);
			stateUpdateWaitStart = stateLoadWaitStart = 0;
			++clickIndex;
			return true;
		},
			()->{
				clickIndex = 0;
				Main.LOGGER.info("MapLoad: DONE!");
			},
			clicks
		);
	}
}