package net.evmodder.KeyBound.mixin;

import net.evmodder.KeyBound.Keybinds.Keybind;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HandledScreen.class)
public abstract class MixinHandledScreen<T> extends Screen{
	protected MixinHandledScreen(Text title){
		super(title);
		throw new RuntimeException(); // Java requires we provide a constructor because of the <T>, but it should never be called
	}

	private boolean handleAllowedInContainerKey(int keyCode, int scanCode, boolean isPressed){
		//Main.LOGGER.info("handleAllowedInContainerKey: "+keyCode);
		//MinecraftClient client = MinecraftClient.getInstance();
		//GameOptions keys = client.options;
		return Keybind.allowedInInventory.stream().anyMatch(kb ->{
			if(kb.keybindInternal.matchesKey(keyCode, scanCode)){kb.onPressedSupplier.run(); return kb.allowInScreen.apply(this);}
			return false;
		});
	}

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void handle_keyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir){
		//Main.LOGGER.info("keyPressed");
		if(handleAllowedInContainerKey(keyCode, scanCode, true)) cir.setReturnValue(true);
	}

	@Override public boolean keyReleased(int keyCode, int scanCode, int modifiers){
		//Main.LOGGER.info("keyReleased");
		if(handleAllowedInContainerKey(keyCode, scanCode, false)) return true;
		return super.keyReleased(keyCode, scanCode, modifiers);
	}

//	//@Mixin(net.minecraft.client.gui.ParentElement.class)
//	@Inject(method = "keyReleased", at = @At("HEAD"), cancellable = true)
//	private void handle_keyReleased(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir){
//		//Main.LOGGER.info("keyReleased");
//		if(handleAllowedInContainerKey(keyCode, scanCode, false)) cir.setReturnValue(true);
//	}
}