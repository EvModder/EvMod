package net.evmodder.evmod.commands;

import static net.evmodder.evmod.apis.MojangProfileLookupConstants.NAME_404;
import static net.evmodder.evmod.apis.MojangProfileLookupConstants.NAME_U_404;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.List;
import net.evmodder.evmod.apis.EpearlLookupFabric;
import net.evmodder.evmod.apis.MiscUtils;
import net.evmodder.evmod.apis.MojangProfileLookup;
import net.evmodder.evmod.apis.MojangProfileLookupConstants;
import net.evmodder.evmod.mixin.AccessorProjectileEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;

public class CommandAssignPearl{
	private final EpearlLookupFabric epearlLookup;

//	public static final class Friend{private Friend(){}}
//	private static final Friend friend = new Friend();

//	private int assignPearl(CommandContext<FabricClientCommandSource> ctx, Command cmd){
//		assert cmd == Command.DB_PEARL_STORE_BY_UUID || cmd == Command.DB_PEARL_STORE_BY_XZ;
//		final Entity player = ctx.getSource().getPlayer();
//		final Box box = player.getBoundingBox().expand(8, 6, 8);
//		List<EnderPearlEntity> epearls =  player.getWorld().getEntitiesByType(EntityType.ENDER_PEARL, box, e->{
//			return MiscUtils.isLookingAt(e, player) && !epearlLookup.isLoadedOwnerName(epearlLookup.getOwnerName(e));
//		});
//		if(epearls.isEmpty()){
//			ctx.getSource().sendError(Text.literal("Unable to detect target unassigned epearl"));
//			return 1;
//		}
//		if(epearls.size() > 1){
//			ctx.getSource().sendError(Text.literal("Warning: Command does not currently work with multiple (stacked) epearls"));
//		}
//		final Entity epearl = epearls.getFirst();
//		final UUID key = cmd == Command.DB_PEARL_STORE_BY_UUID ? epearl.getUuid()
//				: new UUID(Double.doubleToRawLongBits(epearl.getX()), Double.doubleToRawLongBits(epearl.getZ()));
//
//		final String name = ctx.getArgument("name", String.class);
////		ctx.getSource().sendFeedback(Text.literal("Fetching UUID for name: "+name+"..."));
//		MojangProfileLookup.uuidLookup.get(name, (uuid)->{
//			if(uuid == MojangProfileLookup.UUID_404){
//				ctx.getSource().sendError(Text.literal("Invalid player name"));
//				return;
//			}
////			ctx.getSource().sendFeedback(Text.literal("Sending DB update for epearl: "+key+" -> "+uuid));
//			final PearlDataClient pdc = new PearlDataClient(uuid, epearl.getBlockX(), epearl.getBlockY(), epearl.getBlockZ());
//			if(remoteSender == null) epearlLookup.assignPearlOwner(friend, key, pdc, cmd);
//			else{
//				remoteSender.sendBotMessage(cmd, /*udp=*/true, /*timeout=*/3000, PacketHelper.toByteArray(key, uuid), (reply)->{
//					if(reply != null && reply.length == 1){
//						if(reply[0] == -1/*aka (byte)255*/) ctx.getSource().sendFeedback(Text.literal("Added pearl owner to remote DB!").withColor(16755200));
//						else ctx.getSource().sendError(Text.literal("Remote DB already contains pearl owner").withColor(16755200));
//	
//						epearlLookup.assignPearlOwner(friend, key, pdc, cmd);
//					}
//					else ctx.getSource().sendError(Text.literal("Unexpected response from RMS for "+cmd+": "+reply).withColor(16733525));
//				});
//			}
//		});
//		return 1;
//	}

	private final boolean isOverwritableName(String name){return name == null || name.equals(NAME_404) || name.equals(NAME_U_404);}

	private final int assignPearl(CommandContext<FabricClientCommandSource> ctx){
		if(epearlLookup.isDisabled()){
			ctx.getSource().sendError(Text.literal("EpearlLookup is disbled (either key-by UUID or XZ must be on)"));
			return 1;
		}
		final Entity player = ctx.getSource().getPlayer();
		final Box box = player.getBoundingBox().expand(8, 6, 8);
		final List<EnderPearlEntity> epearls = player.getWorld().getEntitiesByType(EntityType.ENDER_PEARL, box, e->{
			return MiscUtils.isLookingAt(e, player) && isOverwritableName(epearlLookup.getOwnerName(e));
		});
		if(epearls.isEmpty()){
			ctx.getSource().sendError(Text.literal("Unable to detect target unassigned epearl"));
			return 1;
		}
		if(epearls.size() > 1){
			ctx.getSource().sendError(Text.literal("Warning: Command does not currently work with multiple (stacked) epearls"));
		}
		final EnderPearlEntity epearl = epearls.getFirst();

		final String name = ctx.getArgument("name", String.class);
//		ctx.getSource().sendFeedback(Text.literal("Fetching UUID for name: "+name+"..."));
		MojangProfileLookup.uuidLookup.get(name, (uuid)->{
			if(uuid == MojangProfileLookupConstants.UUID_404){
				ctx.getSource().sendError(Text.literal("Invalid player name"));
				return;
			}
			if(epearl == null || epearl.isRemoved()){
				ctx.getSource().sendError(Text.literal("Epearl disappeared while fetching player UUID!"));
				return;
			}
			((AccessorProjectileEntity)epearl).setOwnerUUID(uuid);
			epearlLookup.getOwnerName(epearl); // Calling this updates EpearlOwners using the uuid we just provided
			ctx.getSource().sendFeedback(Text.literal("Assigned owner for epearl: "+epearl.getUuidAsString()+" <- "+name));
		});
		return 1;
	}

	public CommandAssignPearl(EpearlLookupFabric epl){
		epearlLookup = epl;
		ClientCommandRegistrationCallback.EVENT.register(
			(dispatcher, _0) -> dispatcher.register(
				ClientCommandManager.literal("assignpearl").then(
					ClientCommandManager.argument("name", /*EntityArgumentType.players()*/StringArgumentType.word())
					.executes(this::assignPearl)
				)
			)
		);
	}
}