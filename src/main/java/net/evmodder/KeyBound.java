package net.evmodder;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.InputUtil.Type;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.player.PlayerModelPart;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;

// Export jar from Terminal:
// gradle build --refresh-dependencies
// Fix broken eclipse build paths after updating loom,fabric-api,version in configs: gradle eclipse
public class KeyBound implements ClientModInitializer{
	//TODO:
	// Reference/depend on https://github.com/Siphalor/amecs-api

	// Reference variables
	public static final String MOD_ID = "keybound";
	public static final String MOD_NAME = "KeyBound";
	//public static final String MOD_VERSION = "@MOD_VERSION@";
	private final String SKIN_LAYER_CATEGORY = "category."+MOD_ID+".skin_toggles";
	private final String COLOR_SCROLL_CATEGORY = "category."+MOD_ID+".color_scroll";
	private final String CHAT_MSG_CATEGORY = "category."+MOD_ID+".chat_messages";
	private final String BOT_MSG_CATEGORY = "category."+MOD_ID+".bot_messages";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);
	private static HashMap<String, String> config;

	private int CLIENT_ID;
	private String CLIENT_KEY;

	private List<String[]> variantLists;
	// {*_wool"->[white,red,...], *_coral_fan->[tube,brain,...], rail->[rail,powered_rail,...], *_rail->[rail,powered_rail,...]}
	// replace * with variant_name
	private HashMap<String, String[]> scrollableItems;

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
				//client.player.sendMessage(net.minecraft.text.Text.literal("Key was pressed! part:"+part.name().toLowerCase()));
			}
		}).forEach(KeyBindingHelper::registerKeyBinding);
	}
	private void registerColorScrollKeybinds(){
		Stream.of(true, false).map(k -> new EvKeyBinding("key."+MOD_ID+".color_scroll_"+(k ? "up" : "down"), InputUtil.Type.KEYSYM, -1, COLOR_SCROLL_CATEGORY){
			@Override public void onPressed(){
				final MinecraftClient client = MinecraftClient.getInstance();
				PlayerInventory inventory = client.player.getInventory();
				if(!PlayerInventory.isValidHotbarIndex(inventory.selectedSlot)) return;
				ItemStack is = inventory.getMainHandStack();
				Identifier id = Registries.ITEM.getId(is.getItem());
				if(!ItemStack.areItemsAndComponentsEqual(is, new ItemStack(Registries.ITEM.get(id)))) return;  // don't scroll if has custom NBT
				String path = id.getPath();
				String[] colors = scrollableItems.get(id.toString());//e.g., "rail" -> [,powered,detector,activator]"
				int i = 0;
				if(colors == null) for(String[] cs : variantLists){
					for(i=0; !path.contains(cs[i]+"_") && ++i < cs.length;);
					if(i != cs.length && scrollableItems.get(id.getNamespace()+":"+path.replace(cs[i], "*")) == cs){
						colors = cs; break;
					}
				}
				if(colors == null) return; // not a supported item (eg: "red_sandstone")

				int new_i = k ? (i == colors.length-1 ? 0 : i+1) : (i == 0 ? colors.length-1 : i-1);
				id = Identifier.of(id.getNamespace(), id.getPath().replace(colors[i], colors[new_i]));
				if(client.player.isInCreativeMode()){
					inventory.setStack(inventory.selectedSlot, new ItemStack(Registries.ITEM.get(id), is.getCount()));
				}
				else{//  survival mode
					do{
						int j = 0;
						for(; j<inventory.main.size(); ++j){
							ItemStack jis = inventory.main.get(j);
							if(jis.isEmpty()) continue;
							Identifier jid = Registries.ITEM.getId(jis.getItem());
							if(!jid.equals(id)) continue;
							if(!ItemStack.areItemsAndComponentsEqual(jis, new ItemStack(Registries.ITEM.get(jid)))) continue;
							//found an item to use
							break;
						}
						if(j != inventory.main.size()){
							//use the item (change selected hotbar slot or swap with main inv)
							if(PlayerInventory.isValidHotbarIndex(j)) inventory.selectedSlot = j;
							else{
								inventory.main.set(inventory.selectedSlot, inventory.main.get(j));
								inventory.main.set(j, is);
							}
							LOGGER.error("did swap");
							break;
						}
						new_i = k ? (new_i == colors.length-1 ? 0 : new_i+1) : (new_i == 0 ? colors.length-1 : new_i-1);
						id = Identifier.of(id.getNamespace(), id.getPath().replace(colors[i], colors[new_i]));
					}while(new_i != i);
					LOGGER.error("full wrap-around: "+i);
				}
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

	private boolean loadScrollableItems(String[] colors){
		if(colors.length < 2){
			LOGGER.warn("Scroll list is too short: "+colors.toString());
			return false;
		}
		final boolean hasEmpty = colors[0].isEmpty();//TODO: icky
		final String colorA = hasEmpty ? colors[1] : colors[0];
		final String[] colorsB = Arrays.copyOfRange(colors, hasEmpty? 2 : 1, colors.length);

		Stream<Identifier> s = Registries.ITEM.getIds().stream().filter(id -> id.getPath().contains(colorA));
		if(hasEmpty) s = s.filter(id -> Registries.ITEM.containsId(Identifier.of(id.getNamespace(), id.getPath().replace(colorA+"_", ""))));//TODO: icky
		s = s.filter(id -> Arrays.stream(colorsB).allMatch(b -> Registries.ITEM.containsId(Identifier.of(id.getNamespace(), id.getPath().replace(colorA, b)))));
		List<Identifier> ids = s.toList();
		if(ids.isEmpty()) return false;
		ids.stream().map(id -> id.getNamespace()+":"+id.getPath().replace(colorA, "*")).forEach(name -> {
			scrollableItems.put(name, colors);
			if(hasEmpty) scrollableItems.put(name.replace("*_", ""), colors);//TODO: icky
		});
		return true;
	}

	private void loadConfig(){
		config = new HashMap<>();
		final String configContents = FileIO.loadFile("keybound.txt", getClass().getResourceAsStream("/keybound.txt"));
		String listKey = null, listValue = null;
		int listDepth = 0;
		for(String line : configContents.split("\\r?\\n")){
			if(listKey != null){
				line = line.trim();
				listValue += line;
				listDepth += StringUtils.countMatches(line, '[') - StringUtils.countMatches(line, ']');
				if(listDepth == 0 && line.charAt(line.length()-1) == ']'){
					config.put(listKey, listValue);
					listKey = null;
				}
				continue;
			}
			final int sep = line.indexOf(':');
			if(sep == -1) continue;
			final String key = line.substring(0, sep).trim();
			final String value = line.substring(sep+1).trim();
			if(key.isEmpty() || value.isEmpty()) continue;
			if(value.charAt(0) == '[' && value.charAt(value.length()-1) != ']'){
				listDepth = StringUtils.countMatches(value, '[') - StringUtils.countMatches(value, ']');
				listKey = key; listValue = value;
			}
			config.put(key, value);
		}
		if(listKey != null) LOGGER.error("Unterminated list in ./config/keybound.txt!\nkey: "+listKey);

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
			else if(key.startsWith("chat_msg_"));  // These are handled above
			else if(key.startsWith("bot_msg_"));  //
			else switch(key){
				case "client_id":
					CLIENT_ID = Integer.parseInt(config.get(key));
					break;
				case "client_key":
					CLIENT_KEY = config.get(key);
					break;
				case "repaircost_tooltip":
					if(!config.get(key).equalsIgnoreCase("false")) ItemTooltipCallback.EVENT.register(RepairCostTooltip::addRC);
					break;
				case "scroll_order":
					final String listOfListsStr = config.get(key).replaceAll("\\s","");
					final String[] listOfListStrs = listOfListsStr.substring(2, listOfListsStr.length()-2).split("\\],\\[");
					variantLists = new ArrayList<>();
					scrollableItems = new HashMap<>();
					for(String listStr : listOfListStrs){
						final String[] variants = listStr.split(",");
						if(loadScrollableItems(variants)) variantLists.add(variants);
						else LOGGER.warn("Could not find items for the given scroll list: ["+listStr+"]");
					}
					LOGGER.debug("Defined scrollable variants: ["+String.join("], [", variantLists.stream().map(l -> String.join(",", l)).toList())+"]");
					LOGGER.debug("Found matching items: "+String.join(", ", scrollableItems.keySet()));
					if(!scrollableItems.isEmpty()) registerColorScrollKeybinds();
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