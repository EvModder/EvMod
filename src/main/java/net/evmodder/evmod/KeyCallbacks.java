package net.evmodder.evmod;

import static net.evmodder.evmod.compat.MinecraftCompat.screen;

import net.evmodder.evmod.apis.ChatBroadcaster;
import net.evmodder.evmod.apis.EpearlExternalAdapter;
import net.evmodder.evmod.apis.EpearlLookup;
import net.evmodder.evmod.apis.RemoteServerSender;
import net.evmodder.evmod.apis.TickListener;
import net.evmodder.evmod.apis.WhisperPlaySound;
import net.evmodder.evmod.keybinds.*;
import net.evmodder.evmod.listeners.GameMessageFilter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.CartographyTableScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.world.entity.player.PlayerModelPart;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;
import fi.dy.masa.malilib.config.options.ConfigBase;
import fi.dy.masa.malilib.event.InputEventHandler;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.hotkeys.IKeybind;
import fi.dy.masa.malilib.hotkeys.IKeybindManager;
import fi.dy.masa.malilib.hotkeys.IKeybindProvider;
import fi.dy.masa.malilib.hotkeys.KeyAction;
import fi.dy.masa.malilib.hotkeys.KeybindMulti;

final class KeyCallbacks{
	private boolean mapLoadSharesOrganizerBinding;

	private static boolean overlapsMapLoad(IKeybind bind){
		final IKeybind mapLoad = Configs.Hotkeys.MAP_LOAD.getKeybind();
		return bind.overlaps(mapLoad) || mapLoad.overlaps(bind);
	}

	private final void valueChangeCallback(final ConfigBase<?> hotkey, final Runnable callback){
		hotkey.setValueChangeCallback(_ -> callback.run());
	}

	private final void keybindCallback(final IHotkey hotkey, final Function<Screen, Boolean> allowInScreen, final Runnable callback){
		if(allowInScreen == null) hotkey.getKeybind().setCallback((KeyAction _, IKeybind _) -> {callback.run(); return true;});
		else hotkey.getKeybind().setCallback((KeyAction _, IKeybind _) -> {
			if(allowInScreen.apply(screen(Minecraft.getInstance()))){callback.run(); return true;}
			return false;
		});
	}

