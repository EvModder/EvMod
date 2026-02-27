package net.evmodder.evmod.listeners;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;
import net.evmodder.EvLib.util.Command;
import net.evmodder.EvLib.util.FileIO;
import net.evmodder.EvLib.util.PacketHelper;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.Main;
import net.evmodder.evmod.apis.MiscUtils;
import net.evmodder.evmod.apis.MojangProfileLookup;
import net.evmodder.evmod.apis.RemoteServerSender;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.text.Text;

public final class GameMessageFilter{
	// TODO: limit reply size from server (to 1024 ids?), and allow bundling borrowLists into 1 request (limit to 8 ids?)
	private final HashMap<UUID, Integer> borrowedIgnoreList = new HashMap<>(0);
	private final RemoteServerSender remoteSender;
	private int currentServer;

	private final void incrIgnore(final UUID u){
		borrowedIgnoreList.merge(u, 1, Integer::sum);
	}
	private final void decrIgnore(final UUID u){
		borrowedIgnoreList.computeIfPresent(u, (_0, v) -> v == 1 ? null : v-1);
	}

	public final void fetchIgnoreList(final UUID uuid){
		MojangProfileLookup.prefetchName(uuid);

		long lastFetchTs = 0;
		try{
			lastFetchTs = Files.getLastModifiedTime(Paths.get("ignores/"+uuid+".cache")).toMillis();
			assert lastFetchTs >= 0 && lastFetchTs <= System.currentTimeMillis();
		}
		catch(NoSuchFileException e){lastFetchTs = 0;}
		catch(IOException e){e.printStackTrace(); lastFetchTs = 0;}

		@SuppressWarnings("unchecked")
		final HashSet<UUID> ignoreList = lastFetchTs <= 0 ? new HashSet<UUID>() : (HashSet<UUID>)FileIO.readObject("ignores/"+uuid+".cache");

		if(remoteSender == null){
			ignoreList.forEach(this::incrIgnore);
			return;
		}

		final byte[] args = PacketHelper.toByteArray(uuid, /*tsForDelta=*/new UUID(0, lastFetchTs));
		remoteSender.sendBotMessage(Command.DB_PLAYER_FETCH_IGNORES, /*udp=*/false, /*timeout=*/5000, args, reply -> {
			final String nameOrUUID = MojangProfileLookup.nameOrUUID(uuid);
			if(reply == null || (reply.length != 1 && reply.length % 16 != 0)){
				Main.LOGGER.error("MsgFilter: Fetch ignorelist for '"+nameOrUUID+"' got invalid response! "+(reply==null?"null":"len%16 != 0"));
				return;
			}
			if(reply.length == 1){
				assert reply[0] == 0;
				Main.LOGGER.info("MsgFilter: Fetch ignorelist for '"+nameOrUUID+"' returned an empty list");
				return;
			}
			Main.LOGGER.info("MsgFilter: Fetch ignorelist for '"+nameOrUUID+"' returned a list with "+reply.length/16+" accounts");
			final ByteBuffer bb = ByteBuffer.wrap(reply);
			if(ignoreList.isEmpty()) for(int i=0; i<reply.length/16; ++i) ignoreList.add(new UUID(bb.getLong(), bb.getLong()));
			else for(int i=0; i<reply.length/16; ++i){
				final UUID u = new UUID(bb.getLong(), bb.getLong());
				if(!ignoreList.remove(u)){
					ignoreList.add(u); // Basically, toggle if set contains ignored uuid
					incrIgnore(u);
				}
				else decrIgnore(u);
			}

			File dir = new File(FileIO.DIR+"ignores/");
			if(!dir.isDirectory()){Main.LOGGER.info("MsgFilter: Creating dir '"+dir.getName()+"'"); dir.mkdir();}
			FileIO.writeObject("ignores/"+uuid+".cache", ignoreList);
		});
	}

	public final void recomputeIgnoreLists(){
		Main.LOGGER.info("MsgFilter: Recomputing borrowed ignorelists");

		for(UUID uuid : Configs.Database.BORROW_IGNORES.getUUIDs()){
			if(uuid == null) continue;
			fetchIgnoreList(uuid);
		}
	}

	private final UUID determineSender(final Text text){
		final String str = text.getString();
		if(!str.matches("<\\w+> .*")) return null;
		final String name = str.substring(1, str.indexOf('>'));
		return MojangProfileLookup.uuidLookup.get(name, null);
	}

	public GameMessageFilter(final RemoteServerSender rms){
		remoteSender = rms;
//		ClientReceiveMessageEvents.ALLOW_CHAT.register((msg, signedMessage, sender, params, ts) -> {
//			final int onServer = MiscUtils.getCurrentServerAddressHashCode();
//			if(onServer != currentServer){currentServer = onServer; recomputeIgnoreLists();}
//			Main.LOGGER.info("currentServer: "+onServer);
//
//			return !borrowedIgnoreList.contains(sender.getId());
//		});
		ClientReceiveMessageEvents.ALLOW_GAME.register((msg, overlay) -> {
			if(overlay) return true;
			if(!Configs.Database.SAVE_IGNORES.getDefaultBooleanValue()) return true;

			final int onServer = MiscUtils.getServerAddressHashCode();
			if(onServer != currentServer){currentServer = onServer; recomputeIgnoreLists();}
			return !borrowedIgnoreList.containsKey(determineSender(msg));
		});
	}
}