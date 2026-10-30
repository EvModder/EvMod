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
import net.evmodder.evmod.onTick.MuteMusicOnMenuScreen;
import net.evmodder.evmod.onTick.SyncPlayerPos;
import net.evmodder.evmod.onTick.TooltipMapLoreMetadata;
import net.evmodder.evmod.onTick.TooltipMapNameColor;
import net.evmodder.evmod.onTick.TooltipRepairCost;
import net.evmodder.evmod.onTick.UpdateContainerContents;
import net.evmodder.evmod.onTick.UpdateInventoryContents;
import net.evmodder.evmod.onTick.UpdateItemFrameContents;
import net.evmodder.evmod.render.StaticEntityRenderCache;
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
	// Bulk-analysis stats (for containers/walls), e.g., replacing colored '*' with '{count}'
	// EpearlPullCommand: if received (by EvMod or EvModZP), if requester has their own pearl in render range, pull any pearl they request.
	// AIE Helper: no low-Y kick if connected to a proxy!! (or use illegal disconnect)
	// fix NULL map detection (bundle stow isn't sucking up unloaded maps rn)
	// map-click-move: work multiple copies of same NxM map in inventory(s)
	// map load from bundle skip already-loaded maps
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
	public static final String MOD_NAME;  // Accessed by: KeyCallbacks
	public static final String MOD_VERSION;  // Accessed by: ConfigGui
	public static final String CONFIG_DIR;  // Accessed by: Configs
	public static final Logger LOGGER;
	static{
		FileIO.DIR = CONFIG_DIR = FabricLoader.getInstance().getConfigDir().toString()+"/"+MOD_ID+"/";
		final ModMetadata metadata = FabricLoader.getInstance().getModContainer(MOD_ID).get().getMetadata();
		MOD_NAME = metadata.getName();
		MOD_VERSION = metadata.getVersion().getFriendlyString();
		LOGGER = LoggerFactory.getLogger(MOD_ID);
	}

	// Accessed by: MixinClientPacketListener, MixinClientPacketListenerCrafting, MixinClientPacketListenerPlayerPosition,
	//              MixinClientPacketListenerSeenMaps, MixinEntityRendererPearl, MixinMultiPlayerGameModeCrafting
	private static Main instance;
	// Accessed by: MixinClientPacketListenerSeenMaps
	private final RemoteServerSender remoteSender;
	// Accessed by: MixinClientPacketListener, MixinEntityRendererPearl
	private final EpearlLookupFabric epearlLookup;
	// Accessed by: MixinClientPacketListenerCrafting, MixinMultiPlayerGameModeCrafting
	private final KeybindCraftingRestock kbCraftRestock;
	// Accessed by: MixinClientPacketListenerPlayerPosition
	private final SyncPlayerPos syncPlayerPos;

	// Accessed by: MixinClientPacketListenerSeenMaps
	@SuppressWarnings("unused") private static RemoteServerSender getRemoteSenderForMixin(){return instance.remoteSender;}
	// Accessed by: MixinClientPacketListener, MixinEntityRendererPearl
	@SuppressWarnings("unused") private static EpearlLookupFabric getEpearlLookupForMixin(){return instance.epearlLookup;}
	// Accessed by: MixinClientPacketListenerCrafting, MixinMultiPlayerGameModeCrafting
	@SuppressWarnings("unused") private static KeybindCraftingRestock getKbCraftRestockForMixin(){return instance.kbCraftRestock;}
	// Accessed by: MixinClientPacketListenerPlayerPosition
	@SuppressWarnings("unused") private static SyncPlayerPos getSyncPlayerPosForMixin(){return instance.syncPlayerPos;}

	Main(){
		Main.LOGGER.info("Loading "+MOD_NAME+" "+MOD_VERSION);
		instance = this;
		final Settings settings = Settings.get();
		LegacySettingsMigration.mapGroups(!settings.enabled(Setting.STORE_DATA_IN_CONFIG_FOLDER));
		if(!settings.enabled(Setting.STORE_DATA_IN_CONFIG_FOLDER)) FileIO.DIR = FabricLoader.getInstance().getGameDir()+"/"+MOD_ID+"/";
		final Configs configs = new Configs(settings);
		configs.load();
		new ServerListScrollListener(configs.getServerListScroll(), configs::setServerListScroll,
				Configs.Generic.REMEMBER_SERVER_LIST_SCROLL::getBooleanValue);

		if(settings.usesClicks()){
			InitUtils.refreshClickLimits();
			InitUtils.refreshClickRenderer();
		}

		if(!settings.database) remoteSender = null;
		else{
			remoteSender = new RemoteServerSender(LOGGER, MiscUtils::getRemoteServerDescriptor);
			InitUtils.refreshRemoteServerSender(remoteSender);  // Potentially using DUMMY_CLIENT_ID
			InitUtils.checkValidClientKeyAndRequestIfNot(remoteSender, configs);  // Request a real clientId if needed
		}

		epearlLookup = settings.epearlOwners ? new EpearlLookupFabric(remoteSender) : null;

		if(settings.serverJoinListener) new ServerJoinListener(remoteSender);
		if(settings.serverQuitListener) new ServerQuitListener(remoteSender);
		if(settings.enabled(Setting.FIREWORK_BOOST)) new FireworkBoostListener();
		if(settings.blockClickListener) new BlockClickListener();
		final WhisperPlaySound whisperPlaySound = settings.enabled(Setting.WHISPER_SOUND) ? new WhisperPlaySound() : null;
		if(settings.gameMessageListener) new GameMessageListener(remoteSender, epearlLookup, whisperPlaySound);
		final GameMessageFilter gameMessageFilter = settings.gameMessageFilter ? new GameMessageFilter(remoteSender) : null;
		final KeybindInventoryOrganize[] kbInvOrgs = !settings.enabled(Setting.INVENTORY_TOOLS) ? null : new KeybindInventoryOrganize[]{
				new KeybindInventoryOrganize(Configs.Generic.INV_ORGANIZE_1.getStrings()),
				new KeybindInventoryOrganize(Configs.Generic.INV_ORGANIZE_2.getStrings()),
				new KeybindInventoryOrganize(Configs.Generic.INV_ORGANIZE_3.getStrings())
		};
		final KeybindInventoryRestock kbInvRestock = settings.enabled(Setting.INVENTORY_TOOLS) ? new KeybindInventoryRestock(kbInvOrgs) : null;
		kbCraftRestock = settings.enabled(Setting.CRAFTING) ? new KeybindCraftingRestock() : null;

		if(settings.broadcaster) ChatBroadcaster.refreshBroadcast();
		if(settings.tooltipRepairCost) Tooltip.register(new TooltipRepairCost());
		if(settings.playerMoveListener) TickListener.register(syncPlayerPos = new SyncPlayerPos());
		else syncPlayerPos = null;

		if(settings.placementHelperIframeAutoPlace) new AutoPlaceItemFrames();
		if(settings.placementHelperMapArt) new MapHangListener(settings.placementHelperMapArtAutoPlace, settings.placementHelperMapArtAutoRemove);

		if(settings.epearlOwners) new CommandAssignPearl(epearlLookup);
		if(settings.epearlOwners) new CommandPearlRescue(epearlLookup);
		new CommandEvMod(()->new ConfigGui(configs));
		new CommandDeletedMapsNearby();
		new CommandExportMapImg(settings.onTickIframes);
		if(settings.cmdExportMapNames) new CommandExportMapNames();
		if(settings.cmdMapArtGroup) new CommandMapArtGroup();
		new CommandMapHashCode();
		if(settings.database) new CommandSeen();
		if(settings.database) new CommandSendAs(remoteSender);
		if(settings.cmdTimeOnline) new CommandTimeOnline(remoteSender);

		if(settings.onTickInventory) TickListener.register(new UpdateInventoryContents());
		if(settings.onTickIframes) TickListener.register(new UpdateItemFrameContents());
		if(settings.onTickContainer) TickListener.register(new UpdateContainerContents());
		if(settings.muteMusicOnMenuScreen) TickListener.register(new MuteMusicOnMenuScreen());
		if(settings.containerOpenCloseListener) TickListener.register(new ContainerOpenCloseListener(kbInvRestock));

		if(settings.tooltipMapHighlights) Tooltip.register(new TooltipMapNameColor());
		if(settings.tooltipMapMetadata) Tooltip.register(new TooltipMapLoreMetadata());

		ConfigManager.getInstance().registerConfigHandler(MOD_ID, configs);
		Registry.CONFIG_SCREEN.registerConfigScreenFactory(new ModInfo(MOD_ID, MOD_NAME, ()->new ConfigGui(configs)));
		new KeyCallbacks(settings, configs, remoteSender, epearlLookup, kbCraftRestock, whisperPlaySound, gameMessageFilter, kbInvOrgs, kbInvRestock);
		if(settings.enabled(Setting.STATIC_ENTITY_RENDERING)) StaticEntityRenderCache.init();
	}
}