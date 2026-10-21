package net.evmodder.evmod.mixin;

import java.util.UUID;
import com.llamalad7.mixinextras.sugar.Local;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.apis.NewMapNotifier;
import net.evmodder.evmod.onTick.UpdateItemFrameContents;
import net.minecraft.world.entity.decoration.ItemFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Reuse the existing out-of-group classification; no extra scan or disabled-feature checks. */
@Mixin(value=UpdateItemFrameContents.class, remap=false)
abstract class MixinUpdateItemFrameContentsNotifications{
	@Inject(method="updateIframeHighlights", at=@At(value="FIELD",
			target="Lnet/evmodder/evmod/onTick/UpdateItemFrameContents$Highlight;NOT_IN_CURR_GROUP:Lnet/evmodder/evmod/onTick/UpdateItemFrameContents$Highlight;"), allow=1)
	private static void notifyNewMapArt(CallbackInfoReturnable<Boolean> _cir, @Local ItemFrame frame, @Local UUID colorsId){
		if(Configs.Visuals.NEW_MAP_NOTIFIER_IFRAME.getBooleanValue()) NewMapNotifier.call(frame, colorsId);
	}
}