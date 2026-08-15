package net.evmodder.evmod.keybinds;

import static net.evmodder.evmod.compat.MinecraftCompat.screen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import org.lwjgl.glfw.GLFW;
import fi.dy.masa.malilib.hotkeys.KeybindMulti;
import net.evmodder.evmod.Main;
import net.evmodder.evmod.apis.ClickUtils;
import net.evmodder.evmod.apis.ClickUtils.ActionType;
import net.evmodder.evmod.apis.ClickUtils.InvAction;
import net.evmodder.evmod.apis.TickListener;
import net.evmodder.evmod.mixin.AccessorAnvilScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;

public final class KeybindCraftingRestock implements TickListener{
	public interface AnvilNameController{
		void evmod$clearNameProtection();
	}

	private record Source(int slot, int count){}
	private record Layout(int resultSlot, int[] inputSlots){}
	private record Ingredient(int slot, ItemStack stack){}
	private record CachedRecipe(Class<? extends AbstractContainerMenu> menuClass, int resultSlot,
			List<Ingredient> ingredients, int selectionButton, String anvilName, String anvilInputName){}
	private static final class PendingCraft{
		final Class<? extends AbstractContainerMenu> menuClass;
		final int containerId, initialStateId, resultSlot;
		final String expectedName;
		final boolean requiresServerConfirmation;
		boolean serverConfirmed;

		PendingCraft(Class<? extends AbstractContainerMenu> menuClass, int containerId, int initialStateId,
				int resultSlot, String expectedName, boolean requiresServerConfirmation){
			this.menuClass = menuClass;
			this.containerId = containerId;
			this.initialStateId = initialStateId;
			this.resultSlot = resultSlot;
			this.expectedName = expectedName;
			this.requiresServerConfirmation = requiresServerConfirmation;
		}
	}

	private CachedRecipe cachedRecipe;
	private PendingCraft pendingCraft;

	public KeybindCraftingRestock(){TickListener.register(this);}

	private static final boolean willCraftItem(ContainerInput action){
		return switch(action){
			case PICKUP, QUICK_MOVE, SWAP, THROW -> true;
			default -> false;
		};
	}

	private static final Layout getLayout(AbstractContainerMenu menu){
		if(menu instanceof InventoryMenu) return new Layout(InventoryMenu.RESULT_SLOT,
				IntStream.range(InventoryMenu.CRAFT_SLOT_START, InventoryMenu.CRAFT_SLOT_END).toArray());
		if(menu instanceof CraftingMenu) return new Layout(CraftingMenu.RESULT_SLOT, IntStream.range(1, 10).toArray());
		if(menu instanceof ItemCombinerMenu itemCombiner){
			final int resultSlot = itemCombiner.getResultSlot();
			return new Layout(resultSlot, IntStream.range(0, resultSlot).toArray());
		}
		if(menu instanceof GrindstoneMenu) return new Layout(GrindstoneMenu.RESULT_SLOT,
				new int[]{GrindstoneMenu.INPUT_SLOT, GrindstoneMenu.ADDITIONAL_SLOT});
		if(menu instanceof StonecutterMenu) return new Layout(StonecutterMenu.RESULT_SLOT, new int[]{StonecutterMenu.INPUT_SLOT});
//		if(menu instanceof CartographyTableMenu) return new Layout(CartographyTableMenu.RESULT_SLOT,
//				new int[]{CartographyTableMenu.MAP_SLOT, CartographyTableMenu.ADDITIONAL_SLOT});
//		if(menu instanceof LoomMenu) return new Layout(/*resultSlot=*/3, new int[]{/*banner=*/0, /*dye=*/1, /*pattern=*/2});
		return null;
	}

	private static final int getSelectionButton(AbstractContainerMenu menu){
		if(menu instanceof StonecutterMenu stonecutter) return stonecutter.getSelectedRecipeIndex();
//		if(menu instanceof LoomMenu loom) return loom.getSelectedBannerPatternIndex();
		return -1;
	}

