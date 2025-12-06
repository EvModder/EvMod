package net.evmodder.evmod;

import net.evmodder.evmod.apis.ChatBroadcaster;
import net.evmodder.evmod.apis.ClickUtils;
import net.evmodder.evmod.apis.MiscUtils;
import net.evmodder.evmod.apis.RemoteServerSender;
import net.evmodder.evmod.keybinds.*;
import net.evmodder.evmod.listeners.WhisperPlaySound;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CartographyTableScreen;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.entity.player.PlayerModelPart;
import java.util.Objects;
import java.util.function.Function;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.hotkeys.IHotkey;

public class KeyCallbacks{
	public static final void remakeClickUtils(IConfigBase _0){
		Main.clickUtils = new ClickUtils(Configs.Generic.CLICK_LIMIT_COUNT.getIntegerValue(), Configs.Generic.CLICK_LIMIT_DURATION.getIntegerValue());
	}
	public static final void remakeRemoteServerSender(IConfigBase _0){
		String fullAddress = Configs.Database.ADDRESS.getStringValue();
		final int sep = fullAddress.indexOf(':');
		final String addr;
		final int port;
		if(sep == -1){addr = fullAddress; port = RemoteServerSender.DEFAULT_PORT;}
		else{addr = fullAddress.substring(0, sep).trim(); port = Integer.parseInt(fullAddress.substring(sep+1).trim());}
		Main.remoteSender = new RemoteServerSender(Main.LOGGER, addr, port,
				Configs.Database.CLIENT_ID.getIntegerValue(), Configs.Database.CLIENT_KEY.getStringValue(),
				MiscUtils::getCurrentServerAddressHashCode);
	}

	public static final KeybindInventoryOrganize kbInvOrg1 = new KeybindInventoryOrganize(Configs.Hotkeys.INV_ORGANIZE_1.getStrings());
	public static final KeybindInventoryOrganize kbInvOrg2 = new KeybindInventoryOrganize(Configs.Hotkeys.INV_ORGANIZE_2.getStrings());
	public static final KeybindInventoryOrganize kbInvOrg3 = new KeybindInventoryOrganize(Configs.Hotkeys.INV_ORGANIZE_3.getStrings());
	public static final KeybindInventoryRestock kbInvRestock = new KeybindInventoryRestock();

	private static final void keybindCallback(IHotkey hotkey, Function<Screen, Boolean> allowInScreen, Runnable callback){
		hotkey.getKeybind().setCallback((_0, _1) ->{
			if(allowInScreen == null || allowInScreen.apply(MinecraftClient.getInstance().currentScreen)){callback.run(); return true;}
			return false;
		});
	}

