package net.evmodder.KeyBound.Keybinds;

import java.util.Set;
import net.evmodder.KeyBound.Main;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.slot.SlotActionType;

public final class KeybindEjectJunk{
	private boolean canGoOnArmor(RegistryEntry<Enchantment> re, EquipmentSlot slot){
		if(re.matchesKey(Enchantments.PROTECTION)) return true;
		if(re.matchesKey(Enchantments.PROJECTILE_PROTECTION)) return true;
		if(re.matchesKey(Enchantments.BLAST_PROTECTION)) return true;
		if(re.matchesKey(Enchantments.FIRE_PROTECTION)) return true;
		if(re.matchesKey(Enchantments.THORNS)) return true;
		if(re.matchesKey(Enchantments.UNBREAKING)) return true;
		if(re.matchesKey(Enchantments.MENDING)) return true;

		if(re.matchesKey(Enchantments.RESPIRATION) && (slot==null || slot == EquipmentSlot.HEAD)) return true;
		if(re.matchesKey(Enchantments.AQUA_AFFINITY) && (slot==null || slot == EquipmentSlot.HEAD)) return true;
		if(re.matchesKey(Enchantments.SWIFT_SNEAK) && (slot==null || slot == EquipmentSlot.LEGS)) return true;
		if(re.matchesKey(Enchantments.SOUL_SPEED) && (slot==null || slot == EquipmentSlot.FEET)) return true;
		if(re.matchesKey(Enchantments.FROST_WALKER) && (slot==null || slot == EquipmentSlot.FEET)) return true;
		if(re.matchesKey(Enchantments.DEPTH_STRIDER) && (slot==null || slot == EquipmentSlot.FEET)) return true;
		if(re.matchesKey(Enchantments.FEATHER_FALLING) && (slot==null || slot == EquipmentSlot.FEET)) return true;
		return false;
	}
	private boolean isUnrenewOutsideFishing(RegistryEntry<Enchantment> re, int lvl, Set<RegistryEntry<Enchantment>> allEnchs){
		if(re.matchesKey(Enchantments.MENDING) && allEnchs.size() > 1) return true;
		if(re.matchesKey(Enchantments.VANISHING_CURSE) && allEnchs.size() > 1) return true;
		if(re.matchesKey(Enchantments.SWIFT_SNEAK)) return true;
		if(re.matchesKey(Enchantments.BINDING_CURSE) && allEnchs.stream().anyMatch(r -> !r.matchesKey(Enchantments.BINDING_CURSE)
				&& canGoOnArmor(r, null))) return true;
		if(re.matchesKey(Enchantments.SHARPNESS) && lvl == re.value().getMaxLevel() && allEnchs.size() > 1) return true;
		if(re.matchesKey(Enchantments.EFFICIENCY) && lvl == re.value().getMaxLevel() && allEnchs.size() > 1) return true;

		// Not technically unrenewable, but rare enough to want it:
		if(re.matchesKey(Enchantments.FEATHER_FALLING) && lvl == re.value().getMaxLevel() &&
				allEnchs.stream().anyMatch(r -> !r.matchesKey(Enchantments.FEATHER_FALLING) && canGoOnArmor(r, EquipmentSlot.FEET))) return true;
		return false;
	}
	enum JunkCategory{END_CITY, FISHING, RAID_FARM, GOLD_FARM}
	private JunkCategory junkType = null;
	private boolean shouldEject(ItemStack stack){
		if(stack == null || stack.isEmpty()) return false;
		final int rc = stack.getComponents().get(DataComponentTypes.REPAIR_COST);
		if(rc != 0) return false;

		final ItemEnchantmentsComponent iec = stack.getEnchantments();
		final Set<RegistryEntry<Enchantment>> enchs = iec.getEnchantments();
		boolean isJunk = false;
		switch(Registries.ITEM.getId(stack.getItem()).getPath()){
			//========== GoldFarm section ========================================
			case "golden_sword":
				if(enchs.size() < 4 && enchs.stream().anyMatch(r -> iec.getLevel(r) < r.value().getMaxLevel())){
					junkType = JunkCategory.GOLD_FARM;
				}
			case "feather":
			case "raw_chicken":
			case "rotten_flesh":
				return junkType == JunkCategory.GOLD_FARM;
			//========== RaidFarm section ========================================
//			case "crossbow":
//			case "white_banner":
//			case "iron_axe":
//			case "glass_bottle":
//			case "spider_eye":
//			case "stick":
//			case "potion":
////		case "saddle":
//				return junkType == JunkCategory.RAID_FARM;
			//========== Fishing section ========================================
			case "bow":
				isJunk = enchs.size() < 4 || enchs.stream() .anyMatch(r -> iec.getLevel(r) < Math.min(4, r.value().getMaxLevel()));
				if(isJunk) junkType = JunkCategory.FISHING;
				return isJunk;
			case "fishing_rod":
				isJunk = enchs.size() < 4 || enchs.stream().anyMatch(r -> iec.getLevel(r) < r.value().getMaxLevel());
				if(isJunk) junkType = JunkCategory.FISHING;
				return isJunk;
			case "enchanted_book": {
				final ItemEnchantmentsComponent siec = stack.getComponents().get(DataComponentTypes.STORED_ENCHANTMENTS);
				final Set<RegistryEntry<Enchantment>> sEnchs = siec.getEnchantments();
				isJunk = sEnchs.size() == 1 || (sEnchs.size() < 4 && sEnchs.stream().noneMatch(r -> isUnrenewOutsideFishing(r, siec.getLevel(r), sEnchs)));
				if(isJunk) junkType = JunkCategory.FISHING;
				return isJunk;
			}
			case "raw_cod":
			case "salmon":
			case "pufferfish":
			case "saddle":
			case "leather_boots":
				return junkType == JunkCategory.FISHING;
			//========== End loot section ========================================
			case "diamond_sword": case "diamond_pickaxe": case "diamond_shovel":
			case "diamond_helmet": case "diamond_chestplate": case "diamond_leggings": case "diamond_boots":
				// Don't throw out raw gear, gear with >=4 ench, or gear with unrenewable enchants
				isJunk = !enchs.isEmpty() && enchs.size() < 4 && enchs.stream().noneMatch(r -> isUnrenewOutsideFishing(r, iec.getLevel(r), enchs));
				if(isJunk) junkType = JunkCategory.END_CITY;
				return isJunk;
			case "iron_sword": case "iron_pickaxe": case "iron_shovel":
			case "iron_helmet": case "iron_chestplate": case "iron_leggings": case "iron_boots":
				junkType = JunkCategory.END_CITY;
				return true;
			case "purpur_pillar":
			case "purpur_stairs":
			case "purpur_slab":
			case "end_stone_bricks":
			case "item_frame":
				return junkType == JunkCategory.END_CITY;
			//====================================================================
		}
		return false;
	}

