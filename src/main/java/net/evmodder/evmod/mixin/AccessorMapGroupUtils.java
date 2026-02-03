package net.evmodder.evmod.mixin;

import java.util.HashSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.evmodder.evmod.apis.MapGroupUtils;

// Used by MixinClientPlayNetworkHandler
@Mixin(value=MapGroupUtils.class, remap=false)
interface AccessorMapGroupUtils{
	@Accessor("loadedMapIds") static HashSet<Integer> loadedMapIds(){throw new AssertionError();}
	@Accessor("nullMapIds") static HashSet<Integer> nullMapIds(){throw new AssertionError();}
}