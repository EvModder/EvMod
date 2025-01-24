package net.evmodder.KeyBound.mixin;

import net.evmodder.KeyBound.JunkItemEjector;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin<T> extends Screen{
	@Shadow public abstract boolean keyPressed(int keyCode, int scanCode, int modifiers);

	protected HandledScreenMixin(Text title){
		super(title);
		throw new RuntimeException();
	}

	private boolean handleAllowedInContainerKey(int keyCode, int scanCode, boolean isPressed){
		//Main.LOGGER.info("handleAllowedInContainerKey() called, keyCode: "+keyCode);
		//MinecraftClient client = MinecraftClient.getInstance();
		//GameOptions keys = client.options;
		boolean keyHandled = false;
		if(JunkItemEjector.kb != null && JunkItemEjector.kb.matchesKey(keyCode, scanCode)) {
			JunkItemEjector.kb.setPressed(isPressed);
			keyHandled = true;
		}
		return keyHandled;
	}

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void BLAH_BLAH_BLAH_KEY_PRESSED_KeyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir){
		if(handleAllowedInContainerKey(keyCode, scanCode, true)) cir.setReturnValue(true);
	}

	@Override public boolean keyReleased(int keyCode, int scanCode, int modifiers){
		if(handleAllowedInContainerKey(keyCode, scanCode, false)) return true;
		return super.keyReleased(keyCode, scanCode, modifiers);
	}
}