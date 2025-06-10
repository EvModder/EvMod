package net.evmodder.KeyBound.Keybinds;

import java.util.HashSet;
import java.util.function.Function;
import net.evmodder.KeyBound.Main;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil.Type;

public final class Keybind{
	public static HashSet<Keybind> allowedInInventory = new HashSet<>();
	public final Function<Screen, Boolean> allowInScreen;
	public final KeyBinding keybindInternal;
	//public AbstractKeybind(String translationKey, Type type, int code, String category){super(translationKey, type, code, category);}

	public final Runnable onPressedSupplier, onReleasedSupplier;
	public Keybind(String translationKey, Runnable onPressed, Runnable onReleased, Function<Screen, Boolean> allowInScreen){
		onPressedSupplier = onPressed;
		onReleasedSupplier = onReleased;
		this.allowInScreen = allowInScreen;
		if(allowInScreen != null) allowedInInventory.add(this);
		keybindInternal = new KeyBinding("key."+Main.MOD_ID+"."+translationKey, Type.KEYSYM, -1, Main.KEYBIND_CATEGORY);
		KeyBindingHelper.registerKeyBinding(keybindInternal);
		//Main.LOGGER.debug("Registered keybind: "+translationKey);
	}

	public Keybind(String translationKey, Runnable onPressed){this(translationKey, onPressed, ()->{}, null);}
	public Keybind(String translationKey){this(translationKey, ()->{}, ()->{}, null);}

	public Keybind(String translationKey, Runnable onPressed, Function<Screen, Boolean> allowInScreen){this(translationKey, onPressed, ()->{}, allowInScreen);}
	public Keybind(String translationKey, Function<Screen, Boolean> allowInScreen){this(translationKey, ()->{}, ()->{}, allowInScreen);}

//	public void onPressed(){
//		Main.LOGGER.info("Keybind pressed: "+getTranslationKey());
//		onPressedSupplier.run();
//	}
//	public void onReleased(){
//		Main.LOGGER.info("Keybind released: "+getTranslationKey());
//		onReleasedSupplier.run();
//	}

	public final void setPressed(boolean pressed){
		if(pressed != keybindInternal.isPressed()){
			if(pressed){
				//Main.LOGGER.info("Keybind pressed: "+getTranslationKey());
				onPressedSupplier.run();
			}
			else{
				//Main.LOGGER.info("Keybind released: "+getTranslationKey());
				onReleasedSupplier.run();
			}
		}
		keybindInternal.setPressed(pressed);
	}
}