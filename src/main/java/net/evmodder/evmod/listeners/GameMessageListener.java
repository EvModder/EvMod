package net.evmodder.evmod.listeners;

import java.util.UUID;
import net.evmodder.EvLib.util.Command;
import net.evmodder.EvLib.util.PacketHelper;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.Main;
import net.evmodder.evmod.apis.EpearlActivator;
import net.evmodder.evmod.apis.EpearlLookup;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;

public class GameMessageListener{
	private final String MSG_MATCH_END = "";//"( .*)?"

	private void sendIgnoreStatePacket(final String name, final boolean ignored){
		Main.LOGGER.info("Ignore update: "+name+"="+ignored);
		MinecraftClient client = MinecraftClient.getInstance();
		PlayerListEntry ple = client.getNetworkHandler().getPlayerListEntry(name);
		if(ple == null){Main.LOGGER.error("Unable to find PlayerListEntry for player name: "+name); return;}
		final UUID ignoredUUID = ple.getProfile().getId();

		Main.LOGGER.info("Sending "+(ignored?"":"un")+"ignore packet to RMS");
		Main.remoteSender.sendBotMessage(
				ignored ? Command.DB_PLAYER_STORE_IGNORE : Command.DB_PLAYER_STORE_UNIGNORE,
				/*udp=*/true, 2000, PacketHelper.toByteArray(client.player.getUuid(), ignoredUUID), /*recv=*/null);
	}

	public GameMessageListener(EpearlLookup epl){
		final EpearlActivator pearlActivator = epl == null ? null : new EpearlActivator(epl);

		ClientReceiveMessageEvents.GAME.register((msg, overlay) -> {
			if(overlay) return;
			final String literal = msg.getString();
			if(literal == null) return;
//			Main.LOGGER.info("GameMsgListener: received msg: "+literal);

			if(literal.matches("^\\w+ whispers: .*")){// TODO: per-server format support
//				Main.LOGGER.info("GameMsgListener: whisper detected");
				WhisperPlaySound.playSound();

				final String PEARL_PULL_TRIGGER = Configs.Generic.WHISPER_PEARL_PULL.getStringValue();
				if(!PEARL_PULL_TRIGGER.isBlank() && pearlActivator != null){
					final String name = literal.substring(0, literal.indexOf(' '));
					if(literal.substring(name.length()+" whispers: ".length()).matches(PEARL_PULL_TRIGGER+MSG_MATCH_END)){
						pearlActivator.triggerPearl(name);
					}
				}
			}
			if(Configs.Database.SHARE_IGNORES.getBooleanValue() && Main.remoteSender != null
					/*&& MiscUtils.getCurrentServerAddressHashCode() == Main.HASHCODE_2B2T*/){
				//"Permanently ignoring ___. This is saved in /ignorelist."
				if(literal.startsWith("Permanently ignoring ") && literal.endsWith(". This is saved in /ignorelist."))
					sendIgnoreStatePacket(literal.substring(21, literal.length()-31), true);
				if(literal.startsWith("No longer permanently ignoring "))
					sendIgnoreStatePacket(literal.substring(31), false);
			}
		});
	}
}