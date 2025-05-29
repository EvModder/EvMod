package net.evmodder.KeyBound.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.evmodder.KeyBound.Main;
import net.evmodder.KeyBound.MapGroupUtils;
import net.evmodder.KeyBound.EventListeners.TooltipMapNameColor;
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
	private static long lastNotif;

	private boolean isLookngInGeneralDirection(Entity entity){
		Vec3d vec3d = client.player.getRotationVec(1.0F).normalize();
		Vec3d vec3d2 = new Vec3d(entity.getX() - client.player.getX(), entity.getEyeY() - client.player.getEyeY(), entity.getZ() - client.player.getZ());
		double d = vec3d2.length();
		vec3d2 = new Vec3d(vec3d2.x / d, vec3d2.y / d, vec3d2.z / d);//normalize
		double e = vec3d.dotProduct(vec3d2);
		final double asdf = client.player.squaredDistanceTo(entity) > 5*5 ? 0.3d : 0.1d;
		return e > 1.0d - asdf / d;
	}

	@Inject(method = "hasLabel", at = @At("HEAD"), cancellable = true)
	public void hasLabel_Mixin(T itemFrameEntity, double squaredDistanceToCamera, CallbackInfoReturnable<Boolean> cir){
		if(!Main.mapColorIFrame && !Main.notifyIfNotInGroup) return; // Features are disabled
		ItemStack stack = itemFrameEntity.getHeldItemStack();
		if(stack == null || stack.isEmpty()) return;
		MapIdComponent mapId = itemFrameEntity.getHeldItemStack().get(DataComponentTypes.MAP_ID);
		if(mapId == null) return;
		MapState state = itemFrameEntity.getWorld().getMapState(mapId);
		if(state == null) return;
		final boolean newMapart = MapGroupUtils.isMapNotInCurrentGroup(state);
		if(Main.notifyIfNotInGroup && newMapart){
			final long now = System.currentTimeMillis();
			if(now - lastNotif > 20*1000){
				lastNotif = now;
				client.player.sendMessage(Text.literal("New mapart detected! % "
						+(itemFrameEntity.getBlockX()%100)+" "+(itemFrameEntity.getBlockZ()%100)), true);
			}
		}

		if(!Main.mapColorIFrame) return; // Feature is disabled
		if(!MinecraftClient.isHudEnabled()) return;
		if(squaredDistanceToCamera > 20*20) return;

		if(stack.getCustomName() == null || !state.locked || newMapart){
			if(!newMapart && !client.player.canSee(itemFrameEntity)) return; // Skip if player doesn't have LOS to the map (unless uncollected)
			if(!isLookngInGeneralDirection(itemFrameEntity)) return;
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "getDisplayName", at = @At("INVOKE"), cancellable = true)
	public void getDisplayName_Mixin(T itemFrameEntity, CallbackInfoReturnable<Text> cir){
		if(!Main.mapColorIFrame) return; // Feature is disabled
		ItemStack stack = itemFrameEntity.getHeldItemStack();
		if(stack == null || stack.isEmpty() || !Registries.ITEM.getId(stack.getItem()).getPath().equals("filled_map")) return;
		MapState state = FilledMapItem.getMapState(stack, itemFrameEntity.getWorld());
		if(state == null) return;
		if(MapGroupUtils.isMapNotInCurrentGroup(state)) cir.setReturnValue(stack.getName().copy().withColor(TooltipMapNameColor.UNCOLLECTED_COLOR));
		else if(!state.locked) cir.setReturnValue(stack.getName().copy().withColor(TooltipMapNameColor.UNLOCKED_COLOR));
		else if(stack.getCustomName() == null) cir.setReturnValue(stack.getName().copy().withColor(TooltipMapNameColor.UNNAMED_COLOR));
	}
}