package net.evmodder.evmod.keybinds;

import static net.evmodder.evmod.compat.MinecraftCompat.screen;

import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.Queue;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.apis.ClickUtils;
import net.evmodder.evmod.apis.ClickUtils.ActionType;
import net.evmodder.evmod.apis.ClickUtils.InvAction;
import net.evmodder.evmod.apis.TickListener;
import net.minecraft.client.Minecraft;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;

public final class KeybindArmorSwap implements TickListener{
	private static final boolean ALLOW_BINDING = false;
	public enum Swap{
		SPECIAL_HELMET(5, ItemTags.HEAD_ARMOR, Items.CARVED_PUMPKIN, Items.TURTLE_HELMET,
				Items.SKELETON_SKULL, Items.WITHER_SKELETON_SKULL, Items.ZOMBIE_HEAD,
				Items.PLAYER_HEAD, Items.CREEPER_HEAD, Items.DRAGON_HEAD, Items.PIGLIN_HEAD),
		LEATHER_BOOTS(8, ItemTags.FOOT_ARMOR, Items.LEATHER_BOOTS),
		GOLD_HELMET(5, ItemTags.HEAD_ARMOR, Items.GOLDEN_HELMET),
		GOLD_LEGGINGS(7, ItemTags.LEG_ARMOR, Items.GOLDEN_LEGGINGS),
		GOLD_BOOTS(8, ItemTags.FOOT_ARMOR, Items.GOLDEN_BOOTS);

		final int slot;
		final TagKey<Item> armor;
		final Item[] special;
		Swap(int slot, TagKey<Item> armor, Item... special){this.slot = slot; this.armor = armor; this.special = special;}
		boolean isSpecial(ItemStack stack){
			for(Item item : special) if(stack.is(item)) return true;
			return false;
		}
		boolean isRegular(ItemStack stack){
			if(!stack.is(armor)) return false;
			for(Swap swap : values()) if(swap.slot == slot && swap.isSpecial(stack)) return false;
			return true;
		}
	}
	record Expected(ItemStack slot, ItemStack cursor){}
	private final EnumSet<Swap> pending = EnumSet.noneOf(Swap.class);

	// Batch shared-key callbacks after other mods finish processing the key event.
	public void request(Swap swap){pending.add(swap);}

	static boolean allowed(ItemStack stack){
		return ALLOW_BINDING || stack.getEnchantments().keySet().stream().noneMatch(e -> e.is(Enchantments.BINDING_CURSE));
	}
	private static void click(Queue<InvAction> clicks, Queue<Expected> expected, int slot, int button, ItemStack before, ItemStack cursor){
		clicks.add(new InvAction(slot, button, ActionType.CLICK));
		expected.add(new Expected(before, cursor));
	}
	static boolean planSwap(Swap swap, boolean keepSpecial, ItemStack[] slots, Queue<InvAction> clicks, Queue<Expected> expected){
		final ItemStack worn = slots[swap.slot];
		if(!allowed(worn)) return false;
		final boolean toRegular = swap.isSpecial(worn);
		if(!worn.isEmpty() && !toRegular && !swap.isRegular(worn)) return false;
		if(keepSpecial && toRegular) return true;
		// First matching inventory slot, then hotbar. Never take armor from another equipped slot.
		for(int source=9; source<45; ++source){
			final ItemStack replacement = slots[source];
			if(!(toRegular ? swap.isRegular(replacement) : swap.isSpecial(replacement)) || !allowed(replacement)) continue;
			if(replacement.getCount() == 1){
				click(clicks, expected, source, 0, replacement, ItemStack.EMPTY);
				click(clicks, expected, swap.slot, 0, worn, replacement);
				if(!worn.isEmpty()) click(clicks, expected, source, 0, ItemStack.EMPTY, worn);
				slots[source] = worn;
			}
			else{
				// A stack of heads/pumpkins needs somewhere to put the old helmet first.
				if(!worn.isEmpty()){
					int empty = 9;
					while(empty < 45 && !slots[empty].isEmpty()) ++empty;
					if(empty == 45) continue;
					click(clicks, expected, swap.slot, 0, worn, ItemStack.EMPTY);
					click(clicks, expected, empty, 0, ItemStack.EMPTY, worn);
					slots[empty] = worn;
				}
				click(clicks, expected, source, 0, replacement, ItemStack.EMPTY);
				click(clicks, expected, swap.slot, 1, ItemStack.EMPTY, replacement);
				slots[source] = replacement.copyWithCount(replacement.getCount()-1);
				click(clicks, expected, source, 0, ItemStack.EMPTY, slots[source]);
			}
			slots[swap.slot] = replacement.copyWithCount(1);
			return true;
		}
		return false;
	}

	@Override public void onTickEnd(Minecraft client){
		if(pending.isEmpty()) return;
		try{
			if(client.player == null || client.player.isSpectator() || screen(client) != null || ClickUtils.hasOngoingClicks()) return;
			final var menu = client.player.inventoryMenu;
			if(client.player.containerMenu != menu || !menu.getCarried().isEmpty()) return;
			final ItemStack[] slots = new ItemStack[45];
			for(int i=5; i<45; ++i) slots[i] = menu.getSlot(i).getItem().copy();
			boolean keepSpecial = false;
			if(Configs.Hotkeys.SYNC_ARMOR_WITH_ELYTRA.getBooleanValue() && slots[6].is(Items.ELYTRA)){
				// Without a usable chestplate, allow independent toggles even while wearing elytra.
				for(int i=9; i<45 && !keepSpecial; ++i) keepSpecial = slots[i].is(ItemTags.CHEST_ARMOR) && allowed(slots[i]);
			}
			final Queue<InvAction> clicks = new ArrayDeque<>();
			final Queue<Expected> expected = new ArrayDeque<>();
			final boolean[] planned = new boolean[9];
			for(Swap swap : pending){
				if(planned[swap.slot] || !menu.getSlot(swap.slot).mayPickup(client.player)) continue;
				planned[swap.slot] = planSwap(swap, keepSpecial, slots, clicks, expected);
			}
			if(clicks.isEmpty()) return;
			ClickUtils.executeClicks(action -> {
				final Expected before = expected.remove();
				if(ItemStack.matches(menu.getSlot(action.slot()).getItem(), before.slot)
						&& ItemStack.matches(menu.getCarried(), before.cursor)) return true;
				clicks.clear();  // Inventory changed while waiting for the click budget; never move an unexpected item.
				return false;
			}, ()->{}, clicks);
		}
		finally{pending.clear();}
	}
}