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
import net.evmodder.KeyBound.apis.Tooltip;
import net.evmodder.KeyBound.commands.*;
import net.evmodder.KeyBound.keybinds.ClickUtils;
import net.evmodder.KeyBound.keybinds.KeybindCraftingRestock;
import net.evmodder.KeyBound.listeners.*;
import net.evmodder.KeyBound.onTick.AutoPlaceItemFrames;
import net.evmodder.KeyBound.onTick.TooltipMapLoreMetadata;
import net.evmodder.KeyBound.onTick.TooltipMapNameColor;
import net.evmodder.KeyBound.onTick.TooltipRepairCost;
import net.evmodder.KeyBound.onTick.UpdateContainerHighlights;
import net.evmodder.KeyBound.onTick.UpdateInventoryHighlights;
import net.evmodder.KeyBound.onTick.UpdateItemFrameHighlights;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.MinecraftClient;

//MC source code will be in ~/.gradle/caches/fabric-loom or ./.gradle/loom-cache
// gradle tasks --all
// gradle genSources/eclipse/--stop
// gradle build --refresh-dependencies
// gradle migrateMappings --mappings "1.21.4+build.8"

public class Main{
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


	// Static references (TODO: fetch from fabric.mod.json?)
	public static final String MOD_ID = "evmod";
	public static final String MOD_NAME = "Ev's Mod";
//	public static final String MOD_VERSION = MiscUtils.getModVersionString(MOD_ID);
//	public static final String MC_VERSION = MinecraftVersion.CURRENT.getName();
//	public static final String MOD_TYPE = "fabric";
//	public static final String MOD_STRING = MOD_ID + "-" + MOD_TYPE + "-" + MC_VERSION + "-" + MOD_VERSION;
	private static final String CONFIG_NAME = "enabled_features_for_mapart_ver.txt";

