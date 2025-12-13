package net.evmodder.evmod;
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

import java.io.InputStreamReader;
import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.event.InputEventHandler;
import fi.dy.masa.malilib.registry.Registry;
import fi.dy.masa.malilib.util.data.ModInfo;
import net.evmodder.EvLib.util.Command;
import net.evmodder.EvLib.util.FileIO_New;
import net.evmodder.evmod.apis.ChatBroadcaster;
import net.evmodder.evmod.apis.ClickUtils;
import net.evmodder.evmod.apis.EpearlLookup;
import net.evmodder.evmod.apis.RemoteServerSender;
import net.evmodder.evmod.apis.Tooltip;
import net.evmodder.evmod.commands.*;
import net.evmodder.evmod.keybinds.KeybindCraftingRestock;
import net.evmodder.evmod.keybinds.KeybindInventoryOrganize;
import net.evmodder.evmod.keybinds.KeybindInventoryRestock;
import net.evmodder.evmod.listeners.*;
import net.evmodder.evmod.onTick.AutoPlaceItemFrames;
import net.evmodder.evmod.onTick.TooltipMapLoreMetadata;
import net.evmodder.evmod.onTick.TooltipMapNameColor;
import net.evmodder.evmod.onTick.TooltipRepairCost;
import net.evmodder.evmod.onTick.UpdateContainerHighlights;
import net.evmodder.evmod.onTick.UpdateInventoryHighlights;
import net.evmodder.evmod.onTick.UpdateItemFrameHighlights;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;

//MC source code will be in ~/.gradle/caches/fabric-loom or ./.gradle/loom-cache
// gradle tasks --all
// gradle genSources/eclipse/--stop
// gradle build --refresh-dependencies
// gradle migrateMappings --mappings "1.21.4+build.8"

public class Main{
	// Splash potion harming, weakness (spider eyes, sugar, gunpowder, brewing stand)
	//TODO:
	// map load from bundle skip loaded maps
	// support non-QWERTY?
	// GUI: StringHotkeyed, SlotListHotkeyed, ServerAddress(addr:port)
	// Investigate https://github.com/Siphalor/amecs-api (potential better alternative to MaLiLib?)
	// ignorelist sync, /seen and other misc stats cmds for DB mode
	// keybind to sort maps in inventory (request from FartRipper), 2 modes: incr by slot index vs make a rectangle in inv
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

//	private static final String fabricModJsonStr = FileIO_New.loadResource(FabricEntryPoint.class, "fabric.mod.json", null);
//	private static final JsonObject fabricModJsonObj = JsonParser.parseString(fabricModJsonStr).getAsJsonObject();
	public static final String MOD_ID;
	public static final String MOD_NAME;
	static{
		InputStreamReader inputStreamReader = new InputStreamReader(Main.class.getResourceAsStream("/fabric.mod.json"));
		JsonObject fabricModJsonObj = JsonParser.parseReader(inputStreamReader).getAsJsonObject();
		MOD_ID = fabricModJsonObj.get("id").getAsString();
		MOD_NAME = fabricModJsonObj.get("name").getAsString();
	}
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	// TODO: delete this
	private static Main instance; static Main getInstance(){return instance;} // Accessors: Configs

	// TODO: Worth finding a way to make these private+final+nonstatic?
	public static ClickUtils clickUtils; // Public accessors: virtually all keybinds
	public static RemoteServerSender remoteSender; // Public accessors: EpearlLookup, MiscUtils
	public static EpearlLookup epearlLookup; // Public accessors: MixinEntityRenderer

	public static final KeybindCraftingRestock kbCraftRestock = new KeybindCraftingRestock(); // Public accessors: MixinClientPlayerInteractionManager

	// TOOD: Worth finding a way to make these private?
	final GameMessageFilter gameMessageFilter; // Accessors: Configs, KeyCallbacks
	final WhisperPlaySound whisperPlaySound; // Accessors: KeyCallbacks
	final KeybindInventoryOrganize[] kbInvOrgs;
	final KeybindInventoryRestock kbInvRestock;
	final boolean inventoryRestockAuto, placementHelperIframe, placementHelperMapArt, placementHelperMapArtAutoPlace, placementHelperMapArtAutoRemove, broadcaster;
	final boolean serverJoinListener, serverQuitListener, gameMessageListener/*, gameMessageFilter*/, containerOpenCloseListener;
	final boolean cmdExportMapImg, cmdMapArtGroup;
	final boolean mapHighlights, mapHighlightsInGUIs, tooltipMapHighlights, tooltipMapMetadata, tooltipRepairCost;

