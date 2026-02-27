package net.evmodder.evmod.mixin;

import java.util.HashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.evmodder.evmod.onTick.UpdateItemFrameContents;
import net.evmodder.evmod.onTick.UpdateItemFrameContents.Highlight;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

// Used by MixinItemFrameRenderer
@Mixin(value=UpdateItemFrameContents.class, remap=false)
interface AccessorUpdateItemFrameContents{
	@Accessor("highlightedIFrames") static HashMap<Integer, Highlight> highlightedIFrames(){throw new AssertionError();}
	@Accessor("hasLabelCache") static HashMap<Integer, Boolean> hasLabelCache(){throw new AssertionError();}
	@Accessor("displayNameCache") static HashMap<Integer, Text> displayNameCache(){throw new AssertionError();}
	@Accessor("clientRotationNormalized") static Vec3d clientRotationNormalized(){throw new AssertionError();}
}