package net.evmodder.KeyBound.listeners;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;
import net.evmodder.EvLib.Command;
import net.evmodder.EvLib.PacketHelper;
import net.evmodder.KeyBound.Configs;
import net.evmodder.KeyBound.Main;
import net.evmodder.KeyBound.apis.MojangProfileLookup;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;

public class GameMessageFilter{
	private HashSet<UUID> borrowedIgnoreList = new HashSet<>();

	public void recomputeIgnoreLists(){
		if(Main.remoteSender == null) return;
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
			Main.remoteSender.sendBotMessage(Command.DB_PLAYER_FETCH_IGNORES, /*udp=*/false, 20_000, PacketHelper.toByteArray(uuid), reply -> {
				if(reply == null || reply.length % 16 != 0){
					String name = MojangProfileLookup.nameLookup.get(uuid, null);
					String nameOrUUID = name == MojangProfileLookup.NAME_404 || name == MojangProfileLookup.NAME_LOADING ? uuid.toString() : name;
					Main.LOGGER.warn("Fetch ignore list for '"+nameOrUUID+"' got invalid response! "+(reply==null?"null":"len%16 != 0"));
					return;
				}
				final ByteBuffer bb = ByteBuffer.wrap(reply);
				for(int i=0; i<reply.length/16; ++i) borrowedIgnoreList.add(new UUID(bb.getLong(), bb.getLong()));
			});
		}
	}

	public GameMessageFilter(){
		recomputeIgnoreLists();

		ClientReceiveMessageEvents.ALLOW_CHAT.register((msg, signedMessage, sender, params, ts) -> {
			return !borrowedIgnoreList.contains(sender.getId());
		});
	}
}