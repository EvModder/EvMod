package net.evmodder.evmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import net.evmodder.evmod.apis.MapGroupUtils;

// Used by MixinClientPacketListenerMaps
@Mixin(value=MapGroupUtils.class, remap=false)
interface AccessorMapGroupUtils{
	@Invoker("markMapLoaded") static boolean callMarkMapLoaded(int id){throw new AssertionError();}
}