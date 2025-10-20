package net.evmodder.KeyBound.keybinds;

import java.util.Arrays;
import net.evmodder.KeyBound.Main;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerModelPart;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

public final class KeybindsSimple{
	//private static final String SKIN_LAYER_CATEGORY = "key.categories."+KeyBound.MOD_ID+".skin_toggles";
	//private static final String CHAT_MSG_CATEGORY = "key.categories."+KeyBound.MOD_ID+".chat_messages";
	private static final boolean SYNC_CAPE_WITH_ELYTRA = true;

	public static final void registerSkinLayerKeybinds(){
		Arrays.stream(PlayerModelPart.values())
		.forEach(part -> new Keybind("skin_toggle."+part.name().toLowerCase(), ()->{
			//Main.LOGGER.info("skin toggle pressed for part: "+part.name());
			final MinecraftClient client = MinecraftClient.getInstance();
			if(SYNC_CAPE_WITH_ELYTRA && part == PlayerModelPart.CAPE && client.player != null && client.options.isPlayerModelPartEnabled(part)){
				ItemStack chestItem = client.player.getInventory().getArmorStack(2);
				// Don't disable cape if we just switched to an elytra
				if(Registries.ITEM.getId(chestItem.getItem()).getPath().equals("elytra")) return;
			}
			client.options.setPlayerModelPart(part, !client.options.isPlayerModelPartEnabled(part));
			client.options.sendClientSettings();
			//Main.LOGGER.info("new value for part "+part.name()+": "+client.options.isPlayerModelPartEnabled(part));
		}));
	}

	public static final void registerSnapAngleKeybind(String keybind_name, String yaw_pitch){
		final int i = yaw_pitch.indexOf(',');
		if(i == -1) Main.LOGGER.error("Invalid yaw,pitch for "+keybind_name+": "+yaw_pitch);
		final float yaw, pitch;
		try{
			yaw = Float.parseFloat(yaw_pitch.substring(0, i).trim());
			pitch = Float.parseFloat(yaw_pitch.substring(i+1).trim());
		}
		catch(NumberFormatException e){
			Main.LOGGER.error("Invalid number value in yaw,pitch for "+keybind_name+": "+yaw_pitch);
			return;
		}
		new Keybind(keybind_name, ()->{
			MinecraftClient instance = MinecraftClient.getInstance();
			instance.player.setAngles(yaw, pitch);
		});
	}
}