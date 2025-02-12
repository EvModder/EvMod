package net.evmodder.KeyBound;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

public final class MapHandRestock{
	private boolean isSingleMapArt(ItemStack stack){
		if(stack == null || stack.isEmpty() || stack.getCount() != 1) return false;
		return Registries.ITEM.getId(stack.getItem()).getPath().equals("filled_map");
	}

	private String getCustomName(ItemStack stack){
		return stack == null || stack.getCustomName() == null ? null : stack.getCustomName().getLiteralString();
	}

	private record SubstrIndices(int a, int b){}
	private SubstrIndices longestCommonSubstr(String a, String b){
		int[] lens = new int[b.length()];

		int slen=0, si=0;
		for(int i=0; i<a.length(); ++i){
			for(int j=0; j<b.length(); ++j) {
				if(a.charAt(i) == b.charAt(j)){
					if(++lens[j] > slen){slen = lens[j]; si = i+1;}
				}
				else lens[j] = 0;
			}
		}
		return new SubstrIndices(si-slen, si);
	}

	// 0 = no. 1 = maybe (L->R), 2 = probably (L->M), 3 = definitely (4->5)
	//TODO: passing metadata, particularly NxM if known
	private int checkComesAfter(String posA, String posB){
		if(posA.isBlank() || posB.isBlank()) return 1; // "Map"->"Map p2", "Map start"->"Map"

		final int cutA = posA.indexOf(' '), cutB = posB.indexOf(' ');
		assert (cutA==-1) == (cutB==-1);
		final String posA1, posA2, posB1, posB2;
		if(cutA == -1){posA1 = posB1 = null; posA2 = posA; posB2 = posB;}
		else{
			posA1 = posA.substring(0, cutA); posA2 = posA.substring(cutA+1);
			posB1 = posB.substring(0, cutB); posB2 = posB.substring(cutB+1);
		}
		if(posA1 != posB1){
			Main.LOGGER.info("MapRestock: 2d pos not yet supported. A:"+posA+", B:"+posB);
			return 1;
		}

		final boolean sameLen = posA2.length() == posB2.length();
		if((sameLen || posA2.length()+1 == posB2.length()) && posA2.matches("\\d{1,3}")){
			return (""+(Integer.parseInt(posA2)+1)).equals(posB2) ? 3 : 0; // 4->5, 9->10
		}
		if(sameLen && posA2.regionMatches(0, posB2, 0, posA2.length()-1) &&
				posA2.charAt(posA2.length()-1)+1 == posB2.charAt(posA2.length()-1)) return 3; // 4->5, E->F

		if(posA.equals("TR") && posB.equals("ML")) return 3;
		if(posA.equals("TR") && posB.equals("BL")) return 2;
		if(posA.equals("L") && posB.equals("M")) return 3;
		if(posA.equals("M") && posB.equals("R")) return 3;
		if(posA.equals("L") && posB.equals("R")) return 2;

		Main.LOGGER.info("MapRestock: pos are not adjacent. A:"+posA+", B:"+posB);
		return 1;
	}

