package net.evmodder.KeyBound.mixin;

import net.minecraft.entity.projectile.ProjectileEntity;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ProjectileEntity.class)
public interface ProjectileEntityAccessor{
	@Accessor(value="ownerUuid")
	UUID getOwnerUUID();
}