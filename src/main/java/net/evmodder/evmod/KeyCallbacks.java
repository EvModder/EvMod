package net.evmodder.evmod;

import net.evmodder.evmod.apis.ChatBroadcaster;
import net.evmodder.evmod.apis.EpearlLookup;
import net.evmodder.evmod.apis.RemoteServerSender;
import net.evmodder.evmod.apis.WhisperPlaySound;
import net.evmodder.evmod.keybinds.*;
import net.evmodder.evmod.listeners.GameMessageFilter;
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
import fi.dy.masa.malilib.config.options.ConfigBase;
import fi.dy.masa.malilib.event.InputEventHandler;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.hotkeys.IKeybindManager;
import fi.dy.masa.malilib.hotkeys.IKeybindProvider;

final class KeyCallbacks{
	private final void valueChangeCallback(final ConfigBase<?> hotkey, final Runnable callback){
		hotkey.setValueChangeCallback(_0 -> callback.run());
	}

	private final void keybindCallback(final IHotkey hotkey, final Function<Screen, Boolean> allowInScreen, final Runnable callback){
		if(allowInScreen == null) hotkey.getKeybind().setCallback((_0, _1) -> {callback.run(); return true;});
		else hotkey.getKeybind().setCallback((_0, _1) -> {
			if(allowInScreen.apply(MinecraftClient.getInstance().currentScreen)){callback.run(); return true;}
			return false;
		});
	}

