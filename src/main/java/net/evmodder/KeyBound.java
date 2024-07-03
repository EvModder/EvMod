package net.evmodder;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerModelPart;

// Export jar from Terminal:
// gradle build --refresh-dependencies
public class KeyBound implements ClientModInitializer{
	//public static final Config CONFIG = Config.createAndLoad();

	// Reference variables
	public static final String MOD_ID = "keybound";
	public static final String MOD_NAME = "KeyBound";
	public static final String MOD_VERSION = "@MOD_VERSION@";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);
	private static HashMap<String, String> config;

	private record SkinLayerKeybind(KeyBinding keybind, ArrayList<PlayerModelPart> parts){}
	private record ChatMsgKeybind(KeyBinding keybind, String message){}
	private record BotMsgKeybind(KeyBinding keybind, UUID message){}
	private List<SkinLayerKeybind> skinKeybinds;
	private List<ChatMsgKeybind> chatMsgKeybinds;
	private List<BotMsgKeybind> botMsgKeybinds;
	private int CLIENT_ID;
	private String CLIENT_KEY;

	private void loadSkinKeybind(final HashMap<Integer, ArrayList<PlayerModelPart>> skinKeybindLookup, final String key){
		//ASSERT key.startsWith("skin_toggle_")
		try{
			final int glfwKey = GLFW.class.getField("GLFW_"+config.get(key).toUpperCase()).getInt(null);
			final PlayerModelPart part = PlayerModelPart.valueOf(key.substring("skin_toggle_".length()).toUpperCase());
			final String translationCategoryKey = "category."+MOD_ID+".skin_toggles";
			final String translationKey = "key."+MOD_ID+"."+key.toLowerCase();

			if(skinKeybindLookup.containsKey(glfwKey)){
				skinKeybindLookup.get(glfwKey).add(part);
			}
			else{
				final ArrayList<PlayerModelPart> parts = new ArrayList<>(List.of(part));
				skinKeybinds.add(new SkinLayerKeybind(
						KeyBindingHelper.registerKeyBinding(new KeyBinding(translationKey, InputUtil.Type.KEYSYM, glfwKey, translationCategoryKey)),
						parts
				));
				skinKeybindLookup.put(glfwKey, parts);
			}
		}
		catch(IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e){
			e.printStackTrace();
			LOGGER.info(e.getMessage());
		}
	}
	private void loadChatMessageKeybind(final HashMap<String, String> chatMsgs, String key){
		//ASSERT key.startsWith("chat_msg_keybind_")
		final String msg_name = key.substring("chat_msg_keybind_".length());
		final String message = chatMsgs.get(msg_name);
		if(message == null){
			LOGGER.error("Unknown chat message '"+msg_name+"', please specify it in the config somewhere *before* the keybind");
			return;
		}
		try{
			final int glfwKey = GLFW.class.getField("GLFW_"+config.get(key).toUpperCase()).getInt(null);
			final String translationCategoryKey = "category."+MOD_ID+".chat_messages";
			final String translationKey = "key."+MOD_ID+".chat_msg_"+msg_name;
			chatMsgKeybinds.add(new ChatMsgKeybind(
					KeyBindingHelper.registerKeyBinding(new KeyBinding(translationKey, InputUtil.Type.KEYSYM, glfwKey, translationCategoryKey)),
					message
			));
			//LOGGER.info("added chat msg keybind "+config.get(key).toUpperCase()+" for: "+message);
		}
		catch(IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e){
			e.printStackTrace();
			LOGGER.info(e.getMessage());
		}
	}
	private void loadBotMessageKeybind(final HashMap<String, UUID> botMsgs, String key){
		//ASSERT key.startsWith("bot_msg_keybind_")
		final String msg_name = key.substring("bot_msg_keybind_".length());
		final UUID message = botMsgs.get(msg_name);
		if(message == null){
			LOGGER.error("Unknown bot message '"+msg_name+"', please specify it in the config somewhere *before* the keybind");
			return;
		}
		try{
			final int glfwKey = GLFW.class.getField("GLFW_"+config.get(key).toUpperCase()).getInt(null);
			final String translationCategoryKey = "category."+MOD_ID+".bot_messages";
			final String translationKey = "key."+MOD_ID+".bot_msg_"+msg_name;
			botMsgKeybinds.add(new BotMsgKeybind(
					KeyBindingHelper.registerKeyBinding(new KeyBinding(translationKey, InputUtil.Type.KEYSYM, glfwKey, translationCategoryKey)),
					message
			));
			//LOGGER.info("added bot msg keybind "+config.get(key).toUpperCase()+" for: "+message);
		}
		catch(IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e){
			e.printStackTrace();
			LOGGER.info(e.getMessage());
		}
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

		skinKeybinds = new ArrayList<>();
		chatMsgKeybinds = new ArrayList<>();
		botMsgKeybinds = new ArrayList<>();

		final HashMap<Integer, ArrayList<PlayerModelPart>> skinKeybindLookup = new HashMap<>();
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
			if(key.startsWith("skin_toggle_")){
				loadSkinKeybind(skinKeybindLookup, key);
			}
			else if(key.startsWith("chat_msg_keybind_")){
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
		loadConfig();
		//============================== Listen for key presses ==============================//
		ClientTickEvents.END_CLIENT_TICK.register(client->{
			for(SkinLayerKeybind data : skinKeybinds){
				if(data.keybind.wasPressed()){
					for(PlayerModelPart part : data.parts){
						//client.player.sendMessage(net.minecraft.text.Text.literal("Key was pressed! part:"+part.name().toLowerCase()), false);
						client.options.togglePlayerModelPart(part, !client.options.isPlayerModelPartEnabled(part));
					}
				}
			}
			for(ChatMsgKeybind data : chatMsgKeybinds){
				if(data.keybind.wasPressed()){
					if(data.message.charAt(0) == '/'){
						//client.player.sendMessage(net.minecraft.text.Text.literal("cmd"), false);
						client.player.networkHandler.sendChatCommand(data.message.substring(1));
					}
					else{
						//client.player.sendMessage(net.minecraft.text.Text.literal("msg"), false);
						client.player.networkHandler.sendChatMessage(data.message);
					}
				}
			}
			for(BotMsgKeybind data : botMsgKeybinds){
				if(data.keybind.wasPressed()){
					//client.player.sendMessage(net.minecraft.text.Text.literal("Key was pressed! bot message:"+data.message), false);
					sendBotMessage(client.player.getUuid(), data.message);
				}
			}
//			while(keybindPearl != null && keybindPearl.wasPressed()){
//				sendEpearlPacket(client.player.getUuid());
//			}
//			if(stickyBinding.isPressed()){
//				client.player.sendMessage(Text.literal("Sticky Key was pressed!"), false);
//			}
		});
	}

//	private static KeyBinding kb_Cape = KeyBindingHelper.registerKeyBinding(new KeyBinding(
//	"key.keybound.toggle_cape", // The translation key of the keybinding's name.
//	InputUtil.Type.KEYSYM, // The type of the keybinding, KEYSYM for keyboard, MOUSE for mouse.
//	GLFW.GLFW_KEY_I, // The keycode of the key.
//	"category.keybound.skin_toggles")); // The translation key of the keybinding's category.
//
//	// Behaves like Vanilla's Sneak and Sprint when set to 'Toggle'
//	private static KeyBinding stickyBinding = KeyBindingHelper.registerKeyBinding(new StickyKeyBinding(
//				"key.keybound.somethingsomething",
//				GLFW.GLFW_KEY_H,
//				"keybound.category.somethingsomething",
//				()->true));
}