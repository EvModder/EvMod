package net.evmodder.mixin;

import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

//Author: fzzyhmstrs
@Mixin(KeyBinding.class)
public interface TimesPressedAccessor {
	@Accessor(value="timesPressed")
	int getTimesPressed();
	@Accessor(value="timesPressed")
	void setTimesPressed(int value);
}