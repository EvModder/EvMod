package net.evmodder.evmod.compat;

import java.util.stream.Stream;
import com.google.common.collect.Streams;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.gui.screens.Screen;
//? 1.21.4 {
/*import net.minecraft.client.gui.screens.GenericMessageScreen;*/
//?}
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.decoration.ItemFrame;
import org.apache.commons.lang3.math.Fraction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
//? >=26.1 {
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStackTemplate;
//?}
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * Small boundary for APIs whose shape changed between EvMod's supported
 * Minecraft versions. Keep version conditionals here when the difference is
 * behavioral or changes a method signature; simple class renames remain in the
 * Stonecutter replacement table.
 */
public final class MinecraftCompat{
	private MinecraftCompat(){}

	public static void sendOverlay(Player player, Component message){
		//? >=26.1 {
		player.sendOverlayMessage(message);
		//?} else {
		/*player.displayClientMessage(message, true);*/
		//?}
	}

	public static void sendSystem(Player player, Component message){
		//? >=26.1 {
		player.sendSystemMessage(message);
		//?} else {
		/*player.displayClientMessage(message, false);*/
		//?}
	}

	public static Screen screen(Minecraft client){
		//? >=26.1 {
		return client.gui.screen();
		//?} else {
		/*return client.screen;*/
		//?}
	}

	public static PlayerTabOverlay tabList(Minecraft client){
		//? >=26.1 {
		return client.gui.hud.getTabList();
		//?} else {
		/*return client.gui.getTabList();*/
		//?}
	}

	public static boolean isHudHidden(Minecraft client){
		//? >=26.1 {
		return client.gui.hud.isHidden();
		//?} else {
		/*return client.options.hideGui;*/
		//?}
	}

	public static Stream<ItemStack> nonEmptyItems(ItemContainerContents contents){
		//? >=26.1 {
		return contents.nonEmptyItemCopyStream();
		//?} else {
		/*return contents.nonEmptyStream();*/
		//?}
	}

	/** Ordered, borrowed item views; callers must not mutate legacy ItemStacks. */
	//? >=26.1 {
	public static Stream<? extends ItemInstance> bundleItems(BundleContents contents){
	//?} else {
	/*public static Stream<ItemStack> bundleItems(BundleContents contents){*/
	//?}
		return Streams.stream(contents.items());
	}
	//? >=26.1 {
	public static Stream<? extends ItemInstance> nonEmptyItemViews(ItemContainerContents contents){
	//?} else {
	/*public static Stream<ItemStack> nonEmptyItemViews(ItemContainerContents contents){*/
	//?}
		return Streams.stream(contents.nonEmptyItems());
	}
	//? >=26.1 {
	public static int itemCount(ItemInstance item){
		return item.count();
	//?} else {
	/*public static int itemCount(ItemStack item){
		return item.getCount();*/
	//?}
	}
	//? >=26.1 {
	public static ItemInstance bundleItemView(BundleContents contents, int index){
		return contents.items().get(index);
	//?} else {
	/*public static ItemStack bundleItemView(BundleContents contents, int index){
		return contents.getItemUnsafe(index);*/
	//?}
	}
	//? >=26.1 {
	public static ItemStack itemStack(ItemInstance item){
		return item instanceof ItemStack stack ? stack : ((ItemStackTemplate)item).create();
	//?} else {
	/*public static ItemStack itemStack(ItemStack item){
		return item;*/
	//?}
	}

	public static Fraction bundleWeight(BundleContents contents){
		//? >=26.1 {
		return contents.weight().getOrThrow();
		//?} else {
		/*return contents.weight();*/
		//?}
	}

	public static ItemStack bundleItem(BundleContents contents, int index){
		//? >=26.1 {
		return contents.items().get(index).create();
		//?} else {
		/*return contents.getItemUnsafe(index);*/
		//?}
	}

	public static Component newMapNotification(
			String position, int color, ItemFrame frame, int x, int y, int z){
		final MutableComponent message = Component.literal("New mapart: "+position+" \u2398 ").withColor(color);
		//? >=26.1 {
		return message
				.withStyle(style -> style.withClickEvent(new ClickEvent.CopyToClipboard(x+" "+y+" "+z)))
				.withStyle(style -> style.withHoverEvent(new HoverEvent.ShowItem(ItemStackTemplate.fromStack(frame.getItem()))));
		//?}
		//? 1.21.11 {
		/*return message
				.withStyle(style -> style.withClickEvent(new ClickEvent.CopyToClipboard(x+" "+y+" "+z)))
				.withStyle(style -> style.withHoverEvent(new HoverEvent.ShowItem(frame.getItem())));
		*///?}
		//? 1.21.4 {
		/*return message
				.withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, x+" "+y+" "+z)))
				.withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, frame.getItem().getHoverName())));*/
		//?}
	}

	public static void selectSlot(Inventory inventory, int slot){
		//? >=1.21.11 {
		inventory.setSelectedSlot(slot);
		//?} else {
		/*inventory.selected = slot;*/
		//?}
	}

	public static void disconnect(Minecraft client, Component reason){
		//? >=1.21.11 {
		client.disconnectFromWorld(reason);
		//?} else {
		/*client.disconnect(new GenericMessageScreen(reason));*/
		//?}
	}

	public static ClickEvent copyToClipboard(String text){
		//? >=1.21.11 {
		return new ClickEvent.CopyToClipboard(text);
		//?} else {
		/*return new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, text);*/
		//?}
	}

	public static ClickEvent openFile(String path){
		//? >=1.21.11 {
		return new ClickEvent.OpenFile(path);
		//?} else {
		/*return new ClickEvent(ClickEvent.Action.OPEN_FILE, path);*/
		//?}
	}
}