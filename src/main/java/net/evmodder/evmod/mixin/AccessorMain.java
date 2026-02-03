package net.evmodder.evmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.evmodder.evmod.Main;

// Used by MixinClientPlayNetworkHandler, MixinEntityRenderer, MixinClientPlayerInteractionManager
@Mixin(value=Main.class, remap=false)
interface AccessorMain{
	@Accessor("instance") static Main getInstance(){throw new AssertionError();}
}