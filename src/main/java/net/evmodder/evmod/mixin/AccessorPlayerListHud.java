package net.evmodder.evmod.mixin;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerTabOverlay.class)
public interface AccessorPlayerListHud{
//	@Accessor("header") Text getHeader();
	@Accessor("footer") Component getFooter();
}