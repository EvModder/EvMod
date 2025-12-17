package net.evmodder.evmod.apis;

import java.util.UUID;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.Main;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;

public final class NewMapNotifier{
	private static long lastNewMapNotify;
	private static final long mapNotifyCooldown = 5000;
	private static int lastNewMapIfeId;
	private static long lastNewMapColorId;
	private static boolean notifyInChat = true;

	public static final void call(ItemFrameEntity ife, UUID colorsId){ // Called by MixinItemFrameRenderer
		if(ife.getId() != lastNewMapIfeId && colorsId.getMostSignificantBits() == lastNewMapColorId) return;
		if(System.currentTimeMillis() - lastNewMapNotify < mapNotifyCooldown) return;
		lastNewMapNotify = System.currentTimeMillis();
		lastNewMapColorId = colorsId.getMostSignificantBits();
		lastNewMapIfeId = ife.getId();

		// TODO: play sound?
		String pos = ife.getBlockX()+" "+ife.getBlockY()+" "+ife.getBlockZ();

		Main.LOGGER.info("NewMapNotifier: "+colorsId+" ("+ife.getHeldItemStack().getName().getString()+") at "+pos);
		MinecraftClient.getInstance().player.sendMessage(Text.literal("New mapart: "+pos)
				.withColor(Configs.Visuals.MAP_COLOR_NOT_IN_GROUP.getIntegerValue()), true);
		if(notifyInChat){
			MinecraftClient.getInstance().player.sendMessage(Text.literal("New mapart: "+pos+" \u2398") // unicode for symbol ⎘
					.withColor(Configs.Visuals.MAP_COLOR_NOT_IN_GROUP.getIntegerValue())
					.styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, pos))), false);
		}
	}
}