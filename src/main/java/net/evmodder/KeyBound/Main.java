package net.evmodder.KeyBound;

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
import net.evmodder.KeyBound.Commands.*;
import net.evmodder.KeyBound.Keybinds.*;
import net.evmodder.KeyBound.Keybinds.KeybindsSimple;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.item.ItemRenderer;
// gradle genSources/eclipse/cleanloom/--stop
//MC source will be in ~/.gradle/caches/fabric-loom or ./.gradle/loom-cache
// gradle build --refresh-dependencies
// Fix broken eclipse build paths after updating loom,fabric-api,version in configs: gradle eclipse
public class Main implements ClientModInitializer{
	//TODO:
	// Reference/depend on https://github.com/Siphalor/amecs-api
	// majorly improve TravelHelper (mining blocks only in way, specifically non-diag & mining 3 high tunnel)

	// Feature Ideas:
	// totems in offhand - render itemcount for total totems in inv
	// Maps - smaller text for item count in slot
	// Map - next hand autorestock, consider all maps in inv (later: look at all edges) and stick to RowByCol or ColByRow for whole map
	// cont.: save LastMapCommonSubstr and LastMapRowByCol
	// steal activated spawner and similar stuff from trouser-streak?
	// /msgas Anuvin target hi - send msg from alt acc
	// time left on 2b show in miniHUD infoline
	// auto enchant dia sword, auto grindstone, auto rename, auto anvil combine
	// auto enchant bulk misc items
	// inv-keybind-craft-latest-item,also for enchant table and grindstone (eg. spam enchanting axes)
	// Look at Yaw+Pitch (for triggering remote redstone)

	// Reference variables
	public static final String MOD_ID = "keybound";
	public static final String configFilename = MOD_ID+".txt";
	//public static final String MOD_NAME = "KeyBound";
	//public static final String MOD_VERSION = "@MOD_VERSION@";
	public static final String KEYBIND_CATEGORY = "key.categories."+MOD_ID;


	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static HashMap<String, String> config;

	public static InventoryUtils inventoryUtils;
	public static RemoteServerSender remoteSender;
	public static EpearlLookup epearlLookup;
	public static boolean rcHotbarHUD, mapartDb=true, mapartDbContact, mapColorHUD, mapColorIFrame, totemShowTotalCount;
	public static long joinedServerTimestamp;

	private void loadConfig(){
		//=================================== Parsing config into a map
		config = new HashMap<>();
		final String configContents = FileIO.loadFile(configFilename, getClass().getResourceAsStream("/"+configFilename));
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
		if(listKey != null) LOGGER.error("Unterminated list in ./config/"+configFilename+"!\nkey: "+listKey);
	}

