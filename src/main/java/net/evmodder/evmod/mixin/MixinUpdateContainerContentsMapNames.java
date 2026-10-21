package net.evmodder.evmod.mixin;

import java.util.List;
import java.util.Objects;
import com.llamalad7.mixinextras.sugar.Local;
import net.evmodder.evmod.onTick.UpdateContainerContents;
import net.evmodder.evmod.commands.CommandExportMapNames;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
//? >=26.1 {
import net.minecraft.world.item.ItemInstance;
//?} else {
/*import net.minecraft.world.item.ItemStack;*/
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value=UpdateContainerContents.class, remap=false)
abstract class MixinUpdateContainerContentsMapNames{
	@Shadow private static int mapsInContainerRevision;
	@ModifyVariable(method="onTickStart", at=@At("STORE"), name="newRevision", allow=1)
	//? >=26.1 {
	private int collectMapNames(int revision, @Local List<ItemInstance> maps){
	//?} else {
	/*private int collectMapNames(int revision, @Local List<ItemStack> maps){*/
	//?}
		if(revision != mapsInContainerRevision) maps.stream().map(stack -> stack.get(DataComponents.CUSTOM_NAME))
				.filter(Objects::nonNull).map(Component::getString).forEach(CommandExportMapNames::addMapName);
		return revision;
	}
}