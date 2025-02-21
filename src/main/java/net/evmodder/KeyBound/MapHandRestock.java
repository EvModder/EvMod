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
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

public final class MapHandRestock{
	final boolean USE_NAME, USE_IMG, JUST_PICK_A_MAP = true;
	private boolean isSingleMapArt(ItemStack stack){
		if(stack == null || stack.isEmpty() || stack.getCount() != 1) return false;
		return Registries.ITEM.getId(stack.getItem()).getPath().equals("filled_map");
	}

	private String getCustomName(ItemStack stack){
		return stack == null || stack.getCustomName() == null ? null : stack.getCustomName().getLiteralString();
	}

	//TODO: passing metadata, particularly NxM if known
	private int checkComesAfter(String posA, String posB){
		if(posA.isBlank() || posB.isBlank() || posA.equals(posB)) return 1; // "Map"->"Map p2", "Map start"->"Map"

		if(posA.equals("T R") && posB.equals("M L")) return 4;
		if(posA.equals("T R") && posB.equals("B L")) return 3;
		if(posA.endsWith("L") && posB.endsWith("M")) return 4;
		if(posA.endsWith("M") && posB.endsWith("R")) return 4;
		if(posA.endsWith("L") && posB.endsWith("R")) return 3;

		final int cutA = posA.indexOf(' '), cutB = posB.indexOf(' ');
		assert (cutA==-1) == (cutB==-1);
		if(cutA != -1){
			//Main.LOGGER.info("MapRestock: 2d pos not yet fully supported. A:"+posA+", B:"+posB);
			String posA1 = posA.substring(0, cutA), posA2 = posA.substring(cutA+1);
			String posB1 = posB.substring(0, cutB), posB2 = posB.substring(cutB+1);
			if(posA1.equals(posB1)) return checkComesAfter(posA2, posB2);
			// TODO: Column before row? maybe better solution than just nerfing with -1
			if(posA2.equals(posB2)) return checkComesAfter(posA1, posB1) - 1;
			int dim1Step = checkComesAfter(posA1, posB1);
			if(dim1Step >= 3 && posB2.matches("[A01]")) return dim1Step - (posB2.equals("1") ? 2 : 1);
			int dim2Step = checkComesAfter(posA2, posB2);
			if(dim2Step >= 3 && posB1.matches("[A01]")) return dim2Step - (posB1.equals("1") ? 2 : 1);
			//Main.LOGGER.info("MapRestock: Unable to resolve 2d pos");
			return 0;
		}

		final boolean sameLen = posA.length() == posB.length();
		if((sameLen || posA.length()+1 == posB.length()) && posA.matches("\\d{1,3}")){
			return (""+(Integer.parseInt(posA)+1)).equals(posB) ? 4 : 0; // 4->5, 9->10
		}
		if(sameLen && posA.regionMatches(0, posB, 0, posA.length()-1) &&
				posA.charAt(posA.length()-1)+1 == posB.charAt(posA.length()-1)) return 4; // 4->5, E->F

		//Main.LOGGER.info("MapRestock: pos are not adjacent. A:"+posA+", B:"+posB);
		return 1;
	}

	private int commonPrefixLen(String a, String b){
		int i=0; while(i<a.length() && i<b.length() && a.charAt(i) == b.charAt(i)) ++i; return i;
	}
	private int commonSuffixLen(String a, String b){
		int i=0; while(a.length()-i > 0 && b.length()-i > 0 && a.charAt(a.length()-i-1) == b.charAt(b.length()-i-1)) ++i; return i;
	}

	private String simplifyPosStr(String rawPos){
		String pos = Normalizer.normalize(
				rawPos.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", " ").trim(),
				Normalizer.Form.NFD).toUpperCase();
		while(pos.matches(".*[A-Z][A-Z].*")) pos = pos.replaceAll("([A-Z])([A-Z])", "$1 $2");
		return pos;
	}
	private boolean isValidPosStr(String posStr){
		return !posStr.isBlank() && posStr.split(" ").length <= 2;
	}

