package net.evmodder.evmod.keybinds;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.stream.IntStream;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.math.Fraction;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.Main;
import net.evmodder.evmod.apis.ClickUtils;
import net.evmodder.evmod.apis.ClickUtils.ActionType;
import net.evmodder.evmod.apis.ClickUtils.InvAction;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;

public final class KeybindMapMoveBundle{
	//final int WITHDRAW_MAX = 27;
	//enum BundleSelectionMode{FIRST, LAST, MOST_FULL_butNOT_FULL, MOST_EMPTY_butNOT_EMPTY};
	enum BundleSelectionPriority {
		FULLEST, FULLEST_NOT_FULL,
		EMPTIEST, EMPTIEST_NOT_EMPTY
	}
	// Can be: EMPTIEST_NOT_EMPTY or FULLEST
	private final BundleSelectionPriority BUNDLE_EXTRACT = BundleSelectionPriority.EMPTIEST_NOT_EMPTY; // TODO: config settings for these
	// Can be: FULLEST_NOT_FULL or EMPTIEST
	private final BundleSelectionPriority BUNDLE_STOW = BundleSelectionPriority.FULLEST_NOT_FULL;

	private final int getNumStored(Fraction fraction){
		assert 64 % fraction.getDenominator() == 0;
		return (64/fraction.getDenominator())*fraction.getNumerator();
	}
	private final boolean isMapItem(ItemStack stack){return stack.getItem() == Items.FILLED_MAP;}

