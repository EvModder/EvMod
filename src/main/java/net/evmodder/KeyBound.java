package net.evmodder;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.InputUtil.Type;
import net.minecraft.entity.player.PlayerModelPart;

// Export jar from Terminal:
// gradle build --refresh-dependencies
public class KeyBound implements ClientModInitializer{
	//TODO:
	// Reference/depend on https://github.com/Siphalor/amecs-api

	// Reference variables
	public static final String MOD_ID = "keybound";
	public static final String MOD_NAME = "KeyBound";
	public static final String MOD_VERSION = "@MOD_VERSION@";
	private final String SKIN_LAYER_CATEGORY = "category."+MOD_ID+".skin_toggles";
	private final String CHAT_MSG_CATEGORY = "category."+MOD_ID+".chat_messages";
	private final String BOT_MSG_CATEGORY = "category."+MOD_ID+".bot_messages";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);
	private static HashMap<String, String> config;

	private int CLIENT_ID;
	private String CLIENT_KEY;

	private static class EvKeyBinding extends KeyBinding{
		public EvKeyBinding(String translationKey, Type type, int code, String category){super(translationKey, type, code, category);}
		@Override public void setPressed(boolean pressed){
			super.setPressed(pressed);
			if(pressed) onPressed();
			else onReleased();
		}
		public void onPressed(){}
		public void onReleased(){}
	}

	private void registerSkinLayerKeybinds(){
		Arrays.stream(PlayerModelPart.values())
		.map(part -> new EvKeyBinding("key."+MOD_ID+".skin_toggle_"+part.name().toLowerCase(), InputUtil.Type.KEYSYM, -1, SKIN_LAYER_CATEGORY){
			@Override public void onPressed(){
				final MinecraftClient client = MinecraftClient.getInstance();
				client.options.togglePlayerModelPart(part, !client.options.isPlayerModelPartEnabled(part));
			}
		}).forEach(KeyBindingHelper::registerKeyBinding);
	}

	private void loadChatMessageKeybind(final HashMap<String, String> chatMsgs, String key){
		//ASSERT key.startsWith("chat_msg_keybind_")
		final String msg_name = key.substring("chat_msg_keybind_".length());
		final String message = chatMsgs.get(msg_name);
		if(message == null){
			LOGGER.error("Unknown chat message '"+msg_name+"', please specify it in the config somewhere *before* the keybind");
			return;
		}
		final String translationKey = "key."+MOD_ID+".chat_msg_"+msg_name;
		if(message.charAt(0) == '/'){
			final String command = message.substring(1);
			KeyBindingHelper.registerKeyBinding(new EvKeyBinding(translationKey, InputUtil.Type.KEYSYM, -1, CHAT_MSG_CATEGORY){
				@Override public void onPressed(){
					MinecraftClient instance = MinecraftClient.getInstance();
					instance.player.networkHandler.sendChatCommand(command);
				}
			});
		}
		else{
			KeyBindingHelper.registerKeyBinding(new EvKeyBinding(translationKey, InputUtil.Type.KEYSYM, -1, CHAT_MSG_CATEGORY){
				@Override public void onPressed(){
					MinecraftClient instance = MinecraftClient.getInstance();
					instance.player.networkHandler.sendChatMessage(message);
				}
			});
		}
		//LOGGER.info("added chat msg keybind "+config.get(key).toUpperCase()+" for: "+message);
	}
	private void loadBotMessageKeybind(final HashMap<String, UUID> botMsgs, String key){
		//ASSERT key.startsWith("bot_msg_keybind_")
		final String msg_name = key.substring("bot_msg_keybind_".length());
		final UUID message = botMsgs.get(msg_name);
		if(message == null){
			LOGGER.error("Unknown bot message '"+msg_name+"', please specify it in the config somewhere *before* the keybind");
			return;
		}
		final String translationKey = "key."+MOD_ID+".bot_msg_"+msg_name;
		KeyBindingHelper.registerKeyBinding(new EvKeyBinding(translationKey, InputUtil.Type.KEYSYM, -1, BOT_MSG_CATEGORY){
			@Override public void onPressed(){
				MinecraftClient instance = MinecraftClient.getInstance();
				sendBotMessage(instance.player.getUuid(), message);
			}
		});
		//LOGGER.info("added bot msg keybind "+config.get(key).toUpperCase()+" for: "+message);
	}

	private void loadConfig(){
		config = new HashMap<>();
		final String configContents = FileIO.loadFile("keybound.txt", getClass().getResourceAsStream("/keybound.txt"));
		for(String line : configContents.split("\\r?\\n")){
			final int sep = line.indexOf(':');
			if(sep == -1) continue;
			final String key = line.substring(0, sep).trim();
			final String value = line.substring(sep+1).trim();
			if(key.isEmpty() || value.isEmpty()) continue;
			config.put(key, value);
		}

		final HashMap<String, String> chatMsgLookup = new HashMap<>();
		final HashMap<String, UUID> botMsgLookup = new HashMap<>();
		for(String key : config.keySet()){
			if(key.startsWith("chat_msg_") && !key.startsWith("keybind_", "chat_msg_".length())){
				chatMsgLookup.put(key.substring("chat_msg_".length()), config.get(key));
			}
			else if(key.startsWith("bot_msg_") && !key.startsWith("keybind_", "bot_msg_".length())){
				botMsgLookup.put(key.substring("bot_msg_".length()), UUID.fromString(config.get(key)));
			}
		}
		for(String key : config.keySet()){
			if(key.startsWith("chat_msg_keybind_")){
				loadChatMessageKeybind(chatMsgLookup, key);
			}
			else if(key.startsWith("bot_msg_keybind_")){
				loadBotMessageKeybind(botMsgLookup, key);
			}
			else switch(key){
				case "client_id":
					CLIENT_ID = Integer.parseInt(config.get(key));
					break;
				case "client_key":
					CLIENT_KEY = config.get(key);
					break;
				default:
					LOGGER.warn("Unrecognized config setting: "+key);
			}
		}
	}

	private void sendBotMessage(UUID uuid, UUID message){
		ByteBuffer bb1 = ByteBuffer.allocate(16+16);
		bb1.putLong(uuid.getMostSignificantBits());
		bb1.putLong(uuid.getLeastSignificantBits());
		bb1.putLong(message.getMostSignificantBits());
		bb1.putLong(message.getLeastSignificantBits());
		byte[] encryptedUUID = PacketHelper.encrypt(bb1.array(), CLIENT_KEY);
		//LOGGER.info("bytes length: "+encryptedUUID.length);

		ByteBuffer bb2 = ByteBuffer.allocate(4+32);
		bb2.putInt(CLIENT_ID);
		bb2.put(encryptedUUID);
		PacketHelper.sendPacket(bb2.array());
	}

	@Override
	public void onInitializeClient(){
		registerSkinLayerKeybinds();
		loadConfig();
	}
}