	static boolean mapArtFeaturesOnly = true; //// TODO: ewww hacky
	private final String internalConfigFile = mapArtFeaturesOnly ? "enabled_features_for_mapart_ver.txt" : "enabled_features.txt";

	private HashMap<String, Boolean> loadConfig(){
		HashMap<String, Boolean> config = new HashMap<>();
		final String configContents = FileIO_New.loadFile("enabled_features.txt", getClass().getResourceAsStream("/"+internalConfigFile));
		for(String line : configContents.split("\\r?\\n")){
			final int sep = line.indexOf(':');
			if(sep == -1) continue;
			final String key = line.substring(0, sep).trim();
			final String value = line.substring(sep+1).trim();
			if(key.isEmpty() || value.isEmpty()) continue;
			if(key.equals("mapart_features_only")) config.put(key, !value.equalsIgnoreCase("false")); // Prefer true when ambiguous
			else config.put(key, value.equalsIgnoreCase("true")); // Prefer false when ambiguous
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
		placementHelperMapArtAutoPlace = placementHelperMapArt && extractConfigValue(config, "placement_helper.mapart.auto");
		placementHelperMapArtAutoRemove = placementHelperMapArt && extractConfigValue(config, "placement_helper.mapart.autoremove");
		serverJoinListener = extractConfigValue(config, "listener.server_join");
		serverQuitListener = extractConfigValue(config, "listener.server_quit");
		gameMessageListener = extractConfigValue(config, "listener.game_message.read");
		boolean registerGameMessageFilter = extractConfigValue(config, "listener.game_message.filter");
		containerOpenCloseListener = extractConfigValue(config, "listener.container_open");
		mapHighlights = extractConfigValue(config, "map_highlights");
		mapHighlightsInGUIs = extractConfigValue(config, "map_highlights.in_gui");
		tooltipMapHighlights = mapHighlights && extractConfigValue(config, "tooltip.map_highlights");
		tooltipMapMetadata = extractConfigValue(config, "tooltip.map_metadata");
		tooltipRepairCost = extractConfigValue(config, "tooltip.repair_cost");
		inventoryRestockAuto = containerOpenCloseListener && extractConfigValue(config, "inventory_restock.auto");

		boolean cmdAssignPearl = epearlOwners && extractConfigValue(config, "command.assignpearl");
		cmdExportMapImg = extractConfigValue(config, "command.exportmapimg");
		cmdMapArtGroup = extractConfigValue(config, "command.mapartgroup");
		boolean cmdSeen = database && extractConfigValue(config, "command.seen");
		boolean cmdSendAs = database && extractConfigValue(config, "command.sendas");
		boolean cmdTimeOnline = database && extractConfigValue(config, "command.timeonline");

		if(!config.isEmpty()){
			LOGGER.error("Unrecognized config setting(s)!: "+config);
		}

		KeyCallbacks.remakeClickUtils();
		if(database){
			KeyCallbacks.remakeRemoteServerSender();
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
		whisperPlaySound = mapArtFeaturesOnly ? null : new WhisperPlaySound();
		if(gameMessageListener) new GameMessageListener(epearlLookup, whisperPlaySound);
		gameMessageFilter = registerGameMessageFilter ? new GameMessageFilter() : null;

		kbInvOrgs = mapArtFeaturesOnly ? null : new KeybindInventoryOrganize[]{
				new KeybindInventoryOrganize(Configs.Hotkeys.INV_ORGANIZE_1.getStrings()),
				new KeybindInventoryOrganize(Configs.Hotkeys.INV_ORGANIZE_2.getStrings()),
				new KeybindInventoryOrganize(Configs.Hotkeys.INV_ORGANIZE_3.getStrings())
		};
		kbInvRestock = kbInvOrgs == null ? null : new KeybindInventoryRestock(kbInvOrgs);
		if(containerOpenCloseListener){
			ContainerOpenCloseListener cocl = new ContainerOpenCloseListener(kbInvRestock);
			ClientTickEvents.END_CLIENT_TICK.register(cocl::onUpdateTick);
		}

		if(placementHelperIframe) new AutoPlaceItemFrames();
		if(placementHelperMapArt) new MapHandRestock(placementHelperMapArtAutoPlace, placementHelperMapArtAutoRemove);
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
		KeyCallbacks.init(this);
	}
}