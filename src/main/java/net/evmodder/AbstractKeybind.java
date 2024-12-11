package net.evmodder;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil.Type;

public abstract class AbstractKeybind extends KeyBinding{
	public AbstractKeybind(String translationKey, Type type, int code, String category){super(translationKey, type, code, category);}

	public void onPressed(){}
	public void onReleased(){}

	@Override public void setPressed(boolean pressed){
		if(pressed != isPressed()){
			if(pressed) onPressed();
			else onReleased();
		}
		super.setPressed(pressed);
	}
}