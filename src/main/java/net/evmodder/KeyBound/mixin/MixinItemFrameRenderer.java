package net.evmodder.KeyBound.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.evmodder.KeyBound.Configs;
import net.evmodder.KeyBound.apis.MapColorUtils;
import net.evmodder.KeyBound.apis.MapGroupUtils;
import net.evmodder.KeyBound.onTick.UpdateItemFrameHighlights;
import net.evmodder.KeyBound.onTick.UpdateItemFrameHighlights.Highlight;
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

	private boolean isLookingInGeneralDirection(Entity entity){
		Vec3d vec3d2 = new Vec3d(entity.getX() - client.player.getX(), entity.getEyeY() - client.player.getEyeY(), entity.getZ() - client.player.getZ());
		double d = vec3d2.length(); // Calls Math.sqrt()
		vec3d2 = new Vec3d(vec3d2.x / d, vec3d2.y / d, vec3d2.z / d);//normalize
		double e = UpdateItemFrameHighlights.clientRotationNormalized.dotProduct(vec3d2);
		final double asdf = client.player.squaredDistanceTo(entity) > 5*5 ? 0.3d : 0.1d;
		return e > 1.0d - asdf / d;
	}

	@Inject(method="hasLabel", at=@At("HEAD"), cancellable=true)
	public void hasLabel_Mixin(T itemFrameEntity, double squaredDistanceToCamera, CallbackInfoReturnable<Boolean> cir){
		if(!Configs.Visuals.MAP_HIGHLIGHT_IFRAME.getBooleanValue()) return; // Feature is disabled
		if(!MinecraftClient.isHudEnabled()) return;

		final ItemStack stack = itemFrameEntity.getHeldItemStack();
		if(stack.isEmpty()) return;
		final MapState state = FilledMapItem.getMapState(stack, itemFrameEntity.getWorld());
		if(state == null) return;
		Highlight hl = UpdateItemFrameHighlights.iFrameGetHighlight(itemFrameEntity.getId());
		if(hl == null) return;

		Boolean hasLabel = UpdateItemFrameHighlights.hasLabelCache.get(itemFrameEntity);
		if(hasLabel != null){cir.setReturnValue(hasLabel); return;}

		if(hl == Highlight.MULTI_HUNG && Configs.Generic.SKIP_MONO_COLOR_MAPS.getBooleanValue() && MapColorUtils.isMonoColor(state.colors)){
			UpdateItemFrameHighlights.hasLabelCache.put(itemFrameEntity, false);
			return; // Don't do boosted hasLabel()
		}

		if(hl == Highlight.INV_OR_NESTED_INV){ // Show this label even if not looking in general direction
			cir.setReturnValue(true);
			UpdateItemFrameHighlights.hasLabelCache.put(itemFrameEntity, true);
			return;
		}
		if(!isLookingInGeneralDirection(itemFrameEntity)){
			UpdateItemFrameHighlights.hasLabelCache.put(itemFrameEntity, false);
			return;
		}
		if(hl == Highlight.NOT_IN_CURR_GROUP){ // Show this label even if no LOS and > 20 blocks away
			cir.setReturnValue(true);
			UpdateItemFrameHighlights.hasLabelCache.put(itemFrameEntity, true);
			return;
		}
		if(squaredDistanceToCamera <= 20*20 && client.player.canSee(itemFrameEntity)){ // Show all other labels only if LOS and <= 20
			cir.setReturnValue(true);
			UpdateItemFrameHighlights.hasLabelCache.put(itemFrameEntity, true);
			if(!state.locked) MapGroupUtils.getIdForMapState(state, /*evictUnlocked*/true); // Evict cached state for maps the player is directly looking at
			return;
		}
	}


	@Inject(method="getDisplayName", at=@At("INVOKE"), cancellable = true)
	public void getDisplayName_Mixin(T itemFrameEntity, CallbackInfoReturnable<Text> cir){
		if(!Configs.Visuals.MAP_HIGHLIGHT_IFRAME.getBooleanValue()) return; // Feature is disabled
		final ItemStack stack = itemFrameEntity.getHeldItemStack();
		if(stack == null || stack.isEmpty());
		final MapState state = FilledMapItem.getMapState(stack, itemFrameEntity.getWorld());
		if(FilledMapItem.getMapState(stack, itemFrameEntity.getWorld()) == null) return;
		Highlight hl = UpdateItemFrameHighlights.iFrameGetHighlight(itemFrameEntity.getId());
		if(hl == null) return;

		Text cachedName = UpdateItemFrameHighlights.displayNameCache.get(itemFrameEntity);
		if(cachedName != null){cir.setReturnValue(cachedName); return;}

		MutableText name = stack.getName().copy();
		if(hl == Highlight.INV_OR_NESTED_INV){
			final boolean notInCurrGroup = MapGroupUtils.shouldHighlightNotInCurrentGroup(state);
			name.withColor(Configs.Visuals.MAP_COLOR_IN_INV.getIntegerValue());
			if(notInCurrGroup) name.append(Text.literal("*").withColor(Configs.Visuals.MAP_COLOR_NOT_IN_GROUP.getIntegerValue()));
			if(!state.locked) name.append(Text.literal("*").withColor(Configs.Visuals.MAP_COLOR_UNLOCKED.getIntegerValue()));
			if(UpdateItemFrameHighlights.isHungMultiplePlaces(MapGroupUtils.getIdForMapState(state)))
				name.append(Text.literal("*").withColor(Configs.Visuals.MAP_COLOR_MULTI_IFRAME.getIntegerValue()));
		}
		else if(hl == Highlight.NOT_IN_CURR_GROUP){
			name.withColor(Configs.Visuals.MAP_COLOR_NOT_IN_GROUP.getIntegerValue());
			if(!state.locked) name.append(Text.literal("*").withColor(Configs.Visuals.MAP_COLOR_UNLOCKED.getIntegerValue()));
		}
		else if(!state.locked) name.withColor(Configs.Visuals.MAP_COLOR_UNLOCKED.getIntegerValue());
		else if(stack.getCustomName() == null) name.withColor(Configs.Visuals.MAP_COLOR_UNNAMED.getIntegerValue());
		else if(hl == Highlight.MULTI_HUNG){
			if(Configs.Generic.SKIP_MONO_COLOR_MAPS.getBooleanValue() && MapColorUtils.isMonoColor(state.colors))
				name.append(Text.literal("*").withColor(Configs.Visuals.MAP_COLOR_MULTI_IFRAME.getIntegerValue()));
			else name.withColor(Configs.Visuals.MAP_COLOR_MULTI_IFRAME.getIntegerValue());
		}
		else{
			UpdateItemFrameHighlights.displayNameCache.put(itemFrameEntity, stack.getName());
			return;
		}
		UpdateItemFrameHighlights.displayNameCache.put(itemFrameEntity, name);
		cir.setReturnValue(name);
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
		ifers.invisible |= (Configs.Visuals.INVIS_IFRAMES.getBooleanValue() && ifers.mapId != null
				&& (!Configs.Visuals.INVIS_IFRAMES_SEMI_TRANSPARENT.getBooleanValue() || isSemiTransparent(ifers)));
	}
}