package net.evmodder.evmod.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.evmodder.evmod.Main;
import net.evmodder.evmod.apis.EpearlLookupFabric;
import net.evmodder.evmod.apis.MiscUtils;
import net.evmodder.evmod.apis.MojangProfileLookup;
import net.evmodder.evmod.apis.MojangProfileLookupConstants;
import net.evmodder.evmod.apis.PearlTriggerLookup;
import net.evmodder.evmod.apis.PlayerUnload;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action;
//? >=26.3 {
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
//?} else {
/*import net.minecraft.network.protocol.game.ServerboundSwingPacket;*/
//?}
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class CommandPearlRescue{
	private record Target(String name, UUID uuid){}
	private record Watch(String name, UUID uuid, ClientPacketListener connection, ClientLevel level, BlockHitResult hit, String trigger){}
	// Accessed only on the client thread, including the packet hook after vanilla's thread handoff.
	private static Watch watch;
	private static long nextAntiAfkNanos;
	private final EpearlLookupFabric epearlLookup;

	private static void message(final String text, final ChatFormatting color){
		Main.LOGGER.info("[Pearl Rescue] {}", text);
		final Minecraft client = Minecraft.getInstance();
		if(client.player != null){
			//? >=26.1 {
			client.player.sendSystemMessage(Component.literal("[Pearl Rescue] "+text).withStyle(color));
			//?} else {
			/*client.player.displayClientMessage(Component.literal("[Pearl Rescue] "+text).withStyle(color), false);*/
			//?}
		}
	}

	private static String readiness(final Minecraft client){
		if(client.player == null || client.level == null || client.gameMode == null) return "Not connected to a world";
		if(!client.player.isAlive() || client.player.isSpectator()) return "Must be alive and able to interact";
		if(client.player.isShiftKeyDown()) return "Release sneak before arming";
		return null;
	}

	private static BlockHitResult aimedBlock(final Minecraft client){
		final HitResult hit = client.player.pick(client.player.blockInteractionRange(), 1f, false);
		return hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK ? block : null;
	}

	private static boolean isTrigger(final BlockState state){
		return state.is(Blocks.NOTE_BLOCK) || (state.is(BlockTags.TRAPDOORS) && !state.is(Blocks.IRON_TRAPDOOR));
	}

	private static String checkTarget(final Minecraft client, final Watch target){
		final String problem = readiness(client);
		if(problem != null) return problem;
		if(client.getConnection() != target.connection || client.level != target.level) return "World/connection changed";
		final BlockHitResult hit = aimedBlock(client);
		return hit == null || !hit.getBlockPos().equals(target.hit.getBlockPos())
				|| !isTrigger(client.level.getBlockState(hit.getBlockPos()))
				? "No longer aiming at the selected "+target.trigger+" within reach" : null;
	}

	private static void keepActive(final Minecraft client){
		if(watch == null) return;
		final String problem = checkTarget(client, watch);
		if(problem != null){
			watch = null;
			message("Disarmed: "+problem+". No click sent.", ChatFormatting.YELLOW);
			return;
		}
		if(watch.uuid == null) return;  // Name lookup is still pending.
		final long now = System.nanoTime();
		if(now - nextAntiAfkNanos < 0) return;
		nextAntiAfkNanos = now + 30_000_000_000L;
		// Animation packet only: never attack/use the aimed block, move, or rotate for anti-AFK.
		//? >=26.3 {
		watch.connection.send(ServerboundPunchPacket.INSTANCE);
		//?} else {
		/*watch.connection.send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));*/
		//?}
	}

	private int armResolved(final Watch request, final Target target, final boolean force){
		final Minecraft client = Minecraft.getInstance();
		watch = null;
		final String problem = checkTarget(client, request);
		if(problem != null){message("Not armed: "+problem, ChatFormatting.YELLOW); return 0;}
		if(client.getConnection().getPlayerInfo(target.uuid) != null){
			message("Not armed: "+target.name+" is already in the player list. No click sent.", ChatFormatting.YELLOW);
			return 0;
		}
		for(var pearl : PearlTriggerLookup.findAffectedPearls(client.level, request.hit.getBlockPos())){
			epearlLookup.getOwnerName(pearl);  // Reuse normal cached/database owner resolution.
			final UUID owner = MiscUtils.getPearlUUID(pearl);
			if(owner != null && !owner.equals(target.uuid)){
				message((force ? "Warning: " : "Not armed: ")+"triggered pearl belongs to "+MojangProfileLookup.nameOrUUID(owner)+", not "+target.name
						+(force ? ". Overridden with -f." : ". Use -f to override."), ChatFormatting.YELLOW);
				if(!force) return 0;
				break;
			}
		}
		watch = new Watch(target.name, target.uuid, request.connection, request.level, request.hit, request.trigger);
		nextAntiAfkNanos = System.nanoTime();
		message("ARMED for "+target.name+"; will right-click on arrival.", ChatFormatting.GREEN);
		return 1;
	}

	private int arm(final String name, final boolean force){
		watch = null;
		final Minecraft client = Minecraft.getInstance();
		final String problem = readiness(client);
		if(problem != null){message("Not armed: "+problem, ChatFormatting.RED); return 0;}
		final BlockHitResult hit = aimedBlock(client);
		if(hit == null || !isTrigger(client.level.getBlockState(hit.getBlockPos()))){
			message("Not armed: aim at a stasis noteblock or hand-openable trapdoor within normal interaction reach", ChatFormatting.RED);
			return 0;
		}
		final Watch request = new Watch(name, null, client.getConnection(), client.level, hit,
				client.level.getBlockState(hit.getBlockPos()).is(Blocks.NOTE_BLOCK) ? "noteblock" : "trapdoor");
		UUID parsedUUID = null;
		if(name.length() == 36){
			try{
				parsedUUID = UUID.fromString(name);
			}
			catch(IllegalArgumentException ignored){}
			if(parsedUUID == null || !parsedUUID.toString().equalsIgnoreCase(name)){
				message("Not armed: invalid player UUID", ChatFormatting.RED);
				return 0;
			}
		}
		else if(!name.matches("[A-Za-z0-9_]{1,16}")){message("Not armed: invalid player name", ChatFormatting.RED); return 0;}
		final UUID suppliedUUID = parsedUUID;
		watch = request;
		CompletableFuture.supplyAsync(() -> {
			final UUID uuid = suppliedUUID != null ? suppliedUUID : MojangProfileLookup.uuidLookup.getSync(name.toLowerCase(Locale.ROOT));
			if(uuid == MojangProfileLookupConstants.UUID_404) return null;
			final String resolvedName = MojangProfileLookup.nameLookup.getSync(uuid);
			return new Target(resolvedName == MojangProfileLookupConstants.NAME_404 || resolvedName == MojangProfileLookupConstants.NAME_U_404 ? name : resolvedName, uuid);
		}).whenComplete((target, error) -> client.execute(() -> {
			if(watch != request) return;
			if(error != null || target == null){
				watch = null;
				message("Not armed: could not resolve "+name, ChatFormatting.RED);
				return;
			}
			armResolved(request, target, force);
		}));
		return 1;
	}

	public CommandPearlRescue(final EpearlLookupFabric epearlLookup){
		this.epearlLookup = epearlLookup;
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, _) -> dispatcher.register(
				ClientCommands.literal("pearlrescue")
				.then(ClientCommands.argument("name", StringArgumentType.word())
						.executes(ctx -> arm(StringArgumentType.getString(ctx, "name"), false))
						.then(ClientCommands.literal("-f").executes(ctx -> arm(StringArgumentType.getString(ctx, "name"), true))))));
		PlayerUnload.register(_ -> watch = null);
		ClientTickEvents.END_CLIENT_TICK.register(CommandPearlRescue::keepActive);
	}

	// Accessed by: MixinClientPacketListener
	@SuppressWarnings("unused") private static void onPlayerInfo(final ClientPacketListener connection, final ClientboundPlayerInfoUpdatePacket packet){
		final Watch watch = CommandPearlRescue.watch;
		if(watch == null || watch.uuid == null || watch.connection != connection) return;
		final boolean added = packet.actions().contains(Action.ADD_PLAYER);
		final boolean listed = packet.actions().contains(Action.UPDATE_LISTED);
		if(!added && !listed) return;
		for(var entry : packet.entries()){
			if((added || entry.listed()) && watch.uuid.equals(entry.profileId())){
				CommandPearlRescue.watch = null;  // Consume before interaction: duplicate packets must never click twice.
				final Minecraft client = Minecraft.getInstance();
				final String problem = checkTarget(client, watch);
				if(problem == null){
					final BlockHitResult hit = aimedBlock(client);
					// Normal, sequenced interaction, without a timer or queued task.
					client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit);
					final var info = entry.profile() == null ? connection.getPlayerInfo(watch.uuid) : null;
					final String name = entry.profile() != null ? entry.profile().name() : info != null ? info.getProfile().name() : watch.name;
					message("Rescue attempted for "+name+"; now disarmed. Teleport is NOT confirmed.", ChatFormatting.YELLOW);
					return;
				}
				message("CANCELLED for "+watch.name+": "+problem+". No click sent.", ChatFormatting.RED);
				return;
			}
		}
	}
}