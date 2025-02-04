package net.evmodder.KeyBound.Keybinds;

import net.evmodder.KeyBound.Main;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil.Type;

public abstract class AbstractKeybind extends KeyBinding{
	//public AbstractKeybind(String translationKey, Type type, int code, String category){super(translationKey, type, code, category);}

	private final Runnable onPressedSupplier, onReleasedSupplier;
	public AbstractKeybind(String translationKey, Runnable onPressed, Runnable onReleased){
		super("key."+Main.MOD_ID+"."+translationKey, Type.KEYSYM, -1, Main.KEYBIND_CATEGORY);
		onPressedSupplier = onPressed;
		onReleasedSupplier = onReleased;
	}
	public AbstractKeybind(String translationKey, Runnable onPressed){this(translationKey, onPressed, ()->{});}
	public AbstractKeybind(String translationKey){this(translationKey, ()->{}, ()->{});}

	public void onPressed(){onPressedSupplier.run();;}
	public void onReleased(){onReleasedSupplier.run();}

	@Override public void setPressed(boolean pressed){
		if(pressed != isPressed()){
			if(pressed) onPressed();
			else onReleased();
		}
		super.setPressed(pressed);
	}
}