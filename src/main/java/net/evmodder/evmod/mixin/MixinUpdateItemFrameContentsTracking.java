package net.evmodder.evmod.mixin;

import java.util.HashMap;
import java.util.List;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.evmodder.evmod.apis.MiscUtils;
import net.evmodder.evmod.onTick.UpdateItemFrameContents;
import net.evmodder.evmod.onTick.UpdateItemFrameContents.Highlight;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Highlight work shared by frame rendering and new-mapart notifications. */
@Mixin(value=UpdateItemFrameContents.class, remap=false)
abstract class MixinUpdateItemFrameContentsTracking{
	@Shadow private static HashMap<Integer, Highlight> highlightedIFrames;
	@Shadow private static HashMap<Integer, Boolean> hasLabelCache;
	@Shadow private static HashMap<Integer, Component> displayNameCache;
	@Shadow private static Vec3 clientRotationNormalized;
	@Shadow private static boolean updateIframeHighlights(List<ItemFrame> frames){throw new AssertionError();}

	@Inject(method="<init>", at=@At("TAIL"), allow=1)
	private void registerRenderCacheCleanup(CallbackInfo _ci){
		ClientEntityEvents.ENTITY_UNLOAD.register((entity, _) -> {
			if(!(entity instanceof ItemFrame)) return;
			highlightedIFrames.remove(entity.getId());
			hasLabelCache.remove(entity.getId());
			displayNameCache.remove(entity.getId());
		});
	}
	@Inject(method="onTickStart", at=@At("HEAD"), allow=1)
	private void updateViewDirection(Minecraft client, CallbackInfo _ci){
		if(client.level == null) return;
		final Vec3 rotation = client.player.getViewVector(1.0F).normalize();
		if(!rotation.equals(clientRotationNormalized) || MiscUtils.hasMoved(client.player)){
			clientRotationNormalized = rotation;
			hasLabelCache.clear();  // Depends on client looking direction
		}
	}
	@Inject(method="onTickStart", at=@At("TAIL"), allow=1)
	private void updateRenderCaches(Minecraft _client, CallbackInfo _ci, @Local List<ItemFrame> frames){
		if(!updateIframeHighlights(frames)) return;
//		Main.LOGGER.info("IframeHighlighter: Recomputing hasLabel/getDisplayName cache");
		hasLabelCache.clear();
		displayNameCache.clear();
	}
	@ModifyExpressionValue(method="scanIFrameContents", at=@At(value="INVOKE",
			target="Ljava/util/HashMap;remove(Ljava/lang/Object;)Ljava/lang/Object;", ordinal=0), allow=1)
	private static Object removeFrameHighlight(Object oldColorsId, @Local ItemFrame frame){
		if(oldColorsId != null) highlightedIFrames.remove(frame.getId());  // IFHU: Removed map at xyzd
		return oldColorsId;
	}
}