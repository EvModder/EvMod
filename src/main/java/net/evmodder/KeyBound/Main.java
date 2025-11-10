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
import net.evmodder.KeyBound.config.ConfigGui;
import net.evmodder.KeyBound.config.Configs;
import net.evmodder.KeyBound.config.InputHandler;
import net.evmodder.KeyBound.config.KeyCallbacks;
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
	// fix the JANKY BROKENESS of MapMove when count==2 (i think selective move?)
	// GUI: StringHotkeyed, SlotListHotkeyed, YawPitchHotkeyed, UUIDList/PlayerList, ServerAddress(addr:port)

	// Investigate https://github.com/Siphalor/amecs-api (potential better alternative to MaLiLib?)

	// ignorelist sync, /seen and other misc stats cmds for DB mode

	// keybind to sort maps in inventory (rly good request from FartRipper), 2 modes: incr by slot index vs make a rectangle in inv

	// SendOnServerJoin configureable per-server (via ip?)

	// timeOfDay >= 2000 && timeOfDay < 9000

	// Feature Ideas:
	// change render order of certain villager trades (in particular: make cleric redstone always above rotten flesh)
	// totem in offhand - render itemcount for sum of totems in inv (instead of itemcount 1) - IMO nicer than the RH/meteor/etc UI overlay
	// Maps - make item count font smaller, cuz it kinda covers img in slot
	// Buff MapRelationUtils- HandRestock/MoveNeighbor/AutoPlace/AutoSort assembling items into NxM. rn it's greedy; can make it DFS + DP (img ver as well)

	// /msgas EvDoc <target> <msg> - send msgs as another acc (TODO: also make as a zenithproxy plugin)
	// add time left on 2b (8h-time online) infoline to miniHUD settings
	// Low RC quest: auto enchant dia sword, auto grindstone, auto rename, auto anvil combine. auto enchant bulk misc items
	// inv-keybind-craft-latest-item, also for enchant table and grindstone (eg. spam enchanting axes) via spacebar, like vanilla

	// Reference variables
	public static final String MOD_ID = "keybound"; // TODO: pull from fabric/gradle?
	public static final String MOD_NAME = "KeyBound"; // TODO: pull from fabric/gradle?
	//public static final String MOD_VERSION = "@MOD_VERSION@";
	private static final String CONFIG_NAME = "enabled_features.txt";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final int HASHCODE_2B2T = -437714968;//"2b2t.org".hashCode() // TODO: make mod more server-independent 

	public static ClickUtils clickUtils;
	public static RemoteServerSender remoteSender;
	public static EpearlLookup epearlLookup;
	public static GameMessageFilter gameMessageFilter;
	public static KeybindInventoryRestock kbInvRestock;
	public static ContainerOpenCloseListener containerOpenCloseListener;

	public static boolean mapArtFeaturesOnly = true; ////////////////// TODO: ewww
	public static boolean inventoryRestockAuto, placementHelperIframe, placementHelperMapArt, placementHelperMapArtAuto, broadcaster;
	public static boolean serverJoinListener, serverQuitListener, gameMessageListener;//, gameMessageFilter;
	public static boolean cmdExportMapImg, cmdMapArtGroup;
//	public static boolean keybindMapArtMove, keybindMapArtMoveBundle;

	public static boolean mapHighlights, mapHighlightsInGUIs;

	private HashMap<String, String> loadConfig(){
		HashMap<String, String> config = new HashMap<>();
		final String configContents = FileIO.loadFile(CONFIG_NAME, getClass().getResourceAsStream("/"+CONFIG_NAME));
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
		boolean epearlOwners=false;
		boolean mapHighlightTooltip=false;

		//config.forEach((key, value) -> {
		for(String key : config.keySet()){
			final String value = config.get(key);
			switch(key){
				case "database": database = !value.equalsIgnoreCase("false"); break;
				case "epearl_owners": epearlOwners = !value.equalsIgnoreCase("false"); break;
				case "mapart_features_only": mapArtFeaturesOnly = !value.equalsIgnoreCase("false"); break;///////////

				case "broadcaster": broadcaster = !value.equalsIgnoreCase("false"); break;
				case "placement_helper.iframe": placementHelperIframe = !value.equalsIgnoreCase("false"); break;
				case "placement_helper.mapart": placementHelperMapArt = !value.equalsIgnoreCase("false"); break;
				case "placement_helper.mapart.auto": placementHelperMapArtAuto = !value.equalsIgnoreCase("false"); break;
				case "listener.server_join": serverJoinListener = !value.equalsIgnoreCase("false"); break;
				case "listener.server_quit": serverQuitListener = !value.equalsIgnoreCase("false"); break;
				case "listener.game_message.read": if(gameMessageListener = !value.equalsIgnoreCase("false")); break;
				case "listener.game_message.filter": if(!value.equalsIgnoreCase("false")) gameMessageFilter = new GameMessageFilter(); break;
				case "listener.container_open": if(!value.equalsIgnoreCase("false")) containerOpenCloseListener = new ContainerOpenCloseListener(); break;
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
		}
		if(epearlOwners){
			epearlLookup = new EpearlLookup(); // MUST be instantiated AFTER remoteSender
			if(cmdAssignPearl) new CommandAssignPearl();
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
		if(cmdTimeOnline/* && serverJoinListener*/) new CommandTimeOnline();

		kbInvRestock = new KeybindInventoryRestock();
		if(inventoryRestockAuto &= (containerOpenCloseListener!=null)) ClientTickEvents.END_CLIENT_TICK.register(containerOpenCloseListener::onUpdateTick);
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