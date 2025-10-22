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
import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.event.InputEventHandler;
import fi.dy.masa.malilib.registry.Registry;
import fi.dy.masa.malilib.util.data.ModInfo;
import net.evmodder.EvLib.Command;
import net.evmodder.EvLib.FileIO;
import net.evmodder.KeyBound.apis.ChatBroadcaster;
import net.evmodder.KeyBound.apis.EpearlLookup;
import net.evmodder.KeyBound.apis.RemoteServerSender;
import net.evmodder.KeyBound.commands.*;
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
import net.minecraft.client.MinecraftClient;

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
	public static final String MOD_NAME = "KeyBound";
	public static final String configFilename = "enabled_features.txt";
	//public static final String MOD_NAME = "KeyBound";
	//public static final String MOD_VERSION = "@MOD_VERSION@";
	public static final String KEYBIND_CATEGORY = "key.categories."+MOD_ID;
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final int HASHCODE_2B2T = -437714968;//"2b2t.org".hashCode() // TODO: make mod more server-independent 

	public static ClickUtils clickUtils;
	public static RemoteServerSender remoteSender;
	public static EpearlLookup epearlLookup;
	public static GameMessageFilter gameMessageFilter;
	public static KeybindInventoryRestock kbInvRestock;
	public static ContainerOpenListener containerOpenListener;

	public static boolean placementHelperIframe, placementHelperMapArt, placementHelperMapArtAuto, broadcaster;
	public static boolean serverJoinListener, serverQuitListener, gameMessageListener;//, gameMessageFilter;
	public static boolean cmdExportMapImg, cmdMapArtGroup;
