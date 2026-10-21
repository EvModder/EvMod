package net.evmodder.evmod.mixin;

import static net.evmodder.evmod.compat.MinecraftCompat.screen;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.keybinds.KeybindCraftingRestock.AnvilNameController;
import net.evmodder.evmod.apis.ClickUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

@Mixin(MultiPlayerGameMode.class)
abstract class MixinMultiPlayerGameModeCrafting{
	@WrapOperation(method="handleContainerInput", at=@At(value="INVOKE",
			target="Lnet/minecraft/world/inventory/AbstractContainerMenu;clicked(IILnet/minecraft/world/inventory/ContainerInput;Lnet/minecraft/world/entity/player/Player;)V"))
	private void clearAnvilNameAfterInputChange(AbstractContainerMenu menu, int slot, int button,
			ContainerInput action, Player player, Operation<Void> original){
		if(!player.isCreative() && menu == player.containerMenu && Configs.Hotkeys.CRAFT_RESTOCK.getKeybind().isValid())
			((AccessorKeybindCraftingRestock)(Object)AccessorMain.getKbCraftRestock()).callCheckIfCraftAction(menu, slot, button, action, ClickUtils.isThisClickBotted());
		final ItemStack inputBefore = menu instanceof AnvilMenu ? menu.getSlot(AnvilMenu.INPUT_SLOT).getItem().copy() : null;
		original.call(menu, slot, button, action, player);
		if(inputBefore != null && !ItemStack.matches(inputBefore, menu.getSlot(AnvilMenu.INPUT_SLOT).getItem())
				&& screen(Minecraft.getInstance()) instanceof AnvilNameController controller)
			controller.evmod$clearNameProtection();
	}
}