	private static final String getCustomName(ItemStack stack){
		return stack.getCustomName() == null ? null : stack.getCustomName().tryCollapseToString();
	}

	public final void checkIfCraftAction(AbstractContainerMenu menu, int slot, int button, ContainerInput action, boolean automated){
		if(!automated) pendingCraft = null;
		if(menu == null || !willCraftItem(action)) return;
		final Layout layout = getLayout(menu);
		if(layout == null || slot != layout.resultSlot || !menu.getSlot(slot).hasItem()) return;

		final List<Ingredient> ingredients = IntStream.of(layout.inputSlots)
				.mapToObj(i -> new Ingredient(i, menu.getSlot(i).getItem().copy())).toList();
		final ItemStack result = menu.getSlot(layout.resultSlot).getItem();
		final String anvilName, anvilInputName;
		if(menu instanceof AnvilMenu){
			anvilName = getCustomName(result);
			anvilInputName = getCustomName(menu.getSlot(AnvilMenu.INPUT_SLOT).getItem());
		}
		else anvilName = anvilInputName = null;

		cachedRecipe = new CachedRecipe(menu.getClass(), layout.resultSlot, ingredients,
				getSelectionButton(menu), anvilName, anvilInputName);
		pendingCraft = null;
		Main.LOGGER.info("CraftRestock: Cached "+menu.getClass().getSimpleName()+" recipe");
	}

	private static final boolean isShiftDown(){
		return KeybindMulti.isKeyDown(GLFW.GLFW_KEY_LEFT_SHIFT) || KeybindMulti.isKeyDown(GLFW.GLFW_KEY_RIGHT_SHIFT);
	}

	private static final boolean isSameMenu(CachedRecipe recipe, AbstractContainerMenu menu){return recipe.menuClass == menu.getClass();}

	private static final List<Ingredient> getMissingIngredients(CachedRecipe recipe, AbstractContainerMenu menu){
		final List<Ingredient> missing = new ArrayList<>();
		for(Ingredient ingredient : recipe.ingredients){
			if(ingredient.slot < 0 || ingredient.slot >= menu.slots.size()) return null;
			final Slot slot = menu.getSlot(ingredient.slot);
			if(ingredient.stack.isEmpty()){
				if(slot.hasItem()) return null;
			}
			else if(!slot.hasItem()){
				if(!slot.mayPlace(ingredient.stack)) return null;
				missing.add(ingredient);
			}
			else if(!ItemStack.isSameItemSameComponents(ingredient.stack, slot.getItem())) return null;
		}
		return missing;
	}

	private static final class IngredientGroup{
		final ItemStack stack;
		final List<Integer> destinations = new ArrayList<>();
		IngredientGroup(ItemStack stack){this.stack = stack;}
	}
	private static final List<IngredientGroup> groupIngredients(List<Ingredient> missing){
		final List<IngredientGroup> groups = new ArrayList<>();
		for(Ingredient ingredient : missing){
			IngredientGroup matchingGroup = null;
			for(IngredientGroup group : groups){
				if(ItemStack.isSameItemSameComponents(group.stack, ingredient.stack)){matchingGroup = group; break;}
			}
			if(matchingGroup == null){matchingGroup = new IngredientGroup(ingredient.stack); groups.add(matchingGroup);}
			matchingGroup.destinations.add(ingredient.slot);
		}
		return groups;
	}

	private static final List<Source> findSources(AbstractContainerMenu menu, Inventory inventory, ItemStack needed){
		final List<Source> sources = new ArrayList<>();
		for(int i=0; i<menu.slots.size(); ++i){
			final Slot slot = menu.getSlot(i);
			if(slot.container != inventory || slot.getContainerSlot() >= Inventory.INVENTORY_SIZE) continue;
			final ItemStack stack = slot.getItem();
			if(!stack.isEmpty() && ItemStack.isSameItemSameComponents(needed, stack)) sources.add(new Source(i, stack.getCount()));
		}
		sources.sort(Comparator.comparingInt(Source::count).reversed());
		return sources;
	}