//	public static boolean keybindMapArtMove, keybindMapArtMoveBundle;

	public static boolean mapHighlights, mapHighlightsInGUIs;

	private HashMap<String, String> loadConfig(){
		HashMap<String, String> config = new HashMap<>();
		final String configContents = FileIO.loadFile(configFilename, getClass().getResourceAsStream("/"+configFilename));
		for(String line : configContents.split("\\r?\\n")){
			final int sep = line.indexOf(':');
			if(sep == -1) continue;
			final String key = line.substring(0, sep).trim();
			final String value = line.substring(sep+1).trim();
			if(key.isEmpty() || value.isEmpty()) continue;
			config.put(key, value);
		}
		return config;
	}

	@Override public void onInitializeClient(){
		HashMap<String, String> config = loadConfig();
		boolean cmdAssignPearl=false,/* cmdExportMapImg=false,*//* cmdMapArtGroup=false,*/ cmdSeen=false, cmdSendAs=false, cmdTimeOnline=false;
		boolean database=false;
		boolean epearlOwners=false, epearlOwnersByUUID=false, epearlOwnersByXZ=false;
		boolean mapHighlightTooltip=false;
		boolean inventoryRestockAuto=false;

		//config.forEach((key, value) -> {
		for(String key : config.keySet()){
			final String value = config.get(key);
			switch(key){
				case "database": database = !value.equalsIgnoreCase("false"); break;
				case "database.epearls": epearlOwners = !value.equalsIgnoreCase("false"); break;
				case "database.epearls.by_uuid": epearlOwnersByUUID = !value.equalsIgnoreCase("false"); break;
				case "database.epearls.by_coords": epearlOwnersByXZ = !value.equalsIgnoreCase("false"); break;

				case "broadcaster": broadcaster = !value.equalsIgnoreCase("false"); break;
				case "placement_helper.iframe": placementHelperIframe = !value.equalsIgnoreCase("false"); break;
				case "placement_helper.mapart": placementHelperMapArt = !value.equalsIgnoreCase("false"); break;
				case "placement_helper.mapart.auto": placementHelperMapArtAuto = !value.equalsIgnoreCase("false"); break;
				case "listener.server_join": serverJoinListener = !value.equalsIgnoreCase("false"); break;
				case "listener.server_quit": serverQuitListener = !value.equalsIgnoreCase("false"); break;
				case "listener.game_message.read": if(gameMessageListener = !value.equalsIgnoreCase("false")); break;
				case "listener.game_message.filter": if(!value.equalsIgnoreCase("false")) gameMessageFilter = new GameMessageFilter(); break;
				case "map_highlights": mapHighlights = !value.equalsIgnoreCase("false"); break;
				case "map_highlights.in_gui": mapHighlightsInGUIs = !value.equalsIgnoreCase("false"); break;
				case "tooltip.map_highlights": mapHighlightTooltip = !value.equalsIgnoreCase("false"); break;
				case "tooltip.map_metadata": new TooltipMapLoreMetadata(); break;
				case "tooltip.repair_cost": if(!value.equalsIgnoreCase("false")) ItemTooltipCallback.EVENT.register(TooltipRepairCost::addRC); break;
				case "inventory_restock.auto": inventoryRestockAuto = !value.equalsIgnoreCase("false"); break;

				case "command.assignpearl": cmdAssignPearl = !value.equalsIgnoreCase("false"); break;
				case "command.exportmapimg": cmdExportMapImg = !value.equalsIgnoreCase("false"); break;
				case "command.mapartgroup": cmdMapArtGroup = !value.equalsIgnoreCase("false"); break;
				case "command.seen": cmdSeen = !value.equalsIgnoreCase("false"); break;
				case "command.sendas": cmdSendAs = !value.equalsIgnoreCase("false"); break;
				case "command.timeonline": cmdTimeOnline = !value.equalsIgnoreCase("false"); break;

				case "keybind.toggle_skin_layers": if(!value.equalsIgnoreCase("false")) KeybindsSimple.registerSkinLayerKeybinds(); break;
				//case "mapart_notify_not_in_group": notifyIfLoadNewMapArt = !value.equalsIgnoreCase("false"); break;
				default:
					LOGGER.warn("Unrecognized config setting: "+key);
			}
		}
		KeyCallbacks.remakeClickUtils(null);
		if(database){
			KeyCallbacks.remakeRemoteServerSender(null);
			remoteSender.sendBotMessage(Command.PING, /*udp=*/false, /*timeout=*/5000, /*msg=*/new byte[0], null);
//			msg->LOGGER.info("Remote server responded to ping: "+(msg == null ? null : new String(msg)))

			if(epearlOwners){
				epearlLookup = new EpearlLookup(epearlOwnersByUUID, epearlOwnersByXZ);
				if(cmdAssignPearl) new CommandAssignPearl();
			}
		}
		if(serverJoinListener) new ServerJoinListener();
		if(serverQuitListener) new ServerQuitListener();
//		if(gameMessageListener) new GameMessageListener();
//		if(registerGameMessageFilter) gameMessageFilter = new GameMessageFilter();

		if(placementHelperIframe) new AutoPlaceItemFrames();
		if(placementHelperMapArt) new MapHandRestock();
		if(broadcaster) ChatBroadcaster.refreshBroadcast();

		if(cmdAssignPearl) new CommandAssignPearl();
		if(cmdExportMapImg) new CommandExportMapImg();
		if(cmdMapArtGroup) new CommandMapArtGroup();
		if(cmdSeen) new CommandSeen();
		if(cmdSendAs) new CommandSendAs();
		if(cmdTimeOnline) new CommandTimeOnline();

		kbInvRestock = new KeybindInventoryRestock();
		if(inventoryRestockAuto){
			containerOpenListener = new ContainerOpenListener();
			ClientTickEvents.END_CLIENT_TICK.register(containerOpenListener::onUpdateTick);
		}
		//new KeybindSpamclick();

		if(mapHighlights){
			if(mapHighlightTooltip) ItemTooltipCallback.EVENT.register(TooltipMapNameColor::tooltipColors);
			ClientTickEvents.START_CLIENT_TICK.register(client -> {
				UpdateInventoryHighlights.onUpdateTick(client);
				UpdateItemFrameHighlights.onUpdateTick(client);
				if(mapHighlightsInGUIs) UpdateContainerHighlights.onUpdateTick(client);
			});
		}

		ConfigManager.getInstance().registerConfigHandler(MOD_ID, new Configs());
		Registry.CONFIG_SCREEN.registerConfigScreenFactory(new ModInfo(MOD_ID, "KeyBound", ConfigGui::new));

		InputEventHandler.getKeybindManager().registerKeybindProvider(InputHandler.getInstance());
		KeyCallbacks.init(MinecraftClient.getInstance());
	}
}