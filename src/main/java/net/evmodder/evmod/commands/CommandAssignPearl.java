package net.evmodder.evmod.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.List;
import java.util.UUID;
import net.evmodder.EvLib.util.Command;
import net.evmodder.EvLib.util.PacketHelper;
import net.evmodder.EvLib.util.PearlDataClient;
import net.evmodder.evmod.Main;
import net.evmodder.evmod.apis.EpearlLookup;
import net.evmodder.evmod.apis.MiscUtils;
import net.evmodder.evmod.apis.MojangProfileLookup;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;

public class CommandAssignPearl{
	private final EpearlLookup epearlLookup;

	public static final class Friend{private Friend(){}}
	private static final Friend friend = new Friend();

	private int assignPearl(CommandContext<FabricClientCommandSource> ctx, Command cmd){
		assert cmd == Command.DB_PEARL_STORE_BY_UUID || cmd == Command.DB_PEARL_STORE_BY_XZ;
		final Entity player = ctx.getSource().getPlayer();
		final Box box = player.getBoundingBox().expand(8, 6, 8);
		List<EnderPearlEntity> epearls =  player.getWorld().getEntitiesByType(EntityType.ENDER_PEARL, box, e->{
			return MiscUtils.isLookingAt(e, player) && !epearlLookup.isLoadedOwnerName(epearlLookup.getOwnerName(e));
		});
		if(epearls.isEmpty()){
			ctx.getSource().sendError(Text.literal("Unable to detect target unassigned epearl"));
			return 1;
		}
		if(epearls.size() > 1){
			ctx.getSource().sendError(Text.literal("Warning: Command does not currently work with multiple (stacked) epearls"));
		}
		final Entity epearl = epearls.getFirst();
		final UUID key = cmd == Command.DB_PEARL_STORE_BY_UUID ? epearl.getUuid()
				: new UUID(Double.doubleToRawLongBits(epearl.getX()), Double.doubleToRawLongBits(epearl.getZ()));

		final String name = ctx.getArgument("name", String.class);
//		ctx.getSource().sendFeedback(Text.literal("Fetching UUID for name: "+name+"..."));
		MojangProfileLookup.uuidLookup.get(name, (uuid)->{
			if(uuid == MojangProfileLookup.UUID_404){
				ctx.getSource().sendError(Text.literal("Invalid player name"));
				return;
			}
//			ctx.getSource().sendFeedback(Text.literal("Sending DB update for epearl: "+key+" -> "+uuid));
			final PearlDataClient pdc = new PearlDataClient(uuid, epearl.getBlockX(), epearl.getBlockY(), epearl.getBlockZ());
			if(Main.remoteSender == null) epearlLookup.assignPearlOwner(friend, key, pdc, cmd);
			else{
				Main.remoteSender.sendBotMessage(cmd, /*udp=*/true, /*timeout=*/3000, PacketHelper.toByteArray(key, uuid), (reply)->{
					if(reply != null && reply.length == 1){
						if(reply[0] == -1/*aka (byte)255*/) ctx.getSource().sendFeedback(Text.literal("Added pearl owner to remote DB!").withColor(16755200));
						else ctx.getSource().sendError(Text.literal("Remote DB already contains pearl owner").withColor(16755200));
	
						epearlLookup.assignPearlOwner(friend, key, pdc, cmd);
					}
					else ctx.getSource().sendError(Text.literal("Unexpected response from RMS for "+cmd+": "+reply).withColor(16733525));
				});
			}
		});
		return 1;
	}

	public CommandAssignPearl(EpearlLookup epl){
		epearlLookup = epl;
		ClientCommandRegistrationCallback.EVENT.register(
			(dispatcher, _0) -> dispatcher.register(
				ClientCommandManager.literal("assignpearl").then(
					ClientCommandManager.literal("by_uuid") // TODO: only register if DB_UUID is enabled?
					.then(ClientCommandManager.argument("name", StringArgumentType.word()) // TODO: tab-complete suggest online names
							.executes(ctx->assignPearl(ctx, Command.DB_PEARL_STORE_BY_UUID)))
				)
				.then(
					ClientCommandManager.literal("by_xz") // TODO: only register if DB_XZ is enabled?
					.then(ClientCommandManager.argument("name", StringArgumentType.word())
							.executes(ctx->assignPearl(ctx, Command.DB_PEARL_STORE_BY_XZ)))
				)
			)
		);
	}
}