package net.evmodder;

/*recommended order:
public / private / protected
abstract
static
final
transient
volatile
**default**
synchronized
native
strictfp

*/

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.evmodder.EvLib.FileIO;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
// gradle genSources/eclipse/cleanloom/--stop
//MC source will be in ~/.gradle/caches/fabric-loom or ./.gradle/loom-cache
// gradle build --refresh-dependencies
// Fix broken eclipse build paths after updating loom,fabric-api,version in configs: gradle eclipse
public class KeyBound implements ClientModInitializer{
	//TODO:
	// Reference/depend on https://github.com/Siphalor/amecs-api

	// Feature Ideas:
	// Maps - smaller text for item count in slot
	// Maps - hotkey to mass-copy (copy every map 1->64, or 1->2)
	// steal activated spawner and similar stuff from trouser-streak?
	// /msgas Anuvin target hi - send msg from alt acc
	// timer countdown showing time left on 2b for non prio before kick
	// auto enchant dia sword, auto grindstone, auto rename, auto anvil combine

	// Reference variables
	public static final String MOD_ID = "keybound";
	//public static final String MOD_NAME = "KeyBound";
	//public static final String MOD_VERSION = "@MOD_VERSION@";
	public static final String KEYBIND_CATEGORY = "key.categories."+MOD_ID;


	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static HashMap<String, String> config;

	public static RemoteServerSender remoteSender;
	public static EpearlLookup epearlLookup;
	public static boolean rcTooltip;

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
	}

	@Override public void onInitializeClient(){
		loadConfig();
		//=================================== Loading config features
		HashMap<String, String> remoteMessages = new HashMap<>();
		int clientId=0; String clientKey=null;
		String remoteAddr=null; int remotePort=0;
		boolean epearlOwners=false, epearlOwnersDbUUID=false, epearlOwnersDbXZ=false;

		//config.forEach((key, value) -> {
		for(String key : config.keySet()){
			String value = config.get(key);
			if(key.startsWith("chat_msg.")) SimpleKeybindFeatures.registerChatKeybind(key, value);
			else if(key.startsWith("remote_msg.")) remoteMessages.put(key, value);
			else if(key.startsWith("organize_inventory.")) SimpleKeybindFeatures.registerInvOrganizeKeyBind(key, value.replaceAll("\\s",""));
			else switch(key){
				case "client_id": clientId = Integer.parseInt(value); break;
				case "client_key": clientKey = value; break;
				case "remote_addr": remoteAddr = value; break;
				case "remote_port": remotePort = Integer.parseInt(value); break;
//				case "spawner_highlight":
//					if(!value.equalsIgnoreCase("false"));
//					break;
				case "repaircost_tooltip":
					if(!value.equalsIgnoreCase("false")){rcTooltip = true; ItemTooltipCallback.EVENT.register(RepairCostTooltip::addRC);}
					break;
				case "enderpearl_owners":
					if(!value.equalsIgnoreCase("false")) epearlOwners = true;
					break;
				case "enderpearl_owners_database_by_uuid":
					if(!value.equalsIgnoreCase("false")) epearlOwnersDbUUID = true;
				case "enderpearl_owners_database_by_coords":
					if(!value.equalsIgnoreCase("false")) epearlOwnersDbXZ = true;
					break;
				case "keybind_drop_items":
					if(!value.equalsIgnoreCase("false")) JunkItemEjector.registerJunkEjectKeybind();
					break;
				case "keybind_toggle_skin_layers":
					if(!value.equalsIgnoreCase("false")) SimpleKeybindFeatures.registerSkinLayerKeybinds();
					break;
				case "seen_shared_database":
					if(!value.equalsIgnoreCase("false")) new SeenCommand();//TODO
					break;
				case "scroll_order": {
					final String listOfListsStr = value.replaceAll("\\s","");
					List<String[]> colorLists = Arrays.stream(listOfListsStr.substring(2, listOfListsStr.length()-2).split("\\],\\[")).map(s->s.split(",")).toList();
					new VariantHotbarScroller(colorLists);
					break;
				}
				default:
					LOGGER.warn("Unrecognized config setting: "+key);
			}
		}
		if(epearlOwners) epearlLookup = new EpearlLookup(epearlOwnersDbUUID, epearlOwnersDbXZ);
		if(clientId != 0 && clientKey != null && remoteAddr != null && remotePort != 0 && (!remoteMessages.isEmpty() || epearlOwnersDbUUID || epearlOwnersDbXZ)){
			remoteSender = new RemoteServerSender(remoteAddr, remotePort, clientId, clientKey, remoteMessages);
		}
	}
}