	private static final ArrayDeque<InvAction> planRestock(AbstractContainerMenu menu, Inventory inventory, List<Ingredient> missing){
		final ArrayDeque<InvAction> clicks = new ArrayDeque<>();
		for(IngredientGroup group : groupIngredients(missing)){
			final List<Source> sources = findSources(menu, inventory, group.stack);
			final int required = group.destinations.size();
			if(sources.stream().mapToInt(Source::count).sum() < required){
				Main.LOGGER.info("CraftRestock: Unable to find enough of "+group.stack.getHoverName().getString());
				return null;
			}

			if(sources.size() >= required){
				for(int i=0; i<required; ++i){
					clicks.add(new InvAction(sources.get(i).slot, 0, ActionType.CLICK));
					clicks.add(new InvAction(group.destinations.get(i), 0, ActionType.CLICK));
				}
				continue;
			}

			int destinationIndex = 0;
			for(int i=0; destinationIndex<required; ++i){
				final Source source = sources.get(i);
				final int assign = Math.min(source.count, required-destinationIndex);
				clicks.add(new InvAction(source.slot, 0, ActionType.CLICK));
				if(assign == 1) clicks.add(new InvAction(group.destinations.get(destinationIndex++), 0, ActionType.CLICK));
				else{
					clicks.add(new InvAction(AbstractContainerMenu.SLOT_CLICKED_OUTSIDE,
							AbstractContainerMenu.getQuickcraftMask(AbstractContainerMenu.QUICKCRAFT_HEADER_START,
									AbstractContainerMenu.QUICKCRAFT_TYPE_CHARITABLE), ActionType.QUICK_CRAFT));
					for(int j=0; j<assign; ++j) clicks.add(new InvAction(group.destinations.get(destinationIndex++),
							AbstractContainerMenu.getQuickcraftMask(AbstractContainerMenu.QUICKCRAFT_HEADER_CONTINUE,
									AbstractContainerMenu.QUICKCRAFT_TYPE_CHARITABLE), ActionType.QUICK_CRAFT));
					clicks.add(new InvAction(AbstractContainerMenu.SLOT_CLICKED_OUTSIDE,
							AbstractContainerMenu.getQuickcraftMask(AbstractContainerMenu.QUICKCRAFT_HEADER_END,
									AbstractContainerMenu.QUICKCRAFT_TYPE_CHARITABLE), ActionType.QUICK_CRAFT));
					if(source.count % assign != 0) clicks.add(new InvAction(source.slot, 0, ActionType.CLICK));
				}
			}
		}
		return clicks;
	}

	private final boolean updateAnvilName(AnvilScreen screen, String name){
		final EditBox nameField = ((AccessorAnvilScreen)screen).getNameField();
		if(name.equals(nameField.getValue())) return false;
		nameField.setValue(name);
		nameField.moveCursorToStart(false);
		nameField.moveCursorToEnd(false);
		return true;
	}

	private final void finishRestock(Minecraft client, CachedRecipe recipe, boolean craftAfter, boolean restocked){
		if(client.player == null || client.gameMode == null || !(screen(client) instanceof AbstractContainerScreen<?> handledScreen)) return;
		final AbstractContainerMenu menu = handledScreen.getMenu();
		if(!isSameMenu(recipe, menu)) return;

		boolean changedSelection = false;
		if(recipe.selectionButton >= 0 && getSelectionButton(menu) != recipe.selectionButton
				&& menu.clickMenuButton(client.player, recipe.selectionButton)){
			client.gameMode.handleInventoryButtonClick(menu.containerId, recipe.selectionButton);
			changedSelection = true;
		}

		boolean changedAnvilName = false;
		if(recipe.anvilName != null && !Objects.equals(recipe.anvilName, recipe.anvilInputName) && handledScreen instanceof AnvilScreen anvilScreen){
			changedAnvilName = updateAnvilName(anvilScreen, recipe.anvilName);
		}

		if(craftAfter) pendingCraft = new PendingCraft(recipe.menuClass, menu.containerId, menu.getStateId(),
				recipe.resultSlot, recipe.anvilName, restocked || changedSelection || changedAnvilName);
	}

