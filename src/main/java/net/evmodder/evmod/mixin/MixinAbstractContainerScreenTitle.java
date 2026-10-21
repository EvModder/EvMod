package net.evmodder.evmod.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
abstract class MixinAbstractContainerScreenTitle<T extends AbstractContainerMenu> extends Screen{
	@Shadow @Final private final int titleLabelX;
	@Shadow @Final private final int titleLabelY;

	// Java requires we provide a constructor because of the <T>, but it'll never be called
	private MixinAbstractContainerScreenTitle(Component title){
		super(title);
		throw new RuntimeException("Év’s Mod: unreachable (cnstr of MixinAbstractContainerScreenTitle)");
	}

	@Inject(method="extractLabels", at=@At("TAIL"))
	private final void replaceScreenTitleForCurrentContainer(GuiGraphicsExtractor context, int _mouseX, int _mouseY, CallbackInfo _ci){
		if(AccessorUpdateContainerContents.customTitle() == null) return;
		context.text(font, AccessorUpdateContainerContents.customTitle(), titleLabelX, titleLabelY, 0xFF404040, false);
	}
}