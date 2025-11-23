package net.evmodder.KeyBound.commands;

import java.nio.ByteBuffer;
import net.evmodder.EvLib.Command;
import net.evmodder.EvLib.TextUtils;
import net.evmodder.KeyBound.Main;
import net.evmodder.KeyBound.apis.MiscUtils;
import net.evmodder.KeyBound.config.Configs;
import net.evmodder.KeyBound.listeners.ServerJoinListener;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class CommandTimeOnline{

	private void tellTimeOnline(final FabricClientCommandSource source, final long lastJoinTs){
		final long timeOnline = System.currentTimeMillis() - lastJoinTs;
		source.sendFeedback(Text.literal("Time online: "+TextUtils.formatTime(timeOnline)));
		if(MiscUtils.getCurrentServerAddressHashCode() == MiscUtils.HASHCODE_2B2T){
			final long timeLeft = 8l*60l*60l*1000l - timeOnline;
			source.sendFeedback(Text.literal("Time left on 2b2t (assuming 8h limit): "+TextUtils.formatTime(timeLeft)));
		}
	}
	public CommandTimeOnline(){
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

					if(Configs.Database.SHARE_JOIN_QUIT.getBooleanValue() && Main.remoteSender != null){
						Main.remoteSender.sendBotMessage(Command.DB_PLAYER_FETCH_JOIN_TS, /*udp=*/true, 1000, MiscUtils.getCurrentServerAndPlayerData(), (msg)->{
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