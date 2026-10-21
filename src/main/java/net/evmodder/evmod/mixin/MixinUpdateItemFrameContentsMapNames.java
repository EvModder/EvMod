package net.evmodder.evmod.mixin;

import java.util.UUID;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.evmodder.evmod.onTick.UpdateItemFrameContents;
import net.evmodder.evmod.commands.CommandExportMapNames;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value=UpdateItemFrameContents.class, remap=false)
abstract class MixinUpdateItemFrameContentsMapNames{
	@ModifyExpressionValue(method="scanIFrameContents", at=@At(value="INVOKE",
			target="Ljava/util/HashMap;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"), allow=1)
	private static Object collectMapName(Object oldColorsId, @Local UUID colorsId, @Local ItemStack stack){
		if(!colorsId.equals(oldColorsId) && stack.getCustomName() != null) CommandExportMapNames.addMapName(stack.getCustomName().getString());
		return oldColorsId;
	}
}