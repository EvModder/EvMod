package net.evmodder.evmod.commands;

import com.mojang.brigadier.context.CommandContext;
import net.evmodder.evmod.apis.MapGroupUtils;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapState;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;

public class CommandMapHashCode{
	private final int displayHashCode(CommandContext<FabricClientCommandSource> ctx){
		final ClientPlayerEntity player = ctx.getSource().getPlayer();
		final ItemStack stack = player.getMainHandStack();
		if(stack.getItem() != Items.FILLED_MAP){
			ctx.getSource().sendError(Text.literal("Must be holding a FilledMap item"));
			return 1;
		}
		MapState state = FilledMapItem.getMapState(stack, player.getWorld());
		if(state == null || state.colors == null){
			ctx.getSource().sendError(Text.literal("MapState of held item needs to be loaded"));
			return 1;
		}

		final String colorsId = MapGroupUtils.getIdForMapState(state, /*evict=*/true).toString();
		ctx.getSource().sendError(Text.literal(colorsId+" \u2398")
				.styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, colorsId))));
		return 1;
	}

	public CommandMapHashCode(){
		ClientCommandRegistrationCallback.EVENT.register(
			(dispatcher, _0) -> dispatcher.register(ClientCommandManager.literal("maphashcode").executes(this::displayHashCode))
		);
	}
}