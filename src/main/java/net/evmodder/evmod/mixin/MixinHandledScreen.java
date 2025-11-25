package net.evmodder.evmod.mixin;

import net.evmodder.evmod.onTick.UpdateContainerHighlights;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
abstract class MixinHandledScreen<T> extends Screen{
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
}