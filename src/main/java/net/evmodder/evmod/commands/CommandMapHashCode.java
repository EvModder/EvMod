package net.evmodder.evmod.commands;

import static net.evmodder.evmod.compat.MinecraftCompat.copyToClipboard;

import com.mojang.brigadier.context.CommandContext;
import net.evmodder.evmod.apis.MapGroupUtils;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public class CommandMapHashCode{
	private final int displayHashCode(CommandContext<FabricClientCommandSource> ctx){
		final LocalPlayer player = ctx.getSource().getPlayer();
		final ItemStack stack = player.getMainHandItem();
		if(stack.getItem() != Items.FILLED_MAP){
			ctx.getSource().sendError(Component.literal("Must be holding a FilledMap item"));
			return 1;
		}
		MapItemSavedData state = MapItem.getSavedData(stack, player.level());
		if(state == null || state.colors == null){
			ctx.getSource().sendError(Component.literal("MapState of held item needs to be loaded"));
			return 1;
		}

		final String colorsId = MapGroupUtils.getIdForMapState(state, /*evict=*/true).toString();
		ctx.getSource().sendFeedback(Component.literal(colorsId+" \u2398")
				.withStyle(style -> style.withClickEvent(copyToClipboard(colorsId))));
		return 1;
	}

	public CommandMapHashCode(){
		ClientCommandRegistrationCallback.EVENT.register(
			(dispatcher, _) -> dispatcher.register(ClientCommands.literal("maphashcode").executes(this::displayHashCode))
		);
	}
}