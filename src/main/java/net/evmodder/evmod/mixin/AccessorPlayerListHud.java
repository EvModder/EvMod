package net.evmodder.evmod.mixin;

import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.client.gui.hud.PlayerListHud;

@Mixin(PlayerListHud.class)
public interface AccessorPlayerListHud{
//	@Accessor("header") Text getHeader();
	@Accessor("footer") Text getFooter();
}