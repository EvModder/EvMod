package net.evmodder;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
// gradle genSources/eclipse/cleanloom/--stop
//MC source will be in ~/.gradle/caches/fabric-loom or ./.gradle/loom-cache
// gradle build --refresh-dependencies
// Fix broken eclipse build paths after updating loom,fabric-api,version in configs: gradle eclipse
public class KeyBound implements ClientModInitializer{
	//TODO:
	// Reference/depend on https://github.com/Siphalor/amecs-api

	// Reference variables
	public static final String MOD_ID = "keybound";
	public static final String MOD_NAME = "KeyBound";
	//public static final String MOD_VERSION = "@MOD_VERSION@";


	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);
	private static HashMap<String, String> config;

	private void loadConfig(){
		//=================================== Parsing config into a map
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

		//=================================== Loading config features

		HashMap<String, UUID> remoteMessages = new HashMap<>();
		int clientId=0; String clientKey=null;
		String remoteAddr=null; int remotePort=0;

		//config.forEach((key, value) -> {
		for(String key : config.keySet()){
			String value = config.get(key);
			if(key.startsWith("chat_msg.")) SimpleKeybindFeatures.registerChatKeybind(key, value);
			else if(key.startsWith("remote_msg.")) remoteMessages.put(key, UUID.fromString(value));
			else switch(key){
				case "client_id": clientId = Integer.parseInt(value); break;
				case "client_key": clientKey = value; break;
				case "remote_addr": remoteAddr = value; break;
				case "remote_port": remotePort = Integer.parseInt(value); break;
				case "repaircost_tooltip":
					if(!value.equalsIgnoreCase("false")) ItemTooltipCallback.EVENT.register(RepairCostTooltip::addRC);
					break;
				case "enderpearl_owners":
					if(!value.equalsIgnoreCase("false"));//Enable ePearl mixin
					break;
				case "scroll_order": {
					final String listOfListsStr = config.get(key).replaceAll("\\s","");
					List<String[]> colorLists = Arrays.stream(listOfListsStr.substring(2, listOfListsStr.length()-2).split("\\],\\[")).map(s->s.split(",")).toList();
					new VariantHotbarScroller(colorLists);
					break;
				}
				default:
					LOGGER.warn("Unrecognized config setting: "+key);
			}
		}
		if(clientId != 0 && clientKey != null && remoteAddr != null && remotePort != 0 && !remoteMessages.isEmpty()){
			new RemoteServerSender(remoteAddr, remotePort, clientId, clientKey, remoteMessages);
		}
	}

	@Override public void onInitializeClient(){
		SimpleKeybindFeatures.registerSkinLayerKeybinds();
		JunkItemEjector.registerJunkEjectKeybind();
		loadConfig();
	}
}