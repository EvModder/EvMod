package net.evmodder.evmod.mixin;

import net.evmodder.evmod.apis.MiscUtils;
import net.evmodder.evmod.onTick.UpdateContainerHighlights;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.BannerItem;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HandledScreen.class)
abstract class MixinHandledScreen<T extends ScreenHandler> extends Screen{
	@Shadow @Final protected T handler;
	@Final @Shadow protected int titleX;
	@Final @Shadow protected int titleY;

	MixinHandledScreen(Text title){
		super(title);
		throw new RuntimeException(); // Java requires we provide a constructor because of the <T>, but it should never be called
	}

	@Inject(method="drawForeground", at=@At("TAIL"))
	public void mixinFor_drawForeground_overwriteInvTitle(DrawContext context, int mouseX, int mouseY, CallbackInfo ci){
		if(UpdateContainerHighlights.customTitle == null) return;
		context.drawText(textRenderer, UpdateContainerHighlights.customTitle, titleX, titleY, 4210752, false);
	}

	// Pretty much copied from Enderkill's mod:
	// https://github.com/EnderKill98/EnderSpecimina/blob/main/src/main/java/me/enderkill98/enderspecimina/mixin/FixGhostItemsMixin.java
	// MIT License
	@Inject(method="mouseDragged", at=@At("HEAD"), cancellable=true)
	public void mouseDragged(CallbackInfoReturnable<Boolean> cir){
		if(MiscUtils.getCurrentServerAddressHashCode() != MiscUtils.HASHCODE_2B2T) return;
//		if(!Configs.Generic.FIX_2B2T_GHOST_ITEMS.getBooleanValue()) return;

		if(client.player == null || client.player.isCreative()) return; // Breaks creative middle-click drag (on other servers)
		ItemStack cursorStack = handler.getCursorStack();
		if(cursorStack == null || cursorStack.isEmpty()) return;
		if(!cursorStack.isStackable() || cursorStack.getItem() instanceof FilledMapItem || cursorStack.getItem() instanceof BannerItem)
			cir.setReturnValue(true); // Prevent initiating a drag
	}
}