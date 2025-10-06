package net.evmodder.KeyBound.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.evmodder.EvLib.Command;
import net.evmodder.EvLib.PacketHelper;
import net.evmodder.EvLib.PearlDataClient;
import java.util.List;
import java.util.UUID;
import net.evmodder.KeyBound.Main;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.evmodder.KeyBound.MojangProfileLookup;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class CommandAssignPearl{
	private boolean isLookingAt(Entity entity, Entity player){
		Vec3d vec3d = player.getRotationVec(1.0F).normalize();
		Vec3d vec3d2 = new Vec3d(entity.getX() - player.getX(), entity.getEyeY() - player.getEyeY(), entity.getZ() - player.getZ());
		double d = vec3d2.length();
		vec3d2 = new Vec3d(vec3d2.x / d, vec3d2.y / d, vec3d2.z / d);//normalize
		double e = vec3d.dotProduct(vec3d2);
		return e > 1.0D - 0.03D / d ? /*client.player.canSee(entity)*/true : false;
	}

	private int assignPearl(CommandContext<FabricClientCommandSource> ctx, Command cmd){
		assert cmd == Command.DB_PEARL_STORE_BY_UUID || cmd == Command.DB_PEARL_STORE_BY_XZ;
		final Entity player = ctx.getSource().getPlayer();
		final Box box = player.getBoundingBox().expand(8, 6, 8);
		List<EnderPearlEntity> epearls =  player.getWorld().getEntitiesByType(EntityType.ENDER_PEARL, box, e->{
			return isLookingAt(e, player) && !Main.epearlLookup.isLoadedOwnerName(Main.epearlLookup.getOwnerName(e));
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
			Main.remoteSender.sendBotMessage(cmd, /*udp=*/false, /*timeout=*/3000, PacketHelper.toByteArray(key, uuid), (reply)->{
				if(reply != null && reply.length == 1){
					if(reply[0] == -1/*aka (byte)255*/) ctx.getSource().sendFeedback(Text.literal("Added pearl owner to remote DB!"));
					else ctx.getSource().sendError(Text.literal("Remote DB already contains pearl owner"));

					final PearlDataClient pdc = new PearlDataClient(uuid, epearl.getBlockX(), epearl.getBlockY(), epearl.getBlockZ());
					Main.epearlLookup.assignPearlOwner_FriendCommandAssignPearl(key, pdc, cmd);
				}
				else ctx.getSource().sendError(Text.literal("Unexpected/Invalid response from RMS for "+cmd+": "+reply));
			});
		});
		return 1;
	}

	public CommandAssignPearl(){
		ClientCommandRegistrationCallback.EVENT.register(
			(dispatcher, _0) -> dispatcher.register(
				ClientCommandManager.literal("assignpearl").then(
					ClientCommandManager.literal("by_uuid") // TODO: only register if DB_UUID is enabled?
					.then(ClientCommandManager.argument("name", StringArgumentType.word())
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