	@Override public void onInitializeClient(){
		loadConfig();
		//=================================== Loading config features
		HashMap<String, String> remoteMessages = new HashMap<>();
		int clientId=0; String clientKey=null;
		String remoteAddr=null; int remotePort=0;
		//int clicksInDuration = 190, durationTicks = 75;
		int clicksInDuration = 1, durationTicks = 1;
		boolean epearlOwners=false, epearlOwnersDbUUID=false, epearlOwnersDbXZ=false,
				keybindMapArtLoad=false, keybindMapArtCopy=false, keybindMapArtMove=false;
		boolean mapPlaceHelper=false, mapPlaceHelperByName=false, mapPlaceHelperByImg=false;
		boolean keybindHighwayTravelHelper=false;

		String[] temp_evt_msgs=null; long temp_evt_ts=0; String evt_account="";
		KeybindEjectJunk ejectJunk = null;

		//config.forEach((key, value) -> {
		for(String key : config.keySet()){
			String value = config.get(key);
			if(key.startsWith("keybind.chat_msg.")) KeybindsSimple.registerChatKeybind(key.substring(8), value);
			else if(key.startsWith("keybind.remote_msg.")) remoteMessages.put(key.substring(8), value);
			else if(key.startsWith("keybind.snap_angle")) KeybindsSimple.registerSnapAngleKeybind(key.substring(8), value);
			else if(key.startsWith("organize_inventory.")) new KeybindInventoryOrganize(key, value.replaceAll("\\s",""));
			else switch(key){
				// Database
				case "client_id": clientId = Integer.parseInt(value); break;
				case "client_key": clientKey = value; break;
				case "remote_addr": remoteAddr = value; break;
				case "remote_port": remotePort = Integer.parseInt(value); break;
				case "enderpearl_owners": epearlOwners = !value.equalsIgnoreCase("false"); break;
				case "enderpearl_database_by_uuid": epearlOwnersDbUUID = !value.equalsIgnoreCase("false"); break;
				case "enderpearl_database_by_coords": epearlOwnersDbXZ = !value.equalsIgnoreCase("false"); break;
				case "seen_database": if(!value.equalsIgnoreCase("false")) new CommandSeen(); break;//TODO
				case "mapart_database": mapartDb = !value.equalsIgnoreCase("false"); break;
				case "mapart_database_share_contact": mapartDbContact = !value.equalsIgnoreCase("false"); break;
				case "track_time_online": if(!value.equalsIgnoreCase("false")){
					ServerPlayConnectionEvents.JOIN.register((_, _, _)->joinedServerTimestamp=System.currentTimeMillis());
					new CommandTimeOnline();
					break;
				}

				case "limiter_clicks_in_duration": clicksInDuration = Integer.parseInt(value); break;
				case "limiter_duration_ticks": durationTicks = Integer.parseInt(value); break;

				case "join_messages": if(value.startsWith("[")) new SendOnServerJoin(value.substring(1, value.length()-1).split(",")); break;
				case "temp_event_broadcast": if(value.startsWith("[")) temp_evt_msgs = value.substring(1, value.length()-1).split(","); break;
				case "temp_event_timestamp": temp_evt_ts = Long.parseLong(value); break;
				case "temp_event_account": evt_account = value; break;

//				case "spawner_highlight": if(!value.equalsIgnoreCase("false")) new SpawnerHighlighter(); break;
				case "totem_total_count": if(!value.equalsIgnoreCase("false")) totemShowTotalCount = !value.equalsIgnoreCase("false"); break;
				case "repaircost_tooltip": if(!value.equalsIgnoreCase("false")) ItemTooltipCallback.EVENT.register(RepairCostTooltip::addRC); break;
				case "repaircost_hotbarhud": rcHotbarHUD = !value.equalsIgnoreCase("false"); break;
				case "unlocked_map_red_tooltip": if(!value.equalsIgnoreCase("false")) ItemTooltipCallback.EVENT.register(LockedMapTooltip::redName); break;
				case "unlocked_map_red_hotbarhud": mapColorHUD = !value.equalsIgnoreCase("false"); break;
				case "unlocked_map_red_itemframe": mapColorIFrame = !value.equalsIgnoreCase("false"); break;
				case "keybind.mapart.load_from_shulker": keybindMapArtLoad = !value.equalsIgnoreCase("false"); break;
				case "keybind.mapart.take_from_shulker": keybindMapArtMove = !value.equalsIgnoreCase("false"); break;
				case "keybind.mapart.copy_in_inventory": keybindMapArtCopy = !value.equalsIgnoreCase("false"); break;
				case "mapart_placement_helper": mapPlaceHelper=true; break;
				case "mapart_placement_helper_use_name": mapPlaceHelperByName=true; break;
				case "mapart_placement_helper_use_image": mapPlaceHelperByImg=true; break;
				case "mapart_group_track_locked": MapGroupUtils.ENFORCE_LOCKED_STATE = !value.equalsIgnoreCase("false"); break;
				case "mapart_group_command": new CommandSetMapArtGroup(); break;
//				case "max_clicks_per_tick": clicks_per_gt = Integer.parseInt(value); break;
//				case "millis_between_clicks": millis_between_clicks = Integer.parseInt(value); break;

				case "keybind.eject_junk_items": if(!value.equalsIgnoreCase("false")) ejectJunk = new KeybindEjectJunk(); break;
				case "keybind.toggle_skin_layers": if(!value.equalsIgnoreCase("false")) KeybindsSimple.registerSkinLayerKeybinds(); break;
//				case "keybind_smart_inventory_craft": if(!value.equalsIgnoreCase("false")) new KeybindSmartInvCraft(); break;
				case "keybind.2b2t_highway_travel_helper": keybindHighwayTravelHelper = !value.equalsIgnoreCase("false"); break;
				case "keybind.aie_travel_helper": if(!value.equalsIgnoreCase("false")) new KeybindAIETravelHelper(); break;
				case "scroll_order": {
					final String listOfListsStr = value.replaceAll("\\s","");
					List<String[]> colorLists = Arrays.stream(
							listOfListsStr.substring(2, listOfListsStr.length()-2).split("\\],\\[")).map(s->s.split(",")).toList();
					new KeybindHotbarTypeScroller(colorLists);
					break;
				}
				default:
					LOGGER.warn("Unrecognized config setting: "+key);
			}
		}
		inventoryUtils = new InventoryUtils(clicksInDuration, durationTicks);
		if(epearlOwners) epearlLookup = new EpearlLookup(epearlOwnersDbUUID, epearlOwnersDbXZ);
		final boolean anyDbFeaturesEnabled = !remoteMessages.isEmpty() || epearlOwnersDbUUID || epearlOwnersDbXZ || mapartDb;
		if(clientId != 0 && clientKey != null && remoteAddr != null && remotePort != 0 && anyDbFeaturesEnabled){
			remoteSender = new RemoteServerSender(remoteAddr, remotePort, clientId, clientKey, remoteMessages);
		}
		if(keybindMapArtLoad) new KeybindMapLoad();
		if(keybindMapArtCopy) new KeybindMapCopy();
		if(keybindMapArtMove) new KeybindMapMove();
		if(mapPlaceHelper) new MapHandRestock(mapPlaceHelperByName, mapPlaceHelperByImg);
		if(keybindHighwayTravelHelper) new Keybind2b2tHighwayTravelHelper(ejectJunk);
		//new KeybindSpamclick();

		MinecraftClient client = MinecraftClient.getInstance();
		String username = client.getSession().getUsername();
		if(temp_evt_ts*1000L > System.currentTimeMillis() && temp_evt_msgs != null && username.equals(evt_account)) new ChatBroadcaster(temp_evt_ts, temp_evt_msgs);
	}
}