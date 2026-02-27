package net.evmodder.evmod.commands;

import java.nio.ByteBuffer;
import net.evmodder.EvLib.util.Command;
import net.evmodder.EvLib.util.TextUtils_New;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.apis.MiscUtils;
import net.evmodder.evmod.apis.RemoteServerSender;
import net.evmodder.evmod.listeners.ServerJoinListener;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class CommandTimeOnline{

	private void tellTimeOnline(final FabricClientCommandSource source, final long lastJoinTs){
		final long timeOnline = System.currentTimeMillis() - lastJoinTs;
		source.sendFeedback(Text.literal("Time online: "+TextUtils_New.formatTime(timeOnline)));
		if(MiscUtils.getServerAddressHashCode() == MiscUtils.HASHCODE_2B2T){
			final long timeLeft = 8l*60l*60l*1000l - timeOnline;
			source.sendFeedback(Text.literal("Time left on 2b2t (assuming 8h limit): "+TextUtils_New.formatTime(timeLeft)));
		}
	}
	public CommandTimeOnline(RemoteServerSender rms){
		ClientCommandRegistrationCallback.EVENT.register(
			(dispatcher, _0) -> dispatcher.register(
				ClientCommandManager.literal("timeonline")
				.executes(ctx->{
					MinecraftClient client = MinecraftClient.getInstance();
					if(client.getCurrentServerEntry() == null){
						if(client.getServer() != null) tellTimeOnline(ctx.getSource(), client.getServer().getTicks()*50);
						else ctx.getSource().sendFeedback(Text.literal("Error: No remote/local server found (this should be unreachable!)"));
						return 1;
					}

					if(Configs.Database.SHARE_JOIN_QUIT.getBooleanValue() && rms != null){
						rms.sendBotMessage(Command.DB_PLAYER_FETCH_JOIN_TS, /*udp=*/true, 1000, MiscUtils.getEncodedPlayerIds(client), (msg)->{
							if(msg != null && msg.length == Long.BYTES){
								tellTimeOnline(ctx.getSource(), ByteBuffer.wrap(msg).getLong());
								return;
							}
							if(msg == null || msg.length != 1 || msg[0] != 0){
								ctx.getSource().sendFeedback(Text.literal("CommandTimeOnline(Fetch): Invalid server response: "+new String(msg)+" ["+msg.length+"]"));
							}
							else{
								ctx.getSource().sendFeedback(Text.literal("CommandTimeOnline(Fetch): Server doesn't know our last join TS!"));
							}
							tellTimeOnline(ctx.getSource(), ServerJoinListener.lastJoinTs);
						});
					}
					else tellTimeOnline(ctx.getSource(), ServerJoinListener.lastJoinTs);
					return 1;
				})
			)
		);
	}
}