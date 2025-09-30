package net.evmodder.KeyBound.commands;

import net.evmodder.EvLib.TextUtils;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.text.Text;

public class CommandTimeOnline{
	long joinedServerTimestamp;

	public CommandTimeOnline(){
		ClientPlayConnectionEvents.JOIN.register((_0, _1, _2)->joinedServerTimestamp=System.currentTimeMillis());

		ClientCommandRegistrationCallback.EVENT.register(
			(dispatcher, _0) -> dispatcher.register(
				ClientCommandManager.literal("timeonline")
				.executes(ctx->{
					if(joinedServerTimestamp == 0){
						ctx.getSource().sendFeedback(Text.literal("Time online: -1 (???)"));
						return 1;
					}
					final long timeOnline = System.currentTimeMillis() - joinedServerTimestamp;
					ctx.getSource().sendFeedback(Text.literal("Time online: "+TextUtils.formatTime(timeOnline)));
					final long timeLeft = 8l*60l*60l*1000l - timeOnline;
					ctx.getSource().sendFeedback(Text.literal("Time left on 2b2t (assuming 8h limit): "+TextUtils.formatTime(timeLeft)));
					return 1;
				})
			)
		);
	}
}