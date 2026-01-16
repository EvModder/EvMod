package net.evmodder.evmod.listeners;

import java.util.HashSet;
import java.util.UUID;
import net.evmodder.EvLib.util.Command;
import net.evmodder.EvLib.util.FileIO;
import net.evmodder.EvLib.util.PacketHelper;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.Main;
import net.evmodder.evmod.apis.EpearlActivator;
import net.evmodder.evmod.apis.EpearlLookupFabric;
import net.evmodder.evmod.apis.RemoteServerSender;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;

public class GameMessageListener{
	private final String MSG_MATCH_END = "";//"( .*)?"

	private void saveMyIgnores(UUID myUUID, UUID ignoredUUID, boolean ignored){
		if(Configs.Database.SAVE_IGNORES.getBooleanValue()){
			Main.LOGGER.info("Writing "+(ignored?"":"un")+"ignore update to local file cache");
			final String filename = "ignores/"+myUUID+".cache";
			@SuppressWarnings("unchecked")
			HashSet<UUID> ignoreList = (HashSet<UUID>)FileIO.readObject(filename);
			if(ignoreList == null) ignoreList = new HashSet<>();
			if(ignored) ignoreList.add(ignoredUUID);
			else ignoreList.remove(ignoredUUID);
			FileIO.writeObject(filename, ignoreList);
		}
	}

	private void updateIgnoreState(final RemoteServerSender rms, final String name, final boolean ignored){
		Main.LOGGER.info("Ignore update: "+name+"="+ignored);
		MinecraftClient client = MinecraftClient.getInstance();
		PlayerListEntry ple = client.getNetworkHandler().getPlayerListEntry(name);
		if(ple == null){Main.LOGGER.error("Unable to find PlayerListEntry for player name: "+name); return;}
		final UUID ignoredUUID = ple.getProfile().getId();

		if(Configs.Database.SHARE_IGNORES.getBooleanValue() && rms != null){
			Main.LOGGER.info("Sending "+(ignored?"":"un")+"ignore packet to RMS");
			rms.sendBotMessage(ignored ? Command.DB_PLAYER_STORE_IGNORE : Command.DB_PLAYER_STORE_UNIGNORE, /*udp=*/true, 2000,
				PacketHelper.toByteArray(client.player.getUuid(), ignoredUUID),
				msg->{
					if(msg != null && msg.length == 1){
						if(msg[0] != 0){
							Main.LOGGER.info("[IgnoreSync] Updated ignore="+ignored+" in remote DB");
							client.player.sendMessage(Text.literal("Updated ignore="+ignored+" in remote DB"), /*overlay=*/true);
						}
						else{
							Main.LOGGER.info("[IgnoreSync] Remote DB reported ignoreState out of sync!");
							client.player.sendMessage(Text.literal("Remote DB reported ignoreState out of sync!"), /*overlay=*/true);
						}
					}
					else Main.LOGGER.info("[IgnoreSync] Unexpected/Invalid response from RMS for DB_PEARL_STORE_BY_UUID: "+msg);
					// Important that we update local cache AFTER db, for cache-priority reasons (Note: assumes decent clock synchronization, eesh)
					saveMyIgnores(client.player.getUuid(), ignoredUUID, ignored);
				});
		}
		else saveMyIgnores(client.player.getUuid(), ignoredUUID, ignored);
	}

	public GameMessageListener(RemoteServerSender rms, EpearlLookupFabric epl, WhisperPlaySound wps){
		final EpearlActivator pearlActivator = epl == null ? null : new EpearlActivator(epl);

		ClientReceiveMessageEvents.GAME.register((msg, overlay) -> {
			if(overlay) return;
			final String literal = msg.getString();
			if(literal == null) return;
//			Main.LOGGER.info("GameMsgListener: received msg: "+literal);

			if(literal.matches("^\\w+ whispers: .*")){// TODO: per-server format support
//				Main.LOGGER.info("GameMsgListener: whisper detected");
				if(wps != null) wps.playSound();

				final String PEARL_PULL_TRIGGER = Configs.Generic.WHISPER_PEARL_PULL.getStringValue();
				if(!PEARL_PULL_TRIGGER.isBlank() && pearlActivator != null){
					final String name = literal.substring(0, literal.indexOf(' '));
					if(literal.substring(name.length()+" whispers: ".length()).matches(PEARL_PULL_TRIGGER+MSG_MATCH_END)){
						pearlActivator.triggerPearl(name);
					}
				}
			}
			//"Permanently ignoring ___. This is saved in /ignorelist."
			if(Configs.Database.SAVE_IGNORES.getBooleanValue() || Configs.Database.SHARE_IGNORES.getBooleanValue()
					/*&& MiscUtils.getCurrentServerAddressHashCode() == Main.HASHCODE_2B2T*/){
				if(literal.startsWith("Permanently ignoring ") && literal.endsWith(". This is saved in /ignorelist."))
					updateIgnoreState(rms, literal.substring(21, literal.length()-31), true);
				if(literal.startsWith("No longer permanently ignoring "))
					updateIgnoreState(rms, literal.substring(31), false);
			}
		});
	}
}