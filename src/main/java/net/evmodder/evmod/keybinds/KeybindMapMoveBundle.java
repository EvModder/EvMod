package net.evmodder.evmod.keybinds;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.stream.IntStream;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.math.Fraction;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.Main;
import net.evmodder.evmod.apis.ClickUtils.ClickEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
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
import net.minecraft.screen.slot.SlotActionType;

public final class KeybindMapMoveBundle{
	//final int WITHDRAW_MAX = 27;
	//enum BundleSelectionMode{FIRST, LAST, MOST_FULL_butNOT_FULL, MOST_EMPTY_butNOT_EMPTY};

	private final boolean isBundle(ItemStack stack){
		return stack.get(DataComponentTypes.BUNDLE_CONTENTS) != null;
//		return Registries.ITEM.getId(stack.getItem()).getPath().endsWith("bundle");
	}
	private final int getNumStored(Fraction fraction){
		assert 64 % fraction.getDenominator() == 0;
		return (64/fraction.getDenominator())*fraction.getNumerator();
	}

	private long lastBundleOp = 0;
	private final long bundleOpCooldown = 250l;
	public final void moveMapArtToFromBundle(final boolean reverse){
		if(Main.clickUtils.hasOngoingClicks()){Main.LOGGER.warn("MapBundleOp: Already ongoing"); return;}
		//
		MinecraftClient client = MinecraftClient.getInstance();
		if(!(client.currentScreen instanceof HandledScreen hs)) return;
		//
		final long ts = System.currentTimeMillis();
		if(ts - lastBundleOp < bundleOpCooldown){Main.LOGGER.warn("MapBundleOp: in cooldown"); return;}
		lastBundleOp = ts;
		//
		final int SLOT_START = hs instanceof InventoryScreen ? 9 : hs instanceof CraftingScreen ? 10 : 0;
		final int SLOT_END =
				hs instanceof InventoryScreen ? 45 :
				hs.getScreenHandler() instanceof GenericContainerScreenHandler gcsh ? gcsh.getRows()*9 :
				hs instanceof CraftingScreen ? 46 :
				hs instanceof ShulkerBoxScreen ? 27 : 0/*unreachable?*/;
		assert SLOT_END != 0;
		final ItemStack[] slots = hs.getScreenHandler().slots.stream().map(Slot::getStack).toArray(ItemStack[]::new);
		final int[] slotsWithMapArt = IntStream.range(SLOT_START, SLOT_END)
				.filter(i -> slots[i].getItem() == Items.FILLED_MAP && !KeybindMapMove.isFillerMap(slots, slots[i], client.world))
				.toArray();
		final boolean pickupHalf = slotsWithMapArt.length > 0
				&& Arrays.stream(slotsWithMapArt).anyMatch(i -> slots[i].getCount() == 2)
				&& Arrays.stream(slotsWithMapArt).allMatch(i -> slots[i].getCount() <= 2)
				&& (!Screen.hasShiftDown() || Arrays.stream(slotsWithMapArt).noneMatch(i -> slots[i].getCount() == 1));
		final boolean anyArtToPickup = Arrays.stream(slotsWithMapArt).anyMatch(i -> slots[i].getCount() == (pickupHalf ? 2 : 1));

//		Main.LOGGER.info("MapBundleOp: begin bundle search");
		ArrayDeque<ClickEvent> clicks = new ArrayDeque<>();
		final ItemStack cursorStack = hs.getScreenHandler().getCursorStack();
		int bundleSlot = -1, mostEmpty = Integer.MAX_VALUE, mostFull = 0;
		final Fraction occupancy;
		if(isBundle(cursorStack)){
			if(pickupHalf){
				Main.LOGGER.warn("MapBundleOp: Cannot use cursor-bundle when splitting stacked maps");
				return;
			}
			occupancy = cursorStack.get(DataComponentTypes.BUNDLE_CONTENTS).getOccupancy();
		}
		else if(!cursorStack.isEmpty()){
			Main.LOGGER.warn("MapBundleOp: Non-bundle item on cursor");
			return;
			//clicks.add(new ClickEvent(ScreenHandler.EMPTY_SPACE_SLOT_INDEX, 0, SlotActionType.PICKUP));//Toss cursor stack
		}
		else{
			for(int i=0; i<slots.length; ++i){ // Hmm, allow using bundles from outside the container screen
				if(!isBundle(slots[i])) continue;
				BundleContentsComponent contents = slots[i].get(DataComponentTypes.BUNDLE_CONTENTS);
				Fraction occ = contents.getOccupancy();
				if(anyArtToPickup && occ.intValue() == 1) continue; // Skip full bundles
				if(!anyArtToPickup && occ.getNumerator() == 0) continue; // Skip empty bundles
				if(contents.stream().anyMatch(s -> s.getItem() != Items.FILLED_MAP)) continue; // Skip bundles with non-mapart contents
				int stored = getNumStored(occ);
				//Hacky prefer not fully empty bundles but otherwise prefer more empty
				if(anyArtToPickup){if((stored < mostEmpty || mostEmpty == 0) && (stored != 0 || bundleSlot == -1)){mostEmpty = stored; bundleSlot = i;}}
				else if(stored > mostFull){mostFull = stored; bundleSlot = i;}
				//if(mode == FIRST) break;
			}
			if(bundleSlot == -1){
				Main.LOGGER.warn("MapBundleOp: No usable bundle found");
				return;
			}
			if(!pickupHalf){
				Main.LOGGER.warn("MapBundleOp: using bundle in slot: "+bundleSlot);
				clicks.add(new ClickEvent(bundleSlot, 0, SlotActionType.PICKUP));
			}
			occupancy = slots[bundleSlot].get(DataComponentTypes.BUNDLE_CONTENTS).getOccupancy();
		}
//		Main.LOGGER.info("MapBundleOp: contents: "+occupancy.getNumerator()+"/"+occupancy.getDenominator());

		if(anyArtToPickup){
			final int space = 64 - getNumStored(occupancy);
//			Main.LOGGER.warn("MapBundleOp: space in bundle: "+space);
			int suckedUp = 0;
			//for(int i=SLOT_START; i<SLOT_END && deposited < space; ++i){
			if(reverse) ArrayUtils.reverse(slotsWithMapArt);
			for(int i : slotsWithMapArt){
				if(slots[i].getItem() != Items.FILLED_MAP) continue;
				if(slots[i].getCount() != (pickupHalf ? 2 : 1)) continue;
				if(pickupHalf){
					clicks.add(new ClickEvent(i, 1, SlotActionType.PICKUP)); // Pickup half
					clicks.add(new ClickEvent(bundleSlot, 0, SlotActionType.PICKUP)); // Put into bundle
				}
				else clicks.add(new ClickEvent(i, 0, SlotActionType.PICKUP)); // Suck up item with bundle on cursor
				if(++suckedUp == space) break;
			}
			Main.LOGGER.info("MapBundleOp: stored "+suckedUp+" maps in bundle");
		}
		else{
			final int MOVE_LIMIT = Configs.Generic.KEYBIND_BUNDLE_REMOVE_MAX.getIntegerValue();
			final int stored = Math.min(MOVE_LIMIT, getNumStored(occupancy));
			int withdrawn = 0;
			if(reverse){
				for(int i=SLOT_START; i<SLOT_END && withdrawn < stored; ++i){
					if(!slots[i].isEmpty()) continue;
					clicks.add(new ClickEvent(i, 1, SlotActionType.PICKUP));
					++withdrawn;
				}
			}
			else{
				int emptySlots = (int)IntStream.range(SLOT_START, SLOT_END).filter(i -> slots[i].isEmpty()).count();
//				Main.LOGGER.info("MapBundleOp: emptySlots: "+emptySlots+", stored: "+stored);
				int i=SLOT_END-1;
				for(; emptySlots > stored; --i) if(slots[i].isEmpty()) --emptySlots;
				for(; i>=SLOT_START && withdrawn < stored; --i){
					if(!slots[i].isEmpty()) continue;
					clicks.add(new ClickEvent(i, 1, SlotActionType.PICKUP));
					++withdrawn;
				}
			}
			Main.LOGGER.info("MapBundleOp: withdrew "+withdrawn+" maps from bundle");
		}
		if(bundleSlot != -1 && !pickupHalf) clicks.add(new ClickEvent(bundleSlot, 0, SlotActionType.PICKUP));

		Main.clickUtils.executeClicks(clicks, _0->true, ()->Main.LOGGER.info("MapBundleOp: DONE!"));
	}

	/*public KeybindMapMoveBundle(boolean regular, boolean reverse){
		Function<Screen, Boolean> allowInScreen =
				//InventoryScreen.class::isInstance
				s->s instanceof InventoryScreen || s instanceof GenericContainerScreen || s instanceof ShulkerBoxScreen || s instanceof CraftingScreen;

		if(regular) new Keybind("mapart_bundle", ()->moveMapArtToFromBundle(false), allowInScreen, GLFW.GLFW_KEY_D);
		if(reverse) new Keybind("mapart_bundle_reverse", ()->moveMapArtToFromBundle(true), allowInScreen, regular ? -1 : GLFW.GLFW_KEY_D);
	}*/
}