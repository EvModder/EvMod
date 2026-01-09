package net.evmodder.evmod.apis;

import java.util.UUID;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.Main;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;

public final class NewMapNotifier{
	private static long lastNewMapNotify;
	private static final long mapNotifyCooldown = 5000;
	private static int lastNewMapIfeId;
	private static long lastNewMapColorId;
	private static boolean notifyInChat = true;

	public static final void call(ItemFrameEntity ife, UUID colorsId){ // Called by UpdateItemFrameHighlights
		if(ife.getId() != lastNewMapIfeId && colorsId.getMostSignificantBits() == lastNewMapColorId) return;
		if(System.currentTimeMillis() - lastNewMapNotify < mapNotifyCooldown) return;

		// TODO: play sound?
		boolean isFar = ife.getBlockPos().getSquaredDistance(0, 0, 0) > 20_000d*20_000d;
		int x = ife.getBlockX(), z = ife.getBlockZ();
		String pos = (isFar ? ".."+Math.abs(x%1000) : x)+" "+ife.getBlockY()+" "+(isFar ? ".."+Math.abs(z%1000) : z);

		int color = Configs.Visuals.MAP_COLOR_NOT_IN_GROUP.getIntegerValue();
		MinecraftClient.getInstance().player.sendMessage(Text.literal("New mapart: "+pos).withColor(color), true);

		if(colorsId.getMostSignificantBits() != lastNewMapColorId){
			Main.LOGGER.info("NewMapNotifier: "+colorsId+" ("+ife.getHeldItemStack().getName().getString()+") at "+pos);

			if(notifyInChat){
				MinecraftClient.getInstance().player.sendMessage(
						Text.literal("New mapart: "+pos)
						// unicode for symbol ⎘
						.append(Text.literal(" \u2398 ").styled(style -> style.withClickEvent(new ClickEvent.CopyToClipboard(pos))))
						// Hover shows map itemname
						.withColor(color)
						.styled(style -> style.withHoverEvent(new HoverEvent.ShowText(ife.getHeldItemStack().getName())))
						, /*actionbar=*/false);
			}
		}
		lastNewMapNotify = System.currentTimeMillis();
		lastNewMapColorId = colorsId.getMostSignificantBits();
		lastNewMapIfeId = ife.getId();
	}
}