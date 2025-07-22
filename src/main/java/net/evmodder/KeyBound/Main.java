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
import net.evmodder.KeyBound.EventListeners.AutoPearlActivator;
import net.evmodder.KeyBound.EventListeners.ContainerHighlightUpdater;
import net.evmodder.KeyBound.EventListeners.IgnoreListSync2b2t;
import net.evmodder.KeyBound.EventListeners.InventoryHighlightUpdater;
import net.evmodder.KeyBound.EventListeners.ItemFrameHighlightUpdater;
import net.evmodder.KeyBound.EventListeners.TooltipMapNameColor;
import net.evmodder.KeyBound.EventListeners.MapHandRestock;
import net.evmodder.KeyBound.EventListeners.TooltipRepairCost;
import net.evmodder.KeyBound.Keybinds.*;
import net.evmodder.KeyBound.Keybinds.KeybindsSimple;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
// gradle genSources/eclipse/cleanloom/--stop
//MC source will be in ~/.gradle/caches/fabric-loom or ./.gradle/loom-cache
// gradle build --refresh-dependencies
// gradle migrateMappings --mappings "1.21.4+build.8"
// Fix broken eclipse build paths after updating loom,fabric-api,version in configs: gradle eclipse
public class Main implements ClientModInitializer{
	// Splash potion harming, weakness (spider eyes, sugar, gunpowder, brewing stand)
	//TODO:
	// Ultimate KeyBind mod: Buff MaLiLib mod menu with dropdown option for all vanilla (and mod) categories + allow duplicates
	// auto-replenish from opened containers (and maybe even auto-open containers)
	// keybind to sort maps in inventory

	// Mixin onEntityTick_iFrame instead of hasLabel_iFrame <--- less lag, better compatibility with mods, etc.
	// see if possible to pre-load MapStates when joining a server (to reduce lag?)
	// Reference/depend on https://github.com/Siphalor/amecs-api
	// majorly improve TravelHelper (mining blocks only in way, specifically non-diag & mining 3 high tunnel)
	//SendOnServerJoin configured per-server (via ip?)

	// timeOfDay >= 2000 && timeOfDay < 9000

	// Feature Ideas:
	// setting to make InvRestockFromContainer automatic (no keybind press needed, maybe auto-open-container even)
	// multiple itemframes nearby (100? 1k? 10k?) with the same map -> purple name/color/asterisk
	// change render order of certain trades (in particular: cleric redstone always above rotten flesh)
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

	public static ClickUtils clickUtils;
	public static RemoteServerSender remoteSender;
	public static EpearlLookup epearlLookup;
	public static boolean rcHUD, mapHighlightHUD, mapHighlightIFrame, mapHighlightHandledScreen;
	public static boolean mapartDb, mapartDbContact, totemShowTotalCount, skipTransparentMaps, skipMonoColorMaps;
	public static long joinedServerTimestamp;

	public static int MAP_COLOR_UNLOADED = 13150930;
	public static int MAP_COLOR_UNLOCKED = 14692709;
	public static int MAP_COLOR_UNNAMED = 15652823;
	public static int MAP_COLOR_NOT_IN_GROUP = 706660;
	public static int MAP_COLOR_IN_INV = 11862015, MAP_COLOR_IN_IFRAME = 5614310;//TODO: MAP_COLOR_IN_CONTAINER=11862015
	public static int MAP_COLOR_MULTI_IFRAME = 11817190, MAP_COLOR_MULTI_INV = 11817190;

	public static double MAX_IFRAME_TRACKING_DIST_SQ;

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
				keybindMapArtLoad=false, keybindMapArtCopy=false, keybindMapArtMove=false, keybindMapArtBundleStow=false;
		boolean mapMoveIgnoreAirPockets=true;
		boolean mapPlaceHelper=false, mapPlaceHelperByName=false, mapPlaceHelperByImg=false, mapHighlightTooltip=false;
		boolean mapWallCmd=false, mapWallBorder=false;
		boolean keybindEbounceTravelHelper=false;
		boolean uploadIgnoreList=false;
		int mapWallBorderColor1=-14236, mapWallBorderColor2=-8555656, mapWallUpscale=128;
		String[] downloadIgnoreLists=null;

		String[] temp_evt_msgs=null; long temp_evt_ts=0; String evt_account="";
		KeybindEjectJunk ejectJunk = null;

