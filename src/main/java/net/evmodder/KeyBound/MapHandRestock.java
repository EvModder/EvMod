package net.evmodder.KeyBound;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

public final class MapHandRestock{
	private boolean isSingleMapArt(ItemStack stack){
		if(stack == null || stack.isEmpty() || stack.getCount() != 1) return false;
		return Registries.ITEM.getId(stack.getItem()).getPath().equals("filled_map");
	}

	private void tryToStockNextMap(PlayerEntity player, Hand hand, ItemStack prevMap){
		Main.LOGGER.info("Single mapart placed, hand:"+hand.ordinal()+", looking for restock map...");
	}

	MapHandRestock(){
		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if(!(entity instanceof ItemFrameEntity itemFrame)) return ActionResult.PASS;
			Main.LOGGER.info("clicked item frame (start)");
			if(itemFrame.getHeldItemStack().isEmpty()) Main.LOGGER.info("item frame is empty");
			if(isSingleMapArt(player.getStackInHand(hand))) Main.LOGGER.info("item in hand is single map");
			Main.LOGGER.info("clicked item frame (return)");
			return ActionResult.PASS;
		});
	}

	/*
	public static boolean isEnabled = false;
	private static ItemStack placedMap;

	public static void onProcessRightClickPre(PlayerEntity player, Hand hand){
		ItemStack stack = player.getStackInHand(hand);
		if(isSingleMapArt(stack)){
			Main.LOGGER.info("Right clicked with a single map");
			placedMap = stack.copy();
		}
	}

	public static void onProcessRightClickPost(PlayerEntity player, Hand hand){
		Main.LOGGER.info("item in hand (post-process): "+player.getStackInHand(hand).getItem().getName()+", "+player.getStackInHand(hand).getCount());
		if(placedMap != null && player.getStackInHand(hand).isEmpty()) tryToStockNextMap(player, hand, placedMap);
		placedMap = null;
	}*/
}