package net.evmodder.evmod.mixin;

//? >=26.3 {
import com.mojang.blaze3d.platform.Window;
import org.lwjgl.sdl.SDLHints;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Window.class)
abstract class MixinWindow{
	@Inject(method="createWindow", at=@At("HEAD"))
	private static void instantFullscreen(final CallbackInfoReturnable<Long> ci){
		if(!SDLHints.SDL_SetHint(SDLHints.SDL_HINT_VIDEO_MAC_FULLSCREEN_SPACES, "0"))
			LoggerFactory.getLogger("evmod").warn("Could not disable macOS fullscreen Spaces");
	}
}
//?}