package net.evmodder.evmod.apis;

import java.util.UUID;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.Main;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStackTemplate;

public final class NewMapNotifier{
	private static long lastNewMapNotify;
	private static final long mapNotifyCooldown = 5000;
	private static int lastNewMapIfeId;
	private static long lastNewMapColorsId;
	private static boolean notifyInChat = true;

	public static final void call(final ItemFrame ife, final UUID colorsId){ // Called by UpdateItemFrameContents
		if(ife.getId() != lastNewMapIfeId && colorsId.getMostSignificantBits() == lastNewMapColorsId) return;
		if(System.currentTimeMillis() - lastNewMapNotify < mapNotifyCooldown) return;

		final boolean isFar = ife.blockPosition().distToLowCornerSqr(0, 0, 0) > 20_000d*20_000d;
		final int x = ife.getBlockX(), z = ife.getBlockZ();
		final String pos = (isFar ? ".."+Math.abs(x%1000) : x)+" "+ife.getBlockY()+" "+(isFar ? ".."+Math.abs(z%1000) : z);

		final int color = Configs.Visuals.MAP_COLOR_NOT_IN_GROUP.getIntegerValue();
		Minecraft.getInstance().player.sendOverlayMessage(Component.literal("New mapart: "+pos).withColor(color));

		if(colorsId.getMostSignificantBits() != lastNewMapColorsId){
			Main.LOGGER.info("NewMapNotifier: "+colorsId+" ("+ife.getItem().getHoverName().getString()+") at "+pos);

			if(notifyInChat){
				Minecraft.getInstance().player.sendSystemMessage(
						Component.literal("New mapart: "+pos+" \u2398 ")
						.withColor(color)
						// Click to copy coords
						.withStyle(style -> style.withClickEvent(new ClickEvent.CopyToClipboard(x+" "+ife.getBlockY()+" "+z)))
						// Hover shows map itemname
						.withStyle(style -> style.withHoverEvent(new HoverEvent.ShowItem(ItemStackTemplate.fromStack(ife.getItem()))))
						);
			}
//			if(playSound){
//			}
		}
		lastNewMapNotify = System.currentTimeMillis();
		lastNewMapColorsId = colorsId.getMostSignificantBits();
		lastNewMapIfeId = ife.getId();
	}
}