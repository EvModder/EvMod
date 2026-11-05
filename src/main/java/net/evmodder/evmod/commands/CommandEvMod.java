package net.evmodder.evmod.commands;

import fi.dy.masa.malilib.gui.GuiBase;
import java.util.function.Supplier;
import net.minecraft.client.gui.screens.Screen;
import net.evmodder.evmod.Main;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;

public final class CommandEvMod{
	public CommandEvMod(final Supplier<Screen> configScreen){
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, _) -> dispatcher.register(
			ClientCommands.literal(Main.MOD_ID).executes(ctx -> {
				// Open after chat finishes closing, so it cannot immediately close the config GUI.
				ctx.getSource().getClient().schedule(()->GuiBase.openGui(configScreen.get()));
				return 1;
			})
		));
	}
}