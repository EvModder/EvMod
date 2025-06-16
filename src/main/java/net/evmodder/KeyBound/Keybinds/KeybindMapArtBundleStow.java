package net.evmodder.KeyBound.Keybinds;

import java.util.ArrayDeque;
import java.util.stream.IntStream;
import org.apache.commons.lang3.math.Fraction;
import org.lwjgl.glfw.GLFW;
import net.evmodder.KeyBound.Main;
import net.evmodder.KeyBound.Keybinds.ClickUtils.ClickEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

public final class KeybindMapArtBundleStow{
	final int WITHDRAW_MAX = 36;
	//enum BundleSelectionMode{FIRST, LAST, MOST_FULL_butNOT_FULL, MOST_EMPTY_butNOT_EMPTY};

	private boolean isBundle(ItemStack stack){
		return !stack.isEmpty() || Registries.ITEM.getId(stack.getItem()).getPath().endsWith("bundle");
	}
	private int getNumStored(Fraction fraction){
		assert 64 % fraction.getDenominator() == 0;
		return  (64/fraction.getDenominator())*fraction.getNumerator();
	}

	//TODO: support inventories besides InventoryScreen (in particular, shulker screen, double-chest screen)

	private boolean ongoingBundleOp;
	private long lastBundleOp = 0;
	private final long bundleOpCooldown = 250l;
	private final void moveMapArtToFromBundle(){
		if(ongoingBundleOp){Main.LOGGER.warn("MapBundleOp: Already ongoing"); return;}
		//
		MinecraftClient client = MinecraftClient.getInstance();
		if(!(client.currentScreen instanceof InventoryScreen is)) return;
		//
		final long ts = System.currentTimeMillis();
		if(ts - lastBundleOp < bundleOpCooldown){Main.LOGGER.warn("MapBundleOp: in cooldown"); return;}
		lastBundleOp = ts;
		//
		final ItemStack[] slots = is.getScreenHandler().slots.stream().map(Slot::getStack).toArray(ItemStack[]::new);
		final boolean pickupHalf = IntStream.range(9, 46).filter(i -> slots[i].getItem() == Items.FILLED_MAP).allMatch(i -> slots[i].getCount() == 2);
		final boolean anyArtToPickup = IntStream.range(9, 46).anyMatch(i -> slots[i].getCount() == (pickupHalf ? 2 : 1) && slots[i].getItem() == Items.FILLED_MAP);

		Main.LOGGER.info("MapBundleOp: begin bundle search");
		ArrayDeque<ClickEvent> clicks = new ArrayDeque<>();
		final ItemStack cursorStack = is.getScreenHandler().getCursorStack();
		int bundleSlot = -1, mostEmpty = Integer.MAX_VALUE, mostFull = 0;
		Fraction occupancy = null;
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
			//clicks.add(new ClickEvent(ScreenHandler.EMPTY_SPACE_SLOT_INDEX, 0, SlotActionType.PICKUP));
		}
		else{
			for(int i=9; i<46; ++i){
				if(!isBundle(slots[i])) continue;
				BundleContentsComponent contents = slots[i].get(DataComponentTypes.BUNDLE_CONTENTS);
				occupancy = contents.getOccupancy();
				if(anyArtToPickup && occupancy.intValue() == 1) continue; // Skip full bundles
				if(!anyArtToPickup && contents.isEmpty()) continue; // Skip empty bundles
				if(contents.stream().anyMatch(s -> s.getItem() != Items.FILLED_MAP)) continue; // Skip bundles with non-mapart contents
				int stored = getNumStored(occupancy);
				if(anyArtToPickup){if(stored < mostEmpty){mostEmpty = stored; bundleSlot = i;}}
				else if(stored > mostFull){mostFull = stored; bundleSlot = i;}
				//if(mode == FIRST) break;
			}
			if(bundleSlot != -1){
				if(!pickupHalf) clicks.add(new ClickEvent(bundleSlot, 0, SlotActionType.PICKUP));
			}
			else{
				Main.LOGGER.warn("MapBundleOp: No usable bundle found");
				return;
			}
		}
		Main.LOGGER.info("MapBundleOp: contents: "+occupancy.getNumerator()+"/"+occupancy.getDenominator());

		if(anyArtToPickup){
			int available = 64 - getNumStored(occupancy);
			for(int i=9; i<46 && available > 0; ++i){
				if(slots[i].getItem() == Items.FILLED_MAP) continue;
				if(slots[i].getCount() != (pickupHalf ? 2 : 1)) continue;
				if(pickupHalf){
					clicks.add(new ClickEvent(i, 1, SlotActionType.PICKUP)); // Pickup half
					clicks.add(new ClickEvent(i, bundleSlot, SlotActionType.PICKUP)); // Put into bundle
				}
				else clicks.add(new ClickEvent(i, 0, SlotActionType.PICKUP)); // Suck up item with bundle on cursor
				--available;
			}
		}
		else{
			int stored = Math.min(WITHDRAW_MAX, getNumStored(occupancy));
			for(int i=44; i>8 && stored > 0; --i){
				if(!slots[i].isEmpty()) continue;
				clicks.add(new ClickEvent(i, 1, SlotActionType.PICKUP));
				--stored;
			}
		}
		if(bundleSlot != -1 && !pickupHalf) clicks.add(new ClickEvent(bundleSlot, 0, SlotActionType.PICKUP));

		ongoingBundleOp = true;
		Main.inventoryUtils.executeClicks(clicks, _0->true, ()->{Main.LOGGER.info("MapBundleOp: DONE!"); ongoingBundleOp = false;});
	}

	public KeybindMapArtBundleStow(){
		new Keybind("mapart_bundle", this::moveMapArtToFromBundle, InventoryScreen.class::isInstance, GLFW.GLFW_KEY_R);
	}
}