package net.evmodder.evmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.evmodder.evmod.keybinds.KeybindCraftingRestock.AnvilNameController;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.world.inventory.AnvilMenu;

@Mixin(AnvilScreen.class)
abstract class MixinAnvilScreen implements AnvilNameController{
	@Unique private static final boolean evmod$BLANK_UNNAMED_ITEM_NAMES = false;
	@Unique private boolean evmod$syncingInputName;
	@Unique private boolean evmod$protectName;

	@Inject(method="onNameChanged", at=@At("HEAD"))
	private void protectEditedName(String value, CallbackInfo ci){
		if(!evmod$syncingInputName && ((AnvilScreen)(Object)this).getMenu().getSlot(AnvilMenu.INPUT_SLOT).hasItem())
			evmod$protectName = true;
	}

	@Redirect(method="slotChanged", at=@At(value="INVOKE",
			target="Lnet/minecraft/client/gui/components/EditBox;setValue(Ljava/lang/String;)V"))
	private void preserveEditedName(EditBox nameField, String vanillaName){
		if(evmod$protectName) return;
		if(evmod$BLANK_UNNAMED_ITEM_NAMES && ((AnvilScreen)(Object)this).getMenu()
				.getSlot(AnvilMenu.INPUT_SLOT).getItem().getCustomName() == null) vanillaName = "";
		evmod$syncingInputName = true;
		try{nameField.setValue(vanillaName);}
		finally{evmod$syncingInputName = false;}
	}

	@Override public void evmod$clearNameProtection(){evmod$protectName = false;}
}