	private void ejectJunkItems(){
		MinecraftClient client = MinecraftClient.getInstance();
		final int syncId = client.currentScreen instanceof HandledScreen hs ? hs.getScreenHandler().syncId : 0;

		if(client.currentScreen instanceof HandledScreen hs){
			final int invStart, invEnd;
			if(hs instanceof ShulkerBoxScreen){Main.LOGGER.info("EjectJunk: ShulkerBox"); invStart = 0; invEnd = 27;}
			else if(hs instanceof InventoryScreen){Main.LOGGER.info("EjectJunk: Inventory"); invStart = 9; invEnd = 45;}
			else if(hs instanceof GenericContainerScreen gcs){
				Main.LOGGER.info("EjectJunk: GenericContainer"); invStart = 0; invEnd = 9*gcs.getScreenHandler().getRows();}
			else{Main.LOGGER.info("EjectJunk: Unsupported screen type. syncId: "+syncId); return;}

			for(int i=invStart; i<invEnd; ++i) shouldEject(hs.getScreenHandler().getSlot(i).getStack()); // Detect junk category
			for(int i=invStart; i<invEnd; ++i) if(shouldEject(hs.getScreenHandler().getSlot(i).getStack())){
				client.interactionManager.clickSlot(syncId, i, 0, SlotActionType.THROW, client.player);
			}
		}
		else{
			Main.LOGGER.info("EjectJunk: Default (no Screen)");
			for(int i=9; i<45; ++i) shouldEject(client.player.getInventory().getStack(i%36)); // Detect junk category
			for(int i=9; i<45; ++i) if(shouldEject(client.player.getInventory().getStack(i%36))){
				client.interactionManager.clickSlot(syncId, i, 1, SlotActionType.THROW, client.player);
			}
		}
		junkType = null;
	}

	public KeybindEjectJunk(){
		KeyBindingHelper.registerKeyBinding(new EvKeybind("eject_junk_items", this::ejectJunkItems, HandledScreen.class::isInstance));
	}
}