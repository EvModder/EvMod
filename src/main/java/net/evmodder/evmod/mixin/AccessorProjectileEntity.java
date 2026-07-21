package net.evmodder.evmod.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Projectile.class)
public interface AccessorProjectileEntity{
	@Accessor("owner") void setOwnerReference(EntityReference<Entity> owner);
	@Accessor("owner") EntityReference<Entity> getOwnerReference();
}