	private String simplifyPosStr(String rawPos){
		return Normalizer.normalize(
				rawPos.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", " ").trim().replaceAll("\\p{IsAlphabetic}\\p{IsAlphabetic}", "$1 $2"),
				Normalizer.Form.NFD).toUpperCase();
	}
//	private int posAsInt(String pos){
//		if(pos.matches("\\d+")) return Integer.parseInt(pos);
//		if(pos.length() != 1) return -1;
//		return pos.charAt(0);
//	}
	private void tryToStockNextMap(PlayerEntity player, Hand hand, ItemStack prevMap){
		if(!player.getStackInHand(hand).isEmpty()){
			Main.LOGGER.info("MapRestock: hand still not empty after right-clicking item frame"); return;
		}
		Main.LOGGER.info("Single mapart placed, hand:"+hand.ordinal()+", looking for restock map...");
		final String prevName = getCustomName(prevMap);
		if(prevName == null){Main.LOGGER.info("MapRestock: prevMap does not have a custom plain text name"); return;}

		SubstrIndices common = new SubstrIndices(0, 0);
		for(int i=9; i<=45; ++i){
			ItemStack item = player.playerScreenHandler.getSlot(i).getStack();
			if(!isSingleMapArt(item)) continue;
			final String name = getCustomName(item);
			if(name == null || name.equals(prevName)) continue;
			//if(item.equals(prevMap)) continue;
			SubstrIndices ab = longestCommonSubstr(prevName, name);
			if(ab.a != 0 && ab.b != prevName.length()) continue; // We can only handle 2 parts: {shared name | pos data}
			final boolean oldContainsNew = common.b >= ab.b && common.a <= ab.a;
			final boolean newContainsOld = ab.b >= common.b && ab.a <= common.a;
			final int oldLen = common.b-common.a, newLen = ab.b-ab.a; // old="Map_01,", new="Map_", input=[Map_01,01 Map_01,02 Map_02,01 Map_02,02]
			if(!newContainsOld && newLen > oldLen) common = ab;
			else if(oldContainsNew && newLen < oldLen && (ab.a==0)==(common.a==0)) common = ab;
		}
		if(common.a == 0 && common.b == 0){Main.LOGGER.info("MapRestock: No shared-prefix/suffix maps found"); return;}

		Main.LOGGER.info("MapRestock: prevMap name: "+prevName.substring(common.a, common.b));
//		final String posA, posB;
//		if(common.a == 0){posA = prevName.substring(common.b); posB = prevName.substring(common.b);}
//		else{posA = prevName.substring(0, common.a); posB = prevName.substring(0, prevName.length()-(common.b-common.a));}
		final String prevRawPos = common.a == 0 ? prevName.substring(0, common.b) : prevName.substring(0, common.a);
		final String prevPos = prevRawPos.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", " ").replaceAll("\\p{IsAlphabetic}\\p{IsAlphabetic}", "$1 $2").trim();
		Main.LOGGER.info("MapRestock: prevMap pos: "+prevPos);
		final int split2d = prevPos.indexOf(' ');
		if(split2d != -1 && prevPos.indexOf(' ', split2d+1) != -1){Main.LOGGER.info("MapRestock: unrecognized pos data"); return;}

		// Figure out how many maps with matching name are in inventory
		final int commonLen = common.b-common.a;
		ArrayList<Integer> slotsWithMatchingMaps = new ArrayList<>();
		for(int i=9; i<=45; ++i){
			ItemStack item = player.playerScreenHandler.getSlot(i).getStack();
			if(!isSingleMapArt(item)) continue;
			final String name = getCustomName(item);
			if(name == null || name.length() < commonLen) continue;
			final int startB = common.a == 0 ? 0 : name.length()-commonLen;
			if(prevName.regionMatches(common.a, name, startB, commonLen)){
				slotsWithMatchingMaps.add(i);
				final String pos = simplifyPosStr(common.a == 0 ? name.substring(common.b) : name.substring(0, name.length()-commonLen));
				final int split2di = pos.indexOf(' ');
				if((split2d==-1) != (split2di==-1)){Main.LOGGER.info("MapRestock: mismatched pos data: "+name); return;}
				if(split2di != -1 && pos.indexOf(' ', split2di+1) != -1){Main.LOGGER.info("MapRestock: unrecognized pos data: "+name); return;}
			}
		}
		Main.LOGGER.info("MapRestock: Num maps with common name: "+slotsWithMatchingMaps.size());
		assert slotsWithMatchingMaps.size() > 1;

//		// Calculate width/height (although we won't know which is which... need img processing mode for that)
//		final int dim1, dim2;
//		if(split2d == -1) dim1 = dim2 = split2d;
//		else{
//			int dim1Min=Integer.MAX_VALUE, dim1Max=Integer.MIN_VALUE, dim2Min=Integer.MAX_VALUE, dim2Max=Integer.MIN_VALUE;
//			for(int slot : slotsWithMatchingMaps){
//				final String name = getCustomName(player.playerScreenHandler.getSlot(slot).getStack());
//				final String pos = simplifyPosStr(common.a == 0 ? name.substring(common.b) : name.substring(0, name.length()-commonLen));
//				final int cutIdx = pos.indexOf(' ');
//				if(cutIdx == -1){Main.LOGGER.info("MapRestock: inconsistent pos names (some are 2D, some are not)"); return;}
//				final String pos1 = pos.substring(0, cutIdx), pos2 = pos.substring(cutIdx+1);
//				int pos1i = posAsInt(pos1), pos2i = posAsInt(pos2);
//				//if(pos1i == -1 || pos2i == -1){Main.LOGGER.info("MapRestock: unrecognized pos data"); return;}
//				assert pos1i != -1 && pos2i != -1;
//				dim1Min = Math.min(dim1Min, pos1i); dim1Max = Math.max(dim1Max, pos1i);
//				dim2Min = Math.min(dim2Min, pos2i); dim2Max = Math.max(dim2Max, pos2i);
//			}
//			dim1 = dim1Max - dim1Min;
//			dim2 = dim2Max - dim2Min;
//		}
		//if(split2d != -1){Main.LOGGER.info("MapRestock: TODO currently does not support 2d map args"); return;}

		int bestSlot = -1, bestConfidence = -1;
		for(int slot : slotsWithMatchingMaps){
			final String name = getCustomName(player.playerScreenHandler.getSlot(slot).getStack());
			final String pos = simplifyPosStr(common.a == 0 ? name.substring(common.b) : name.substring(0, name.length()-commonLen));
			final int confidence = checkComesAfter(prevPos, pos);
			if(confidence > bestConfidence){bestConfidence = confidence; bestSlot = slot;}
		}
		if(bestSlot == -1){Main.LOGGER.info("MapRestock: Could not find nextMap with high enough confidence"); return;}
		else{
			MinecraftClient client = MinecraftClient.getInstance();
			if(bestSlot >= 36 && bestSlot < 45){
				player.getInventory().selectedSlot = bestSlot - 36;
				client.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(player.getInventory().selectedSlot));
				Main.LOGGER.info("MapRestock: Changed selected hotbar slot to nextMap");
			}
			else{
				client.interactionManager.clickSlot(0, bestSlot, player.getInventory().selectedSlot, SlotActionType.SWAP, player);
				Main.LOGGER.info("MapRestock: Swapped nextMap int inv.selectedSlot");
			}
		}
	}

	MapHandRestock(){
		UseEntityCallback.EVENT.register((player, _, hand, entity, _) -> {
			if(!(entity instanceof ItemFrameEntity itemFrame)) return ActionResult.PASS;
			Main.LOGGER.info("clicked item frame (start)");
			if(itemFrame.getHeldItemStack().isEmpty()) Main.LOGGER.info("item frame is empty");
			if(isSingleMapArt(player.getStackInHand(hand))) Main.LOGGER.info("item in hand is single map");
			final ItemStack stack = player.getStackInHand(hand).copy();
			new Timer().schedule(new TimerTask(){@Override public void run(){tryToStockNextMap(player, hand, stack);}}, 1l);
			//Main.LOGGER.info("clicked item frame (return)");
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