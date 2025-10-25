package net.evmodder.KeyBound.commands;

import java.nio.ByteBuffer;
import net.evmodder.EvLib.Command;
import net.evmodder.EvLib.PacketHelper;
import net.evmodder.EvLib.TextUtils;
import net.evmodder.KeyBound.Main;
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
		final long timeLeft = 8l*60l*60l*1000l - timeOnline;
		source.sendFeedback(Text.literal("Time left on 2b2t (assuming 8h limit): "+TextUtils.formatTime(timeLeft)));
	}
	public CommandTimeOnline(){
		ClientCommandRegistrationCallback.EVENT.register(
			(dispatcher, _0) -> dispatcher.register(
				ClientCommandManager.literal("timeonline") // TODO: proxy time online?
				.executes(ctx->{
//					if(joinedServerTimestamp == 0){
//						ctx.getSource().sendFeedback(Text.literal("Time online: -1 (???)"));
//						return 1;
//					}
					MinecraftClient client = MinecraftClient.getInstance();
					{
//						MinecraftClient client = MinecraftClient.getInstance();
						Main.LOGGER.info("Proxy toString: "+client.getNetworkProxy().toString());
						Main.LOGGER.info("Proxy addr: "+client.getNetworkProxy().address());
						Main.LOGGER.info("network profile name: "+client.getNetworkHandler().getProfile().getName());
						Main.LOGGER.info("client profile name: "+client.player.getGameProfile().getName());
						Main.LOGGER.info("network connection addr: "+client.getNetworkHandler().getConnection().getAddress());
					}
					if(Configs.Database.SHARE_JOIN_QUIT.getBooleanValue() && Main.remoteSender != null){
						Main.remoteSender.sendBotMessage(Command.DB_PLAYER_FETCH_JOIN_TS, /*udp=*/true, 1000, PacketHelper.toByteArray(client.player.getUuid()), (msg)->{
							if(msg.length != Long.BYTES){
								Main.LOGGER.error("CommandTimeOnline(Fetch): Invalid server response: "+new String(msg)+" ["+msg.length+"]");
								return;
							}
							final long ts = ByteBuffer.wrap(msg).getLong();
							tellTimeOnline(ctx.getSource(), ts);
						});
					}
					else tellTimeOnline(ctx.getSource(), ServerJoinListener.lastJoinTs);
					return 1;
				})
			)
		);
	}
}