package net.evmodder.evmod.keybinds;

import static net.evmodder.evmod.compat.MinecraftCompat.screen;
import static net.evmodder.evmod.compat.MinecraftCompat.bundleWeight;

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
import net.evmodder.evmod.config.OptionBundleSelectPrio;
import net.evmodder.evmod.config.OptionBundleSelectPrio.BundleSelectPrio;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;

public final class KeybindMapMoveBundle{
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
		Minecraft client = Minecraft.getInstance();
		if(!(screen(client) instanceof AbstractContainerScreen hs)) return;
		//
		final long ts = System.currentTimeMillis();
		if(ts - lastBundleOp < bundleOpCooldown){Main.LOGGER.warn("MapBundleOp: in cooldown"); return;}
		lastBundleOp = ts;
		//
		final ItemStack[] slots = hs.getMenu().slots.stream().map(Slot::getItem).toArray(ItemStack[]::new);

		final int SLOT_START = hs instanceof InventoryScreen ? 9 : hs instanceof CraftingScreen ? 10 : 0;
		final int SLOT_END =
					// Ignore player inventory slots
				hs instanceof ShulkerBoxScreen ? 27 :
				hs.getMenu() instanceof ChestMenu gcsh ? gcsh.getRowCount()*9 :
					// Use all available slots
				hs instanceof InventoryScreen ? slots.length :
				hs instanceof CraftingScreen ? slots.length :
					slots.length; // unreachable?
		final int BUNDLE_SLOT_START = SLOT_END < slots.length ? SLOT_END : SLOT_START;
		assert SLOT_END != 0;
		final int[] slotsWithMapArt = IntStream.range(SLOT_START, SLOT_END)
				.filter(i -> slots[i].getItem() == Items.FILLED_MAP
					&& !KeybindMapMove.isFillerMap(slots, slots[i], client.level))
				.toArray();
		final int[] slotsWithBundles = IntStream.range(BUNDLE_SLOT_START, slots.length).filter(i -> {
			BundleContents contents = slots[i].get(DataComponents.BUNDLE_CONTENTS);
			return contents != null && contents.itemCopyStream().allMatch(this::isMapItem);
		}).toArray();
		final BundleContents[] bundles = Arrays.stream(slotsWithBundles)
				.mapToObj(i -> slots[i].get(DataComponents.BUNDLE_CONTENTS)).toArray(BundleContents[]::new);

		final ItemStack cursorStack = hs.getMenu().getCarried();
		final BundleContents cursorBundleContents = cursorStack.get(DataComponents.BUNDLE_CONTENTS);
		final boolean cursorIsUsableBundle = cursorBundleContents != null && cursorBundleContents.itemCopyStream().allMatch(this::isMapItem);
		final boolean cursorBundleHasMaps = cursorIsUsableBundle && !cursorBundleContents.isEmpty();
		final boolean anyBundleWithMaps = cursorBundleHasMaps || !Arrays.stream(bundles).allMatch(BundleContents::isEmpty);

		if(slotsWithMapArt.length == 0 && !anyBundleWithMaps){
//			Main.LOGGER.info("MapBundleOp: No maps found to extract/stow");
			return;
		}