	KeyCallbacks(Settings settings, Configs configs,
			RemoteServerSender remoteSender, EpearlLookup epearlLookup, KeybindCraftingRestock kbCraftRestock,
			WhisperPlaySound whisperPlaySound, GameMessageFilter gameMessageFilter, KeybindInventoryOrganize[] kbInvOrgs, KeybindInventoryRestock kbInvRestock
	){
		// TODO: derive these from expandable layout/hotkey pairs instead of fixed entries.
		final IHotkey[] organizerHotkeys = kbInvOrgs == null ? new IHotkey[0] : new IHotkey[]{
				Configs.Hotkeys.TRIGGER_INV_ORGANIZE_1, Configs.Hotkeys.TRIGGER_INV_ORGANIZE_2, Configs.Hotkeys.TRIGGER_INV_ORGANIZE_3};
		InputEventHandler.getKeybindManager().registerKeybindProvider(new IKeybindProvider(){
			@Override public void addKeysToMap(IKeybindManager manager){
//				configs.getHotkeysConfigs().stream().filter(IHotkey.class::isInstance)
//						.map(IHotkey.class::cast).map(IHotkey::getKeybind).forEach(manager::addKeybindToMap);
				mapLoadSharesOrganizerBinding = settings.enabled(Setting.MAP_ACTIONS)
						&& Arrays.stream(organizerHotkeys).anyMatch(hotkey -> overlapsMapLoad(hotkey.getKeybind()));
				configs.getHotkeysConfigs().forEach(opt ->{
					if(opt instanceof IHotkey hotkey && (!mapLoadSharesOrganizerBinding || opt != Configs.Hotkeys.MAP_LOAD)){
						manager.addKeybindToMap(hotkey.getKeybind());
					}
				});
				configs.getGenericConfigs().forEach(opt ->{if(opt instanceof IHotkey hotkey) manager.addKeybindToMap(hotkey.getKeybind());});
				// Only conflicting Map Load bindings are deferred; GUI order and separate-key dispatch stay unchanged.
				if(mapLoadSharesOrganizerBinding) manager.addKeybindToMap(Configs.Hotkeys.MAP_LOAD.getKeybind());
			}
			@Override public void addHotkeys(IKeybindManager manager){
				manager.addHotkeysForCategory(Main.MOD_NAME, Main.MOD_ID + ".hotkeys.category.hotkeys",
						configs.getHotkeysConfigs().stream().filter(IHotkey.class::isInstance).map(IHotkey.class::cast).toList());
				manager.addHotkeysForCategory(Main.MOD_NAME, Main.MOD_ID + ".hotkeys.category.misc_hotkeys",
						configs.getGenericConfigs().stream().filter(IHotkey.class::isInstance).map(IHotkey.class::cast).toList());
			}
		});

		final KeybindArmorSwap kbArmorSwap = settings.enabled(Setting.ARMOR_SWAP) ? new KeybindArmorSwap() : null;
		if(kbArmorSwap != null) TickListener.register(kbArmorSwap);
		final KeybindAIETravelHelper kbAIE = settings.enabled(Setting.TRAVEL) ? new KeybindAIETravelHelper() : null;
		final KeybindEjectJunk kbej = settings.any(Setting.TRAVEL, Setting.INVENTORY_TOOLS) ? new KeybindEjectJunk() : null;
		final KeybindEbounceTravelHelper kbEbounce = settings.enabled(Setting.TRAVEL) ? new KeybindEbounceTravelHelper(kbej) : null;
		final KeybindHotbarTypeScroller kbHbScroll = settings.enabled(Setting.INVENTORY_TOOLS) ? new KeybindHotbarTypeScroller() : null;

		// Value change callbacks
		if(remoteSender != null){
			valueChangeCallback(Configs.Database.ADDRESS, ()->InitUtils.refreshRemoteServerSender(remoteSender));
			valueChangeCallback(Configs.Database.CLIENT_ID, ()->InitUtils.refreshRemoteServerSender(remoteSender));
			valueChangeCallback(Configs.Database.CLIENT_KEY, ()->InitUtils.refreshRemoteServerSender(remoteSender));
		}
		if(gameMessageFilter != null) valueChangeCallback(Configs.Database.BORROW_IGNORES, gameMessageFilter::recomputeIgnoreLists);
		if(epearlLookup != null) Configs.Database.EPEARL_OWNERS_BY_UUID.setValueChangeCallback(newValue -> {if(newValue.getBooleanValue()) epearlLookup.loadEpearlCacheUUID();});
		if(epearlLookup != null) Configs.Database.EPEARL_OWNERS_BY_XZ.setValueChangeCallback(newValue -> {if(newValue.getBooleanValue()) epearlLookup.loadEpearlCacheXZ();});

		if(settings.broadcaster){
			valueChangeCallback(Configs.Generic.TEMP_BROADCAST_ACCOUNT, ChatBroadcaster::refreshBroadcast);
			valueChangeCallback(Configs.Generic.TEMP_BROADCAST_TIMESTAMP, ChatBroadcaster::refreshBroadcast);
			valueChangeCallback(Configs.Generic.TEMP_BROADCAST_MSGS, ChatBroadcaster::refreshBroadcast);
		}
		if(kbHbScroll != null) valueChangeCallback(Configs.Hotkeys.SCROLL_ORDER, kbHbScroll::refreshColorLists);

		if(kbInvRestock != null){
			valueChangeCallback(Configs.Generic.INV_RESTOCK_BLACKLIST, kbInvRestock::refreshLists);
			valueChangeCallback(Configs.Generic.INV_RESTOCK_WHITELIST, kbInvRestock::refreshLists);
			valueChangeCallback(Configs.Generic.INV_RESTOCK_AUTO_FOR_INV_ORGS, ()->kbInvRestock.refreshLayouts(kbInvOrgs));
		}
		if(kbInvOrgs != null){
			Configs.Generic.INV_ORGANIZE_1.setValueChangeCallback(newValue -> kbInvOrgs[0].refreshLayout(newValue.getStrings()));
			Configs.Generic.INV_ORGANIZE_2.setValueChangeCallback(newValue -> kbInvOrgs[1].refreshLayout(newValue.getStrings()));
			Configs.Generic.INV_ORGANIZE_3.setValueChangeCallback(newValue -> kbInvOrgs[2].refreshLayout(newValue.getStrings()));
		}
		if(whisperPlaySound != null){
			valueChangeCallback(Configs.Generic.WHISPER_PLAY_SOUND, whisperPlaySound::recomputeSound);
			valueChangeCallback(Configs.Generic.WHISPER_PLAY_SOUND_UNFOCUSED, whisperPlaySound::recomputeSoundUnfocused);
		}

		// Keybind callbacks
		if(kbArmorSwap != null){
			keybindCallback(Configs.Hotkeys.SWAP_SPECIAL_HELMET, Objects::isNull, ()->kbArmorSwap.request(KeybindArmorSwap.Swap.SPECIAL_HELMET));
			keybindCallback(Configs.Hotkeys.SWAP_LEATHER_BOOTS, Objects::isNull, ()->kbArmorSwap.request(KeybindArmorSwap.Swap.LEATHER_BOOTS));
			keybindCallback(Configs.Hotkeys.SWAP_GOLD_HELMET, Objects::isNull, ()->kbArmorSwap.request(KeybindArmorSwap.Swap.GOLD_HELMET));
			keybindCallback(Configs.Hotkeys.SWAP_GOLD_LEGGINGS, Objects::isNull, ()->kbArmorSwap.request(KeybindArmorSwap.Swap.GOLD_LEGGINGS));
			keybindCallback(Configs.Hotkeys.SWAP_GOLD_BOOTS, Objects::isNull, ()->kbArmorSwap.request(KeybindArmorSwap.Swap.GOLD_BOOTS));
		}
		if(settings.enabled(Setting.APPEARANCE)){
			keybindCallback(Configs.Hotkeys.TOGGLE_CAPE, null, ()->InitUtils.toggleSkinLayer(PlayerModelPart.CAPE));
			keybindCallback(Configs.Hotkeys.TOGGLE_HAT, null, ()->InitUtils.toggleSkinLayer(PlayerModelPart.HAT));
			keybindCallback(Configs.Hotkeys.TOGGLE_JACKET, null, ()->InitUtils.toggleSkinLayer(PlayerModelPart.JACKET));
			keybindCallback(Configs.Hotkeys.TOGGLE_SLEEVE_LEFT, null, ()->InitUtils.toggleSkinLayer(PlayerModelPart.LEFT_SLEEVE));
			keybindCallback(Configs.Hotkeys.TOGGLE_SLEEVE_RIGHT, null, ()->InitUtils.toggleSkinLayer(PlayerModelPart.RIGHT_SLEEVE));
			keybindCallback(Configs.Hotkeys.TOGGLE_PANTS_LEG_LEFT, null, ()->InitUtils.toggleSkinLayer(PlayerModelPart.LEFT_PANTS_LEG));
			keybindCallback(Configs.Hotkeys.TOGGLE_PANTS_LEG_RIGHT, null, ()->InitUtils.toggleSkinLayer(PlayerModelPart.RIGHT_PANTS_LEG));
		}

//			keybindCallback(Configs.Hotkeys.AIE_TRAVEL_HELPER, null, kbAIE::toggle);
//			keybindCallback(Configs.Hotkeys.EBOUNCE_TRAVEL_HELPER, null, kbEbounce::toggle);
		if(kbAIE != null) Configs.Hotkeys.AIE_TRAVEL_HELPER.setValueChangeCallback(newValue->kbAIE.updateEnabled(newValue.getBooleanValue()));
		if(kbEbounce != null) Configs.Hotkeys.EBOUNCE_TRAVEL_HELPER.setValueChangeCallback(newValue->kbEbounce.updateEnabled(newValue.getBooleanValue()));
		if(kbCraftRestock != null) keybindCallback(Configs.Hotkeys.CRAFT_RESTOCK, null/*HandledScreen.class::isInstance*/, kbCraftRestock::restockInputSlots);
		if(kbej != null) keybindCallback(Configs.Hotkeys.EJECT_JUNK_ITEMS, s->s==null || s instanceof AbstractContainerScreen, kbej::ejectJunkItems);
		if(kbHbScroll != null) keybindCallback(Configs.Hotkeys.HOTBAR_TYPE_INCR, null, ()->kbHbScroll.scrollHotbarSlot(true));
		if(kbHbScroll != null) keybindCallback(Configs.Hotkeys.HOTBAR_TYPE_DECR, null, ()->kbHbScroll.scrollHotbarSlot(false));

		for(int i=0; i<organizerHotkeys.length; ++i){
			final KeybindInventoryOrganize organizer = kbInvOrgs[i];
			organizerHotkeys[i].getKeybind().setCallback((_, bind)->{
				// Consume the key unless Map Load shares it and nothing was organized: then Map Load, registered after, takes it.
				return organizer.organizeInventory(false, null) || !settings.enabled(Setting.MAP_ACTIONS) || !overlapsMapLoad(bind);
			});
		}
		if(kbInvRestock != null){
			keybindCallback(Configs.Hotkeys.INV_RESTOCK, s->s instanceof AbstractContainerScreen && s instanceof InventoryScreen == false, kbInvRestock::doRestock);
		}

		if(settings.enabled(Setting.CHAT_ACTIONS)){
			keybindCallback(Configs.Hotkeys.CHAT_MSG_1, null, ()->InitUtils.sendChatMsg(Configs.Hotkeys.CHAT_MSG_1.getStringValue()));
			keybindCallback(Configs.Hotkeys.CHAT_MSG_2, null, ()->InitUtils.sendChatMsg(Configs.Hotkeys.CHAT_MSG_2.getStringValue()));
			keybindCallback(Configs.Hotkeys.CHAT_MSG_3, null, ()->InitUtils.sendChatMsg(Configs.Hotkeys.CHAT_MSG_3.getStringValue()));
		}
		if(settings.usesRemoteCommandHotkeys()){
			keybindCallback(Configs.Hotkeys.REMOTE_MSG_1, null, ()->InitUtils.sendRemoteMsg(remoteSender, Configs.Hotkeys.REMOTE_MSG_1.getStringValue()));
			keybindCallback(Configs.Hotkeys.REMOTE_MSG_2, null, ()->InitUtils.sendRemoteMsg(remoteSender, Configs.Hotkeys.REMOTE_MSG_2.getStringValue()));
			keybindCallback(Configs.Hotkeys.REMOTE_MSG_3, null, ()->InitUtils.sendRemoteMsg(remoteSender, Configs.Hotkeys.REMOTE_MSG_3.getStringValue()));
		}
		if(settings.enabled(Setting.PEARL_MESSAGES)){
			keybindCallback(Configs.Hotkeys.PP_HOTKEY_1, null,
					()->EpearlExternalAdapter.trigger(Configs.Hotkeys.PP_IP_1.getStringValue(), Configs.Hotkeys.PP_TOKEN_1.getStringValue()));
		}
		keybindCallback(Configs.Hotkeys.SNAP_ANGLE_1, null,
				()->Minecraft.getInstance().player.absSnapRotationTo(Configs.Hotkeys.SNAP_ANGLE_1.getYaw(), Configs.Hotkeys.SNAP_ANGLE_1.getPitch()));
		keybindCallback(Configs.Hotkeys.SNAP_ANGLE_2, null,
				()->Minecraft.getInstance().player.absSnapRotationTo(Configs.Hotkeys.SNAP_ANGLE_2.getYaw(), Configs.Hotkeys.SNAP_ANGLE_2.getPitch()));

		if(settings.usesClicks()){
			valueChangeCallback(Configs.Generic.CLICK_LIMIT_COUNT, InitUtils::refreshClickLimits);
			valueChangeCallback(Configs.Generic.CLICK_LIMIT_WINDOW, InitUtils::refreshClickLimits);
			valueChangeCallback(Configs.Visuals.CLICK_DISPLAY_AVAILABLE_PERSISTENT, InitUtils::refreshClickRenderer);
		}

		keybindCallback(Configs.Hotkeys.OPEN_CONFIG_GUI, Objects::isNull, ()->GuiBase.openGui(new ConfigGui(configs)));

		if(settings.enabled(Setting.MAP_ACTIONS)){
			final KeybindMapCopy kbMapCopy = new KeybindMapCopy();
			final KeybindMapLoad kbMapLoad = new KeybindMapLoad();
			final KeybindMapMove kbMapMove = new KeybindMapMove();
			final KeybindMapMoveBundle kbMapMoveBundle = new KeybindMapMoveBundle();

			keybindCallback(Configs.Hotkeys.MAP_COPY, s->s instanceof InventoryScreen
					|| s instanceof CraftingScreen || s instanceof CartographyTableScreen, kbMapCopy::copyMapArtInInventory);
			Configs.Hotkeys.MAP_LOAD.getKeybind().setCallback((_, _)->{
				final Screen gui = screen(Minecraft.getInstance());
				if(!(gui instanceof AbstractContainerScreen) || gui instanceof AnvilScreen) return false;
				// Apply MaLiLib's exclusive/fallback behavior only when an organizer binding overlaps.
				if(mapLoadSharesOrganizerBinding && KeybindMulti.getTriggeredCount() != 0) return false;
				kbMapLoad.loadMapArtFromContainer();
				return true;
			});
			keybindCallback(Configs.Hotkeys.MAP_MOVE,
					s->s instanceof AbstractContainerScreen && s instanceof InventoryScreen == false, kbMapMove::moveMapArtToFromShulker);
			Function<Screen, Boolean> allowInScreenBundleMove = //InventoryScreen.class::isInstance
					s->s instanceof InventoryScreen || s instanceof ContainerScreen || s instanceof ShulkerBoxScreen || s instanceof CraftingScreen;
			keybindCallback(Configs.Hotkeys.MAP_MOVE_BUNDLE, allowInScreenBundleMove, ()->kbMapMoveBundle.moveMapArtToFromBundle(false));
			keybindCallback(Configs.Hotkeys.MAP_MOVE_BUNDLE_REVERSE, allowInScreenBundleMove, ()->kbMapMoveBundle.moveMapArtToFromBundle(true));
		}

		//new KeybindSpamclick();
	}
}