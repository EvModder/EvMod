package net.evmodder.KeyBound.mixin;

import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

//Author: fzzyhmstrs
@Mixin(KeyBinding.class)
interface AccessorTimesPressed {
	@Accessor(value="timesPressed")
	int getTimesPressed();
	@Accessor(value="timesPressed")
	void setTimesPressed(int value);
}