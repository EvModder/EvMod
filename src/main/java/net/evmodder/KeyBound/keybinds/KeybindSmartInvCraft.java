package net.evmodder.KeyBound.keybinds;

import net.minecraft.screen.PlayerScreenHandler;

public final class KeybindSmartInvCraft{
	private void craftSomeItem(){
	}

	public KeybindSmartInvCraft(){
		new Keybind("smart_inventory_craft", this::craftSomeItem, PlayerScreenHandler.class::isInstance);
	}
}