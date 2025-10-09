package net.evmodder.KeyBound.mixin;

import net.evmodder.KeyBound.keybinds.Keybind;
import net.evmodder.KeyBound.onTick.UpdateContainerHighlights;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
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
		for(Keybind kb : Keybind.inventoryKeybinds){
			if(kb.internalKeyBinding.matchesKey(keyCode, scanCode) && kb.allowInScreen.apply(this)){
				if(kb.internalKeyBinding.isPressed() == isPressed){
//					Main.LOGGER.info("MixinHandledScreen: kb stuff is SUPER JANK");
					isPressed = !isPressed;
				}
//				Main.LOGGER.info("evKb pressed:"+isPressed);
				kb.setPresssed(isPressed);
				kb.internalKeyBinding.setPressed(isPressed);
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

	@Inject(method="drawForeground", at=@At("TAIL"))
	public void mixinFor_drawForeground_overwriteInvTitle(DrawContext context, int mouseX, int mouseY, CallbackInfo ci){
		if(UpdateContainerHighlights.customTitle == null) return;
		context.drawText(this.textRenderer, UpdateContainerHighlights.customTitle, titleX, titleY, 4210752, false);
	}
}