	private int getNextSlotByName(PlayerEntity player, String prevName){
		PlayerScreenHandler psh = player.playerScreenHandler;
		ArrayList<Integer> slotsWithMatchingMaps = new ArrayList<>();
		int prefixLen = 0, suffixLen = 0;
		for(int i=9; i<=45; ++i){
			ItemStack item = psh.getSlot(i).getStack();
			if(!isSingleMapArt(item)) continue;
			final String name = getCustomName(item);
			if(name == null) continue;
			if(name.equals(prevName)){
				if(i-36 != player.getInventory().selectedSlot) slotsWithMatchingMaps.add(i);
				continue;
			}
			//if(item.equals(prevMap)) continue;
			int a = commonPrefixLen(prevName, name), b = commonSuffixLen(prevName, name);
			//Main.LOGGER.info("MapRestock: map"+i+" prefixLen|suffixLen: "+a+"|"+b);
			if(prefixLen == a && suffixLen == b) continue;
			//Main.LOGGER.info("MapRestock: map"+i+" prefixLen|suffixLen: "+a+"|"+b);
			if(prefixLen == 0 && suffixLen == 0){prefixLen = a; suffixLen = b; continue;}
			final boolean oldContainsNew = prefixLen >= a && suffixLen >= b;
			//final boolean newContainsOld = a >= prefixLen && b >= suffixLen;
			if(oldContainsNew && isValidPosStr(simplifyPosStr(name.substring(a, name.length()-b)))){
				Main.LOGGER.info("MapRestock: reducing prefix/suffix len");
				prefixLen = a; suffixLen = b;
			}
			if(a+b > prefixLen+suffixLen && !isValidPosStr(name.substring(Math.min(a, prefixLen), name.length()-Math.min(b, suffixLen)))){
				Main.LOGGER.info("MapRestock: expanding prefix/suffix len");
				prefixLen = a; suffixLen = b;
			}
		}
		if(prefixLen == 0 && suffixLen == 0){
			Main.LOGGER.info("MapRestock: no shared prefix/suffix named maps found");
			if(slotsWithMatchingMaps.isEmpty()) return -1;
		}
		Main.LOGGER.info("MapRestock: prefixLen="+prefixLen+", suffixLen="+suffixLen);
		final String posStrPrev = simplifyPosStr(prevName.substring(prefixLen, prevName.length()-suffixLen));
		//if(!isValidPosStr(posStrPrev)){Main.LOGGER.info("MapRestock: unrecognized prevPos data: "+posStrPrev); return -1;}
		final boolean pos2dPrev = posStrPrev.indexOf(' ') != -1;

		//for(int i=9; i<=45; ++i){
		for(int f=0; f<=36; ++f){
			int i = (f+27)%37 + 9; // Hotbar+Offhand [36->45], then Inv [9->35]
			ItemStack item = psh.getSlot(i).getStack();
			if(!isSingleMapArt(item)) continue;
			final String name = getCustomName(item);
			if(name == null || name.length() < prefixLen+suffixLen+1 || name.equals(prevName)) continue;
			if(!prevName.regionMatches(0, name, 0, prefixLen) ||
					!prevName.regionMatches(prevName.length()-suffixLen, name, name.length()-suffixLen, suffixLen)) continue;
			slotsWithMatchingMaps.add(i);
			final String posStr = simplifyPosStr(name.substring(prefixLen, name.length()-suffixLen));
			if(!isValidPosStr(posStr)){Main.LOGGER.info("MapRestock: unrecognized pos data: "+posStr); return -1;}
			final boolean pos2d = posStrPrev.indexOf(' ') != -1;
			if(pos2d != pos2dPrev){Main.LOGGER.info("MapRestock: mismatched pos data: "+name); return -1;}
		}

		int bestSlot = -1, bestConfidence = -1;
		for(int slot : slotsWithMatchingMaps){
			final String name = getCustomName(psh.getSlot(slot).getStack());
			final String posStr = simplifyPosStr(name.substring(prefixLen, name.length()-suffixLen));
			final int confidence = checkComesAfter(posStrPrev, posStr);
			if(confidence > bestConfidence){bestConfidence = confidence; bestSlot = slot;}
		}
		if(bestSlot != -1) Main.LOGGER.info("MapRestock: find-by-name suceeded");
		return bestSlot;
	}
	private int getNextSlotAny(PlayerEntity player, Hand hand){
		//TODO: prioritize "start maps", i.e. posStr "1/4", "Name #0", etc.
		for(int i=PlayerScreenHandler.HOTBAR_START; i<PlayerScreenHandler.HOTBAR_END; ++i){
			if(hand == Hand.MAIN_HAND && i-PlayerScreenHandler.HOTBAR_START == player.getInventory().selectedSlot) continue;
			if(isSingleMapArt(player.playerScreenHandler.getSlot(i).getStack())) return i;
		}
		for(int i=PlayerScreenHandler.INVENTORY_START; i<PlayerScreenHandler.INVENTORY_END; ++i){
			if(isSingleMapArt(player.playerScreenHandler.getSlot(i).getStack())) return i;
		}
		if(hand != Hand.OFF_HAND && isSingleMapArt(player.getStackInHand(Hand.OFF_HAND))) return PlayerScreenHandler.OFFHAND_ID;
		return -1;
	}

