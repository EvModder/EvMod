package net.evmodder.evmod.mixin;

import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.gui.widgets.WidgetListConfigOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value=WidgetListConfigOptions.class, remap=false)
interface AccessorWidgetListConfigOptions{
	@Accessor GuiConfigsBase getParent();
}