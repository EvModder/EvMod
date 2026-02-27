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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.registry.Registry;
import fi.dy.masa.malilib.util.data.ModInfo;
import net.evmodder.EvLib.util.FileIO;
import net.evmodder.evmod.apis.ChatBroadcaster;
import net.evmodder.evmod.apis.EpearlLookupFabric;
import net.evmodder.evmod.apis.MiscUtils;
import net.evmodder.evmod.apis.RemoteServerSender;
import net.evmodder.evmod.apis.TickListener;
import net.evmodder.evmod.apis.Tooltip;
import net.evmodder.evmod.apis.WhisperPlaySound;
import net.evmodder.evmod.commands.*;
import net.evmodder.evmod.keybinds.KeybindCraftingRestock;
import net.evmodder.evmod.keybinds.KeybindInventoryOrganize;
import net.evmodder.evmod.keybinds.KeybindInventoryRestock;
import net.evmodder.evmod.listeners.*;
import net.evmodder.evmod.onTick.AutoPlaceItemFrames;
import net.evmodder.evmod.onTick.ContainerOpenCloseListener;
import net.evmodder.evmod.onTick.MapLoaderBot;
import net.evmodder.evmod.onTick.SyncPlayerPos;
import net.evmodder.evmod.onTick.TooltipMapLoreMetadata;
import net.evmodder.evmod.onTick.TooltipMapNameColor;
import net.evmodder.evmod.onTick.TooltipRepairCost;
import net.evmodder.evmod.onTick.UpdateContainerContents;
import net.evmodder.evmod.onTick.UpdateInventoryContents;
import net.evmodder.evmod.onTick.UpdateItemFrameContents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModMetadata;

//MC source code will be in ~/.gradle/caches/fabric-loom or ./.gradle/loom-cache
// gradle tasks --all
// gradle genSources/eclipse/--stop
// gradle build --refresh-dependencies
// gradle migrateMappings --mappings "1.21.4+build.8"

public class Main{
	// Ev PNG->NBT preset:
	//https://evmodder.net/PNG-to-NBT/?preset=RXYncyBQcmVzZXR8MSwxLDAsMCwxLDksMCw3LDAsMjYsMywxLDAsNiwxMyw0LDMsMywzLDMsMywzLDQsOCwxLDExLDMsMTEsMTgsMCwxLDAsMCwxLDAsMCwyLDAsMCwwLDAsMCwxLDAsMCwwLDAsMSwwLDEsMCwwLDAsMCwwLDAsMCwwLDEsMCwxfHJlc2luX2Jsb2NrfG5vbmV8c3VwcHJlc3NfcGFpcnNfZXd8fDE
	// Splash potion harming, weakness (spider eyes, sugar, gunpowder, brewing stand)
	//TODO:
	// fix NULL map detection (bundle stow isn't sucking up unloaded maps rn)
	// map-click-move: work multiple copies of same NxM map in inventory(s)
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

	// /msgas EvDoc <target> <msg> - send msgs as another acc (also make as a zenithproxy plugin)
	// add time left on 2b (8h-time online) infoline to miniHUD settings
	// Low RC quest: auto enchant dia sword, auto grindstone, auto rename, auto anvil combine. auto enchant bulk misc items
	// inv-keybind-craft-latest-item, also for enchant table and grindstone (eg. spam enchanting axes) via spacebar, like vanilla

	public static final String MOD_ID = InitUtils.getModId();
	public static final String MOD_NAME; // Only accessor: KeyCallbacks
	public static final String MOD_VERSION; // Only accessor: ConfigGui
	public static final String CONFIG_DIR; // Only accessor: Configs
	public static final Logger LOGGER;
	static{
		FileIO.DIR = CONFIG_DIR = FabricLoader.getInstance().getConfigDir().toString()+"/"+MOD_ID+"/";
		final ModMetadata metadata = FabricLoader.getInstance().getModContainer(MOD_ID).get().getMetadata();
		MOD_NAME = metadata.getName();
		MOD_VERSION = metadata.getVersion().getFriendlyString();
		LOGGER = LoggerFactory.getLogger(MOD_ID);
	}

	static boolean mapArtFeaturesOnly = true; // TODO: eww hacky

	@SuppressWarnings("unused") private static Main instance; // Mixin Accessors:
	public final RemoteServerSender remoteSender; // MixinClientPlayNetworkHandler
	public final EpearlLookupFabric epearlLookup; // MixinEntityRenderer
	public final KeybindCraftingRestock kbCraftRestock; // MixinClientPlayerInteractionManager
	public final SyncPlayerPos syncPlayerPos; // ClientPlayNetworkHandler