	private void tryToStockNextMap(PlayerEntity player, Hand hand, ItemStack prevMap){
//		if(!player.getStackInHand(hand).isEmpty()){
//			Main.LOGGER.info("MapRestock: hand still not empty after right-clicking item frame"); return;
//		}
		Main.LOGGER.info("Single mapart placed, hand:"+hand.ordinal()+", looking for restock map...");
		int restockFromSlot;
		final String prevName = getCustomName(prevMap);
		//if(USE_IMG) restockFromSlot = getNextSlotByImage(player.playerScreenHandler, prevMap);
		//else if
		if(USE_NAME && prevName != null){
			Main.LOGGER.info("MapRestock: finding next map by-name: "+prevName);
			restockFromSlot = getNextSlotByName(player, prevName);
			if(restockFromSlot == -1) restockFromSlot = getNextSlotAny(player, hand);
		}
		else if(JUST_PICK_A_MAP){
			Main.LOGGER.info("MapRestock: finding any single map");
			restockFromSlot = getNextSlotAny(player, hand);
		}
		else restockFromSlot = -1;
		if(restockFromSlot == -1){Main.LOGGER.info("MapRestock: unable to find next map"); return;}

		MinecraftClient client = MinecraftClient.getInstance();
		if(restockFromSlot >= 36 && restockFromSlot < 45){
			player.getInventory().selectedSlot = restockFromSlot - 36;
			client.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(player.getInventory().selectedSlot));
			Main.LOGGER.info("MapRestock: Changed selected hotbar slot to nextMap");
		}
		else{
			client.interactionManager.clickSlot(0, restockFromSlot, player.getInventory().selectedSlot, SlotActionType.SWAP, player);
			Main.LOGGER.info("MapRestock: Swapped nextMap int inv.selectedSlot");
		}
	}

	MapHandRestock(boolean useName, boolean useImg){
		USE_NAME = useName;
		USE_IMG = useImg;
		UseEntityCallback.EVENT.register((player, _, hand, entity, _) -> {
			if(!(entity instanceof ItemFrameEntity itemFrame)) return ActionResult.PASS;
			//Main.LOGGER.info("clicked item frame");
			if(hand != Hand.MAIN_HAND){
				Main.LOGGER.info("not main hand");
				return ActionResult.FAIL;
			}
			//Main.LOGGER.info("placed item from offhand");
			if(!itemFrame.getHeldItemStack().isEmpty()) return ActionResult.PASS;
			//Main.LOGGER.info("item frame is empty");
			if(!isSingleMapArt(player.getStackInHand(hand))) return ActionResult.PASS;
			//Main.LOGGER.info("item in hand is single map");
			final ItemStack stack = player.getStackInHand(hand).copy();
			new Timer().schedule(new TimerTask(){@Override public void run(){tryToStockNextMap(player, hand, stack);}}, 50l);
			return ActionResult.PASS;
		});
	}
}