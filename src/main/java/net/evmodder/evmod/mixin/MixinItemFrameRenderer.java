package net.evmodder.evmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.apis.MapColorUtils;
import net.evmodder.evmod.apis.MapGroupUtils;
import net.evmodder.evmod.apis.MapStateCacher;
import net.evmodder.evmod.config.OptionInvisIframes;
import net.evmodder.evmod.onTick.UpdateItemFrameContents;
import net.evmodder.evmod.onTick.UpdateItemFrameContents.Highlight;
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
abstract class MixinItemFrameRenderer<T extends ItemFrameEntity>{
	private final MinecraftClient client = MinecraftClient.getInstance();

	private final boolean isSemiTransparent(final MapState state){
		return state != null && state.colors != null && MapColorUtils.isSemiTransparent(state.colors);
	}
	private final boolean shouldBeInvis(final ItemFrameEntityRenderState ifers){
		switch((OptionInvisIframes)Configs.Visuals.INVIS_IFRAMES.getOptionListValue()){
			case ANY_ITEM: return !ifers.itemRenderState.isEmpty();
			case MAPART: return ifers.mapId != null;
			case SEMI_TRANSPARENT_MAPART: return ifers.mapId != null && isSemiTransparent(client.world.getMapState(ifers.mapId));
			case OFF:
			default:
				return false;
		}
	}

	private static final boolean isLookingInGeneralDirection(final Entity entity){ // Accessor: MixinItemFrameRenderer
//		Entity player = MinecraftClient.getInstance().player;
//		Vec3d vec3d2 = new Vec3d(entity.getX() - player.getX(), entity.getEyeY() - player.getEyeY(), entity.getZ() - player.getZ());
		// maybe use entity.getCenterPos() instead?
		Vec3d vec3d2 = entity.getEyePos().subtract(MinecraftClient.getInstance().player.getEyePos());
		double d = vec3d2.length(); // Calls Math.sqrt()
		vec3d2 = new Vec3d(vec3d2.x / d, vec3d2.y / d, vec3d2.z / d); // normalize
		double e = AccessorUpdateItemFrameContents.clientRotationNormalized().dotProduct(vec3d2);
//		final double asdf = player.squaredDistanceTo(entity) > 5*5 ? 0.3d : 0.1d;
		final double asdf = vec3d2.lengthSquared() > 5*5 ? 0.3d : 0.1d;
		return e > 1.0d - asdf / d;
	}

	@Inject(method="render", at=@At("HEAD"))
	private final void disableItemFrameFrameRenderingWhenHoldingMaps(
			ItemFrameEntityRenderState ifers, MatrixStack _0, VertexConsumerProvider _1, int _2, CallbackInfo _3){
		ifers.invisible |= shouldBeInvis(ifers);
	}

	@Inject(method="hasLabel", at=@At("HEAD"), cancellable=true)
	private final void modifyHasLableBasedOnMapState(T itemFrameEntity, double squaredDistanceToCamera, CallbackInfoReturnable<Boolean> cir){
		if(!Configs.Visuals.MAP_HIGHLIGHT_IFRAME.getBooleanValue()) return; // Feature is disabled
		if(!MinecraftClient.isHudEnabled()) return;

		final ItemStack stack = itemFrameEntity.getHeldItemStack();
		if(stack.isEmpty()) return;
		final MapState state = FilledMapItem.getMapState(stack, itemFrameEntity.getWorld());
		if(state == null) return;
		final Highlight hl = AccessorUpdateItemFrameContents.highlightedIFrames().get(itemFrameEntity.getId());
		if(hl == null) return;

		final Boolean hasLabel = AccessorUpdateItemFrameContents.hasLabelCache().get(itemFrameEntity.getId());
		if(hasLabel != null){cir.setReturnValue(hasLabel); return;}

		if(hl == Highlight.MULTI_HUNG && Configs.Generic.SKIP_MONO_COLOR_MAPS.getBooleanValue() && MapColorUtils.isMonoColor(state.colors)){
			// no-op
		}
		else if(hl == Highlight.INV_OR_NESTED_INV){ // Show this label even if not looking in general direction
			cir.setReturnValue(true);
		}
		else if(!isLookingInGeneralDirection(itemFrameEntity)){
//			Main.LOGGER.info("not looking at: "+stack.getName().getString());
			// no-op
		}
		// If right up in front of iFrame, use the vanilla label check
		else if((squaredDistanceToCamera=client.player.squaredDistanceTo(itemFrameEntity)) <= 16d/*4*4*/ && stack.getCustomName() != null){
//			Main.LOGGER.info("right up in front: "+stack.getName().getString()+", squaredDistanceToCamera="+squaredDistanceToCamera);
			// no-op
			if(!state.locked) MapGroupUtils.getIdForMapState(state, /*evict*/true); // Evict cache for maps being directly looked at
		}
		else if(hl == Highlight.NOT_IN_CURR_GROUP){ // Show this label as long as dist >4
			cir.setReturnValue(true);
		}
		else if(squaredDistanceToCamera <= 400d/*20*20*/ && client.player.canSee(itemFrameEntity)){ // Show other labels only if LOS and dist <= 20
			cir.setReturnValue(true);
		}
		AccessorUpdateItemFrameContents.hasLabelCache().put(itemFrameEntity.getId(), cir.getReturnValue());
	}

