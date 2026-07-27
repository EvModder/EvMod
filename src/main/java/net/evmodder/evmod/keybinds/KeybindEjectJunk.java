package net.evmodder.evmod.keybinds;

import static net.evmodder.evmod.compat.MinecraftCompat.screen;

import java.util.Set;
import net.evmodder.evmod.Main;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public final class KeybindEjectJunk{
	private boolean canGoOnArmor(Holder<Enchantment> re, EquipmentSlot slot){
		if(re.is(Enchantments.PROTECTION)) return true;
		if(re.is(Enchantments.PROJECTILE_PROTECTION)) return true;
		if(re.is(Enchantments.BLAST_PROTECTION)) return true;
		if(re.is(Enchantments.FIRE_PROTECTION)) return true;
		if(re.is(Enchantments.THORNS)) return true;
		if(re.is(Enchantments.UNBREAKING)) return true;
		if(re.is(Enchantments.MENDING)) return true;

		if(re.is(Enchantments.RESPIRATION) && (slot==null || slot == EquipmentSlot.HEAD)) return true;
		if(re.is(Enchantments.AQUA_AFFINITY) && (slot==null || slot == EquipmentSlot.HEAD)) return true;
		if(re.is(Enchantments.SWIFT_SNEAK) && (slot==null || slot == EquipmentSlot.LEGS)) return true;
		if(re.is(Enchantments.SOUL_SPEED) && (slot==null || slot == EquipmentSlot.FEET)) return true;
		if(re.is(Enchantments.FROST_WALKER) && (slot==null || slot == EquipmentSlot.FEET)) return true;
		if(re.is(Enchantments.DEPTH_STRIDER) && (slot==null || slot == EquipmentSlot.FEET)) return true;
		if(re.is(Enchantments.FEATHER_FALLING) && (slot==null || slot == EquipmentSlot.FEET)) return true;
		return false;
	}
	private boolean isUnrenewOutsideFishing(Holder<Enchantment> re, int lvl, Set<Holder<Enchantment>> allEnchs){
		if(re.is(Enchantments.MENDING) && allEnchs.size() > 1) return true;
		if(re.is(Enchantments.VANISHING_CURSE) && allEnchs.size() > 1) return true;
		if(re.is(Enchantments.SWIFT_SNEAK)) return true;
		if(re.is(Enchantments.BINDING_CURSE) && allEnchs.stream().anyMatch(r -> !r.is(Enchantments.BINDING_CURSE)
				&& canGoOnArmor(r, null))) return true;
		if(re.is(Enchantments.SHARPNESS) && lvl == re.value().getMaxLevel() && allEnchs.size() > 1) return true;
		if(re.is(Enchantments.EFFICIENCY) && lvl == re.value().getMaxLevel() && allEnchs.size() > 1) return true;

		// Not technically unrenewable, but rare enough to want it:
		if(re.is(Enchantments.FEATHER_FALLING) && lvl == re.value().getMaxLevel() &&
				allEnchs.stream().anyMatch(r -> !r.is(Enchantments.FEATHER_FALLING) && canGoOnArmor(r, EquipmentSlot.FEET))) return true;
		return false;
	}
	private enum JunkCategory{END_CITY, FISHING, RAID_FARM, NETHER}
	private JunkCategory junkType = null;
	public boolean shouldEject(ItemStack stack){
		if(stack == null || stack.isEmpty()) return false;
		final int rc = stack.getComponents().get(DataComponents.REPAIR_COST);
		if(rc != 0) return false;

		final ItemEnchantments iec = stack.getEnchantments();
		final Set<Holder<Enchantment>> enchs = iec.keySet();
		boolean isJunk = false;
		switch(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath()){
			//========== GoldFarm/NetherHighway section ========================================
			case "netherrack":
			case "crimson_roots": case "warped_roots":
				junkType = JunkCategory.NETHER;
				return true;
			case "golden_sword":
				if(enchs.size() < 4 && enchs.stream().anyMatch(r -> iec.getLevel(r) < r.value().getMaxLevel())){
					junkType = JunkCategory.NETHER;
				}
			//case "gold_nugget":
			//case "golden_helmet":
			case "golden_chestplate": case "golden_leggings": case "golden_boots":
			case "gravel": case "basalt": case "blackstone": case "soul_soil":
			case "feather":
			case "raw_chicken":
			case "rotten_flesh":
			case "red_mushroom": case "brown_mushroom":
			case "magma_cream":
				return junkType == JunkCategory.NETHER;
			case "glowstone_dust": case "gunpowder":
				return junkType == JunkCategory.NETHER || junkType == JunkCategory.RAID_FARM;
			//========== RaidFarm section ========================================
			case "spider_eye":
			case "stick":
			case "sugar":
				junkType = JunkCategory.RAID_FARM;
				return true;
			case "crossbow":
			case "white_banner":
			case "iron_axe":
			case "glass_bottle":
			case "redstone":
				return junkType == JunkCategory.RAID_FARM;
			case "potion":
			case "saddle":
				return junkType == JunkCategory.RAID_FARM || junkType == JunkCategory.FISHING;
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
				final ItemEnchantments siec = stack.getComponents().get(DataComponents.STORED_ENCHANTMENTS);
				final Set<Holder<Enchantment>> sEnchs = siec.keySet();
				isJunk = sEnchs.size() == 1 || (sEnchs.size() < 4 && sEnchs.stream().noneMatch(r -> isUnrenewOutsideFishing(r, siec.getLevel(r), sEnchs)));
				if(isJunk) junkType = JunkCategory.FISHING;
				return isJunk;
			}
			case "cod":
			case "salmon":
			case "pufferfish":
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

	public void ejectJunkItems(){
		Minecraft client = Minecraft.getInstance();
		final int syncId = client.player.containerMenu.containerId;

		if(screen(client) instanceof AbstractContainerScreen hs){
			final int invStart, invEnd;
			if(hs instanceof ShulkerBoxScreen){Main.LOGGER.info("EjectJunk: ShulkerBox"); invStart = 0; invEnd = 27;}
			else if(hs instanceof InventoryScreen){Main.LOGGER.info("EjectJunk: Inventory"); invStart = 9; invEnd = 45;}
			else if(hs instanceof ContainerScreen gcs){
				Main.LOGGER.info("EjectJunk: GenericContainer"); invStart = 0; invEnd = 9*gcs.getMenu().getRowCount();}
			else{Main.LOGGER.info("EjectJunk: Unsupported screen type. syncId: "+syncId); return;}

			for(int i=invStart; i<invEnd; ++i) shouldEject(hs.getMenu().getSlot(i).getItem()); // Detect junk category
			for(int i=invStart; i<invEnd; ++i) if(shouldEject(hs.getMenu().getSlot(i).getItem())){
				client.gameMode.handleContainerInput(syncId, i, 0, ContainerInput.THROW, client.player);
			}
		}
		else{
			Main.LOGGER.info("EjectJunk: Default (no Screen)");
			for(int i=9; i<45; ++i) shouldEject(client.player.getInventory().getItem(i%36)); // Detect junk category
			for(int i=9; i<45; ++i) if(shouldEject(client.player.getInventory().getItem(i%36))){
				client.gameMode.handleContainerInput(syncId, i, 1, ContainerInput.THROW, client.player);
			}
		}
		//junkType = null;
	}

	/*public KeybindEjectJunk(){
		new Keybind("eject_junk_items", this::ejectJunkItems, HandledScreen.class::isInstance, GLFW.GLFW_KEY_R);
	}*/
}