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
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.registry.Registry;
import fi.dy.masa.malilib.util.data.ModInfo;
import net.evmodder.EvLib.FileIO;
import net.evmodder.KeyBound.commands.*;
import net.evmodder.KeyBound.config.ConfigGui;
import net.evmodder.KeyBound.config.Configs;
import net.evmodder.KeyBound.keybinds.*;
import net.evmodder.KeyBound.listeners.*;
import net.evmodder.KeyBound.onTick.AutoPlaceItemFrames;
import net.evmodder.KeyBound.onTick.TooltipMapLoreMetadata;
import net.evmodder.KeyBound.onTick.TooltipMapNameColor;
import net.evmodder.KeyBound.onTick.TooltipRepairCost;
import net.evmodder.KeyBound.onTick.UpdateContainerHighlights;
import net.evmodder.KeyBound.onTick.UpdateInventoryHighlights;
import net.evmodder.KeyBound.onTick.UpdateItemFrameHighlights;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;

// gradle genSources/eclipse/cleanloom/--stop
//MC source will be in ~/.gradle/caches/fabric-loom or ./.gradle/loom-cache
// gradle build --refresh-dependencies
// gradle migrateMappings --mappings "1.21.4+build.8"
// Fix broken eclipse build paths after updating loom,fabric-api,version in configs: gradle eclipse
public class Main implements ClientModInitializer{
	// Splash potion harming, weakness (spider eyes, sugar, gunpowder, brewing stand)
	//TODO:
	// AutoMapPlacer (for LVotU)
	// Modifier key: bundle take/store in reverse
	// Noteblock ping sound when receiving DM
	// ignorelist sync, /seen and other misc stats cmds for DB mode
	// Ultimate KeyBind mod: Buff MaLiLib mod menu with dropdown option for all vanilla (and mod) categories + allow duplicates

	// keybind to sort maps in inventory (rly good request from FartRipper), 2 modes: incr by slot index vs make a rectangle in inv

	// see if possible to pre-load MapStates when joining a server (due 2 rdm ids on 2b2t, can only work for maps in inv/ec)
	// ^ note1: includes maps nested in shulks/bundles/shulks, so potentially can cache a LOT of states (~100k)
	// ^ note2: maybe also option to cache states for chests etc, but potential issues if another player rearranges them
	// Reference/depend on https://github.com/Siphalor/amecs-api
	// majorly improve TravelHelper (mining blocks only in way, specifically non-diag & mining 3 high tunnel)
	//SendOnServerJoin configured per-server (via ip?)

	// timeOfDay >= 2000 && timeOfDay < 9000 

	// Feature Ideas:
	// change render order of certain villager trades (in particular: make cleric redstone always above rotten flesh)
	// totem in offhand - render itemcount for sum of totems in inv (instead of itemcount 1) - IMO nicer than the RH/meteor/etc UI overlay
	// Maps - make item count font smaller, cuz it kinda covers img in slot
	// better job predict next map / assembling into grid (rn it's greedy, need make it DFS+DP (same for img stitching))
	// yoink activated spawner highlight from trouser-streak? seems cool
	// /msgas EvDoc <target> <msg> - send msgs as another acc (TODO: also make as a zenithproxy plugin)
	// add time left on 2b (8h-time online) infoline to miniHUD settings
	// Low RC quest: auto enchant dia sword, auto grindstone, auto rename, auto anvil combine. auto enchant bulk misc items
	// inv-keybind-craft-latest-item, also for enchant table and grindstone (eg. spam enchanting axes) via spacebar, like vanilla
	// Snap-look at preconfigured [Yaw+Pitch] angles (for triggering remote stasis pearl)

/*
Fixed the ghost item in hand autorestock;
now it waits for the client to see the item placed in the frame and for the hotbar slot to be empty before swapping in the next map.
Detection driven instead of hardcoded delay, so should work now regardless of ping.

Also updated some functions:
findRelatedMaps(): groups related maps (parts of the same img)
leftRightScore(leftMap, rightMap): returns 0-1f confidence
upDownScore(topMap, bottomMap): returns 0-1f confidence
^ two versions of each of these, one using item name and one using image edge similarity (used for unnamed maps)
findArrangement(maps[]): tries to find optimal layout rectangle (N*N complexity, but becomes O(N) with DP)

once arrangement is found
*/
	// Reference variables
	public static final String MOD_ID = "keybound"; // TODO: pull from fabric/gradle?
	public static final String configFilename = "enabled_features.txt";
	//public static final String MOD_NAME = "KeyBound";
	//public static final String MOD_VERSION = "@MOD_VERSION@";
	public static final String KEYBIND_CATEGORY = "key.categories."+MOD_ID;
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static HashMap<String, String> config;