		final boolean cursorBundleHasSpace = cursorIsUsableBundle && bundleWeight(cursorBundleContents).intValue() != 1;
		final boolean anyBundleWithSpace = cursorBundleHasSpace || Arrays.stream(bundles).anyMatch(b -> bundleWeight(b).intValue() != 1);
		final boolean doStow = slotsWithMapArt.length > 0 && anyBundleWithSpace && (Configs.Hotkeys.MAP_MOVE_BUNDLE_PREFER_STOW.getBooleanValue() || !anyBundleWithMaps);

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
			stored = getNumStored(bundleWeight(cursorStack.get(DataComponents.BUNDLE_CONTENTS)));
		}
		else if(!cursorStack.isEmpty()){Main.LOGGER.warn("MapBundleOp: Non-bundle item on cursor"); return;}
		else{
			final BundleSelectPrio pickBy = ((OptionBundleSelectPrio)
					(doStow ? Configs.Hotkeys.MAP_MOVE_BUNDLE_SELECT_PRIORITY_STOW : Configs.Hotkeys.MAP_MOVE_BUNDLE_SELECT_PRIORITY_TAKE)
					.getOptionListValue()).getSelectPrio();
			int bestBundleSlot = -1;
			int bestStored = switch(pickBy){
				case FIRST, LAST -> -1; // N/A, don't care
				case FULLEST, FULLEST_NOT_FULL -> -1;
				case EMPTIEST, EMPTIEST_NOT_EMPTY -> Integer.MAX_VALUE;
			};
			for(int i=0; i<slots.length; ++i){ // Hmm, allow using bundles from outside the container screen
				final BundleContents contents = slots[i].get(DataComponents.BUNDLE_CONTENTS);
				if(contents == null) continue;
				final Fraction occ = bundleWeight(contents);
//				if(doStow && occ.intValue() == 1) continue; // Skip full bundles
//				if(!doStow && occ.getNumerator() == 0) continue; // Skip empty bundles
				if(doStow ? occ.intValue() == 1 : occ.getNumerator() == 0) continue; // Same logic as above
				if(!contents.itemCopyStream().allMatch(this::isMapItem)) continue; // Skip bundles with non-mapart contents
				final int storedI = getNumStored(occ);
				if(switch(pickBy){
					case FIRST, LAST -> true;
					case FULLEST -> storedI > bestStored;
					case FULLEST_NOT_FULL -> storedI > bestStored && occ.intValue() != 1;
					case EMPTIEST -> storedI < bestStored;
					case EMPTIEST_NOT_EMPTY -> storedI < bestStored && occ.getNumerator() != 0;
				}){
					bestStored = storedI;
					bestBundleSlot = i;
					if(pickBy == BundleSelectPrio.FIRST) break;
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
			final boolean STOW_ONLY_SINGLE_MAPS = !Configs.Hotkeys.MAP_MOVE_BUNDLE_STOW_NON_SINGLE_MAPS.getBooleanValue();
			final int space = 64 - stored;
//			Main.LOGGER.warn("MapBundleOp: space in bundle: "+space);
			int suckedUp = 0;
			//for(int i=SLOT_START; i<SLOT_END && deposited < space; ++i){
			if(reverse) ArrayUtils.reverse(slotsWithMapArt);
			for(int i : slotsWithMapArt){
				if(slots[i].getItem() != Items.FILLED_MAP) continue;
				if(pickup1of2 ? slots[i].getCount() != 2 : (STOW_ONLY_SINGLE_MAPS ? slots[i].getCount() != 1 : false)) continue;
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
			final int MOVE_LIMIT = Configs.Hotkeys.MAP_MOVE_BUNDLE_TAKE_MAX.getIntegerValue();
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
				final int SKIP_LAST_SLOT = hs instanceof InventoryScreen ? 1 : 0; // Don't extract mapart into empty offhand, ick!
				int emptySlots = (int)IntStream.range(SLOT_START, SLOT_END-SKIP_LAST_SLOT).filter(i -> slots[i].isEmpty()).count();
//				Main.LOGGER.info("MapBundleOp: emptySlots: "+emptySlots+", stored: "+stored);
				int i=SLOT_END-1-SKIP_LAST_SLOT;
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

		ClickUtils.executeClicks(/*canProceed=*/_->true, ()->Main.LOGGER.info("MapBundleOp: DONE!"), clicks);
	}

	/*public KeybindMapMoveBundle(boolean regular, boolean reverse){
		Function<Screen, Boolean> allowInScreen =
				//InventoryScreen.class::isInstance
				s->s instanceof InventoryScreen || s instanceof GenericContainerScreen || s instanceof ShulkerBoxScreen || s instanceof CraftingScreen;

		if(regular) new Keybind("mapart_bundle", ()->moveMapArtToFromBundle(false), allowInScreen, GLFW.GLFW_KEY_D);
		if(reverse) new Keybind("mapart_bundle_reverse", ()->moveMapArtToFromBundle(true), allowInScreen, regular ? -1 : GLFW.GLFW_KEY_D);
	}*/
}