package net.evmodder.evmod.keybinds;

import static net.evmodder.evmod.compat.MinecraftCompat.screen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;
import net.evmodder.evmod.Configs;
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

	private static final class Source{
		final int slot, hotbarButton;
		int count;
		Source(int slot, int count, int hotbarButton){this.slot = slot; this.count = count; this.hotbarButton = hotbarButton;}
	}
	private record QuickMove(int destination, int remaining){}
	private record OneClickMove(Source source, boolean hotbarSwap, int consumed){}
	private record Layout(int resultSlot, int[] inputSlots){}
	private record Ingredient(int slot, ItemStack stack){}
	private record CachedRecipe(Class<? extends AbstractContainerMenu> menuClass, int resultSlot,
			List<Ingredient> ingredients, int selectionButton, String anvilName, String anvilInputName){}
	private static final int MAX_RECIPES_PER_MENU = 16;
	private static final class PendingCraft{
		final CachedRecipe recipe;
		final int containerId, initialStateId;
		final boolean requiresServerConfirmation;
		final boolean bulk;
		boolean serverConfirmed;

		PendingCraft(CachedRecipe recipe, int containerId, int initialStateId,
				boolean requiresServerConfirmation, boolean bulk){
			this.recipe = recipe;
			this.containerId = containerId;
			this.initialStateId = initialStateId;
			this.requiresServerConfirmation = requiresServerConfirmation;
			this.bulk = bulk;
		}
	}
	private static final class PendingBulkRepeat{
		final CachedRecipe recipe;
		final int containerId, initialStateId;
		final List<Ingredient> inputsBefore;
		boolean clickSent, serverConfirmed;

		PendingBulkRepeat(CachedRecipe recipe, AbstractContainerMenu menu){
			this.recipe = recipe;
			containerId = menu.containerId;
			initialStateId = menu.getStateId();
			inputsBefore = recipe.ingredients.stream()
					.map(ingredient -> new Ingredient(ingredient.slot, menu.getSlot(ingredient.slot).getItem().copy())).toList();
		}

		boolean inputChanged(int slot, ItemStack stack){
			return inputsBefore.stream().anyMatch(ingredient -> ingredient.slot == slot && !ItemStack.matches(ingredient.stack, stack));
		}
		boolean inputChanged(AbstractContainerMenu menu){
			return inputsBefore.stream().anyMatch(ingredient -> ingredient.slot >= menu.slots.size()
					|| !ItemStack.matches(ingredient.stack, menu.getSlot(ingredient.slot).getItem()));
		}
	}

	private final Map<Class<? extends AbstractContainerMenu>, ArrayDeque<CachedRecipe>> recipeHistories =
			new HashMap<>();
	private PendingCraft pendingCraft;
	private PendingBulkRepeat pendingBulkRepeat;

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
	private final void cacheRecipe(CachedRecipe recipe){
		final ArrayDeque<CachedRecipe> history = recipeHistories.computeIfAbsent(recipe.menuClass, _->new ArrayDeque<>());
		history.addFirst(recipe);
		while(history.size() > MAX_RECIPES_PER_MENU) history.removeLast();
	}

	public final void checkIfCraftAction(AbstractContainerMenu menu, int slot, int button, ContainerInput action, boolean automated){
		if(!automated){pendingCraft = null; pendingBulkRepeat = null;}
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

		cacheRecipe(new CachedRecipe(menu.getClass(), layout.resultSlot, ingredients,
				getSelectionButton(menu), anvilName, anvilInputName));
		pendingCraft = null;
		Main.LOGGER.info("CraftRestock: Cached "+menu.getClass().getSimpleName()+" recipe");
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
			if(!stack.isEmpty() && ItemStack.isSameItemSameComponents(needed, stack)) sources.add(new Source(
					i, stack.getCount(), Inventory.isHotbarSlot(slot.getContainerSlot()) ? slot.getContainerSlot() : -1));
		}
		sources.sort(Comparator.comparingInt((Source source)->source.count).reversed());
		return sources;
	}
	private static final boolean canRestock(CachedRecipe recipe, AbstractContainerMenu menu, Inventory inventory){
		final List<Ingredient> missing = getMissingIngredients(recipe, menu);
		if(missing == null) return false;
		for(IngredientGroup group : groupIngredients(missing)){
			if(findSources(menu, inventory, group.stack).stream().mapToInt(source -> source.count).sum()
					< group.destinations.size()) return false;
		}
		return true;
	}
	private final CachedRecipe selectRecipe(AbstractContainerMenu menu, Inventory inventory){
		final ArrayDeque<CachedRecipe> history = recipeHistories.get(menu.getClass());
		if(history == null || history.isEmpty()) return null;
		if(!Configs.Hotkeys.CRAFT_RESTOCK_SMART.getBooleanValue()) return history.getFirst();
		return history.stream().filter(recipe -> canRestock(recipe, menu, inventory)).findFirst().orElse(null);
	}

	private static final boolean supportsQuickMoveToInputs(AbstractContainerMenu menu){
		return menu instanceof CraftingMenu || menu instanceof ItemCombinerMenu
				|| menu instanceof GrindstoneMenu || menu instanceof StonecutterMenu;
	}
	private static final QuickMove planQuickMove(AbstractContainerMenu menu, CachedRecipe recipe,
			Map<Integer, ItemStack> plannedInputs, ItemStack source, boolean apply){
		if(!supportsQuickMoveToInputs(menu)) return null;
		int remaining = source.getCount();
		if(source.isStackable()){
			for(Ingredient ingredient : recipe.ingredients){
				final ItemStack target = plannedInputs.get(ingredient.slot);
				if(target.isEmpty() || !ItemStack.isSameItemSameComponents(source, target)) continue;
				final int moved = Math.min(remaining, menu.getSlot(ingredient.slot).getMaxStackSize(target)-target.getCount());
				if(moved > 0){
					remaining -= moved;
					if(apply) target.grow(moved);
				}
				if(remaining == 0) return new QuickMove(/*destination=*/-1, 0);
			}
		}

		for(Ingredient ingredient : recipe.ingredients){
			final Slot slot = menu.getSlot(ingredient.slot);
			if(!plannedInputs.get(ingredient.slot).isEmpty() || !slot.mayPlace(source)) continue;
			final int moved = Math.min(remaining, slot.getMaxStackSize(source));
			if(apply){
				final ItemStack movedStack = source.copy();
				movedStack.setCount(moved);
				plannedInputs.put(ingredient.slot, movedStack);
			}
			return new QuickMove(ingredient.slot, remaining-moved);
		}
		return new QuickMove(/*destination=*/-1, remaining);
	}
	private static final OneClickMove findOneClickMove(AbstractContainerMenu menu, CachedRecipe recipe,
			Map<Integer, ItemStack> plannedInputs, ItemStack stack, List<Source> sources, int destination, int destinationsLeft){
		final int total = sources.stream().mapToInt(source -> source.count).sum();
		OneClickMove best = null;
		for(Source source : sources){
			if(source.count == 0) continue;
			if(source.hotbarButton >= 0 && total-source.count >= destinationsLeft-1
					&& (best == null || source.count <= best.consumed)){
				best = new OneClickMove(source, /*hotbarSwap=*/true, source.count);
			}

			final ItemStack sourceStack = stack.copy();
			sourceStack.setCount(source.count);
			final QuickMove quickMove = planQuickMove(menu, recipe, plannedInputs, sourceStack, /*apply=*/false);
			if(quickMove == null || quickMove.destination != destination) continue;
			final int consumed = source.count-quickMove.remaining;
			if(total-consumed >= destinationsLeft-1 && (best == null || consumed < best.consumed)){
				best = new OneClickMove(source, /*hotbarSwap=*/false, consumed);
			}
		}
		return best;
	}

	private static final ArrayDeque<InvAction> planRestock(AbstractContainerMenu menu, Inventory inventory,
			CachedRecipe recipe, List<Ingredient> missing){
		final ArrayDeque<InvAction> clicks = new ArrayDeque<>();
		final Map<Integer, ItemStack> plannedInputs = new HashMap<>();
		for(Ingredient ingredient : recipe.ingredients){
			plannedInputs.put(ingredient.slot, menu.getSlot(ingredient.slot).getItem().copy());
		}
		for(IngredientGroup group : groupIngredients(missing)){
			final List<Source> sources = findSources(menu, inventory, group.stack);
			final int required = group.destinations.size();
			if(sources.stream().mapToInt(source -> source.count).sum() < required){
				Main.LOGGER.info("CraftRestock: Unable to find enough of "+group.stack.getHoverName().getString());
				return null;
			}

			final ArrayDeque<Integer> destinations = new ArrayDeque<>(group.destinations);
			while(!destinations.isEmpty()){
				final int destination = destinations.getFirst();
				final OneClickMove oneClick = findOneClickMove(
						menu, recipe, plannedInputs, group.stack, sources, destination, destinations.size());
				if(oneClick != null){
					final Source source = oneClick.source;
					if(oneClick.hotbarSwap){
						clicks.add(new InvAction(destination, source.hotbarButton, ActionType.HOTBAR_SWAP));
						final ItemStack moved = group.stack.copy();
						moved.setCount(source.count);
						plannedInputs.put(destination, moved);
						source.count = 0;
					}
					else{
						clicks.add(new InvAction(source.slot, 0, ActionType.SHIFT_CLICK));
						final ItemStack sourceStack = group.stack.copy();
						sourceStack.setCount(source.count);
						source.count = planQuickMove(menu, recipe, plannedInputs, sourceStack, /*apply=*/true).remaining;
					}
					destinations.removeFirst();
					continue;
				}

				final Source source = sources.stream().filter(candidate -> candidate.count > 0)
						.max(Comparator.comparingInt(candidate -> candidate.count)).orElseThrow();
				final int assign = Math.min(source.count, destinations.size());
				clicks.add(new InvAction(source.slot, 0, ActionType.CLICK));
				if(destinations.size() == 1){
					clicks.add(new InvAction(destination, 0, ActionType.CLICK));
					final ItemStack moved = group.stack.copy();
					moved.setCount(source.count);
					plannedInputs.put(destination, moved);
					destinations.removeFirst();
					source.count = 0;
				}
				else{
					for(int i=0; i<assign; ++i){
						final int assignedDestination = destinations.removeFirst();
						clicks.add(new InvAction(assignedDestination, 1, ActionType.CLICK));
						final ItemStack moved = group.stack.copy();
						moved.setCount(1);
						plannedInputs.put(assignedDestination, moved);
					}
					source.count -= assign;
					if(source.count > 0) clicks.add(new InvAction(source.slot, 0, ActionType.CLICK));
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

	private final void finishRestock(Minecraft client, CachedRecipe recipe, boolean craftAfter, boolean bulk, boolean restocked){
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

		if(craftAfter) pendingCraft = new PendingCraft(recipe, menu.containerId, menu.getStateId(),
				restocked || changedSelection || changedAnvilName, bulk);
	}

	private final void restock(Minecraft client, AbstractContainerScreen<?> handledScreen, CachedRecipe recipe,
			boolean craftAfter, boolean bulk){
		final AbstractContainerMenu menu = handledScreen.getMenu();
		if(!isSameMenu(recipe, menu) || !menu.getCarried().isEmpty()) return;

		final List<Ingredient> missing = getMissingIngredients(recipe, menu);
		if(missing == null){Main.LOGGER.info("CraftRestock: Current inputs conflict with cached recipe"); return;}
		if(missing.isEmpty()){finishRestock(client, recipe, craftAfter, bulk, /*restocked=*/false); return;}

		final ArrayDeque<InvAction> clicks = planRestock(menu, client.player.getInventory(), recipe, missing);
		if(clicks == null || clicks.isEmpty()) return;
		ClickUtils.executeClicks(/*canProceed=*/_->true,
				()->client.execute(()->finishRestock(client, recipe, craftAfter, bulk, /*restocked=*/true)), clicks);
	}

	public final void restockInputSlots(){
		final Minecraft client = Minecraft.getInstance();
		if(client.player == null || client.gameMode == null || ClickUtils.hasOngoingClicks()
				|| pendingCraft != null || pendingBulkRepeat != null) return;
		if(!(screen(client) instanceof AbstractContainerScreen<?> handledScreen)) return;
		final AbstractContainerMenu menu = handledScreen.getMenu();
		if(!menu.getCarried().isEmpty()) return;
		final CachedRecipe recipe = selectRecipe(menu, client.player.getInventory());
		if(recipe == null) return;

		final boolean bulk = Configs.Hotkeys.CRAFT_BULK_MODIFIER.getKeybind().isKeybindHeld();
		restock(client, handledScreen, recipe, /*craftAfter=*/bulk, bulk);
	}

	private static final boolean isExpectedResult(PendingCraft craft, ItemStack result){
		return !result.isEmpty() && (craft.recipe.anvilName == null || craft.recipe.anvilName.equals(getCustomName(result)));
	}

	private final void confirmBulkRepeat(int containerId, int stateId, int slot, ItemStack stack){
		final PendingBulkRepeat repeat = pendingBulkRepeat;
		if(repeat != null && repeat.clickSent && repeat.containerId == containerId
				&& repeat.initialStateId != stateId && repeat.inputChanged(slot, stack)) repeat.serverConfirmed = true;
	}
	public final void onServerSlotUpdate(int containerId, int stateId, int slot, ItemStack result){
		final PendingCraft craft = pendingCraft;
		if(craft != null && craft.requiresServerConfirmation && craft.containerId == containerId
				&& craft.initialStateId != stateId && craft.recipe.resultSlot == slot
				&& isExpectedResult(craft, result)) craft.serverConfirmed = true;
		confirmBulkRepeat(containerId, stateId, slot, result);
	}

	public final void onServerContainerContent(){
		final Minecraft client = Minecraft.getInstance();
		final PendingCraft craft = pendingCraft;
		if(client.player == null) return;
		final AbstractContainerMenu menu = client.player.containerMenu;
		if(craft != null && craft.requiresServerConfirmation && craft.recipe.menuClass == menu.getClass()
				&& craft.containerId == menu.containerId && craft.initialStateId != menu.getStateId()
				&& craft.recipe.resultSlot < menu.slots.size()
				&& isExpectedResult(craft, menu.getSlot(craft.recipe.resultSlot).getItem())) craft.serverConfirmed = true;
		final PendingBulkRepeat repeat = pendingBulkRepeat;
		if(repeat != null && repeat.clickSent && repeat.recipe.menuClass == menu.getClass()
				&& repeat.containerId == menu.containerId
				&& repeat.initialStateId != menu.getStateId() && repeat.inputChanged(menu)) repeat.serverConfirmed = true;
	}

	private final void craftWhenReady(Minecraft client){
		final PendingCraft craft = pendingCraft;
		if(craft == null || ClickUtils.hasOngoingClicks()) return;
		if(client.player == null || !(screen(client) instanceof AbstractContainerScreen<?> handledScreen)){
			pendingCraft = null; return;
		}
		final AbstractContainerMenu menu = handledScreen.getMenu();
		if(craft.recipe.menuClass != menu.getClass() || craft.containerId != menu.containerId
				|| craft.recipe.resultSlot >= menu.slots.size()){
			pendingCraft = null; return;
		}
		final ItemStack result = menu.getSlot(craft.recipe.resultSlot).getItem();
		if((craft.requiresServerConfirmation && !craft.serverConfirmed) || !isExpectedResult(craft, result)) return;

		pendingCraft = null;
		if(craft.bulk) pendingBulkRepeat = new PendingBulkRepeat(craft.recipe, menu);
		ClickUtils.executeClicks(/*canProceed=*/_->{
			if(pendingBulkRepeat != null) pendingBulkRepeat.clickSent = true;
			return true;
		}, ()->{}, new InvAction(craft.recipe.resultSlot, 0, ActionType.SHIFT_CLICK));
	}

	private final void repeatBulkWhenReady(Minecraft client){
		final PendingBulkRepeat repeat = pendingBulkRepeat;
		if(repeat == null || !repeat.serverConfirmed || ClickUtils.hasOngoingClicks()) return;
		if(client.player == null || !(screen(client) instanceof AbstractContainerScreen<?> handledScreen)){
			pendingBulkRepeat = null; return;
		}
		final AbstractContainerMenu menu = handledScreen.getMenu();
		if(repeat.recipe.menuClass != menu.getClass() || repeat.containerId != menu.containerId){
			pendingBulkRepeat = null; return;
		}

		pendingBulkRepeat = null;
		restock(client, handledScreen, repeat.recipe, /*craftAfter=*/true, /*bulk=*/true);
	}

	@Override public final void onTickEnd(Minecraft client){
		craftWhenReady(client);
		repeatBulkWhenReady(client);
	}
}