	private static Main instance; static Main getInstance(){return instance;}
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);


	// TODO: Worth finding a way to make these private+final+nonstatic?
	public static ClickUtils clickUtils; // Public accessors: virtually all keybinds
	public static RemoteServerSender remoteSender; // Public accessors: EpearlLookup, MiscUtils
	public static EpearlLookup epearlLookup; // Public accessors: MixinEntityRenderer
	public static final KeybindCraftingRestock kbCraftRestock = new KeybindCraftingRestock(); // Public accessors: MixinClientPlayerInteractionManager

	static boolean mapArtFeaturesOnly = true; //// TODO: ewww hacky

	// TOOD: Worth finding a way to make these private?
	final GameMessageFilter gameMessageFilter; // Accessors: Configs
	final ContainerOpenCloseListener containerOpenCloseListener; // Accessors: Configs

	final boolean inventoryRestockAuto, placementHelperIframe, placementHelperMapArt, placementHelperMapArtAuto, broadcaster;
	final boolean serverJoinListener, serverQuitListener, gameMessageListener;//, gameMessageFilter;
	final boolean cmdExportMapImg, cmdMapArtGroup;
	final boolean mapHighlights, mapHighlightsInGUIs, tooltipMapHighlights, tooltipMapMetadata, tooltipRepairCost;

	private HashMap<String, Boolean> loadConfig(){
		HashMap<String, Boolean> config = new HashMap<>();
		final String configContents = FileIO.loadFile(CONFIG_NAME, getClass().getResourceAsStream("/"+CONFIG_NAME));
		for(String line : configContents.split("\\r?\\n")){
			final int sep = line.indexOf(':');
			if(sep == -1) continue;
			final String key = line.substring(0, sep).trim();
			final String value = line.substring(sep+1).trim();
			if(key.isEmpty() || value.isEmpty()) continue;
			config.put(key, !value.equalsIgnoreCase("false"));
		}
		return config;
	}

	private boolean extractConfigValue(HashMap<String, Boolean> config, String key){
		Boolean value = config.remove(key);
		return value != null ? value : false;
	}

	Main(){
		instance = this;
		final HashMap<String, Boolean> config = loadConfig();

		mapArtFeaturesOnly = config.getOrDefault("mapart_features_only", true); // Note: true instead of false
		config.remove("mapart_features_only");

		boolean database = extractConfigValue(config, "database");
		boolean epearlOwners = extractConfigValue(config, "epearl_owners");
		broadcaster = extractConfigValue(config, "broadcaster");
		placementHelperIframe = extractConfigValue(config, "placement_helper.iframe");
		placementHelperMapArt = extractConfigValue(config, "placement_helper.mapart");
		placementHelperMapArtAuto = placementHelperMapArt && extractConfigValue(config, "placement_helper.mapart.auto");
		serverJoinListener = extractConfigValue(config, "listener.server_join");
		serverQuitListener = extractConfigValue(config, "listener.server_quit");
		gameMessageListener = extractConfigValue(config, "listener.game_message.read");
		boolean registerGameMessageFilter = extractConfigValue(config, "listener.game_message.filter");
		boolean registerContainerOpenListener = extractConfigValue(config, "listener.container_open");
		mapHighlights = extractConfigValue(config, "map_highlights");
		mapHighlightsInGUIs = extractConfigValue(config, "map_highlights.in_gui");
		tooltipMapHighlights = mapHighlights && extractConfigValue(config, "tooltip.map_highlights");
		tooltipMapMetadata = extractConfigValue(config, "tooltip.map_metadata");
		tooltipRepairCost = extractConfigValue(config, "tooltip.repair_cost");
		inventoryRestockAuto = registerContainerOpenListener && extractConfigValue(config, "inventory_restock.auto");

		boolean cmdAssignPearl = epearlOwners && extractConfigValue(config, "command.assignpearl");
		cmdExportMapImg = extractConfigValue(config, "command.exportmapimg");
		cmdMapArtGroup = extractConfigValue(config, "command.mapartgroup");
		boolean cmdSeen = database && extractConfigValue(config, "command.seen");
		boolean cmdSendAs = database && extractConfigValue(config, "command.sendas");
		boolean cmdTimeOnline = database && extractConfigValue(config, "command.timeonline");

		if(!config.isEmpty()){
			LOGGER.error("Unrecognized config setting(s)!: "+config);
		}

		KeyCallbacks.remakeClickUtils(null);
		if(database){
			KeyCallbacks.remakeRemoteServerSender(null);
			remoteSender.sendBotMessage(Command.PING, /*udp=*/false, /*timeout=*/5000, /*msg=*/new byte[0], null);
//			msg->LOGGER.info("Remote server responded to ping: "+(msg == null ? null : new String(msg)))
		}
		if(epearlOwners){
			epearlLookup = new EpearlLookup(); // MUST be instantiated AFTER remoteSender
			if(cmdAssignPearl) new CommandAssignPearl(epearlLookup);
		}
		else epearlLookup = null;

		if(serverJoinListener) new ServerJoinListener();
		if(serverQuitListener) new ServerQuitListener();
//		if(gameMessageListener) new GameMessageListener();
		gameMessageFilter = registerGameMessageFilter ? new GameMessageFilter() : null;

		containerOpenCloseListener = registerContainerOpenListener ? new ContainerOpenCloseListener() : null;
		if(inventoryRestockAuto) ClientTickEvents.END_CLIENT_TICK.register(containerOpenCloseListener::onUpdateTick);

		if(placementHelperIframe) new AutoPlaceItemFrames();
		if(placementHelperMapArt) new MapHandRestock(placementHelperMapArtAuto);
		if(broadcaster) ChatBroadcaster.refreshBroadcast();

		if(cmdAssignPearl) new CommandAssignPearl(epearlLookup);
		if(cmdExportMapImg) new CommandExportMapImg();
		if(cmdMapArtGroup) new CommandMapArtGroup();
		if(cmdSeen) new CommandSeen();
		if(cmdSendAs) new CommandSendAs();
		if(cmdTimeOnline) new CommandTimeOnline();

		//new KeybindSpamclick();

		if(mapHighlights){
			if(tooltipMapHighlights) ItemTooltipCallback.EVENT.register(TooltipMapNameColor::tooltipColors);
			ClientTickEvents.START_CLIENT_TICK.register(client -> {
				UpdateInventoryHighlights.onUpdateTick(client.player);
				UpdateItemFrameHighlights.onUpdateTick(client);
				if(mapHighlightsInGUIs) UpdateContainerHighlights.onUpdateTick(client);
			});
		}
		if(tooltipMapMetadata) Tooltip.register(new TooltipMapLoreMetadata());
		if(tooltipRepairCost) Tooltip.register(new TooltipRepairCost());

		ConfigManager.getInstance().registerConfigHandler(MOD_ID, new Configs());
		Registry.CONFIG_SCREEN.registerConfigScreenFactory(new ModInfo(MOD_ID, MOD_NAME, ConfigGui::new));

		InputEventHandler.getKeybindManager().registerKeybindProvider(InputHandler.getInstance());
		KeyCallbacks.init(MinecraftClient.getInstance());
	}
}