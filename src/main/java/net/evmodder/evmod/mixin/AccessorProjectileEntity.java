package net.evmodder.evmod.mixin;

import net.minecraft.entity.projectile.ProjectileEntity;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ProjectileEntity.class)
public interface AccessorProjectileEntity{
	@Accessor(value="ownerUuid") UUID getOwnerUUID();
	@Accessor(value="ownerUuid") void setOwnerUUID(UUID uuid);
}