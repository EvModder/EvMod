package net.evmodder.KeyBound.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;

@Mixin(AnvilScreen.class)
public interface AccessorAnvilScreen{
	@Accessor("nameField") TextFieldWidget getNameField();
//	@Invoker("onRenamed") void onRenamed(String name);
}