package net.evmodder.KeyBound.listeners;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;
import net.evmodder.EvLib.Command;
import net.evmodder.EvLib.PacketHelper;
import net.evmodder.KeyBound.Main;
import net.evmodder.KeyBound.apis.MiscUtils;
import net.evmodder.KeyBound.apis.MojangProfileLookup;
import net.evmodder.KeyBound.config.Configs;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.text.Text;

public class GameMessageFilter{
	// TODO: cache to file. Also, limit reply size from server (to 1024 ids?), and allow bundling borrowLists into 1 request (limit to 8 ids?)
	private HashSet<UUID> borrowedIgnoreList = new HashSet<>(0);
	private int currentServer;

	public void recomputeIgnoreLists(){
		if(Main.remoteSender == null || currentServer != Main.HASHCODE_2B2T) return;
		Main.LOGGER.info("GameMessageFilter: Recomputing borrowed ignorelists for 2b2t");
		boolean waitingOnLookup = false;
		ArrayList<UUID> uuids = new ArrayList<>(Configs.Database.BORROW_IGNORES.getStrings().size());
		for(String provider : Configs.Database.BORROW_IGNORES.getStrings()){
			try{uuids.add(UUID.fromString(provider));}
			catch(IllegalArgumentException e){
				final String providerName = provider.toLowerCase();
				if(!MojangProfileLookup.uuidLookup.contains(providerName)){
					MojangProfileLookup.uuidLookup.get(providerName, _0->recomputeIgnoreLists());
					waitingOnLookup = true;
				}
				else{
					UUID uuid = MojangProfileLookup.uuidLookup.getSync(providerName);
					if(uuid == MojangProfileLookup.UUID_404) continue;
					uuids.add(uuid);
				}
			}
		}
		if(!waitingOnLookup) for(UUID uuid : uuids){
			Main.remoteSender.sendBotMessage(Command.DB_PLAYER_FETCH_IGNORES, /*udp=*/false, 5000, PacketHelper.toByteArray(uuid), reply -> {
				String name = MojangProfileLookup.nameLookup.get(uuid, null);
				String nameOrUUID = name == MojangProfileLookup.NAME_404 || name == MojangProfileLookup.NAME_LOADING ? uuid.toString() : name;
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
				for(int i=0; i<reply.length/16; ++i) borrowedIgnoreList.add(new UUID(bb.getLong(), bb.getLong()));
			});
		}
	}

	private UUID determineSender(Text text){
		final String str = text.getString();
		if(!str.matches("<\\w+> .*")) return null;
		final String name = str.substring(1, str.indexOf('>'));
		return MojangProfileLookup.uuidLookup.get(name, null);
	}

	public GameMessageFilter(){
//		ClientReceiveMessageEvents.ALLOW_CHAT.register((msg, signedMessage, sender, params, ts) -> {
//			final int onServer = MiscUtils.getCurrentServerAddressHashCode();
//			if(onServer != currentServer){currentServer = onServer; recomputeIgnoreLists();}
//			Main.LOGGER.info("currentServer: "+onServer);
//
//			return !borrowedIgnoreList.contains(sender.getId());
//		});
		ClientReceiveMessageEvents.ALLOW_GAME.register((msg, overlay) -> {
			if(overlay) return true;
			final int onServer = MiscUtils.getCurrentServerAddressHashCode();
			if(onServer != currentServer){currentServer = onServer; recomputeIgnoreLists();}
			return !borrowedIgnoreList.contains(determineSender(msg));
		});
	}
}