	Main(){
		Main.LOGGER.info("Loading "+MOD_NAME+" "+MOD_VERSION);
		instance = this;
		final Settings settings = new Settings();
		if(settings.storeDataInInstanceFolder) FileIO.DIR = FabricLoader.getInstance().getGameDir()+"/"+MOD_ID+"/";
		final Configs configs = new Configs(settings);
		configs.load();

		InitUtils.refreshClickLimits();
		InitUtils.refreshClickRenderer();

		if(!settings.database) remoteSender = null;
		else{
			remoteSender = new RemoteServerSender(LOGGER, MiscUtils::getServerAddressHashCode);
			InitUtils.refreshRemoteServerSender(remoteSender); // Potentially using DUMMY_CLIENT_ID
			InitUtils.checkValidClientKeyAndRequestIfNot(remoteSender, configs); // Request a real clientId if needed
		}

		epearlLookup = settings.epearlOwners ? new EpearlLookupFabric(remoteSender) : null;

		if(settings.serverJoinListener) new ServerJoinListener(remoteSender);
		if(settings.serverQuitListener) new ServerQuitListener(remoteSender);
		if(settings.blockClickListener) new BlockClickListener();
		final WhisperPlaySound whisperPlaySound;
		final GameMessageFilter gameMessageFilter;
		final KeybindInventoryOrganize[] kbInvOrgs;
		final KeybindInventoryRestock kbInvRestock;
		if(mapArtFeaturesOnly){
			whisperPlaySound = null; gameMessageFilter = null; kbInvOrgs = null; kbInvRestock = null; kbCraftRestock = null; syncPlayerPos = null;
		}
		else{
			whisperPlaySound = new WhisperPlaySound();
			if(settings.gameMessageListener) new GameMessageListener(remoteSender, epearlLookup, whisperPlaySound);
			gameMessageFilter = settings.gameMessageFilter ? new GameMessageFilter(remoteSender) : null;
			kbInvOrgs = new KeybindInventoryOrganize[]{
					new KeybindInventoryOrganize(Configs.Hotkeys.INV_ORGANIZE_1.getStrings()),
					new KeybindInventoryOrganize(Configs.Hotkeys.INV_ORGANIZE_2.getStrings()),
					new KeybindInventoryOrganize(Configs.Hotkeys.INV_ORGANIZE_3.getStrings())
			};
			kbInvRestock = new KeybindInventoryRestock(kbInvOrgs);
			kbCraftRestock = new KeybindCraftingRestock();

			if(settings.broadcaster) ChatBroadcaster.refreshBroadcast();
			if(settings.tooltipRepairCost) Tooltip.register(new TooltipRepairCost());
			if(settings.playerMoveListener) TickListener.register(syncPlayerPos = new SyncPlayerPos());
			else syncPlayerPos = null;
		}

		if(settings.placementHelperIframeAutoPlace) new AutoPlaceItemFrames();
		if(settings.placementHelperMapArt) new MapHangListener(settings.placementHelperMapArtAutoPlace, settings.placementHelperMapArtAutoRemove);

		if(/*!mapArtFeaturesOnly && */settings.cmdAssignPearl) new CommandAssignPearl(epearlLookup);
		if(settings.cmdDeletedMapsNearby) new CommandDeletedMapsNearby();
		if(settings.cmdExportMapImg) new CommandExportMapImg();
		if(settings.cmdMapArtGroup) new CommandMapArtGroup();
		if(settings.cmdMapHashCode) new CommandMapHashCode();
		if(/*!mapArtFeaturesOnly && */settings.cmdSeen) new CommandSeen();
		if(/*!mapArtFeaturesOnly && */settings.cmdSendAs) new CommandSendAs(remoteSender);
		if(/*!mapArtFeaturesOnly && */settings.cmdTimeOnline) new CommandTimeOnline(remoteSender);

		if(settings.onTickInventory) TickListener.register(new UpdateInventoryContents());
		if(settings.onTickIframes) TickListener.register(new UpdateItemFrameContents());
		if(settings.onTickContainer) TickListener.register(new UpdateContainerContents());
		if(settings.containerOpenCloseListener) TickListener.register(new ContainerOpenCloseListener(kbInvRestock));
		if(settings.mapLoaderBot) TickListener.register(new MapLoaderBot());

		if(settings.tooltipMapHighlights) Tooltip.register(new TooltipMapNameColor());
		if(settings.tooltipMapMetadata) Tooltip.register(new TooltipMapLoreMetadata());

		ConfigManager.getInstance().registerConfigHandler(MOD_ID, configs);
		Registry.CONFIG_SCREEN.registerConfigScreenFactory(new ModInfo(MOD_ID, MOD_NAME, ()->new ConfigGui(configs)));
		new KeyCallbacks(configs, remoteSender, epearlLookup, kbCraftRestock, whisperPlaySound, gameMessageFilter, kbInvOrgs, kbInvRestock);
	}
}