	public static ClickUtils clickUtils;
	public static RemoteServerSender remoteSender;
	public static EpearlLookup epearlLookup;

	public static boolean database, placementHelperIframe, placementHelperMapArt, placementHelperMapArtAuto, whisperListener, broadcaster;
	public static boolean cmdExportMapImg, cmdMapArtGroup, keybindMapArtMove, keybindBundleStowOrReverseStow;
	public static boolean rcHUD, mapHighlightHUD, mapHighlightIFrame, mapHighlightHandledScreen;
	public static boolean mapartDb, mapartDbContact, totemShowTotalCount, skipTransparentMaps, skipMonoColorMaps;

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
		boolean cmdAssignPearl=false,/* cmdExportMapImg=false,*//* cmdMapArtGroup=false,*/ cmdSeen=false, cmdSendAs=false, cmdTimeOnline=false;
		boolean keybindEbounceTravelHelper=false, keybindRestock=false, inventoryRestockAuto=false,
				keybindMapArtLoad=false, keybindMapArtCopy=false, /*keybindMapArtMove=false,*/ keybindMapArtBundleStow=false, keybindMapArtBundleStowReverse=false;

		boolean epearlOwners=false, epearlOwnersDbUUID=false, epearlOwnersDbXZ=false;
		boolean mapHighlightTooltip=false;
		boolean mapMetadataTooltip=false;

		String[] restockBlacklist=null, restockWhitelist=null, restockAutoInvSchemes=null;
		HashMap<String, KeybindInventoryOrganize> inventoryOrganizationSchemes = new HashMap<>();

		boolean uploadIgnoreList=false;
		String[] downloadIgnoreLists=null;

		KeybindEjectJunk ejectJunk = null;
		KeybindInventoryRestock inventoryRestock = null;

