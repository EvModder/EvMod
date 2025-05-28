package net.evmodder.KeyBound;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.UUID;
import net.evmodder.EvLib.Command;
import net.evmodder.EvLib.FileIO;
import net.evmodder.EvLib.PacketHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;

class IgnoreListSync2b2t{
	private final boolean syncMyIgnored;
	private final ArrayList<UUID> ignoreList;
	public static final int HASHCODE_2B2T = -437714968;//"2b2t.org".hashCode()

	private void handleIgnoreEvent(final String name, final boolean ignored){
		Main.LOGGER.info("Ignore update: "+name+"="+ignored);
		MinecraftClient client = MinecraftClient.getInstance();
		PlayerListEntry ple = client.getNetworkHandler().getPlayerListEntry(name);
		if(ple == null){Main.LOGGER.error("Unable to find PlayerListEntry for player name: "+name); return;}
		final UUID ignoredUUID = ple.getProfile().getId();
		if(ignored) ignoreList.add(ignoredUUID);
		else ignoreList.remove(ignoredUUID);
		if(syncMyIgnored){
			Main.remoteSender.sendBotMessage(
					ignored ? Command.DB_PLAYER_STORE_IGNORE : Command.DB_PLAYER_STORE_UNIGNORE,
					/*udp=*/true, 2000, PacketHelper.toByteArray(ignoredUUID), /*recv=*/null);
		}
	}

	IgnoreListSync2b2t(final boolean shareMyIgnoreList, final String[] borrowIgnoreLists){
		syncMyIgnored = shareMyIgnoreList;
		final MinecraftClient client = MinecraftClient.getInstance();
		final int address = (client == null || client.getCurrentServerEntry() == null) ? 0 : client.getCurrentServerEntry().address.hashCode();
		if(address != HASHCODE_2B2T){ignoreList = null; return;}
		ignoreList = new ArrayList<>();

		final byte[] data = FileIO.loadFileBytes("2b2t_ignorelist");
		if(data != null){
			final int numIdsInFile = data.length / 16;
			ignoreList.ensureCapacity(numIdsInFile);
			if(numIdsInFile*16 != data.length) Main.LOGGER.error("Corrupted/unrecognized map group file");
			ignoreList.ensureCapacity(numIdsInFile);
			final ByteBuffer bb = ByteBuffer.wrap(data);
			for(int i=0; i<numIdsInFile; ++i) ignoreList.add(new UUID(bb.getLong(), bb.getLong()));
		}
		if(borrowIgnoreLists != null) for(String name : borrowIgnoreLists){
			UUID uuid;
			try{uuid = UUID.fromString(name);}
			catch(IllegalArgumentException e){
				Main.LOGGER.error("Usernames for 'add_other_ignore_lists' are not yet supported, please provide UUIDs instead");
				continue;
			}
			Main.remoteSender.sendBotMessage(Command.DB_PLAYER_FETCH_IGNORES, /*udp=*/false, 20_000, PacketHelper.toByteArray(uuid), reply -> {
				if(reply == null || reply.length % 16 != 0){
					Main.LOGGER.warn("Fetch ignore list for '"+name+"' got invalid response! "+(reply==null?"null":"len%16 != 0"));
					return;
				}
				final ByteBuffer bb = ByteBuffer.wrap(reply);
				for(int i=0; i<reply.length/16; ++i){
					final UUID ignoredUUID = new UUID(bb.getLong(), bb.getLong());
					if(!ignoreList.contains(ignoredUUID)) client.getNetworkHandler().sendChatCommand("/ignore "+ignoredUUID);
				}
			});
		}
		ClientReceiveMessageEvents.GAME.register((msg, overlay) -> {
			if(overlay) return;
			//Main.LOGGER.info("GAME Message: "+msg.getString());
			final String literal = msg.getString();
			if(literal == null) return;
			if(literal.startsWith("Now ignoring ")) handleIgnoreEvent(literal.substring(13), true);
			if(literal.startsWith("No longer ignoring ")) handleIgnoreEvent(literal.substring(19, literal.length()-1), false);
		});
	}
}
