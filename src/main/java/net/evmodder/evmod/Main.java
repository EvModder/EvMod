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
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.client.MinecraftClient;

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

	public static final String MOD_ID = InitUtils.getModId();
	public static final String MOD_NAME;
	public static final String MOD_VERSION;
	public static final Logger LOGGER;
	static{
		FileIO.DIR = FabricLoader.getInstance().getConfigDir().toString()+"/"+Main.MOD_ID+"/";
		ModMetadata metadata = FabricLoader.getInstance().getModContainer(MOD_ID).get().getMetadata();
		MOD_NAME = metadata.getName();
		MOD_VERSION = metadata.getVersion().getFriendlyString();
		LOGGER = LoggerFactory.getLogger(MOD_ID);
	}

	static boolean mapArtFeaturesOnly = true; // TODO: eww hacky

	private static Main instance; public static Main mixinAccess(){return instance;} // TODO: eww hacky! Accessors:
	public final RemoteServerSender remoteSender; // MixinClientWorld
	public final EpearlLookupFabric epearlLookup; // MixinEntityRenderer
	public final KeybindCraftingRestock kbCraftRestock; // MixinClientPlayerInteractionManager

	Main(){
		Main.LOGGER.info("Loading "+MOD_NAME+" "+MOD_VERSION);
		instance = this;
		final Settings settings = new Settings();
		final Configs configs = new Configs(settings);
		configs.load();

		InitUtils.refreshClickLimits();

		remoteSender = settings.database ? new RemoteServerSender(LOGGER, MiscUtils::getCurrentServerAddressHashCode) : null;
		if(settings.database) InitUtils.refreshRemoteServerSender(remoteSender);

		epearlLookup = settings.epearlOwners ? new EpearlLookupFabric(remoteSender) : null;

		if(settings.serverJoinListener) new ServerJoinListener(remoteSender);
		if(settings.serverQuitListener) new ServerQuitListener(remoteSender);
		final WhisperPlaySound whisperPlaySound = mapArtFeaturesOnly ? null : new WhisperPlaySound();
		if(settings.gameMessageListener) new GameMessageListener(remoteSender, epearlLookup, whisperPlaySound);
		final GameMessageFilter gameMessageFilter = settings.gameMessageFilter ? new GameMessageFilter(remoteSender) : null;

		final KeybindInventoryOrganize[] kbInvOrgs = mapArtFeaturesOnly ? null : new KeybindInventoryOrganize[]{
				new KeybindInventoryOrganize(Configs.Hotkeys.INV_ORGANIZE_1.getStrings()),
				new KeybindInventoryOrganize(Configs.Hotkeys.INV_ORGANIZE_2.getStrings()),
				new KeybindInventoryOrganize(Configs.Hotkeys.INV_ORGANIZE_3.getStrings())
		};
		final KeybindInventoryRestock kbInvRestock = kbInvOrgs == null ? null : new KeybindInventoryRestock(kbInvOrgs);
		if(settings.containerOpenCloseListener){
			TickListener.register(new ContainerOpenCloseListener(kbInvRestock));
			ContainerClickListener.register();
		}
		kbCraftRestock = new KeybindCraftingRestock();

		if(settings.placementHelperIframe) new AutoPlaceItemFrames();
		if(settings.placementHelperMapArt) new MapHandRestock(settings.placementHelperMapArtAutoPlace, settings.placementHelperMapArtAutoRemove);
		if(settings.broadcaster) ChatBroadcaster.refreshBroadcast();

		if(settings.cmdAssignPearl) new CommandAssignPearl(epearlLookup);
		if(settings.cmdExportMapImg) new CommandExportMapImg();
		if(settings.cmdMapArtGroup) new CommandMapArtGroup();
		if(settings.cmdMapHashCode) new CommandMapHashCode();
		if(settings.cmdSeen) new CommandSeen();
		if(settings.cmdSendAs) new CommandSendAs(remoteSender);
		if(settings.cmdTimeOnline) new CommandTimeOnline(remoteSender);

		if(settings.mapHighlights){
			TickListener.register(new TickListener(){
				@Override public void onTickStart(MinecraftClient client){
					UpdateInventoryHighlights.onTickStart(client.player);
					UpdateItemFrameHighlights.onTickStart(client);
					if(settings.mapHighlightsInGUIs) UpdateContainerHighlights.onTickStart(client);
				}
			});
		}
		if(settings.tooltipMapHighlights) Tooltip.register(new TooltipMapNameColor());
		if(settings.tooltipMapMetadata) Tooltip.register(new TooltipMapLoreMetadata());
		if(settings.tooltipRepairCost) Tooltip.register(new TooltipRepairCost());

		ConfigManager.getInstance().registerConfigHandler(MOD_ID, configs);
		Registry.CONFIG_SCREEN.registerConfigScreenFactory(new ModInfo(MOD_ID, MOD_NAME, ()->new ConfigGui(configs)));
		new KeyCallbacks(configs, remoteSender, epearlLookup, kbCraftRestock, gameMessageFilter, whisperPlaySound, kbInvOrgs, kbInvRestock);
	}
}