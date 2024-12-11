package net.evmodder.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.item.ItemStack;

@Mixin(InGameHud.class)
public interface InGameHudAccessor{
	@Accessor("currentStack") ItemStack getCurrentStack();
	@Accessor("heldItemTooltipFade") int getHeldItemTooltipFade();
}