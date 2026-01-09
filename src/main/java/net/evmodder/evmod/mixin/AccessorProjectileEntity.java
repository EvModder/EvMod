package net.evmodder.evmod.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LazyEntityReference;
import net.minecraft.entity.projectile.ProjectileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ProjectileEntity.class)
public interface AccessorProjectileEntity{
	@Accessor(value="owner") LazyEntityReference<Entity> getOwner();
	@Accessor(value="owner") void setOwnerUUID(LazyEntityReference<Entity> owner);
}