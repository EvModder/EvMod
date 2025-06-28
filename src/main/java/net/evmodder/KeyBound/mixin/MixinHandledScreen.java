package net.evmodder.KeyBound.mixin;

import net.evmodder.KeyBound.Main;
import net.evmodder.KeyBound.MapGroupUtils;
import net.evmodder.KeyBound.MapRelationUtils;
import net.evmodder.KeyBound.EventListeners.InventoryHighlightUpdater;
import net.evmodder.KeyBound.EventListeners.ItemFrameHighlightUpdater;
import net.evmodder.KeyBound.Keybinds.Keybind;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapState;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HandledScreen.class)
abstract class MixinHandledScreen<T> extends Screen{
	@Final @Shadow protected ScreenHandler handler;
	@Final @Shadow protected int titleX;
	@Final @Shadow protected int titleY;

	protected MixinHandledScreen(Text title){
		super(title);
		throw new RuntimeException(); // Java requires we provide a constructor because of the <T>, but it should never be called
	}

	private boolean handleAllowedInContainerKey(int keyCode, int scanCode, boolean isPressed){
		boolean hadKeyPress = false;
		for(Keybind kb : Keybind.allowedInInventory){
			if(kb.keybindInternal.matchesKey(keyCode, scanCode) && kb.allowInScreen.apply(this)){
				kb.onPressedSupplier.run();
				hadKeyPress = true;
			}
		}
		return hadKeyPress;
	}

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void handle_keyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir){
		if(handleAllowedInContainerKey(keyCode, scanCode, true)) cir.setReturnValue(true);
	}

	@Override public boolean keyReleased(int keyCode, int scanCode, int modifiers){
		if(handleAllowedInContainerKey(keyCode, scanCode, false)) return true;
		return super.keyReleased(keyCode, scanCode, modifiers);
	}


	//------------------- Container Title Rendering
	private final boolean isNotInCurrentGroup(MapState state){
		return MapGroupUtils.shouldHighlightNotInCurrentGroup(state);
	}
	private final boolean isUnlocked(MapState state){
		return !state.locked;
	}
	private final boolean isInInv(MapState state){
		return !MapRelationUtils.isFillerMap(state) && InventoryHighlightUpdater.isInInventory(MapGroupUtils.getIdForMapState(state));
	}
	private final boolean isOnDisplay(MapState state){
		return !MapRelationUtils.isFillerMap(state) && ItemFrameHighlightUpdater.isInItemFrame(MapGroupUtils.getIdForMapState(state));
	}
	private final boolean isNotOnDisplay(MapState state){
		return !MapRelationUtils.isFillerMap(state) && !ItemFrameHighlightUpdater.isInItemFrame(MapGroupUtils.getIdForMapState(state));
	}
	private final boolean isUnnamed(ItemStack item){
		return item.getCustomName() == null;
	}

	private static final Stream<ItemStack> getAllNestedItems(Stream<ItemStack> items){
		return items.flatMap(s -> {
			ContainerComponent container = s.get(DataComponentTypes.CONTAINER);
			return container == null ? Stream.of(s) : getAllNestedItems(container.streamNonEmpty());
		});
	}

	private static void addAsterisk(MutableText text, int color){text.append(Text.literal("*").withColor(color).formatted(Formatting.BOLD));}

	@Inject(method="drawForeground", at=@At("TAIL"))
	public void mixinFor_drawForeground_overwriteInvTitle(DrawContext context, int mouseX, int mouseY, CallbackInfo ci){
		MutableText title = getTitle().copy();
		List<ItemStack> items = getAllNestedItems(IntStream.range(0, handler.slots.size()-36)
				.mapToObj(i -> handler.slots.get(i)).map(Slot::getStack))
				.filter(s -> s.getItem() == Items.FILLED_MAP).toList();
		List<MapState> states = items.stream().map(i -> FilledMapItem.getMapState(i, client.world)).filter(Objects::nonNull).toList();

		if(states.stream().anyMatch(this::isInInv)) addAsterisk(title, Main.MAP_COLOR_IN_INV);
		if(states.stream().anyMatch(this::isNotInCurrentGroup)) addAsterisk(title, Main.MAP_COLOR_NOT_IN_GROUP);
		if(states.stream().anyMatch(this::isUnlocked)) addAsterisk(title, Main.MAP_COLOR_UNLOCKED);
		if(states.stream().anyMatch(this::isOnDisplay) && states.stream().anyMatch(this::isNotOnDisplay)) addAsterisk(title, Main.MAP_COLOR_IN_IFRAME);
		if(items.stream().anyMatch(this::isUnnamed)) title.append(Text.literal("*").withColor(Main.MAP_COLOR_UNNAMED));
		// Check for duplicates within this container
		if(!states.stream()/*.filter(Predicate.not(this::isInInv)).map(MapState::hashCode)*/.allMatch(new HashSet<>(states.size())::add))
			addAsterisk(title, Main.MAP_COLOR_MULTI_INV);

		if(!title.getString().equals(getTitle().getString())) context.drawText(this.textRenderer, title, titleX, titleY, 4210752, false);
	}
}