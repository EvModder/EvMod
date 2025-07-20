package net.evmodder.KeyBound.Keybinds;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.OptionalInt;
import java.util.stream.IntStream;
import org.lwjgl.glfw.GLFW;
import net.evmodder.KeyBound.Main;
import net.evmodder.KeyBound.Keybinds.ClickUtils.ClickEvent;
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
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;

public final class KeybindMapLoad{
	private boolean isUnloadedMapArt(World world, ItemStack stack){
//		if(stack == null || stack.isEmpty()) return false;
//		if(!Registries.ITEM.getId(stack.getItem()).getPath().equals("filled_map")) return false;
//		return FilledMapItem.getMapState(stack, world) == null;
		if(stack.getItem() != Items.FILLED_MAP) return false;
		MapState state = FilledMapItem.getMapState(stack, world);
		return state == null || state.colors == null;
	}
	private boolean isLoadedMapArt(World world, ItemStack stack){
		if(stack.getItem() != Items.FILLED_MAP) return false;
		MapState state = FilledMapItem.getMapState(stack, world);
		return state != null && state.colors != null && state.colors.length == 128*128;
	}

	private boolean isShulkerBox(ItemStack stack){
		return !stack.isEmpty() && Registries.ITEM.getId(stack.getItem()).getPath().endsWith("shulker_box");
	}

	private int getNextUsableHotbarButton(MinecraftClient client, int hb){
		while(++hb < 9 && client.currentScreen instanceof ShulkerBoxScreen && isShulkerBox(client.player.getInventory().getStack(hb)));
		return hb;
	}

	private final void loadMapArtFromBundles(){
		MinecraftClient client = MinecraftClient.getInstance();
		InventoryScreen is = (InventoryScreen)client.currentScreen;
		final BundleContentsComponent[] slots = is.getScreenHandler().slots.stream()
				.map(s -> s.getStack().get(DataComponentTypes.BUNDLE_CONTENTS)).toArray(BundleContentsComponent[]::new);
		final int[] slotsWithBundles = IntStream.range(9, 45).filter(i -> slots[i] != null).toArray();
		if(slotsWithBundles.length == 0) return;
		OptionalInt emptyBundleSlotOpt = Arrays.stream(slotsWithBundles).filter(i -> slots[i].getOccupancy().getNumerator() == 0).findAny();
		if(emptyBundleSlotOpt.isEmpty()){Main.LOGGER.warn("MapLoadBundle: Empty bundle not found"); return;}
		final int emptyBundleSlot = emptyBundleSlotOpt.getAsInt();

		ArrayDeque<ClickEvent> clicks = new ArrayDeque<>();
		for(int i : slotsWithBundles){
			if(slots[i].isEmpty()) continue;
			if(slots[i].stream().anyMatch(s -> s.getItem() != Items.FILLED_MAP)) continue; // Skip bundles with non-mapart contents
			if(slots[i].stream().allMatch(s -> isLoadedMapArt(client.world, s))) continue; // Skip bundles with already-loaded mapart
		}
	}

	//TODO: Consider shift-clicks instead of hotbar swaps (basically, MapMove but only for unloaded maps, and keep track of which)
	private long lastLoad;
	private final long loadCooldown = 500L;
	private final void loadMapArtFromContainer(){
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
		int numToLoad = 0;
		for(int i=0; i<slots.size(); ++i) if(isUnloadedMapArt(client.player.clientWorld, slots.get(i).getStack())) ++numToLoad;
		if(numToLoad == 0){Main.LOGGER.warn("MapLoad cancelled: none to load"); return;}
		//
		int hotbarButton = getNextUsableHotbarButton(client, -1);
		if(hotbarButton == 9){Main.LOGGER.warn("MapLoad cancelled: in shulker, and hotbar is full of shulkers"); return;}
		//
		int[] putBackSlots = new int[9];
		for(int i=0; i<putBackSlots.length; ++i) putBackSlots[i] = -1;
		//
		int usableHotbarSlots = 0;
		for(int i=-1; (i=getNextUsableHotbarButton(client, i)) != 9; ++usableHotbarSlots);

		ArrayDeque<ClickEvent> clicks = new ArrayDeque<>();
		int batchSize = 0;
		final int MAX_BATCH_SIZE = Math.min(usableHotbarSlots, Main.clickUtils.MAX_CLICKS/2);
		for(int i=0; i<slots.size() && numToLoad > 0; ++i){
			if(!isUnloadedMapArt(client.player.clientWorld, slots.get(i).getStack())) continue;
			clicks.add(new ClickEvent(i, hotbarButton, SlotActionType.SWAP));
			++batchSize;
			putBackSlots[hotbarButton] = i;
			--numToLoad;

			hotbarButton = getNextUsableHotbarButton(client, hotbarButton);
			if(hotbarButton == 9 || numToLoad == 0 || batchSize == MAX_BATCH_SIZE){
				for(int j=0; j<hotbarButton; ++j) if(putBackSlots[j] != -1) clicks.add(new ClickEvent(putBackSlots[j], j, SlotActionType.SWAP));
				hotbarButton = getNextUsableHotbarButton(client, -1);
				batchSize = 0;
			}
		}
		//Main.LOGGER.info("MapLoad: STARTED");
		Main.clickUtils.executeClicks(clicks,
				c->{
					if(client.player == null || client.world == null) return true;
					ItemStack item = client.player.getInventory().getStack(c.button());
					if(isUnloadedMapArt(/*client.player.clientWorld*/client.world, item)) return false;
					if(isLoadedMapArt(/*client.player.clientWorld*/client.world, item)) return true;
					if(getNextUsableHotbarButton(client, -1) != c.button()
						|| Main.clickUtils.MAX_CLICKS-Main.clickUtils.addClick(null) >= MAX_BATCH_SIZE) return true;
					client.player.sendMessage(Text.literal("MapLoad: Waiting for clicks...").withColor(KeybindMapCopy.WAITING_FOR_CLICKS_COLOR), true);
					return false;
				},
				()->Main.LOGGER.info("MapLoad: DONE!")
		);
	}

	public KeybindMapLoad(){
		new Keybind("mapart_load", ()->loadMapArtFromContainer(), _0->true, GLFW.GLFW_KEY_E);
	}
}