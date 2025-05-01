package net.evmodder.KeyBound.mixin;

import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.evmodder.KeyBound.LockedMapTooltip;
import net.evmodder.KeyBound.Main;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.ItemFrameEntityRenderer;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.map.MapState;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

@Mixin(ItemFrameEntityRenderer.class)
class MixinItemFrameRenderer<T extends ItemFrameEntity>{
	private static final MinecraftClient client = MinecraftClient.getInstance();

	private boolean isLookngInGeneralDirection(Entity entity){
		Vec3d vec3d = client.player.getRotationVec(1.0F).normalize();
		Vec3d vec3d2 = new Vec3d(entity.getX() - client.player.getX(), entity.getEyeY() - client.player.getEyeY(), entity.getZ() - client.player.getZ());
		double d = vec3d2.length();
		vec3d2 = new Vec3d(vec3d2.x / d, vec3d2.y / d, vec3d2.z / d);//normalize
		double e = vec3d.dotProduct(vec3d2);
		return e > 1.0d - 0.3d / d ? /*client.player.canSee(entity)*/true : false;
	}

	@Inject(method = "hasLabel", at = @At("HEAD"), cancellable = true)
	public void yupyupyup(T itemFrameEntity, double squaredDistanceToCamera, CallbackInfoReturnable<Boolean> cir){
		if(!Main.mapColorIFrame) return; // Feature is disabled
		if(squaredDistanceToCamera > 20*20) return;
		ItemStack stack = itemFrameEntity.getHeldItemStack();
		if(stack == null || stack.isEmpty()) return;
		MapIdComponent mapId = itemFrameEntity.getHeldItemStack().get(DataComponentTypes.MAP_ID);
		if(mapId == null) return;
		MapState state = itemFrameEntity.getWorld().getMapState(mapId);
		if(state == null) return;
		if(stack.getCustomName() == null || !state.locked ||
			(Main.mapsInGroup != null && !Main.mapsInGroup.contains(UUID.nameUUIDFromBytes(state.colors)))
		){
			if(!isLookngInGeneralDirection(itemFrameEntity)) return;
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "getDisplayName", at = @At("INVOKE"), cancellable = true)
	public void yupyupyup(T itemFrameEntity, CallbackInfoReturnable<Text> cir){
		if(!Main.mapColorIFrame) return; // Feature is disabled
		ItemStack stack = itemFrameEntity.getHeldItemStack();
		if(stack == null || stack.isEmpty() || !Registries.ITEM.getId(stack.getItem()).getPath().equals("filled_map")) return;
		MapState state = FilledMapItem.getMapState(stack, itemFrameEntity.getWorld());
		if(state == null) return;
		if(Main.mapsInGroup != null && !Main.mapsInGroup.contains(UUID.nameUUIDFromBytes(state.colors))){
			cir.setReturnValue(stack.getName().copy().withColor(LockedMapTooltip.UNCOLLECTED_COLOR));
		}
		else if(!state.locked) cir.setReturnValue(stack.getName().copy().withColor(LockedMapTooltip.UNLOCKED_COLOR));
		else if(stack.getCustomName() == null) cir.setReturnValue(stack.getName().copy().withColor(LockedMapTooltip.UNNAMED_COLOR));
	}
}