		//config.forEach((key, value) -> {
		for(String key : config.keySet()){
			String value = config.get(key);
			if(key.startsWith("keybind.chat_msg.")) KeybindsSimple.registerChatKeybind(key.substring(8), value);
			else if(key.startsWith("keybind.remote_msg.")) remoteMessages.put(key.substring(8), value);
			else if(key.startsWith("keybind.snap_angle.")) KeybindsSimple.registerSnapAngleKeybind(key.substring(8), value);
			else if(key.startsWith("keybind.inventory_organize."))
				inventoryOrganizationSchemes.put(key.substring(27), new KeybindInventoryOrganize(key.substring(8), value.replaceAll("\\s","")));
			else switch(key){
				case "database": database = !value.equalsIgnoreCase("false"); break;
				case "placement_helper.iframes": placementHelperIframe = !value.equalsIgnoreCase("false"); break;
				case "placement_helper.maparts": placementHelperMapArt = !value.equalsIgnoreCase("false"); break;
				case "placement_helper.maparts.auto": placementHelperMapArtAuto = !value.equalsIgnoreCase("false"); break;
				case "whisper_listener": whisperListener = !value.equalsIgnoreCase("false"); break;
				case "broadcaster": broadcaster = !value.equalsIgnoreCase("false"); break;
				case "enderpearl_owners": epearlOwners = !value.equalsIgnoreCase("false"); break;

				case "command.assignpearl": cmdAssignPearl = !value.equalsIgnoreCase("false"); break;
				case "command.exportmapimg": cmdExportMapImg = !value.equalsIgnoreCase("false"); break;
				case "command.mapartgroup": cmdMapArtGroup = !value.equalsIgnoreCase("false"); break;
				case "command.seen": cmdSeen = !value.equalsIgnoreCase("false"); break;
				case "command.sendas": cmdSendAs = !value.equalsIgnoreCase("false"); break;
				case "command.timeonline": cmdTimeOnline = !value.equalsIgnoreCase("false"); break;

				case "keybind.mapart.copy": keybindMapArtCopy = !value.equalsIgnoreCase("false"); break;
				case "keybind.mapart.load": keybindMapArtLoad = !value.equalsIgnoreCase("false"); break;
				case "keybind.mapart.move.bundle": keybindMapArtBundleStow = !value.equalsIgnoreCase("false"); break;
				case "keybind.mapart.move.bundle.reverse": keybindMapArtBundleStowReverse = !value.equalsIgnoreCase("false"); break;
				case "keybind.mapart.move.3x9": keybindMapArtMove = !value.equalsIgnoreCase("false"); break;
				case "keybind.eject_junk_items": if(!value.equalsIgnoreCase("false")) ejectJunk = new KeybindEjectJunk(); break;
				case "keybind.toggle_skin_layers": if(!value.equalsIgnoreCase("false")) KeybindsSimple.registerSkinLayerKeybinds(); break;
//				case "keybind.smart_inventory_craft": if(!value.equalsIgnoreCase("false")) new KeybindSmartInvCraft(); break;
				case "keybind.inventory_restock": keybindRestock=!value.equalsIgnoreCase("false"); break;
				case "keybind.inventory_restock.blacklist": if(value.startsWith("[")) restockBlacklist = value.substring(1, value.length()-1).split("\\s*,\\s*"); break;
				case "keybind.inventory_restock.whitelist": if(value.startsWith("[")) restockWhitelist = value.substring(1, value.length()-1).split("\\s*,\\s*"); break;
				case "keybind.inventory_restock.auto": inventoryRestockAuto=!value.equalsIgnoreCase("false"); break;
				case "keybind.inventory_restock.auto.matching_inventory": if(value.startsWith("[")) restockAutoInvSchemes = value.substring(1, value.length()-1).split("\\s*,\\s*"); break;
				case "keybind.ebounce_travel_helper": keybindEbounceTravelHelper = !value.equalsIgnoreCase("false"); break;
				case "keybind.aie_travel_helper": if(!value.equalsIgnoreCase("false")) new KeybindAIETravelHelper(); break;

				case "enderpearl_database_by_uuid": epearlOwnersDbUUID = !value.equalsIgnoreCase("false"); break;
				case "enderpearl_database_by_coords": epearlOwnersDbXZ = !value.equalsIgnoreCase("false"); break;

				case "mapart_database": mapartDb = !value.equalsIgnoreCase("false"); break;
				case "mapart_database_share_contact": mapartDbContact = !value.equalsIgnoreCase("false"); break;
				case "track_time_online": if(!value.equalsIgnoreCase("false")) new CommandTimeOnline(); break;
				case "log_xyz_on_quit": if(!value.equalsIgnoreCase("false")) new LogCoordsOnServerDisconnect(); break;
				case "database.ignorelist.share": uploadIgnoreList = !value.equalsIgnoreCase("false"); break;
				case "database.ignorelist.borrow": if(value.startsWith("[")) downloadIgnoreLists = value.substring(1, value.length()-1).split("\\s&,\\s&"); break;

				case "join_messages": if(value.startsWith("[")) new SendOnServerJoin(value.substring(1, value.length()-1).split(",")); break;

//				case "spawner_highlight": if(!value.equalsIgnoreCase("false")) new SpawnerHighlighter(); break;
				case "totem_total_count": if(!value.equalsIgnoreCase("false")) totemShowTotalCount = !value.equalsIgnoreCase("false"); break;
				case "repaircost_in_tooltip": if(!value.equalsIgnoreCase("false")) ItemTooltipCallback.EVENT.register(TooltipRepairCost::addRC); break;
				case "repaircost_in_hotbarhud": rcHUD = !value.equalsIgnoreCase("false"); break;
				case "map_state_cache": if(!value.equalsIgnoreCase("false")) new MapStateInventoryCacher(); break;
				case "map_metadata_in_tooltip": mapMetadataTooltip = !value.equalsIgnoreCase("false"); break;
				case "map_highlight_in_tooltip": mapHighlightTooltip = !value.equalsIgnoreCase("false"); break;
				case "map_highlight_in_hotbarhud": mapHighlightHUD = !value.equalsIgnoreCase("false"); break;
				case "map_highlight_in_itemframe": mapHighlightIFrame = !value.equalsIgnoreCase("false"); break;
				case "map_highlight_in_container_name": mapHighlightHandledScreen = !value.equalsIgnoreCase("false"); break;
				case "fully_transparent_map_is_filler_item": skipTransparentMaps = !value.equalsIgnoreCase("false"); break;
				case "highlight_duplicate_monocolor_maps": skipMonoColorMaps = value.equalsIgnoreCase("false"); break;
				//case "mapart_notify_not_in_group": notifyIfLoadNewMapArt = !value.equalsIgnoreCase("false"); break;
				case "scroll_order": {
					final String listOfLists = value.replaceAll("\\s","");
					List<String[]> colorLists = Arrays.stream(listOfLists.substring(2, listOfLists.length()-2).split("\\],\\[")).map(s->s.split(",")).toList();
					new KeybindHotbarTypeScroller(colorLists);
					break;
				}
				default:
					LOGGER.warn("Unrecognized config setting: "+key);
			}
		}
		ConfigManager.getInstance().registerConfigHandler(MOD_ID, new Configs());
		Registry.CONFIG_SCREEN.registerConfigScreenFactory(new ModInfo(MOD_ID, "KeyBound", ConfigGui::new));

