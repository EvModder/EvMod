package net.evmodder.KeyBound.Keybinds;

import java.util.Arrays;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerModelPart;

public final class KeybindsSimple{
	//private static final String SKIN_LAYER_CATEGORY = "key.categories."+KeyBound.MOD_ID+".skin_toggles";
	//private static final String CHAT_MSG_CATEGORY = "key.categories."+KeyBound.MOD_ID+".chat_messages";

	public static final void registerSkinLayerKeybinds(){
		Arrays.stream(PlayerModelPart.values())
		.map(part -> new EvKeybind("skin_toggle."+part.name().toLowerCase(), ()->{
			final MinecraftClient client = MinecraftClient.getInstance();
			client.options.setPlayerModelPart(part, !client.options.isPlayerModelPartEnabled(part));
		})).forEach(KeyBindingHelper::registerKeyBinding);
	}

	public static final void registerChatKeybind(String keybind_name, String chat_message){
		if(chat_message.charAt(0) == '/'){
			final String command = chat_message.substring(1);
			KeyBindingHelper.registerKeyBinding(new EvKeybind(keybind_name, ()->{
				MinecraftClient instance = MinecraftClient.getInstance();
				instance.player.networkHandler.sendChatCommand(command);
			}));
		}
		else{
			KeyBindingHelper.registerKeyBinding(new EvKeybind(keybind_name, ()->{
				MinecraftClient instance = MinecraftClient.getInstance();
				instance.player.networkHandler.sendChatMessage(chat_message);
			}));
		}
	}
}