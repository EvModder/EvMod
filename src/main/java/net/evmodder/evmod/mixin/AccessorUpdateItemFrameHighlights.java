package net.evmodder.evmod.mixin;

import java.util.HashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.evmodder.evmod.onTick.UpdateItemFrameHighlights;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

// Used by MixinItemFrameRenderer
@Mixin(value=UpdateItemFrameHighlights.class, remap=false)
interface AccessorUpdateItemFrameHighlights{
	@Accessor("hasLabelCache") static HashMap<ItemFrameEntity, Boolean> hasLabelCache(){throw new AssertionError();}
	@Accessor("displayNameCache") static HashMap<ItemFrameEntity, Text> displayNameCache(){throw new AssertionError();}
	@Accessor("clientRotationNormalized") static Vec3d clientRotationNormalized(){throw new AssertionError();}
}