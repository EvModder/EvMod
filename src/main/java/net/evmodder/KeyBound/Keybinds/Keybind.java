package net.evmodder.KeyBound.Keybinds;

import java.util.HashSet;
import java.util.function.Function;
import net.evmodder.KeyBound.Main;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil.Type;

public final class Keybind{
	public final static HashSet<Keybind> inventoryKeybinds = new HashSet<>();
	public final static HashSet<Keybind> allKeybinds = new HashSet<>();
	public final Function<Screen, Boolean> allowInScreen;
	public final KeyBinding internalKeyBinding;
	//public AbstractKeybind(String translationKey, Type type, int code, String category){super(translationKey, type, code, category);}

	final int ERROR_COLOR = 16733525, SUCCESS_COLOR = 5635925;

	public final Runnable onPressedSupplier, onReleasedSupplier;
	public Keybind(String translationKey, Runnable onPressed, Runnable onReleased, Function<Screen, Boolean> allowInScreen, int defaultKey){
		onPressedSupplier = onPressed;
		onReleasedSupplier = onReleased;
		this.allowInScreen = allowInScreen;
		allKeybinds.add(this);
		if(allowInScreen != null) inventoryKeybinds.add(this);
		internalKeyBinding = new KeyBinding("key."+Main.MOD_ID+"."+translationKey, Type.KEYSYM, defaultKey, Main.KEYBIND_CATEGORY);
		KeyBindingHelper.registerKeyBinding(internalKeyBinding);
		//Main.LOGGER.debug("Registered keybind: "+translationKey);
	}

	public Keybind(String translationKey, Runnable onPressed){this(translationKey, onPressed, ()->{}, null, -1);}
	public Keybind(String translationKey){this(translationKey, ()->{}, ()->{}, null, -1);}

	public Keybind(String translationKey, Runnable onPressed, Function<Screen, Boolean> allowInScreen, int defaultKey){this(translationKey, onPressed, ()->{}, allowInScreen, defaultKey);}
	public Keybind(String translationKey, Runnable onPressed, Function<Screen, Boolean> allowInScreen){this(translationKey, onPressed, ()->{}, allowInScreen, -1);}
	public Keybind(String translationKey, Function<Screen, Boolean> allowInScreen){this(translationKey, ()->{}, ()->{}, allowInScreen, -1);}

//	public void onPressed(){
//		Main.LOGGER.info("Keybind pressed: "+getTranslationKey());
//		onPressedSupplier.run();
//	}
//	public void onReleased(){
//		Main.LOGGER.info("Keybind released: "+getTranslationKey());
//		onReleasedSupplier.run();
//	}

	public final void setPresssed(boolean pressed){
		if(pressed != internalKeyBinding.isPressed()){
			if(pressed){
//				Main.LOGGER.info("EvKeybind pressed: "+internalKeyBinding.getTranslationKey());
				onPressedSupplier.run();
			}
			else{
//				Main.LOGGER.info("EvKeybind released: "+internalKeyBinding.getTranslationKey());
				onReleasedSupplier.run();
			}
		}
		//internalKeyBinding.setPressed(pressed);
	}
}