	public static final void init(MinecraftClient mc){
		Main main = Main.getInstance();
		KeybindAIETravelHelper kbAIE = new KeybindAIETravelHelper();
		KeybindEjectJunk kbej = new KeybindEjectJunk();
		KeybindEbounceTravelHelper kbEbounce = new KeybindEbounceTravelHelper(kbej);
		KeybindHotbarTypeScroller kbHbScroll = new KeybindHotbarTypeScroller();

//		KeybindCraftingRestock kbCraftRestock = new KeybindCraftingRestock(); // Needs to be in Main :(
//		KeybindInventoryRestock kbInvRestock = new KeybindInventoryRestock();
		KeybindMapCopy kbMapCopy = new KeybindMapCopy();
		KeybindMapLoad kbMapLoad = new KeybindMapLoad();
		KeybindMapMove kbMapMove = new KeybindMapMove();
		KeybindMapMoveBundle kbMapMoveBundle = new KeybindMapMoveBundle();

		Configs.Generic.CLICK_LIMIT_COUNT.setValueChangeCallback(KeyCallbacks::remakeClickUtils);
		Configs.Generic.CLICK_LIMIT_DURATION.setValueChangeCallback(KeyCallbacks::remakeClickUtils);
		Configs.Database.ADDRESS.setValueChangeCallback(KeyCallbacks::remakeRemoteServerSender);
		Configs.Database.CLIENT_ID.setValueChangeCallback(KeyCallbacks::remakeRemoteServerSender);
		Configs.Database.CLIENT_KEY.setValueChangeCallback(KeyCallbacks::remakeRemoteServerSender);
		Configs.Database.BORROW_IGNORES.setValueChangeCallback(_0 -> main.gameMessageFilter.recomputeIgnoreLists());
		Configs.Database.EPEARL_OWNERS_BY_UUID.setValueChangeCallback(newValue -> {if(newValue.getBooleanValue()) Main.epearlLookup.loadEpearlCacheUUID();});
		Configs.Database.EPEARL_OWNERS_BY_XZ.setValueChangeCallback(newValue -> {if(newValue.getBooleanValue()) Main.epearlLookup.loadEpearlCacheXZ();});
		Configs.Generic.TEMP_BROADCAST_ACCOUNT.setValueChangeCallback(_0 -> ChatBroadcaster.refreshBroadcast());
		Configs.Generic.TEMP_BROADCAST_TIMESTAMP.setValueChangeCallback(_0 -> ChatBroadcaster.refreshBroadcast());
		Configs.Generic.TEMP_BROADCAST_MSGS.setValueChangeCallback(_0 -> ChatBroadcaster.refreshBroadcast());
		Configs.Generic.SCROLL_ORDER.setValueChangeCallback(newValue -> kbHbScroll.refreshColorLists(newValue.getStrings()));
		Configs.Hotkeys.INV_ORGANIZE_1.setValueChangeCallback(newValue -> kbInvOrg1.refreshLayout(newValue.getStrings()));
		Configs.Hotkeys.INV_ORGANIZE_2.setValueChangeCallback(newValue -> kbInvOrg2.refreshLayout(newValue.getStrings()));
		Configs.Hotkeys.INV_ORGANIZE_3.setValueChangeCallback(newValue -> kbInvOrg3.refreshLayout(newValue.getStrings()));
		Configs.Hotkeys.INV_RESTOCK_BLACKLIST.setValueChangeCallback(_0 -> kbInvRestock.refreshLists());
		Configs.Hotkeys.INV_RESTOCK_WHITELIST.setValueChangeCallback(_0 -> kbInvRestock.refreshLists());
		Configs.Generic.INV_RESTOCK_AUTO_FOR_INV_ORGS.setValueChangeCallback(newValue -> kbInvRestock.refreshLayouts());
		WhisperPlaySound.recomputeSound(Configs.Generic.WHISPER_PLAY_SOUND.getStringValue());
		Configs.Generic.WHISPER_PLAY_SOUND.setValueChangeCallback(newValue -> WhisperPlaySound.recomputeSound(newValue.getStringValue()));

		keybindCallback(Configs.Hotkeys.OPEN_CONFIG_GUI, Objects::isNull, ()->GuiBase.openGui(new ConfigGui()));

		keybindCallback(Configs.Hotkeys.MAP_COPY, s->s instanceof InventoryScreen
				|| s instanceof CraftingScreen || s instanceof CartographyTableScreen, kbMapCopy::copyMapArtInInventory);
		keybindCallback(Configs.Hotkeys.MAP_LOAD, HandledScreen.class::isInstance, kbMapLoad::loadMapArtFromContainer);
		keybindCallback(Configs.Hotkeys.MAP_MOVE,
				s->s instanceof HandledScreen && s instanceof InventoryScreen == false, kbMapMove::moveMapArtToFromShulker);
		Function<Screen, Boolean> allowInScreenBundleMove =
				//InventoryScreen.class::isInstance
				s->s instanceof InventoryScreen || s instanceof GenericContainerScreen || s instanceof ShulkerBoxScreen || s instanceof CraftingScreen;
		keybindCallback(Configs.Hotkeys.MAP_MOVE_BUNDLE, allowInScreenBundleMove, ()->kbMapMoveBundle.moveMapArtToFromBundle(false));
		keybindCallback(Configs.Hotkeys.MAP_MOVE_BUNDLE_REVERSE, allowInScreenBundleMove, ()->kbMapMoveBundle.moveMapArtToFromBundle(true));

		keybindCallback(Configs.Hotkeys.TOGGLE_CAPE, null, ()->MiscUtils.toggleSkinLayer(PlayerModelPart.CAPE));
		keybindCallback(Configs.Hotkeys.TOGGLE_HAT, null, ()->MiscUtils.toggleSkinLayer(PlayerModelPart.HAT));
		keybindCallback(Configs.Hotkeys.TOGGLE_JACKET, null, ()->MiscUtils.toggleSkinLayer(PlayerModelPart.JACKET));
		keybindCallback(Configs.Hotkeys.TOGGLE_SLEEVE_LEFT, null, ()->MiscUtils.toggleSkinLayer(PlayerModelPart.LEFT_SLEEVE));
		keybindCallback(Configs.Hotkeys.TOGGLE_SLEEVE_RIGHT, null, ()->MiscUtils.toggleSkinLayer(PlayerModelPart.RIGHT_SLEEVE));
		keybindCallback(Configs.Hotkeys.TOGGLE_PANTS_LEG_LEFT, null, ()->MiscUtils.toggleSkinLayer(PlayerModelPart.LEFT_PANTS_LEG));
		keybindCallback(Configs.Hotkeys.TOGGLE_PANTS_LEG_RIGHT, null, ()->MiscUtils.toggleSkinLayer(PlayerModelPart.RIGHT_PANTS_LEG));

//		keybindCallback(Configs.Hotkeys.AIE_TRAVEL_HELPER, null, kbAIE::toggle);
//		keybindCallback(Configs.Hotkeys.EBOUNCE_TRAVEL_HELPER, null, kbEbounce::toggle);
		Configs.Hotkeys.AIE_TRAVEL_HELPER.setValueChangeCallback(newValue->kbAIE.updateEnabled(newValue.getBooleanValue()));
		Configs.Hotkeys.EBOUNCE_TRAVEL_HELPER.setValueChangeCallback(newValue->kbEbounce.toggle(newValue.getBooleanValue()));
		keybindCallback(Configs.Hotkeys.CRAFT_RESTOCK, null/*HandledScreen.class::isInstance*/, Main.kbCraftRestock::restockInputSlots);
		keybindCallback(Configs.Hotkeys.EJECT_JUNK_ITEMS, s->s==null || s instanceof HandledScreen, kbej::ejectJunkItems);
		keybindCallback(Configs.Hotkeys.HOTBAR_TYPE_INCR, null, ()->kbHbScroll.scrollHotbarSlot(true));
		keybindCallback(Configs.Hotkeys.HOTBAR_TYPE_DECR, null, ()->kbHbScroll.scrollHotbarSlot(false));

		keybindCallback(Configs.Hotkeys.TRIGGER_INV_ORGANIZE_1, null, ()->kbInvOrg1.organizeInventory(false, null));
		keybindCallback(Configs.Hotkeys.TRIGGER_INV_ORGANIZE_2, null, ()->kbInvOrg2.organizeInventory(false, null));
		keybindCallback(Configs.Hotkeys.TRIGGER_INV_ORGANIZE_3, null, ()->kbInvOrg3.organizeInventory(false, null));
		keybindCallback(Configs.Hotkeys.INV_RESTOCK, s->s instanceof HandledScreen && s instanceof InventoryScreen == false, kbInvRestock::doRestock);

		keybindCallback(Configs.Hotkeys.CHAT_MSG_1, null, ()->MiscUtils.sendChatMsg(Configs.Hotkeys.CHAT_MSG_1.getStringValue()));
		keybindCallback(Configs.Hotkeys.CHAT_MSG_2, null, ()->MiscUtils.sendChatMsg(Configs.Hotkeys.CHAT_MSG_2.getStringValue()));
		keybindCallback(Configs.Hotkeys.CHAT_MSG_3, null, ()->MiscUtils.sendChatMsg(Configs.Hotkeys.CHAT_MSG_3.getStringValue()));
		if(Main.remoteSender != null){
			keybindCallback(Configs.Hotkeys.REMOTE_MSG_1, null, ()->MiscUtils.sendRemoteMsg(Configs.Hotkeys.REMOTE_MSG_1.getStringValue()));
			keybindCallback(Configs.Hotkeys.REMOTE_MSG_2, null, ()->MiscUtils.sendRemoteMsg(Configs.Hotkeys.REMOTE_MSG_2.getStringValue()));
			keybindCallback(Configs.Hotkeys.REMOTE_MSG_3, null, ()->MiscUtils.sendRemoteMsg(Configs.Hotkeys.REMOTE_MSG_3.getStringValue()));
		}
		keybindCallback(Configs.Hotkeys.SNAP_ANGLE_1, null,
				()->MinecraftClient.getInstance().player.setAngles(Configs.Hotkeys.SNAP_ANGLE_1.getYaw(), Configs.Hotkeys.SNAP_ANGLE_1.getPitch()));
		keybindCallback(Configs.Hotkeys.SNAP_ANGLE_2, null,
				()->MinecraftClient.getInstance().player.setAngles(Configs.Hotkeys.SNAP_ANGLE_2.getYaw(), Configs.Hotkeys.SNAP_ANGLE_2.getPitch()));
	}
}