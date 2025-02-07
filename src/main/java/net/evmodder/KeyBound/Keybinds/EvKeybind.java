package net.evmodder.KeyBound.Keybinds;

import java.util.HashSet;
import net.evmodder.KeyBound.Main;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil.Type;

public final class EvKeybind extends KeyBinding{
	public static HashSet<EvKeybind> allowedInInventory = new HashSet<>();
	//public AbstractKeybind(String translationKey, Type type, int code, String category){super(translationKey, type, code, category);}

	public final Runnable onPressedSupplier, onReleasedSupplier;
	public EvKeybind(String translationKey, Runnable onPressed, Runnable onReleased, boolean allowInScreens){
		super("key."+Main.MOD_ID+"."+translationKey, Type.KEYSYM, -1, Main.KEYBIND_CATEGORY);
		onPressedSupplier = onPressed;
		onReleasedSupplier = onReleased;
		if(allowInScreens) allowedInInventory.add(this);
		Main.LOGGER.info("Registered keybind: "+translationKey);
	}

	public EvKeybind(String translationKey, Runnable onPressed){this(translationKey, onPressed, ()->{}, false);}
	public EvKeybind(String translationKey){this(translationKey, ()->{}, ()->{}, false);}

	public EvKeybind(String translationKey, Runnable onPressed, boolean allowInScreens){this(translationKey, onPressed, ()->{}, allowInScreens);}
	public EvKeybind(String translationKey, boolean allowInScreens){this(translationKey, ()->{}, ()->{}, allowInScreens);}

//	public void onPressed(){
//		Main.LOGGER.info("Keybind pressed: "+getTranslationKey());
//		onPressedSupplier.run();
//	}
//	public void onReleased(){
//		Main.LOGGER.info("Keybind released: "+getTranslationKey());
//		onReleasedSupplier.run();
//	}

	@Override public final void setPressed(boolean pressed){
		if(pressed != isPressed()){
			if(pressed){
				Main.LOGGER.info("Keybind pressed: "+getTranslationKey());
				onPressedSupplier.run();
			}
			else{
				Main.LOGGER.info("Keybind released: "+getTranslationKey());
				onReleasedSupplier.run();
			}
		}
		super.setPressed(pressed);
	}
}