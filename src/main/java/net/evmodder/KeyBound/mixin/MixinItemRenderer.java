package net.evmodder.KeyBound.mixin;

import net.evmodder.KeyBound.Main;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ItemRenderer.class)
public abstract class MixinItemRenderer{
	private final MinecraftClient client = MinecraftClient.getInstance();

	// Z = boolean, I = int, V = void, Lpath/to/Class; = Class;

	// Multiple renderItem() methods, so gotta specify the correct one the ugly way.
	/*@Inject(at=@At("HEAD"), method="renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;"
			+ "Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;"
			+ "Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/world/World;III)V")
	public void renderItemMixin(LivingEntity entity, ItemStack stack,
			ModelTransformationMode transformationMode, boolean leftHanded, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, @Nullable World world, int light, int overlay, int seed, CallbackInfo ci
	){*/

	/*@Inject(at=@At("HEAD"), method="renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;II"
			+ "Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/world/World;I)V")
	public void renderItemMixin(ItemStack stack, ModelTransformationMode transformationMode, int light, int overlay,
			MatrixStack matrices, VertexConsumerProvider vertexConsumers, @Nullable World world, int seed
	){*/

	//@Inject(at=@At("HEAD"), method="update")
	//@ModifyArg(method="update", at=@At(value="INVOKE", target="update"), index=0)


	@ModifyVariable(at=@At("HEAD"), method="renderItem(Lnet/minecraft/entity/LivingEntity;"
			+ "Lnet/minecraft/item/ItemStack;"
			+ "Lnet/minecraft/client/render/model/json/ModelTransformationMode;"
			+ "Z"
			+ "Lnet/minecraft/client/util/math/MatrixStack;"
			+ "Lnet/minecraft/client/render/VertexConsumerProvider;"
			+ "Lnet/minecraft/world/World;"
			+ "III)V")
	public ItemStack editFirstItemStackParam(ItemStack stack){;;
		Main.LOGGER.info("mixin triggered");
		if(Main.totemShowTotalCount && stack.getItem() == Items.TOTEM_OF_UNDYING){
			Main.LOGGER.info("holding totem");
			final int numberOfTotems = client.player.getInventory().count(Items.TOTEM_OF_UNDYING);
			if(numberOfTotems > 1){
				Main.LOGGER.info("number of totems: "+numberOfTotems);
				stack = stack.copy();
				stack.setCount(numberOfTotems);
			}
		}
		return stack;
	}
}