	KeyCallbacks(Configs configs,
			RemoteServerSender remoteSender, EpearlLookup epearlLookup, KeybindCraftingRestock kbCraftRestock,
			WhisperPlaySound whisperPlaySound, GameMessageFilter gameMessageFilter, KeybindInventoryOrganize[] kbInvOrgs, KeybindInventoryRestock kbInvRestock
	){
		InputEventHandler.getKeybindManager().registerKeybindProvider(new IKeybindProvider(){
			@Override public void addKeysToMap(IKeybindManager manager){
//				configs.getHotkeysConfigs().stream().filter(IHotkey.class::isInstance)
//						.map(IHotkey.class::cast).map(IHotkey::getKeybind).forEach(manager::addKeybindToMap);
				configs.getHotkeysConfigs().forEach(opt ->{if(opt instanceof IHotkey hotkey) manager.addKeybindToMap(hotkey.getKeybind());});
				configs.getGenericConfigs().forEach(opt ->{if(opt instanceof IHotkey hotkey) manager.addKeybindToMap(hotkey.getKeybind());});
			}
			@Override public void addHotkeys(IKeybindManager manager){
				manager.addHotkeysForCategory(Main.MOD_NAME, Main.MOD_ID + ".hotkeys.category.hotkeys",
						configs.getHotkeysConfigs().stream().filter(IHotkey.class::isInstance).map(IHotkey.class::cast).toList());
				manager.addHotkeysForCategory(Main.MOD_NAME, Main.MOD_ID + ".hotkeys.category.misc_hotkeys",
						configs.getGenericConfigs().stream().filter(IHotkey.class::isInstance).map(IHotkey.class::cast).toList());
			}
		});

		if(!Main.mapArtFeaturesOnly){
			final KeybindAIETravelHelper kbAIE = new KeybindAIETravelHelper();
			final KeybindEjectJunk kbej = new KeybindEjectJunk();
			final KeybindEbounceTravelHelper kbEbounce = new KeybindEbounceTravelHelper(kbej);
			final KeybindHotbarTypeScroller kbHbScroll = new KeybindHotbarTypeScroller();

			// Value change callbacks
			valueChangeCallback(Configs.Database.ADDRESS, ()->InitUtils.refreshRemoteServerSender(remoteSender));
			valueChangeCallback(Configs.Database.CLIENT_ID, ()->InitUtils.refreshRemoteServerSender(remoteSender));
			valueChangeCallback(Configs.Database.CLIENT_KEY, ()->InitUtils.refreshRemoteServerSender(remoteSender));
			if(gameMessageFilter != null) valueChangeCallback(Configs.Database.BORROW_IGNORES, gameMessageFilter::recomputeIgnoreLists);
			Configs.Database.EPEARL_OWNERS_BY_UUID.setValueChangeCallback(newValue -> {if(newValue.getBooleanValue()) epearlLookup.loadEpearlCacheUUID();});
			Configs.Database.EPEARL_OWNERS_BY_XZ.setValueChangeCallback(newValue -> {if(newValue.getBooleanValue()) epearlLookup.loadEpearlCacheXZ();});

			valueChangeCallback(Configs.Generic.TEMP_BROADCAST_ACCOUNT, ChatBroadcaster::refreshBroadcast);
			valueChangeCallback(Configs.Generic.TEMP_BROADCAST_TIMESTAMP, ChatBroadcaster::refreshBroadcast);
			valueChangeCallback(Configs.Generic.TEMP_BROADCAST_MSGS, ChatBroadcaster::refreshBroadcast);
			valueChangeCallback(Configs.Generic.SCROLL_ORDER, kbHbScroll::refreshColorLists);

			if(kbInvRestock != null){
				valueChangeCallback(Configs.Hotkeys.INV_RESTOCK_BLACKLIST, kbInvRestock::refreshLists);
				valueChangeCallback(Configs.Hotkeys.INV_RESTOCK_WHITELIST, kbInvRestock::refreshLists);
				valueChangeCallback(Configs.Generic.INV_RESTOCK_AUTO_FOR_INV_ORGS, ()->kbInvRestock.refreshLayouts(kbInvOrgs));
			}
			if(kbInvOrgs != null){
				Configs.Hotkeys.INV_ORGANIZE_1.setValueChangeCallback(newValue -> kbInvOrgs[0].refreshLayout(newValue.getStrings()));
				Configs.Hotkeys.INV_ORGANIZE_2.setValueChangeCallback(newValue -> kbInvOrgs[1].refreshLayout(newValue.getStrings()));
				Configs.Hotkeys.INV_ORGANIZE_3.setValueChangeCallback(newValue -> kbInvOrgs[2].refreshLayout(newValue.getStrings()));
			}
			if(whisperPlaySound != null){
				valueChangeCallback(Configs.Generic.WHISPER_PLAY_SOUND, whisperPlaySound::recomputeSound);
				valueChangeCallback(Configs.Generic.WHISPER_PLAY_SOUND_UNFOCUSED, whisperPlaySound::recomputeSoundUnfocused);
			}

			// Keybind callbacks
			keybindCallback(Configs.Hotkeys.TOGGLE_CAPE, null, ()->InitUtils.toggleSkinLayer(PlayerModelPart.CAPE));
			keybindCallback(Configs.Hotkeys.TOGGLE_HAT, null, ()->InitUtils.toggleSkinLayer(PlayerModelPart.HAT));
			keybindCallback(Configs.Hotkeys.TOGGLE_JACKET, null, ()->InitUtils.toggleSkinLayer(PlayerModelPart.JACKET));
			keybindCallback(Configs.Hotkeys.TOGGLE_SLEEVE_LEFT, null, ()->InitUtils.toggleSkinLayer(PlayerModelPart.LEFT_SLEEVE));
			keybindCallback(Configs.Hotkeys.TOGGLE_SLEEVE_RIGHT, null, ()->InitUtils.toggleSkinLayer(PlayerModelPart.RIGHT_SLEEVE));
			keybindCallback(Configs.Hotkeys.TOGGLE_PANTS_LEG_LEFT, null, ()->InitUtils.toggleSkinLayer(PlayerModelPart.LEFT_PANTS_LEG));
			keybindCallback(Configs.Hotkeys.TOGGLE_PANTS_LEG_RIGHT, null, ()->InitUtils.toggleSkinLayer(PlayerModelPart.RIGHT_PANTS_LEG));

//			keybindCallback(Configs.Hotkeys.AIE_TRAVEL_HELPER, null, kbAIE::toggle);
//			keybindCallback(Configs.Hotkeys.EBOUNCE_TRAVEL_HELPER, null, kbEbounce::toggle);
			Configs.Hotkeys.AIE_TRAVEL_HELPER.setValueChangeCallback(newValue->kbAIE.updateEnabled(newValue.getBooleanValue()));
			Configs.Hotkeys.EBOUNCE_TRAVEL_HELPER.setValueChangeCallback(newValue->kbEbounce.updateEnabled(newValue.getBooleanValue()));
			if(kbCraftRestock != null) keybindCallback(Configs.Hotkeys.CRAFT_RESTOCK, null/*HandledScreen.class::isInstance*/, kbCraftRestock::restockInputSlots);
			keybindCallback(Configs.Hotkeys.EJECT_JUNK_ITEMS, s->s==null || s instanceof HandledScreen, kbej::ejectJunkItems);
			keybindCallback(Configs.Hotkeys.HOTBAR_TYPE_INCR, null, ()->kbHbScroll.scrollHotbarSlot(true));
			keybindCallback(Configs.Hotkeys.HOTBAR_TYPE_DECR, null, ()->kbHbScroll.scrollHotbarSlot(false));

			if(kbInvOrgs != null){
				keybindCallback(Configs.Hotkeys.TRIGGER_INV_ORGANIZE_1, null, ()->kbInvOrgs[0].organizeInventory(false, null));
				keybindCallback(Configs.Hotkeys.TRIGGER_INV_ORGANIZE_2, null, ()->kbInvOrgs[1].organizeInventory(false, null));
				keybindCallback(Configs.Hotkeys.TRIGGER_INV_ORGANIZE_3, null, ()->kbInvOrgs[2].organizeInventory(false, null));
			}
			if(kbInvRestock != null){
				keybindCallback(Configs.Hotkeys.INV_RESTOCK, s->s instanceof HandledScreen && s instanceof InventoryScreen == false, kbInvRestock::doRestock);
			}

			keybindCallback(Configs.Hotkeys.CHAT_MSG_1, null, ()->InitUtils.sendChatMsg(Configs.Hotkeys.CHAT_MSG_1.getStringValue()));
			keybindCallback(Configs.Hotkeys.CHAT_MSG_2, null, ()->InitUtils.sendChatMsg(Configs.Hotkeys.CHAT_MSG_2.getStringValue()));
			keybindCallback(Configs.Hotkeys.CHAT_MSG_3, null, ()->InitUtils.sendChatMsg(Configs.Hotkeys.CHAT_MSG_3.getStringValue()));
			if(remoteSender != null){
				keybindCallback(Configs.Hotkeys.REMOTE_MSG_1, null, ()->InitUtils.sendRemoteMsg(remoteSender, Configs.Hotkeys.REMOTE_MSG_1.getStringValue()));
				keybindCallback(Configs.Hotkeys.REMOTE_MSG_2, null, ()->InitUtils.sendRemoteMsg(remoteSender, Configs.Hotkeys.REMOTE_MSG_2.getStringValue()));
				keybindCallback(Configs.Hotkeys.REMOTE_MSG_3, null, ()->InitUtils.sendRemoteMsg(remoteSender, Configs.Hotkeys.REMOTE_MSG_3.getStringValue()));
			}
			keybindCallback(Configs.Hotkeys.SNAP_ANGLE_1, null,
					()->MinecraftClient.getInstance().player.setAngles(Configs.Hotkeys.SNAP_ANGLE_1.getYaw(), Configs.Hotkeys.SNAP_ANGLE_1.getPitch()));
			keybindCallback(Configs.Hotkeys.SNAP_ANGLE_2, null,
					()->MinecraftClient.getInstance().player.setAngles(Configs.Hotkeys.SNAP_ANGLE_2.getYaw(), Configs.Hotkeys.SNAP_ANGLE_2.getPitch()));
		}

		final KeybindMapCopy kbMapCopy = new KeybindMapCopy();
		final KeybindMapLoad kbMapLoad = new KeybindMapLoad();
		final KeybindMapMove kbMapMove = new KeybindMapMove();
		final KeybindMapMoveBundle kbMapMoveBundle = new KeybindMapMoveBundle();

		valueChangeCallback(Configs.Generic.CLICK_LIMIT_COUNT, InitUtils::refreshClickLimits);
		valueChangeCallback(Configs.Generic.CLICK_LIMIT_WINDOW, InitUtils::refreshClickLimits);
		valueChangeCallback(Configs.Generic.CLICK_DISPLAY_AVAILABLE_PERSISTENT, InitUtils::refreshClickRenderer);

		keybindCallback(Configs.Hotkeys.OPEN_CONFIG_GUI, Objects::isNull, ()->GuiBase.openGui(new ConfigGui(configs)));

		keybindCallback(Configs.Hotkeys.MAP_COPY, s->s instanceof InventoryScreen
				|| s instanceof CraftingScreen || s instanceof CartographyTableScreen, kbMapCopy::copyMapArtInInventory);
		keybindCallback(Configs.Hotkeys.MAP_LOAD, HandledScreen.class::isInstance, kbMapLoad::loadMapArtFromContainer);
		keybindCallback(Configs.Hotkeys.MAP_MOVE,
				s->s instanceof HandledScreen && s instanceof InventoryScreen == false, kbMapMove::moveMapArtToFromShulker);
		Function<Screen, Boolean> allowInScreenBundleMove = //InventoryScreen.class::isInstance
				s->s instanceof InventoryScreen || s instanceof GenericContainerScreen || s instanceof ShulkerBoxScreen || s instanceof CraftingScreen;
		keybindCallback(Configs.Hotkeys.MAP_MOVE_BUNDLE, allowInScreenBundleMove, ()->kbMapMoveBundle.moveMapArtToFromBundle(false));
		keybindCallback(Configs.Hotkeys.MAP_MOVE_BUNDLE_REVERSE, allowInScreenBundleMove, ()->kbMapMoveBundle.moveMapArtToFromBundle(true));

		//new KeybindSpamclick();
	}
}