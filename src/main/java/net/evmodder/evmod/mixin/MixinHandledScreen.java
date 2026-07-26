package net.evmodder.evmod.mixin;

import net.evmodder.evmod.Configs;
import net.evmodder.evmod.apis.MapColorUtils;
import net.evmodder.evmod.onTick.UpdateContainerContents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
abstract class MixinHandledScreen<T extends AbstractContainerMenu> extends Screen{
	@Shadow @Final private final T menu;
	@Shadow @Final private final int titleLabelX;
	@Shadow @Final private final int titleLabelY;

	// Java requires we provide a constructor because of the <T>, but it'll never be called
	private MixinHandledScreen(Component title){
		super(title);
		throw new RuntimeException("EvMod: unreachable (cnstr of MixinHandledScreen)");
	}

	@Inject(method="extractLabels", at=@At("TAIL"))
	private final void replaceScreenTitleForCurrentContainer(GuiGraphicsExtractor context, int _mouseX, int _mouseY, CallbackInfo _ci){
		if(UpdateContainerContents.customTitle == null) return;
		context.text(font, UpdateContainerContents.customTitle, titleLabelX, titleLabelY, 0xFF404040, false);
	}

	// Credit to Enderkill for the idea:
	// https://github.com/EnderKill98/EnderSpecimina/blob/main/src/main/java/me/enderkill98/enderspecimina/mixin/FixGhostItemsMixin.java
	// MIT License, so I borrowed his logic.
	@Inject(method="mouseDragged", at=@At("HEAD"), cancellable=true)
	private final void disableMouseDragForBundlesAndMapsSinceItIsBuggyOn2b2t(CallbackInfoReturnable<Boolean> cir){
//		if(MiscUtils.getCurrentServerAddressHashCode() != MiscUtils.HASHCODE_2B2T) return;
		if(!Configs.Generic.DISABLE_DRAG_CLICK_ON_MAPS_AND_BUNDLES.getBooleanValue()) return;

		if(minecraft.player == null || minecraft.player.isCreative()) return; // Breaks creative middle-click drag (on other servers)
		final ItemStack cursorStack = menu.getCarried();
		if(cursorStack == null || cursorStack.isEmpty()) return;
		if(!cursorStack.isStackable() || cursorStack.getItem() instanceof BannerItem) cir.setReturnValue(true); // Prevent initiating a drag
		final MapItemSavedData state = MapItem.getSavedData(cursorStack, minecraft.level);
		if(state != null && !MapColorUtils.isMonoColor(state.colors)) cir.setReturnValue(true); // Prevent initiating a drag
	}
}