package net.evmodder.evmod.mixin;

import java.util.stream.Stream;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.evmodder.evmod.apis.InvUtils;
import net.evmodder.evmod.onTick.UpdateInventoryContents;
import net.evmodder.evmod.commands.CommandExportMapNames;
//? >=26.1 {
import net.minecraft.world.item.ItemInstance;
//?}
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Desc;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Export-only observations piggyback on the existing inventory scan without any disabled-feature dispatch. */
@Mixin(value=UpdateInventoryContents.class, remap=false)
abstract class MixinUpdateInventoryContentsMapNames{
	@Inject(method="addMapStateIds", at=@At(value="FIELD",
			target="Lnet/minecraft/core/component/DataComponents;MAP_ID:Lnet/minecraft/core/component/DataComponentType;"), allow=1)
	private static void collectPlainMapName(ItemStack stack, Level _world, CallbackInfo _ci){
		CommandExportMapNames.addMapName(stack);
	}

	@ModifyExpressionValue(method="addMapStateIds", at={
		//? >=26.1 {
		@At(value="INVOKE", desc=@Desc(owner=InvUtils.class, value="getAllNestedItemViews", args=ItemInstance.class, ret=Stream.class)),
		@At(value="INVOKE", desc=@Desc(owner=InvUtils.class, value="getAllNestedItemViewsExcludingBundles", args=ItemInstance.class, ret=Stream.class))
		//?} else {
		/*@At(value="INVOKE", desc=@Desc(owner=InvUtils.class, value="getAllNestedItemViews", args=ItemStack.class, ret=Stream.class)),
		@At(value="INVOKE", desc=@Desc(owner=InvUtils.class, value="getAllNestedItemViewsExcludingBundles", args=ItemStack.class, ret=Stream.class))*/
		//?}
	}, require=2, allow=2)
	//? >=26.1 {
	private static Stream<ItemInstance> collectNestedMapNames(Stream<ItemInstance> maps){
	//?} else {
	/*private static Stream<ItemStack> collectNestedMapNames(Stream<ItemStack> maps){*/
	//?}
		return maps.peek(CommandExportMapNames::addMapName);
	}
}