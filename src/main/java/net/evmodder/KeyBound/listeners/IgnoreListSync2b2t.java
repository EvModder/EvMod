package net.evmodder.KeyBound.listeners;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.UUID;
import net.evmodder.EvLib.Command;
import net.evmodder.EvLib.PacketHelper;
import net.evmodder.KeyBound.Main;
import net.evmodder.KeyBound.MiscUtils;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;

public class IgnoreListSync2b2t{
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

	public IgnoreListSync2b2t(final boolean shareMyIgnoreList, final String[] borrowIgnoreLists){
		final int HASHCODE_2B2T = "2b2t.org".hashCode(); // -437714968;
		if(shareMyIgnoreList) ClientReceiveMessageEvents.GAME.register((msg, overlay) -> {
			if(overlay || MiscUtils.getCurrentServerAddressHashCode() != HASHCODE_2B2T) return;
			Main.LOGGER.info("GAME Message: "+msg.getString());
			final String literal = msg.getString();
			if(literal == null) return;
			//"Permanently ignoring ___. This is saved in /ignorelist."
			Main.LOGGER.info("received msg: "+literal);
			if(literal.startsWith("Permanently ignoring ") && literal.endsWith(". This is saved in /ignorelist."))
				sendIgnoreStatePacket(literal.substring(21, literal.length()-31), true);
			if(literal.startsWith("No longer permanently ignoring "))
				sendIgnoreStatePacket(literal.substring(31), false);
//			if(literal.startsWith("Now ignoring ")) handleIgnoreEvent(literal.substring(13), true);
//			if(literal.startsWith("No longer ignoring ")) handleIgnoreEvent(literal.substring(19, literal.length()-1), false);
		});

		if(borrowIgnoreLists != null) for(String name : borrowIgnoreLists){
			// TODO: caching? (dead code for this already exists in FileIO)
			// TODO: push-pull live updates when someone gets ignored?
			UUID uuid;
			try{uuid = UUID.fromString(name);}
			catch(IllegalArgumentException e){
				Main.LOGGER.error("Usernames for 'database.ignorelist.borrow' are not yet supported, please provide UUIDs instead");
				continue;
			}
			HashSet<UUID> borrowedIgnoreList = new HashSet<>();
			Main.remoteSender.sendBotMessage(Command.DB_PLAYER_FETCH_IGNORES, /*udp=*/false, 20_000, PacketHelper.toByteArray(uuid), reply -> {
				if(reply == null || reply.length % 16 != 0){
					Main.LOGGER.warn("Fetch ignore list for '"+name+"' got invalid response! "+(reply==null?"null":"len%16 != 0"));
					return;
				}
				final ByteBuffer bb = ByteBuffer.wrap(reply);
				for(int i=0; i<reply.length/16; ++i) borrowedIgnoreList.add(new UUID(bb.getLong(), bb.getLong()));
			});
			if(!borrowedIgnoreList.isEmpty()) ClientReceiveMessageEvents.ALLOW_CHAT.register((msg, signedMessage, sender, params, ts) -> {
				return !borrowedIgnoreList.contains(sender.getId());
			});
		}
	}
}