	public final void restockInputSlots(){
		final Minecraft client = Minecraft.getInstance();
		if(client.player == null || client.gameMode == null || cachedRecipe == null || ClickUtils.hasOngoingClicks() || pendingCraft != null) return;
		if(!(screen(client) instanceof AbstractContainerScreen<?> handledScreen)) return;
		final AbstractContainerMenu menu = handledScreen.getMenu();
		final CachedRecipe recipe = cachedRecipe;
		if(!isSameMenu(recipe, menu) || !menu.getCarried().isEmpty()) return;

		final List<Ingredient> missing = getMissingIngredients(recipe, menu);
		if(missing == null){Main.LOGGER.info("CraftRestock: Current inputs conflict with cached recipe"); return;}
		final boolean craftAfter = isShiftDown();
		if(missing.isEmpty()){finishRestock(client, recipe, craftAfter, /*restocked=*/false); return;}

		final ArrayDeque<InvAction> clicks = planRestock(menu, client.player.getInventory(), missing);
		if(clicks == null || clicks.isEmpty()) return;
		ClickUtils.executeClicks(/*canProceed=*/_->true,
				()->client.execute(()->finishRestock(client, recipe, craftAfter, /*restocked=*/true)), clicks);
	}

	private static final boolean isExpectedResult(PendingCraft craft, ItemStack result){
		return !result.isEmpty() && (craft.expectedName == null || craft.expectedName.equals(getCustomName(result)));
	}

	public final void onServerSlotUpdate(int containerId, int slot, ItemStack result){
		final PendingCraft craft = pendingCraft;
		if(craft != null && craft.requiresServerConfirmation && craft.containerId == containerId
				&& craft.resultSlot == slot && isExpectedResult(craft, result)) craft.serverConfirmed = true;
	}

	public final void onServerContainerContent(){
		final Minecraft client = Minecraft.getInstance();
		final PendingCraft craft = pendingCraft;
		if(craft == null || !craft.requiresServerConfirmation || client.player == null) return;
		final AbstractContainerMenu menu = client.player.containerMenu;
		if(craft.menuClass == menu.getClass() && craft.containerId == menu.containerId
				&& craft.initialStateId != menu.getStateId() && craft.resultSlot < menu.slots.size()
				&& isExpectedResult(craft, menu.getSlot(craft.resultSlot).getItem())) craft.serverConfirmed = true;
	}

	private final void craftWhenReady(Minecraft client){
		final PendingCraft craft = pendingCraft;
		if(craft == null || ClickUtils.hasOngoingClicks()) return;
		if(client.player == null || !(screen(client) instanceof AbstractContainerScreen<?> handledScreen)){
			pendingCraft = null; return;
		}
		final AbstractContainerMenu menu = handledScreen.getMenu();
		if(craft.menuClass != menu.getClass() || craft.containerId != menu.containerId || craft.resultSlot >= menu.slots.size()){
			pendingCraft = null; return;
		}
		final ItemStack result = menu.getSlot(craft.resultSlot).getItem();
		if((craft.requiresServerConfirmation && !craft.serverConfirmed) || !isExpectedResult(craft, result)) return;

		pendingCraft = null;
		ClickUtils.executeClicks(/*canProceed=*/_->true, ()->{}, new InvAction(craft.resultSlot, 0, ActionType.SHIFT_CLICK));
	}

	@Override public final void onTickEnd(Minecraft client){
		craftWhenReady(client);
	}
}