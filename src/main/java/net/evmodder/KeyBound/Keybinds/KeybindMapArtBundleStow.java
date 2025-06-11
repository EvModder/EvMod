package net.evmodder.KeyBound.Keybinds;

import java.util.ArrayDeque;
import java.util.stream.IntStream;
import org.apache.commons.lang3.math.Fraction;
import net.evmodder.KeyBound.Main;
import net.evmodder.KeyBound.Keybinds.ClickUtils.ClickEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

public final class KeybindMapArtBundleStow{
	final int WITHDRAW_MAX = 27;

	private boolean isNotBundle(ItemStack stack){
		return stack.isEmpty() || !Registries.ITEM.getId(stack.getItem()).getPath().endsWith("bundle");
	}
	private boolean isNotUsableMapArt(ItemStack stack){
		return stack.getItem() != Items.FILLED_MAP || stack.getCount() != 1;
	}

	//TODO: support inventories besides InventoryScreen (in particular, shulker screen)

	private boolean ongoingBundleOp;
	private long lastBundleOp = 0;
	private final long bundleOpCooldown = 250l;
	private final void moveMapArtToFromBundle(){
		if(ongoingBundleOp){Main.LOGGER.warn("MapBundleOp cancelled: Already ongoing"); return;}
		//
		MinecraftClient client = MinecraftClient.getInstance();
		if(!(client.currentScreen instanceof InventoryScreen is)) return;
		//
		final long ts = System.currentTimeMillis();
		if(ts - lastBundleOp < bundleOpCooldown) return;
		lastBundleOp = ts;
		//
		final ItemStack[] slots = is.getScreenHandler().slots.stream().map(Slot::getStack).toArray(ItemStack[]::new);
		final boolean anyArtToPickup = IntStream.range(9, 46).anyMatch(i -> slots[i].getItem() == Items.FILLED_MAP);

		ArrayDeque<ClickEvent> clicks = new ArrayDeque<>();
		final ItemStack cursorStack = is.getScreenHandler().getCursorStack();
		int bundleFromSlot = -1;
		Fraction occupancy = null;
		if(isNotBundle(cursorStack)){
			if(!cursorStack.isEmpty()) clicks.add(new ClickEvent(ScreenHandler.EMPTY_SPACE_SLOT_INDEX, 0, SlotActionType.PICKUP));
			for(int i=9; i<46; ++i){
				if(isNotBundle(slots[i])) continue;
				BundleContentsComponent contents = slots[i].get(DataComponentTypes.BUNDLE_CONTENTS);
				occupancy = contents.getOccupancy();
//				Main.LOGGER.info("contents: "+occupancy.getNumerator()+"/"+occupancy.getDenominator());
				if(anyArtToPickup && occupancy.intValue() == 1) continue; // Skip full bundles
				if(!anyArtToPickup && occupancy.intValue() == 0) continue; // Skip empty bundles
				if(contents.stream().anyMatch(this::isNotUsableMapArt)) continue; // Skip bundles with non-mapart contents
				clicks.add(new ClickEvent(i, 0, SlotActionType.PICKUP));
				bundleFromSlot = i;
				break;
			}
			if(bundleFromSlot == -1){
				Main.LOGGER.warn("MapBundleOp cancelled: No usable bundle found");
				return;
			}
		}
		else occupancy = cursorStack.get(DataComponentTypes.BUNDLE_CONTENTS).getOccupancy();
		Main.LOGGER.info("MapBundleOp: contents: "+occupancy.getNumerator()+"/"+occupancy.getDenominator());

		if(anyArtToPickup){
			int available = 64 - (64/occupancy.getDenominator())*occupancy.getNumerator();
			Main.LOGGER.info("MapBundleOp: contents: "+occupancy.getNumerator()+"/"+occupancy.getDenominator());
			for(int i=9; i<46 && available > 0; ++i){
				if(isNotUsableMapArt(slots[i])) continue;
				clicks.add(new ClickEvent(i, 0, SlotActionType.PICKUP));
				--available;
			}
		}
		else{
			int stored = Math.min(WITHDRAW_MAX, (64/occupancy.getDenominator())*occupancy.getNumerator());
			Main.LOGGER.info("MapBundleOp: contents: "+occupancy.getNumerator()+"/"+occupancy.getDenominator());
			for(int i=44; i>8 && stored > 0; --i){
				if(!slots[i].isEmpty()) continue;
				clicks.add(new ClickEvent(i, 1, SlotActionType.PICKUP));
				--stored;
			}
		}
		if(bundleFromSlot != -1) clicks.add(new ClickEvent(bundleFromSlot, 0, SlotActionType.PICKUP));

		ongoingBundleOp = true;
		Main.inventoryUtils.executeClicks(clicks, _0->true, ()->{Main.LOGGER.info("MapBundleOp: DONE!"); ongoingBundleOp = false;});
	}

	public KeybindMapArtBundleStow(){
		new Keybind("mapart_bundle", this::moveMapArtToFromBundle, InventoryScreen.class::isInstance);
	}
}