package net.evmodder.evmod.mixin;

import net.evmodder.evmod.Configs;
import net.evmodder.evmod.apis.MapColorUtils;
import net.evmodder.evmod.onTick.UpdateContainerHighlights;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.BannerItem;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.map.MapState;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HandledScreen.class)
abstract class MixinHandledScreen<T extends ScreenHandler> extends Screen{
	@Shadow @Final private final T handler;
	@Shadow @Final private final int titleX;
	@Shadow @Final private final int titleY;

	// Java requires we provide a constructor because of the <T>, but it'll never be called
	private MixinHandledScreen(Text title){
		super(title);
		throw new RuntimeException("EvMod: unreachable (cnstr of MixinHandledScreen)");
	}

	@Inject(method="drawForeground", at=@At("TAIL"))
	private final void replaceScreenTitleForCurrentContainer(DrawContext context, int _mouseX, int _mouseY, CallbackInfo _ci){
		if(UpdateContainerHighlights.customTitle == null) return;
		context.drawText(textRenderer, UpdateContainerHighlights.customTitle, titleX, titleY, 4210752, false);
	}

	// Credit to Enderkill for the idea:
	// https://github.com/EnderKill98/EnderSpecimina/blob/main/src/main/java/me/enderkill98/enderspecimina/mixin/FixGhostItemsMixin.java
	// MIT License, so I borrowed his logic.
	@Inject(method="mouseDragged", at=@At("HEAD"), cancellable=true)
	private final void disableMouseDragForBundlesAndMapsSinceItIsBuggyOn2b2t(CallbackInfoReturnable<Boolean> cir){
//		if(MiscUtils.getCurrentServerAddressHashCode() != MiscUtils.HASHCODE_2B2T) return;
		if(!Configs.Generic.DISABLE_DRAG_CLICK_ON_MAPS_AND_BUNDLES.getBooleanValue()) return;

		if(client.player == null || client.player.isCreative()) return; // Breaks creative middle-click drag (on other servers)
		final ItemStack cursorStack = handler.getCursorStack();
		if(cursorStack == null || cursorStack.isEmpty()) return;
		if(!cursorStack.isStackable() || cursorStack.getItem() instanceof BannerItem) cir.setReturnValue(true); // Prevent initiating a drag
		final MapState state = FilledMapItem.getMapState(cursorStack, client.world);
		if(state != null && !MapColorUtils.isMonoColor(state.colors)) cir.setReturnValue(true); // Prevent initiating a drag
	}
}