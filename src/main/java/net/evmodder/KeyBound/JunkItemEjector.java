package net.evmodder.KeyBound;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.slot.SlotActionType;

final public class JunkItemEjector{
	private final static boolean isUnrenewEnch(RegistryEntry<Enchantment> re, int lvl){
		if(re.matchesKey(Enchantments.MENDING)) return true;
		if(re.matchesKey(Enchantments.VANISHING_CURSE)) return true;
		if(re.matchesKey(Enchantments.SWIFT_SNEAK)) return true;
		if(re.matchesKey(Enchantments.BINDING_CURSE)) return true;
		if(re.matchesKey(Enchantments.SHARPNESS) && lvl == re.value().getMaxLevel()) return true;
		if(re.matchesKey(Enchantments.EFFICIENCY) && lvl == re.value().getMaxLevel()) return true;
		if(re.matchesKey(Enchantments.FEATHER_FALLING) && lvl == re.value().getMaxLevel()) return true;//Not really true
		return false;
	}
	private final static boolean shouldEject(ItemStack stack){
		if(stack == null || stack.isEmpty()) return false;
		final int rc = stack.getComponents().get(DataComponentTypes.REPAIR_COST);
		if(rc != 0) return false;

		switch(Registries.ITEM.getId(stack.getItem()).getPath()){
			//========== Fishing section ========================================
			case "bow":
				return stack.getEnchantments().getSize() < 4 || stack.getEnchantments().getEnchantments().stream()
						.anyMatch(r -> stack.getEnchantments().getLevel(r) < Math.min(4, r.value().getMaxLevel()));
			case "fishing_rod":
				return stack.getEnchantments().getSize() < 4 || stack.getEnchantments().getEnchantments().stream()
						.anyMatch(r -> stack.getEnchantments().getLevel(r) < r.value().getMaxLevel());
			case "enchanted_book":
				ItemEnchantmentsComponent iec = stack.getComponents().get(DataComponentTypes.STORED_ENCHANTMENTS);
				if(iec.getEnchantments().size() == 1) return true;
				//hasBinding() && noneMatch(isArmorEnch)
				return iec.getEnchantments().size() < 4 && iec.getEnchantments().stream().noneMatch(r -> isUnrenewEnch(r, iec.getLevel(r)));
			//========== End loot section ========================================
			case "diamond_sword": case "diamond_pickaxe": case "diamond_shovel":
			case "diamond_helmet": case "diamond_chestplate": case "diamond_leggings": case "diamond_boots":
				if(stack.getEnchantments().getSize() == 0) return false; // Raw gear
				if(stack.getEnchantments().getSize() == 1) return true; // Single-enchant can be done with a book
				if(stack.getEnchantments().getEnchantments().stream().anyMatch(r -> isUnrenewEnch(r, stack.getEnchantments().getLevel(r)))) return false;
				return stack.getEnchantments().getSize() < 3 || stack.getEnchantments().getEnchantments().stream()
						.anyMatch(r -> stack.getEnchantments().getLevel(r) < r.value().getMaxLevel());
			case "iron_sword": case "iron_pickaxe": case "iron_shovel":
			case "iron_helmet": case "iron_chestplate": case "iron_leggings": case "iron_boots":
				return true;
			//====================================================================
		}
		return false;
	}

	public static AbstractKeybind kb;
	final static void registerJunkEjectKeybind(){
		KeyBindingHelper.registerKeyBinding(kb = new AbstractKeybind(
				"key."+Main.MOD_ID+".eject_junk_items", InputUtil.Type.KEYSYM, -1, /*"key.categories."+KeyBound.MOD_ID+".misc"*/Main.KEYBIND_CATEGORY)
		{
			@Override public void onPressed(){
				//Main.LOGGER.info("onPressed() "+kb.getDefaultKey().toInt());

				MinecraftClient client = MinecraftClient.getInstance();
				if(client.currentScreen instanceof GenericContainerScreen containerScreen){
					//Main.LOGGER.info("mode 1");
					Inventory inv = containerScreen.getScreenHandler().getInventory();
					int syncId = containerScreen.getScreenHandler().syncId;
					for(int slot=0; slot<inv.size(); ++slot){
						ItemStack stack = inv.getStack(slot);
						if(shouldEject(stack)) client.interactionManager.clickSlot(syncId, slot, 1, SlotActionType.THROW, client.player);
					}
				}
				else if(client.currentScreen instanceof HandledScreen && !(client.currentScreen instanceof InventoryScreen)) return;

				else{
					//Main.LOGGER.info("mode 2");
					for(int slot=9; slot<45; ++slot){
						int adjustedSlot = slot;
						if(adjustedSlot >= 36) adjustedSlot -= 36;
						ItemStack stack = client.player.getInventory().getStack(adjustedSlot);
						if(shouldEject(stack)) client.interactionManager.clickSlot(/*client.player.playerScreenHandler.syncId*/0,
								slot, 1, SlotActionType.THROW, client.player);
					}
				}
			}
		});
	}
}