		//config.forEach((key, value) -> {
		for(String key : config.keySet()){
			String value = config.get(key);
			if(key.startsWith("keybind.chat_msg.")) KeybindsSimple.registerChatKeybind(key.substring(8), value);
			else if(key.startsWith("keybind.remote_msg.")) remoteMessages.put(key.substring(8), value);
			else if(key.startsWith("keybind.snap_angle.")) KeybindsSimple.registerSnapAngleKeybind(key.substring(8), value);
			else if(key.startsWith("keybind.inventory_organize.")) new KeybindInventoryOrganize(key.substring(8), value.replaceAll("\\s",""));
			else switch(key){
				// Database
				case "client_id": clientId = Integer.parseInt(value); break;
				case "client_key": clientKey = value; break;
				case "remote_address":{
					final int sep = value.indexOf(':');
					if(sep == -1){LOGGER.warn("Invalid server address (RemoteDB): "+value); break;}
					remoteAddr = value.substring(0, sep).trim();
					remotePort = Integer.parseInt(value.substring(sep+1).trim());
					break;
				}
				case "remote_port": remotePort = Integer.parseInt(value); break;
				case "enderpearl_owners": epearlOwners = !value.equalsIgnoreCase("false"); break;
				case "enderpearl_database_by_uuid": epearlOwnersDbUUID = !value.equalsIgnoreCase("false"); break;
				case "enderpearl_database_by_coords": epearlOwnersDbXZ = !value.equalsIgnoreCase("false"); break;
				case "seen_database": if(!value.equalsIgnoreCase("false")) new CommandSeen(); break;//TODO
				case "mapart_database": mapartDb = !value.equalsIgnoreCase("false"); break;
				case "mapart_database_share_contact": mapartDbContact = !value.equalsIgnoreCase("false"); break;
				case "track_time_online": if(!value.equalsIgnoreCase("false")){
					ServerPlayConnectionEvents.JOIN.register((_0, _1, _2)->joinedServerTimestamp=System.currentTimeMillis());
					new CommandTimeOnline();
					break;
				}
				case "publish_my_ignore_list": uploadIgnoreList = !value.equalsIgnoreCase("false"); break;
				case "add_other_ignore_lists": if(value.startsWith("[")) downloadIgnoreLists = value.substring(1, value.length()-1).split(","); break;

				case "msg_for_pearl_trigger": new AutoPearlActivator(value); break;

				case "limiter_clicks_in_duration": clicksInDuration = Integer.parseInt(value); break;
				case "limiter_duration_ticks": durationTicks = Integer.parseInt(value); break;
//				case "max_clicks_per_tick": clicks_per_gt = Integer.parseInt(value); break;
//				case "millis_between_clicks": millis_between_clicks = Integer.parseInt(value); break;

				case "join_messages": if(value.startsWith("[")) new SendOnServerJoin(value.substring(1, value.length()-1).split(",")); break;
				case "temp_event_broadcast": if(value.startsWith("[")) temp_evt_msgs = value.substring(1, value.length()-1).split(","); break;
				case "temp_event_timestamp": temp_evt_ts = Long.parseLong(value); break;
				case "temp_event_account": evt_account = value; break;

//				case "spawner_highlight": if(!value.equalsIgnoreCase("false")) new SpawnerHighlighter(); break;
				case "totem_total_count": if(!value.equalsIgnoreCase("false")) totemShowTotalCount = !value.equalsIgnoreCase("false"); break;
				case "repaircost_in_tooltip": if(!value.equalsIgnoreCase("false")) ItemTooltipCallback.EVENT.register(TooltipRepairCost::addRC); break;
				case "repaircost_in_hotbarhud": rcHUD = !value.equalsIgnoreCase("false"); break;
				case "map_highlight_in_tooltip": mapHighlightTooltip = !value.equalsIgnoreCase("false"); break;
				case "map_highlight_in_hotbarhud": mapHighlightHUD = !value.equalsIgnoreCase("false"); break;
				case "map_highlight_in_itemframe": mapHighlightIFrame = !value.equalsIgnoreCase("false"); break;
				case "map_highlight_in_container_name": mapHighlightHandledScreen = !value.equalsIgnoreCase("false"); break;
				case "map_highlight_color_unloaded": MAP_COLOR_UNLOADED = Integer.parseInt(value); break;
				case "map_highlight_color_unlocked": MAP_COLOR_UNLOCKED = Integer.parseInt(value); break;
				case "map_highlight_color_unnamed": MAP_COLOR_UNNAMED = Integer.parseInt(value); break;
				case "map_highlight_color_ungrouped": MAP_COLOR_NOT_IN_GROUP = Integer.parseInt(value); break;
				case "map_highlight_color_matches_inventory": MAP_COLOR_IN_INV = Integer.parseInt(value); break;
				case "map_highlight_color_matches_itemframe": MAP_COLOR_IN_IFRAME = Integer.parseInt(value); break;
				case "map_highlight_color_multi_itemframe": MAP_COLOR_MULTI_IFRAME = Integer.parseInt(value); break;
				case "map_highlight_color_multi_inventory": MAP_COLOR_MULTI_INV = Integer.parseInt(value); break;
				case "fully_transparent_map_is_filler_item": skipTransparentMaps = !value.equalsIgnoreCase("false"); break;
				case "highlight_duplicate_monocolor_maps": skipMonoColorMaps = value.equalsIgnoreCase("false"); break;
				case "itemframe_tracking_distance": MAX_IFRAME_TRACKING_DIST_SQ = Double.parseDouble(value)*Double.parseDouble(value); break;
				//case "mapart_notify_not_in_group": notifyIfLoadNewMapArt = !value.equalsIgnoreCase("false"); break;
				case "keybind.mapart.copy": keybindMapArtCopy = !value.equalsIgnoreCase("false"); break;
				case "keybind.mapart.load": keybindMapArtLoad = !value.equalsIgnoreCase("false"); break;
				case "keybind.mapart.move.bundle": keybindMapArtBundleStow = !value.equalsIgnoreCase("false"); break;
				case "keybind.mapart.move.3x9": keybindMapArtMove = !value.equalsIgnoreCase("false"); break;
				case "keybind.mapart.move_3x9.ignore_air_pockets": mapMoveIgnoreAirPockets = !value.equalsIgnoreCase("false"); break;
				case "mapart_placement_helper": mapPlaceHelper=!value.equalsIgnoreCase("false"); break;
				case "mapart_placement_helper_use_name": mapPlaceHelperByName=!value.equalsIgnoreCase("false"); break;
				case "mapart_placement_helper_use_image": mapPlaceHelperByImg=!value.equalsIgnoreCase("false"); break;
				case "mapart_group_include_unlocked":
					MapGroupUtils.INCLUDE_UNLOCKED = !value.equalsIgnoreCase("false");
					break;
				case "mapart_group_command": new CommandMapArtGroup(); break;
				case "mapart_generate_img_upscale_to": mapWallUpscale=Integer.parseInt(value); break;
				case "mapart_generate_img_border": mapWallBorder=!value.equalsIgnoreCase("false"); break;
				case "mapart_generate_img_command": mapWallCmd=!value.equalsIgnoreCase("false"); break;
				case "mapart_generate_img_border_color1": mapWallBorderColor1=Integer.parseInt(value); break;
				case "mapart_generate_img_border_color2": mapWallBorderColor2=Integer.parseInt(value); break;

				case "keybind.eject_junk_items": if(!value.equalsIgnoreCase("false")) ejectJunk = new KeybindEjectJunk(); break;
				case "keybind.toggle_skin_layers": if(!value.equalsIgnoreCase("false")) KeybindsSimple.registerSkinLayerKeybinds(); break;
//				case "keybind.smart_inventory_craft": if(!value.equalsIgnoreCase("false")) new KeybindSmartInvCraft(); break;
				case "keybind.inventory_restock": if(!value.equalsIgnoreCase("false")) new KeybindInventoryRestock(); break;
				case "keybind.ebounce_travel_helper": keybindEbounceTravelHelper = !value.equalsIgnoreCase("false"); break;
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
		clickUtils = new ClickUtils(clicksInDuration, durationTicks);
		final boolean anyDbFeaturesEnabled = !remoteMessages.isEmpty() || epearlOwnersDbUUID || epearlOwnersDbXZ || mapartDb
				|| uploadIgnoreList || downloadIgnoreLists != null;
		if(clientId != 0 && clientKey != null && remoteAddr != null && remotePort != 0 && anyDbFeaturesEnabled){
			remoteSender = new RemoteServerSender(remoteAddr, remotePort, clientId, clientKey, remoteMessages);
			if(epearlOwners) epearlLookup = new EpearlLookup(epearlOwnersDbUUID, epearlOwnersDbXZ);
			if(uploadIgnoreList || downloadIgnoreLists != null) new IgnoreListSync2b2t(uploadIgnoreList, downloadIgnoreLists);
		}
		if(keybindMapArtLoad) new KeybindMapLoad();
		if(keybindMapArtCopy) new KeybindMapCopy();
		if(keybindMapArtMove) new KeybindMapMove(mapMoveIgnoreAirPockets);
		if(keybindMapArtBundleStow) new KeybindMapMoveBundle();
		if(mapPlaceHelper) new MapHandRestock(mapPlaceHelperByName, mapPlaceHelperByImg);
		if(keybindEbounceTravelHelper) new KeybindEbounceTravelHelper(ejectJunk);
		//new KeybindSpamclick();

		if(mapWallCmd) new CommandExportMapImg(mapWallUpscale, mapWallBorder, mapWallBorderColor1, mapWallBorderColor2);

		if(mapHighlightTooltip) ItemTooltipCallback.EVENT.register(TooltipMapNameColor::tooltipColors);
		if(mapHighlightTooltip || mapHighlightHUD || mapHighlightIFrame || mapHighlightHandledScreen){
			ClientTickEvents.START_CLIENT_TICK.register(client -> {
				InventoryHighlightUpdater.onUpdateTick(client);
				ItemFrameHighlightUpdater.onUpdateTick(client);
				if(mapHighlightHandledScreen/* || mapHighlightTooltip*/) ContainerHighlightUpdater.onUpdateTick(client);
			});
		}

		final String username = MinecraftClient.getInstance().getSession().getUsername();
		if(temp_evt_ts*1000L > System.currentTimeMillis() && temp_evt_msgs != null && username.equals(evt_account))
			new ChatBroadcaster(temp_evt_ts, temp_evt_msgs);
	}
}