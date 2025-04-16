package net.evmodder.KeyBound;

import com.mojang.brigadier.arguments.StringArgumentType;
//import net.minecraft.client.multiplayer.PlayerInfo;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.text.Text;

public class SendAsCommand{
	SendAsCommand(){
		ClientCommandRegistrationCallback.EVENT.register(
			(dispatcher, _) -> dispatcher.register(
				ClientCommandManager.literal("sendas")
					.then(ClientCommandManager.argument("name", StringArgumentType.word())
					.then(ClientCommandManager.argument("message", StringArgumentType.greedyString()))
					.executes(ctx->{
						//ctx.getArgument("name", EntitySelector.class).getPlayer(ctx.getSource());
						String name = ctx.getArgument("name", String.class);
						boolean targetIsOnline = false;
						for(String onlinePlayerName : ctx.getSource().getPlayerNames()) if(onlinePlayerName.equals(name)) targetIsOnline = true;
						if(!targetIsOnline){
							ctx.getSource().sendError(Text.literal(name+" does not appear to be online"));
							return 1;
						}
						String message = ctx.getArgument("message", String.class).trim();
						if(message.length() > 200){
							ctx.getSource().sendFeedback(Text.literal("Request message >200 chars, trimming it"));
							message = message.substring(0, 200);
						}
						while((name.length() + message.length()) % 16 != 0) name += ' ';
						message = name + message;//if name.length==16, there will be no space between name and msg, otherwise there will be.
						Main.remoteSender.sendBotMessage(net.evmodder.EvLib.Command.P2P_CHAT_AS, message.getBytes(), /*udp=*/true, null);
						return com.mojang.brigadier.Command.SINGLE_SUCCESS;
					})
				)
			)
		);
	}
}
