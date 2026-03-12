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
	private static long lastNewMapColorsId;
	private static boolean notifyInChat = true;

	public static final void call(final ItemFrameEntity ife, final UUID colorsId){ // Called by UpdateItemFrameContents
		if(ife.getId() != lastNewMapIfeId && colorsId.getMostSignificantBits() == lastNewMapColorsId) return;
		if(System.currentTimeMillis() - lastNewMapNotify < mapNotifyCooldown) return;

		final boolean isFar = ife.getBlockPos().getSquaredDistance(0, 0, 0) > 20_000d*20_000d;
		final int x = ife.getBlockX(), z = ife.getBlockZ();
		final String pos = (isFar ? ".."+Math.abs(x%1000) : x)+" "+ife.getBlockY()+" "+(isFar ? ".."+Math.abs(z%1000) : z);

		final int color = Configs.Visuals.MAP_COLOR_NOT_IN_GROUP.getIntegerValue();
		MinecraftClient.getInstance().player.sendMessage(Text.literal("New mapart: "+pos).withColor(color), true);

		if(colorsId.getMostSignificantBits() != lastNewMapColorsId){
			Main.LOGGER.info("NewMapNotifier: "+colorsId+" ("+ife.getHeldItemStack().getName().getString()+") at "+pos);

			if(notifyInChat){
				MinecraftClient.getInstance().player.sendMessage(
//						Text.literal("New mapart: "+pos)
//						// unicode for symbol ⎘
//						.append(Text.literal(" \u2398 ").styled(...COPY_TO_CLIPBOARD))
						Text.literal("New mapart: "+pos+" \u2398 ")
						.withColor(color)
						// Click to copy coords
						.styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD,x+" "+ife.getBlockY()+" "+z)))
						// Hover shows map itemname
						.styled(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, ife.getHeldItemStack().getName())))
						, /*actionbar=*/false);
			}
//			if(playSound){
//			}
		}
		lastNewMapNotify = System.currentTimeMillis();
		lastNewMapColorsId = colorsId.getMostSignificantBits();
		lastNewMapIfeId = ife.getId();
	}
}