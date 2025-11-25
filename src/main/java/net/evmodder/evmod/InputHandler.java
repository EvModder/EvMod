package net.evmodder.evmod;

import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.hotkeys.IKeybindManager;
import fi.dy.masa.malilib.hotkeys.IKeybindProvider;

public class InputHandler implements IKeybindProvider{
	private static final InputHandler INSTANCE = new InputHandler();

	public static InputHandler getInstance(){
		return INSTANCE;
	}

	private InputHandler(){}

	@Override public void addKeysToMap(IKeybindManager manager){
//		Configs.Hotkeys.getOptions().stream().filter(IHotkey.class::isInstance).map(IHotkey.class::cast)
//				.map(IHotkey::getKeybind).forEach(manager::addKeybindToMap);
		Configs.Hotkeys.getOptions().forEach(opt ->{if(opt instanceof IHotkey hotkey) manager.addKeybindToMap(hotkey.getKeybind());});
		Configs.Generic.getOptions().forEach(opt ->{if(opt instanceof IHotkey hotkey) manager.addKeybindToMap(hotkey.getKeybind());});
	}

	@Override public void addHotkeys(IKeybindManager manager){
		manager.addHotkeysForCategory(Main.MOD_NAME, Main.MOD_ID + ".hotkeys.category.hotkeys",
				Configs.Hotkeys.getOptions().stream().filter(IHotkey.class::isInstance).map(IHotkey.class::cast).toList());
		manager.addHotkeysForCategory(Main.MOD_NAME, Main.MOD_ID + ".hotkeys.category.misc_hotkeys",
				Configs.Generic.getOptions().stream().filter(IHotkey.class::isInstance).map(IHotkey.class::cast).toList());
	}
}