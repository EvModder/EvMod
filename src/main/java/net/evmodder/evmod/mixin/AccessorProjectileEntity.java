package net.evmodder.evmod.mixin;

//? >=1.21.11 {
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
//?} else {
/*import java.util.UUID;*/
//?}
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Projectile.class)
public interface AccessorProjectileEntity{
	//? >=1.21.11 {
	@Accessor("owner") void setOwnerReference(EntityReference<Entity> owner);
	@Accessor("owner") EntityReference<Entity> getOwnerReference();
	//?} else {
	/*@Accessor("ownerUUID") UUID getOwnerUUID();
	@Accessor("ownerUUID") void setOwnerUUID(UUID uuid);*/
	//?}
}