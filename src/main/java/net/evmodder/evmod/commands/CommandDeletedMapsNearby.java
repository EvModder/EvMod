package net.evmodder.evmod.commands;

import java.util.stream.Collectors;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

public class CommandDeletedMapsNearby{
	final int RENDER_DIST = 10*16;

	private final int displayHashCode(CommandContext<FabricClientCommandSource> ctx){
		final LocalPlayer player = ctx.getSource().getPlayer();

		final AABB everythingBox = AABB.ofSize(player.position(), RENDER_DIST, RENDER_DIST, RENDER_DIST);
		final String mapNames = player.level().getEntities(EntityTypeTest.forClass(ItemFrame.class), everythingBox,
				e -> e.getItem().getItem() == Items.FILLED_MAP && MapItem.getSavedData(e.getItem(), player.level()) == null)
			.stream().map(e -> e.getItem().getHoverName().getString()).collect(Collectors.joining("\n"));

		final String displayText = mapNames.length() < 1000 ? mapNames : "[Click to copy]";
		ctx.getSource().sendFeedback(Component.literal(displayText)
				.withStyle(style -> style.withClickEvent(new ClickEvent.CopyToClipboard(mapNames))));
		return 1;
	}

	public CommandDeletedMapsNearby(){
		ClientCommandRegistrationCallback.EVENT.register(
			(dispatcher, _0) -> dispatcher.register(ClientCommands.literal("DeletedMapsNearby").executes(this::displayHashCode))
		);
	}
}