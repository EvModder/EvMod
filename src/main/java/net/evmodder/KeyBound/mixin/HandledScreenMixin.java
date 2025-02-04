package net.evmodder.KeyBound.mixin;

import net.evmodder.KeyBound.Keybinds.KeybindEjectJunk;
import net.evmodder.KeyBound.Keybinds.KeybindMapLoad;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin<T> extends Screen{
	protected HandledScreenMixin(Text title){
		super(title);
		throw new RuntimeException(); // Java requires we provide a constructor because of the <T>, but it should never be called
	}

	private boolean handleAllowedInContainerKey(int keyCode, int scanCode, boolean isPressed){
		//Main.LOGGER.info("handleAllowedInContainerKey: "+keyCode);
		//MinecraftClient client = MinecraftClient.getInstance();
		//GameOptions keys = client.options;
		boolean keyHandled = false;
		if(KeybindEjectJunk.kb != null && KeybindEjectJunk.kb.matchesKey(keyCode, scanCode)){
			//Main.LOGGER.info("JunkItemEjector key while in a container! isPressed:"+isPressed);
			//JunkItemEjector.kb.setPressed(isPressed);
			KeybindEjectJunk.kb.onPressed();
			keyHandled = true;
		}
		if(KeybindMapLoad.kbLoad != null && KeybindMapLoad.kbLoad.matchesKey(keyCode, scanCode)){
			KeybindMapLoad.kbLoad.onPressed();
			keyHandled = true;
		}
		if(KeybindMapLoad.kbCopy != null && KeybindMapLoad.kbCopy.matchesKey(keyCode, scanCode)){
			KeybindMapLoad.kbCopy.onPressed();
			keyHandled = true;
		}
		if(KeybindMapLoad.kbCopyBulk != null && KeybindMapLoad.kbCopyBulk.matchesKey(keyCode, scanCode)){
			KeybindMapLoad.kbCopyBulk.onPressed();
			keyHandled = true;
		}
		return keyHandled;
	}

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void AsSeenInFreeMoveMod_keyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir){
		//Main.LOGGER.info("keyPressed");
		if(handleAllowedInContainerKey(keyCode, scanCode, true)) cir.setReturnValue(true);
	}

	@Override public boolean keyReleased(int keyCode, int scanCode, int modifiers){
		//Main.LOGGER.info("keyReleased");
		if(handleAllowedInContainerKey(keyCode, scanCode, false)) return true;
		return super.keyReleased(keyCode, scanCode, modifiers);
	}
}