	private final boolean isMultiHung(MapState state){return UpdateItemFrameContents.isHungMultiplePlaces(MapGroupUtils.getIdForMapState(state));}

	@Inject(method="getDisplayName", at=@At("INVOKE"), cancellable = true)
	public void getDisplayName_Mixin(T itemFrameEntity, CallbackInfoReturnable<Text> cir){
		if(!Configs.Visuals.MAP_HIGHLIGHT_IFRAME.getBooleanValue()) return; // Feature is disabled
		final ItemStack stack = itemFrameEntity.getHeldItemStack();
		if(stack == null || stack.isEmpty());
		final MapState state = FilledMapItem.getMapState(stack, itemFrameEntity.getWorld());
		if(state == null) return;
		final Highlight hl = AccessorUpdateItemFrameContents.highlightedIFrames().get(itemFrameEntity.getId());
		if(hl == null) return;

		final Text cachedName = AccessorUpdateItemFrameContents.displayNameCache().get(itemFrameEntity.getId());
		if(cachedName != null){cir.setReturnValue(cachedName); return;}

		if(Configs.Generic.MAP_CACHE_BY_NAME.getBooleanValue() && stack.getCustomName() != null)
			MapStateCacher.addMapStateByName(stack, state);

		final MutableText name = stack.getName().copy();
		if(hl == Highlight.INV_OR_NESTED_INV){
			final boolean notInCurrGroup = MapGroupUtils.shouldHighlightNotInCurrentGroup(state);
			name.withColor(Configs.Visuals.MAP_COLOR_IN_INV.getIntegerValue());
			if(notInCurrGroup) name.append(Text.literal("*").withColor(Configs.Visuals.MAP_COLOR_NOT_IN_GROUP.getIntegerValue()));
			if(!state.locked) name.append(Text.literal("*").withColor(Configs.Visuals.MAP_COLOR_UNLOCKED.getIntegerValue()));
			if(isMultiHung(state)) name.append(Text.literal("*").withColor(Configs.Visuals.MAP_COLOR_MULTI_IFRAME.getIntegerValue()));
		}
		else if(hl == Highlight.NOT_IN_CURR_GROUP){
			name.withColor(Configs.Visuals.MAP_COLOR_NOT_IN_GROUP.getIntegerValue());
			if(!state.locked) name.append(Text.literal("*").withColor(Configs.Visuals.MAP_COLOR_UNLOCKED.getIntegerValue()));
			if(isMultiHung(state)) name.append(Text.literal("*").withColor(Configs.Visuals.MAP_COLOR_MULTI_IFRAME.getIntegerValue()));
		}
		else if(!state.locked){
			name.withColor(Configs.Visuals.MAP_COLOR_UNLOCKED.getIntegerValue());
			if(isMultiHung(state)) name.append(Text.literal("*").withColor(Configs.Visuals.MAP_COLOR_MULTI_IFRAME.getIntegerValue()));
		}
		else if(stack.getCustomName() == null) name.withColor(Configs.Visuals.MAP_COLOR_UNNAMED.getIntegerValue());
		else if(hl == Highlight.MULTI_HUNG){
			if(Configs.Generic.SKIP_MONO_COLOR_MAPS.getBooleanValue() && MapColorUtils.isMonoColor(state.colors))
				name.append(Text.literal("*").withColor(Configs.Visuals.MAP_COLOR_MULTI_IFRAME.getIntegerValue()));
			else name.withColor(Configs.Visuals.MAP_COLOR_MULTI_IFRAME.getIntegerValue());
		}
		else{
			AccessorUpdateItemFrameContents.displayNameCache().put(itemFrameEntity.getId(), stack.getName());
			return;
		}
		AccessorUpdateItemFrameContents.displayNameCache().put(itemFrameEntity.getId(), name);
		cir.setReturnValue(name);
	}
}