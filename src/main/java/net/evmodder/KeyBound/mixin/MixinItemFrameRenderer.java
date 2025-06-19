package net.evmodder.KeyBound.mixin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.evmodder.KeyBound.Main;
import net.evmodder.KeyBound.MapGroupUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.ItemFrameEntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.map.MapState;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;

@Mixin(ItemFrameEntityRenderer.class)
public class MixinItemFrameRenderer<T extends ItemFrameEntity>{
	private final MinecraftClient client = MinecraftClient.getInstance();
	private final HashMap<UUID, HashSet<Vec3i>> hangLocs = new HashMap<>();

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
		if(!Main.mapHighlightIFrame) return; // Feature is disabled
		if(!MinecraftClient.isHudEnabled()) return;
		final ItemStack stack = itemFrameEntity.getHeldItemStack();
		if(stack == null || stack.isEmpty()) return;
		final MapState state = FilledMapItem.getMapState(stack, itemFrameEntity.getWorld());
		if(state == null) return;

		if(MapGroupUtils.skipIFrameHasLabel.contains(itemFrameEntity.getId())) return;

		final UUID colorsId = MapGroupUtils.getIdForMapState(state);
		final HashSet<Vec3i> l = hangLocs.get(colorsId);
		final boolean isMultiHung;
		if(l == null){hangLocs.put(colorsId, new HashSet<>(List.of(itemFrameEntity.getBlockPos()))); isMultiHung = false;}
		else{l.add(itemFrameEntity.getBlockPos()); isMultiHung = l.size() > 1;}

		final boolean isInInv = MapGroupUtils.isInInventory(colorsId);
		final boolean isNotInCurrGroup = MapGroupUtils.shouldHighlightNotInCurrentGroup(state);
		if(isInInv || isNotInCurrGroup || stack.getCustomName() == null || !state.locked){
			//Skip if player doesn't have LOS or IF is far enough away
			if(!isInInv && !isNotInCurrGroup && !isMultiHung && (!client.player.canSee(itemFrameEntity) || squaredDistanceToCamera > 20*20)) return;
			if(!isInInv && !isLookngInGeneralDirection(itemFrameEntity)) return;
			cir.setReturnValue(true);
		}
		else MapGroupUtils.skipIFrameHasLabel.add(itemFrameEntity.getId());
	}

	private boolean isHungMultiplePlaces(UUID colorsId){
		final HashSet<Vec3i> l = hangLocs.get(colorsId);
		return l != null && l.size() > 1;
	}

	@Inject(method = "getDisplayName", at = @At("INVOKE"), cancellable = true)
	public void getDisplayName_Mixin(T itemFrameEntity, CallbackInfoReturnable<Text> cir){
		if(!Main.mapHighlightIFrame) return; // Feature is disabled
		final ItemStack stack = itemFrameEntity.getHeldItemStack();
		//Registries.ITEM.getId(stack.getItem()).getPath().equals("filled_map")
		if(stack == null || stack.isEmpty());
		final MapState state = FilledMapItem.getMapState(stack, itemFrameEntity.getWorld());
		if(state == null) return;
		final UUID colorsId = MapGroupUtils.getIdForMapState(state);
		final boolean notInCurrGroup = MapGroupUtils.shouldHighlightNotInCurrentGroup(state);
		if(MapGroupUtils.isInInventory(colorsId)){
			MutableText coloredName = stack.getName().copy().withColor(Main.MAP_COLOR_IN_INV);
			if(notInCurrGroup) coloredName = coloredName.append(Text.literal("*").withColor(Main.MAP_COLOR_NOT_IN_GROUP));
			if(!state.locked) coloredName = coloredName.append(Text.literal("*").withColor(Main.MAP_COLOR_UNLOCKED));
			cir.setReturnValue(coloredName);
		}
		else if(notInCurrGroup){
			final MutableText coloredName = stack.getName().copy().withColor(Main.MAP_COLOR_NOT_IN_GROUP);
			cir.setReturnValue(state.locked ? coloredName : coloredName.append(Text.literal("*").withColor(Main.MAP_COLOR_UNLOCKED)));
		}
		else if(!state.locked) cir.setReturnValue(stack.getName().copy().withColor(Main.MAP_COLOR_UNLOCKED));
		else if(stack.getCustomName() == null) cir.setReturnValue(stack.getName().copy().withColor(Main.MAP_COLOR_UNNAMED));
		else if(isHungMultiplePlaces(colorsId)) cir.setReturnValue(stack.getName().copy().withColor(Main.MAP_COLOR_MULTI_IFRAME));
	}
}