package net.evmodder.KeyBound.mixin;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Author: fzzyhmstrs
@Mixin(KeyBinding.class)
interface AccessorBoundKey{
	@Accessor(value="boundKey")
	InputUtil.Key getBoundKey();
}