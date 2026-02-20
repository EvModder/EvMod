package net.evmodder.evmod.commands;

import java.util.stream.Collectors;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.Items;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.Box;

public class CommandDeletedMapsNearby{
	final int RENDER_DIST = 10*16;

	private final int displayHashCode(CommandContext<FabricClientCommandSource> ctx){
		final ClientPlayerEntity player = ctx.getSource().getPlayer();

		final Box everythingBox = Box.of(player.getPos(), RENDER_DIST, RENDER_DIST, RENDER_DIST);
		final String mapNames = player.getWorld().getEntitiesByType(TypeFilter.instanceOf(ItemFrameEntity.class), everythingBox,
				e -> e.getHeldItemStack().getItem() == Items.FILLED_MAP && FilledMapItem.getMapState(e.getHeldItemStack(), player.getWorld()) == null)
			.stream().map(e -> e.getHeldItemStack().getName().getString()).collect(Collectors.joining("\n"));

		final String displayText = mapNames.length() < 1000 ? mapNames : "[Click to copy]";
		ctx.getSource().sendFeedback(Text.literal(displayText)
				.styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, mapNames))));
		return 1;
	}

	public CommandDeletedMapsNearby(){
		ClientCommandRegistrationCallback.EVENT.register(
			(dispatcher, _0) -> dispatcher.register(ClientCommandManager.literal("DeletedMapsNearby").executes(this::displayHashCode))
		);
	}
}