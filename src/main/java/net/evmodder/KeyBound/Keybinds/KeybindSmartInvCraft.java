package net.evmodder.KeyBound.Keybinds;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.screen.PlayerScreenHandler;

public final class KeybindSmartInvCraft{
	private void craftSomeItem(){
	}

	public KeybindSmartInvCraft(){
		KeyBindingHelper.registerKeyBinding(new Keybind("smart_inventory_craft", this::craftSomeItem, PlayerScreenHandler.class::isInstance));
	}
}