	private long lastBundleOp = 0;
	private final long bundleOpCooldown = 250l;
	public final void moveMapArtToFromBundle(final boolean reverse){
		if(ClickUtils.hasOngoingClicks()){Main.LOGGER.warn("MapBundleOp: Already ongoing"); return;}
		//
		MinecraftClient client = MinecraftClient.getInstance();
		if(!(client.currentScreen instanceof HandledScreen hs)) return;
		//
		final long ts = System.currentTimeMillis();
		if(ts - lastBundleOp < bundleOpCooldown){Main.LOGGER.warn("MapBundleOp: in cooldown"); return;}
		lastBundleOp = ts;
		//
		final ItemStack[] slots = hs.getScreenHandler().slots.stream().map(Slot::getStack).toArray(ItemStack[]::new);

		final int SLOT_START = hs instanceof InventoryScreen ? 9 : hs instanceof CraftingScreen ? 10 : 0;
		final int SLOT_END =
					// Ignore player inventory slots
				hs instanceof ShulkerBoxScreen ? 27 :
				hs.getScreenHandler() instanceof GenericContainerScreenHandler gcsh ? gcsh.getRows()*9 :
					// Use all available slots
				hs instanceof InventoryScreen ? slots.length :
				hs instanceof CraftingScreen ? slots.length :
					slots.length; // unreachable?
		final int BUNDLE_SLOT_START = SLOT_END < slots.length ? SLOT_END : SLOT_START;
		assert SLOT_END != 0;
		final int[] slotsWithMapArt = IntStream.range(SLOT_START, SLOT_END)
				.filter(i -> slots[i].getItem() == Items.FILLED_MAP
					&& !KeybindMapMove.isFillerMap(slots, slots[i], client.world))
				.toArray();
		final int[] slotsWithBundles = IntStream.range(BUNDLE_SLOT_START, slots.length).filter(i -> {
			BundleContentsComponent contents = slots[i].get(DataComponentTypes.BUNDLE_CONTENTS);
			return contents != null && contents.stream().allMatch(this::isMapItem);
		}).toArray();
		final BundleContentsComponent[] bundles = Arrays.stream(slotsWithBundles)
				.mapToObj(i -> slots[i].get(DataComponentTypes.BUNDLE_CONTENTS)).toArray(BundleContentsComponent[]::new);

		final ItemStack cursorStack = hs.getScreenHandler().getCursorStack();
		final BundleContentsComponent cursorBundleContents = cursorStack.get(DataComponentTypes.BUNDLE_CONTENTS);
		final boolean cursorIsUsableBundle = cursorBundleContents != null && cursorBundleContents.stream().allMatch(this::isMapItem);
		final boolean cursorBundleHasMaps = cursorIsUsableBundle && !cursorBundleContents.isEmpty();
		final boolean anyBundleWithMaps = cursorBundleHasMaps || !Arrays.stream(bundles).allMatch(BundleContentsComponent::isEmpty);

		if(slotsWithMapArt.length == 0 && !anyBundleWithMaps){
//			Main.LOGGER.info("MapBundleOp: No maps found to extract/stow");
			return;
		}

		final boolean doStow = slotsWithMapArt.length > 0 && (Configs.Generic.KEYBIND_BUNDLE_PREFER_STOW.getBooleanValue() || !anyBundleWithMaps);

		long numMapsWithCount2 = -1;
		final boolean pickup1of2 = doStow
				&& Arrays.stream(slotsWithMapArt).allMatch(i -> slots[i].getCount() <= 2)
				&& (numMapsWithCount2=Arrays.stream(slotsWithMapArt).filter(i -> slots[i].getCount() == 2).count()) > 0
//				&& (!Screen.hasShiftDown() || Arrays.stream(slotsWithMapArt).noneMatch(i -> slots[i].getCount() == 1))
				;
		final long numToStow = !doStow ? 0 : pickup1of2 ? numMapsWithCount2 : slotsWithMapArt.length;

//		Main.LOGGER.info("MapBundleOp: begin bundle search");
		final ArrayDeque<InvAction> clicks = new ArrayDeque<>();
		final int bundleSlot;
		final int stored;
		final boolean pickedUpBundle;
		if(cursorIsUsableBundle){
			if(pickup1of2){Main.LOGGER.warn("MapBundleOp: Cannot use cursor-bundle when splitting stacked maps"); return;}
			bundleSlot = -1;
			pickedUpBundle = true;
			stored = getNumStored(cursorStack.get(DataComponentTypes.BUNDLE_CONTENTS).getOccupancy());
		}
		else if(!cursorStack.isEmpty()){Main.LOGGER.warn("MapBundleOp: Non-bundle item on cursor"); return;}
		else{
			final BundleSelectionPriority pickBy = doStow ? BUNDLE_STOW : BUNDLE_EXTRACT;
			int bestBundleSlot = -1;
			int bestStored = switch(pickBy){
				case FULLEST, FULLEST_NOT_FULL -> -1;
				case EMPTIEST, EMPTIEST_NOT_EMPTY -> Integer.MAX_VALUE;
			};
			for(int i=0; i<slots.length; ++i){ // Hmm, allow using bundles from outside the container screen
				final BundleContentsComponent contents = slots[i].get(DataComponentTypes.BUNDLE_CONTENTS);
				if(contents == null) continue;
				final Fraction occ = contents.getOccupancy();
//				if(doStow && occ.intValue() == 1) continue; // Skip full bundles
//				if(!doStow && occ.getNumerator() == 0) continue; // Skip empty bundles
				if(doStow ? occ.intValue() == 1 : occ.getNumerator() == 0) continue; // Same logic as above
				if(!contents.stream().allMatch(this::isMapItem)) continue; // Skip bundles with non-mapart contents
				final int storedI = getNumStored(occ);
				final boolean updatePick = switch(pickBy){
					case FULLEST -> storedI > bestStored;
					case FULLEST_NOT_FULL -> storedI > bestStored && occ.intValue() != 1;
					case EMPTIEST -> storedI < bestStored;
					case EMPTIEST_NOT_EMPTY -> storedI < bestStored && occ.getNumerator() != 0;
				};
				if(updatePick){
					bestStored = storedI;
					bestBundleSlot = i;
				}
			}
			if(bestBundleSlot == -1){
				Main.LOGGER.warn("MapBundleOp: No usable bundle found");
				return;
			}
			bundleSlot = bestBundleSlot;
			stored = bestStored;
//			Main.LOGGER.warn("MapBundleOp: using bundle in slot="+bundleSlot
//					+", doStow="+doStow+", stored="+stored+", numToStow="+numToStow+", pickup1of2="+pickup1of2
//			);
			pickedUpBundle = !pickup1of2 && (doStow ? numToStow : stored) > 2;
			if(pickedUpBundle){
//				Main.LOGGER.warn("MapBundleOp: picking up bundle ");
				clicks.add(new InvAction(bundleSlot, 0, ActionType.CLICK));
			}
		}
		Main.LOGGER.info("MapBundleOp: contents="+stored+", pickedUp="+pickedUpBundle);

		if(doStow){
			final boolean STOW_NON_SINGLE_MAPS = Configs.Generic.KEYBIND_BUNDLE_STOW_NON_SINGLE_MAPS.getBooleanValue();
			final int space = 64 - stored;
//			Main.LOGGER.warn("MapBundleOp: space in bundle: "+space);
			int suckedUp = 0;
			//for(int i=SLOT_START; i<SLOT_END && deposited < space; ++i){
			if(reverse) ArrayUtils.reverse(slotsWithMapArt);
			for(int i : slotsWithMapArt){
				if(slots[i].getItem() != Items.FILLED_MAP) continue;
				if(pickup1of2 ? slots[i].getCount() != 2 : (STOW_NON_SINGLE_MAPS ? slots[i].getCount() != 1 : false)) continue;
				if(pickedUpBundle) clicks.add(new InvAction(i, 0, ActionType.CLICK)); // Suck up item with bundle on cursor
				else{
					clicks.add(new InvAction(i, pickup1of2 ? 1 : 0, ActionType.CLICK)); // Pickup all/half
					clicks.add(new InvAction(bundleSlot, 0, ActionType.CLICK)); // Put into bundle
				}
				if(++suckedUp == space) break;
			}
			Main.LOGGER.info("MapBundleOp: storing "+suckedUp+" maps in bundle");
		}
		else{
			final int MOVE_LIMIT = Configs.Generic.KEYBIND_BUNDLE_REMOVE_MAX.getIntegerValue();
			final int withdrawable = Math.min(MOVE_LIMIT, stored);
			int withdrawn = 0;
			if(reverse){
				for(int i=SLOT_START; i<SLOT_END && withdrawn < withdrawable; ++i){
					if(!slots[i].isEmpty()) continue;
					if(pickedUpBundle) clicks.add(new InvAction(i, 1, ActionType.CLICK)); // Place from bundle
					else{
						clicks.add(new InvAction(bundleSlot, 1, ActionType.CLICK)); // Take top from bundle
						clicks.add(new InvAction(i, 0, ActionType.CLICK)); // Place
					}
					++withdrawn;
				}
			}
			else{
				int emptySlots = (int)IntStream.range(SLOT_START, SLOT_END).filter(i -> slots[i].isEmpty()).count();
//				Main.LOGGER.info("MapBundleOp: emptySlots: "+emptySlots+", stored: "+stored);
				int i=SLOT_END-1;
				for(; emptySlots > withdrawable; --i) if(slots[i].isEmpty()) --emptySlots;
				for(; i>=SLOT_START && withdrawn < withdrawable; --i){
					if(!slots[i].isEmpty()) continue;
					if(pickedUpBundle) clicks.add(new InvAction(i, 1, ActionType.CLICK)); // Place from bundle
					else{
						clicks.add(new InvAction(bundleSlot, 1, ActionType.CLICK)); // Take top from bundle
						clicks.add(new InvAction(i, 0, ActionType.CLICK)); // Place
					}
					++withdrawn;
				}
			}
			Main.LOGGER.info("MapBundleOp: withdrawing "+withdrawn+" maps from bundle");
		}
		if(pickedUpBundle && bundleSlot != -1){
			Main.LOGGER.info("MapBundleOp: Placed bundle back in starting slot");
			clicks.add(new InvAction(bundleSlot, 0, ActionType.CLICK)); // Put back bundle in src slot
		}

		ClickUtils.executeClicks(_0->true, ()->Main.LOGGER.info("MapBundleOp: DONE!"), clicks);
	}

	/*public KeybindMapMoveBundle(boolean regular, boolean reverse){
		Function<Screen, Boolean> allowInScreen =
				//InventoryScreen.class::isInstance
				s->s instanceof InventoryScreen || s instanceof GenericContainerScreen || s instanceof ShulkerBoxScreen || s instanceof CraftingScreen;

		if(regular) new Keybind("mapart_bundle", ()->moveMapArtToFromBundle(false), allowInScreen, GLFW.GLFW_KEY_D);
		if(reverse) new Keybind("mapart_bundle_reverse", ()->moveMapArtToFromBundle(true), allowInScreen, regular ? -1 : GLFW.GLFW_KEY_D);
	}*/
}