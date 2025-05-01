package net.evmodder.KeyBound.Commands;

import net.evmodder.EvLib.TextUtils;
import net.evmodder.KeyBound.Main;
//import net.minecraft.client.multiplayer.PlayerInfo;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.text.Text;

public class CommandTimeOnline{
	public CommandTimeOnline(){
		ClientCommandRegistrationCallback.EVENT.register(
			(dispatcher, _) -> dispatcher.register(
				ClientCommandManager.literal("timeonline")
//					.then(ClientCommandManager.argument("yeet", StringArgumentType.word()))
				.executes(ctx->{
					final long timeOnline = System.currentTimeMillis() - Main.joinedServerTimestamp;
					ctx.getSource().sendFeedback(Text.literal("Time online: "+TextUtils.formatTime(timeOnline)));
					final long timeLeft = 8l*60l*60l*1000l;
					ctx.getSource().sendFeedback(Text.literal("Time left on 2b2t (assuming 8h limit): "+TextUtils.formatTime(timeLeft)));
					return 1;
				})
			)
		);
	}
}
