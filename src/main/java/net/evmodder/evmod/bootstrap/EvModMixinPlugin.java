package net.evmodder.evmod.bootstrap;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.lwjgl.system.Platform;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import net.evmodder.evmod.Setting;
import net.evmodder.evmod.Settings;

/** Select hooks before their target classes load; do not reference Main, Minecraft, or GUI configs here. */
public final class EvModMixinPlugin implements IMixinConfigPlugin{
	@Override public void onLoad(String mixinPackage){}
	@Override public String getRefMapperConfig(){return null;}
	@Override public List<String> getMixins(){return null;}
	@Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets){}
	@Override public void preApply(String targetName, ClassNode target, String mixinName, IMixinInfo info){}
	@Override public void postApply(String targetName, ClassNode target, String mixinName, IMixinInfo info){}

	@Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName){
		final Settings settings = Settings.get();
		final String mixin = mixinClassName.substring(mixinClassName.lastIndexOf('.')+1);
		return switch(mixin){
			case "AccessorClickUtils" -> settings.usesClicks();
			case "AccessorCommandPearlRescue", "AccessorEpearlLookup" -> settings.enabled(Setting.DATABASE_EPEARL_OWNERS);
			case "AccessorKeybindCraftingRestock" -> settings.enabled(Setting.CRAFTING);
			case "AccessorUpdateContainerContents" -> settings.enabled(Setting.MAP_SCAN_CONTAINERS);
			case "MixinWindow" -> settings.enabled(Setting.MAC_INSTANT_FULLSCREEN) && Platform.get() == Platform.MACOSX;
			case "AccessorMain" -> settings.any(Setting.DATABASE_EPEARL_OWNERS, Setting.CRAFTING, Setting.PLAYER_POSITION_SYNC, Setting.DATABASE_MAP_HISTORY);
			case "AccessorMapGroupUtils", "MixinClientPacketListenerMaps" -> settings.usesMapPackets();
			case "MixinUpdateInventoryContentsMapNames", "MixinUpdateContainerContentsMapNames", "MixinUpdateItemFrameContentsMapNames" -> settings.enabled(Setting.CMD_EXPORT_MAPNAMES);
			case "MixinClientPacketListenerMapCache" -> settings.enabled(Setting.MAP_CACHE);
			case "MixinClientPacketListenerSeenMaps" -> settings.enabled(Setting.DATABASE_MAP_HISTORY);
			case "AccessorUpdateItemFrameContents", "MixinItemFrameRenderer" -> settings.enabled(Setting.MAP_SCAN_IFRAMES);
			case "MixinUpdateItemFrameContentsTracking" -> settings.any(Setting.MAP_SCAN_IFRAMES, Setting.NOTIFY_NEW_MAPART_IFRAME);
			case "MixinUpdateItemFrameContentsNotifications" -> settings.enabled(Setting.NOTIFY_NEW_MAPART_IFRAME);
			case "AccessorAnvilScreen", "MixinAnvilScreen", "MixinClientPacketListenerCrafting", "MixinMultiPlayerGameModeCrafting" -> settings.enabled(Setting.CRAFTING);
			case "AccessorPlayerTabOverlay", "MixinMultiPlayerGameMode" -> settings.usesClicks();
			case "AccessorProjectile", "MixinClientPacketListener", "MixinEntityRendererPearl" -> settings.enabled(Setting.DATABASE_EPEARL_OWNERS);
			case "MixinClientPacketListenerPlayerPosition" -> settings.enabled(Setting.PLAYER_POSITION_SYNC);
			case "MixinClientLevel", "MixinEntityRendererStatic", "MixinLevelExtractor", "MixinLevelRenderer" -> settings.enabled(Setting.STATIC_ENTITY_RENDERING);
			case "MixinAbstractContainerScreenTitle" -> settings.enabled(Setting.MAP_SCAN_CONTAINERS);
			case "MixinAbstractContainerScreenGhostItems" -> settings.enabled(Setting.GHOST_ITEM_FIX);
			case "MixinHudMap" -> settings.enabled(Setting.MAP_HUD);
			case "MixinHudRepairCost" -> settings.enabled(Setting.REPAIR_COST_TOOLTIP);
			case "MixinAbstractContainerMenu" -> settings.enabled(Setting.MAP_ACTIONS);
			case "AccessorWidgetListConfigOptions", "MixinWidgetConfigOption" -> settings.usesCustomWidgets();
			default -> throw new IllegalArgumentException("Év’s Mod mixin has no feature owner: "+mixin);
		};
	}
}