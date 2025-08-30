package net.evmodder.KeyBound.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.evmodder.KeyBound.Main;
import net.evmodder.KeyBound.MapColorUtils;
import net.evmodder.KeyBound.MapGroupUtils;
import net.evmodder.KeyBound.events.ItemFrameHighlightUpdater;
import net.evmodder.KeyBound.events.ItemFrameHighlightUpdater.Highlight;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.ItemFrameEntityRenderer;
import net.minecraft.client.render.entity.state.ItemFrameEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.map.MapState;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

@Mixin(ItemFrameEntityRenderer.class)
public class MixinItemFrameRenderer<T extends ItemFrameEntity>{
	private final MinecraftClient client = MinecraftClient.getInstance();

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
		if(stack.isEmpty()) return;
		final MapState state = FilledMapItem.getMapState(stack, itemFrameEntity.getWorld());
		if(state == null) return;
		Highlight hl = ItemFrameHighlightUpdater.iFrameGetHighlight(itemFrameEntity.getId());
		if(hl == null) return;
		if(hl == Highlight.MULTI_HUNG && Main.skipMonoColorMaps && MapColorUtils.isMonoColor(state.colors)) return; // Don't do boosted hasLabel()

		if(hl == Highlight.INV_OR_NESTED_INV){cir.setReturnValue(true); return;} // Show this label even if not looking in general direction
		if(!isLookngInGeneralDirection(itemFrameEntity)) return;
		if(hl == Highlight.NOT_IN_CURR_GROUP){cir.setReturnValue(true); return;} // Show this label even if no LOS and > 20 blocks away
		if(squaredDistanceToCamera > 20*20 || !client.player.canSee(itemFrameEntity)) return;
		cir.setReturnValue(true); // Show all other labels only if LOS and <= 20
	}


	@Inject(method = "getDisplayName", at = @At("INVOKE"), cancellable = true)
	public void getDisplayName_Mixin(T itemFrameEntity, CallbackInfoReturnable<Text> cir){
		if(!Main.mapHighlightIFrame) return; // Feature is disabled
		final ItemStack stack = itemFrameEntity.getHeldItemStack();
		if(stack == null || stack.isEmpty());
		final MapState state = FilledMapItem.getMapState(stack, itemFrameEntity.getWorld());
		if(FilledMapItem.getMapState(stack, itemFrameEntity.getWorld()) == null) return;
		Highlight hl = ItemFrameHighlightUpdater.iFrameGetHighlight(itemFrameEntity.getId());
		if(hl == null) return;

		if(hl == Highlight.INV_OR_NESTED_INV){
			final boolean notInCurrGroup = MapGroupUtils.shouldHighlightNotInCurrentGroup(state);
			MutableText coloredName = stack.getName().copy().withColor(Main.MAP_COLOR_IN_INV);
			if(notInCurrGroup) coloredName.append(Text.literal("*").withColor(Main.MAP_COLOR_NOT_IN_GROUP));
			if(!state.locked) coloredName.append(Text.literal("*").withColor(Main.MAP_COLOR_UNLOCKED));
			if(ItemFrameHighlightUpdater.isHungMultiplePlaces(MapGroupUtils.getIdForMapState(state)))
				coloredName.append(Text.literal("*").withColor(Main.MAP_COLOR_MULTI_IFRAME));
			cir.setReturnValue(coloredName);
		}
		else if(hl == Highlight.NOT_IN_CURR_GROUP){
			final MutableText coloredName = stack.getName().copy().withColor(Main.MAP_COLOR_NOT_IN_GROUP);
			cir.setReturnValue(state.locked ? coloredName : coloredName.append(Text.literal("*").withColor(Main.MAP_COLOR_UNLOCKED)));
		}
		else if(!state.locked) cir.setReturnValue(stack.getName().copy().withColor(Main.MAP_COLOR_UNLOCKED));
		else if(stack.getCustomName() == null) cir.setReturnValue(stack.getName().copy().withColor(Main.MAP_COLOR_UNNAMED));
		else if(hl == Highlight.MULTI_HUNG){
			if(Main.skipMonoColorMaps && MapColorUtils.isMonoColor(state.colors)){
				cir.setReturnValue(stack.getName().copy().append(Text.literal("*").withColor(Main.MAP_COLOR_MULTI_IFRAME)));
			}
			else cir.setReturnValue(stack.getName().copy().withColor(Main.MAP_COLOR_MULTI_IFRAME));
		}
	}

	private boolean isSemiTransparent(ItemFrameEntityRenderState ifers){
		MapState state = client.world.getMapState(ifers.mapId);
		if(state == null || state.colors == null) return false;
		boolean has0 = false, hasColor = false;
		for(byte b : state.colors){has0 |= (b==0); hasColor |= (b!=0);}
		return has0 && hasColor;
	}

	@Inject(method="render", at=@At("HEAD"))
	private void disableItemFrameFrameRenderingWhenHoldingMaps(ItemFrameEntityRenderState ifers, MatrixStack _0, VertexConsumerProvider _1, int _2, CallbackInfo _3) {
		ifers.invisible |= (Main.invisItemFramesWithMaps && ifers.mapId != null
				&& (!Main.invisItemFramesWithMapsSemiTransparentOnly || isSemiTransparent(ifers)));
	}
}