		clickUtils = new ClickUtils(Configs.Misc.CLICK_LIMIT_COUNT.getIntegerValue(), Configs.Misc.CLICK_LIMIT_DURATION.getIntegerValue());
		if(database){
			String fullAddress = Configs.Database.ADDRESS.getStringValue();
			final int sep = fullAddress.indexOf(':');
			final String addr;
			final int port;
			if(sep == -1){addr = fullAddress; port = RemoteServerSender.DEFAULT_PORT;}
			else{addr = fullAddress.substring(0, sep).trim(); port = Integer.parseInt(fullAddress.substring(sep+1).trim());}
			remoteSender = new RemoteServerSender(LOGGER, addr, port,
					Configs.Database.CLIENT_ID.getIntegerValue(), Configs.Database.CLIENT_KEY.getStringValue(),
					MiscUtils::getCurrentServerAddressHashCode);
			MiscUtils.registerRemoteMsgKeybinds(remoteMessages);
			if(epearlOwners){
				epearlLookup = new EpearlLookup(epearlOwnersDbUUID, epearlOwnersDbXZ);
				if(cmdAssignPearl) new CommandAssignPearl();
			}
			if(uploadIgnoreList || downloadIgnoreLists != null) new IgnoreListSync2b2t(uploadIgnoreList, downloadIgnoreLists);
		}
		if(whisperListener) new WhisperListener();
		if(placementHelperIframe) new AutoPlaceItemFrames();
		if(placementHelperMapArt) new MapHandRestock();
		if(broadcaster) ChatBroadcaster.refreshBroadcast();

		if(cmdAssignPearl) new CommandAssignPearl();
		if(cmdExportMapImg) new CommandExportMapImg();
		if(cmdMapArtGroup) new CommandMapArtGroup();
		if(cmdSeen) new CommandSeen();
		if(cmdSendAs) new CommandSendAs();
		if(cmdTimeOnline) new CommandTimeOnline();

		if(keybindMapArtLoad) new KeybindMapLoad();
		if(keybindMapArtCopy) new KeybindMapCopy();
		if(keybindMapArtMove) new KeybindMapMove();
		keybindBundleStowOrReverseStow = keybindMapArtBundleStow || keybindMapArtBundleStowReverse;
		if(keybindBundleStowOrReverseStow) new KeybindMapMoveBundle(keybindMapArtBundleStow, keybindMapArtBundleStowReverse);
		if(keybindEbounceTravelHelper) new KeybindEbounceTravelHelper(ejectJunk);
		if(keybindRestock){
			inventoryRestock = new KeybindInventoryRestock(restockBlacklist, restockWhitelist);
			if(inventoryRestockAuto){
				final KeybindInventoryOrganize[] selectedInvOrganizations = restockAutoInvSchemes == null ? null :
					Arrays.stream(restockAutoInvSchemes).map(inventoryOrganizationSchemes::get).filter(Objects::nonNull).toArray(KeybindInventoryOrganize[]::new);
				ClientTickEvents.END_CLIENT_TICK.register(new ContainerOpenListener(inventoryRestock, selectedInvOrganizations)::onUpdateTick);
			}
		}
		//new KeybindSpamclick();

		if(mapHighlightTooltip) ItemTooltipCallback.EVENT.register(TooltipMapNameColor::tooltipColors);
		if(mapHighlightTooltip || mapHighlightHUD || mapHighlightIFrame || mapHighlightHandledScreen){
			ClientTickEvents.START_CLIENT_TICK.register(client -> {
				UpdateInventoryHighlights.onUpdateTick(client);
				UpdateItemFrameHighlights.onUpdateTick(client);
				if(mapHighlightHandledScreen/* || mapHighlightTooltip*/) UpdateContainerHighlights.onUpdateTick(client);
			});
		}
		if(mapMetadataTooltip) new TooltipMapLoreMetadata();
	}
}