package net.evmodder;

import java.nio.ByteBuffer;
import java.util.UUID;
import com.mojang.authlib.yggdrasil.ProfileResult;
import net.evmodder.mixin.ProjectileEntityAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;

public final class EpearlLookup{
	private static final MinecraftClient client = MinecraftClient.getInstance();

	private static final String NAME_404 = "[404]", NAME_LOADING = "Loading...";
	private static final UUID UUID_404 = null, UUID_LOADING = new UUID(114141414, 282828282);
	private static final boolean USE_COORDS = false;
	private static final boolean ONLY_FOR_2b2t = true;
	private static final String LOCAL_DB_FILENAME = "keybound-epearl_cache_"+(USE_COORDS ? "sxz" : "u");

	private final boolean USE_REMOTE_DB;
	EpearlLookup(boolean useDB){USE_REMOTE_DB = useDB;}

	private static final LoadingCache<UUID, String> usernameCacheMojang = new LoadingCache<>(NAME_404, NAME_LOADING){@Override public String load(UUID key){
			KeyBound.LOGGER.info("fetch name called for uuid: "+key);
			ProfileResult pr = MinecraftClient.getInstance().getSessionService().fetchProfile(key, false);
			if(pr == null || pr.profile() == null || pr.profile().getName() == null) return NAME_404;
			else return pr.profile().getName();
	}};
	static{
		usernameCacheMojang.putIfAbsent(UUID_404, NAME_404);
		usernameCacheMojang.putIfAbsent(UUID_LOADING, NAME_LOADING);
	}

	private static final LoadingCache<UUID, UUID> uuidCacheRemoteServer = new LoadingCache<>(UUID_404, UUID_LOADING){@Override public UUID load(UUID key){
			KeyBound.LOGGER.info("fetch ownerUUID called for pearlUUID: "+key);
			final UUID ownerUUID = FileIO.lookupInFile(LOCAL_DB_FILENAME, key);
			if(ownerUUID != null) return ownerUUID;
			if(KeyBound.remoteSender == null) return UUID_404;
			//Request UUID of epearl for <Server>,<ePearlPosEncrypted>
			final int command = Commands.EPEARL_OWNER_FETCH + (USE_COORDS ? Commands.EPEARL_SXZ : Commands.EPEARL_U);
			KeyBound.remoteSender.sendBotMessage(command, PacketHelper.toByteArray(key),
				(msg)->{
					if(msg == null || msg.length != 16){
						KeyBound.LOGGER.error("Got invalid response from remote server for ePearlOwnerFetch: "+(msg == null ? null : new String(msg)));
						putIfAbsent(key, UUID_404);
						return;
					}
					final ByteBuffer bb = ByteBuffer.wrap(msg);
					final UUID fetchedUUID = new UUID(bb.getLong(), bb.getLong());
					if(!fetchedUUID.equals(UUID_404) && !fetchedUUID.equals(UUID_LOADING)) FileIO.appendToFile(LOCAL_DB_FILENAME, key, fetchedUUID);
					putIfAbsent(key, fetchedUUID);
				}
			);
			return UUID_LOADING;
	}};

	private UUID getEpearlKey(Entity epearl){
		if(USE_COORDS){
			KeyBound.LOGGER.info("current server: "+client.getCurrentServerEntry().address);
			KeyBound.LOGGER.info("epearl pos: "+epearl.getPos().toString());
			byte[] addr = client.getCurrentServerEntry().address.getBytes();
			ByteBuffer bb = ByteBuffer.allocate(addr.length + 16);
			bb.put(addr).putDouble(epearl.getX()).putDouble(epearl.getZ());//Double.doubleToRawLongBits(epearl.getX());
			return UUID.nameUUIDFromBytes(bb.array());
		}
		else return epearl.getUuid();
	}

	public String getOwnerName(Entity epearl){
		//if(epearl.getType() != EntityType.ENDER_PEARL) throw IllegalArgumentException();
		UUID ownerUUID = ((ProjectileEntityAccessor)epearl).getOwnerUUID();
		if(USE_REMOTE_DB){
			final UUID keyUUID = getEpearlKey(epearl);
			if(ownerUUID == null){
				ownerUUID = uuidCacheRemoteServer.get(keyUUID);
				KeyBound.LOGGER.info("fetched owner uuid: "+ownerUUID);
			}
			else if(!uuidCacheRemoteServer.containsKey(keyUUID) && FileIO.lookupInFile(LOCAL_DB_FILENAME, keyUUID) == null
					&& (!ONLY_FOR_2b2t || client.getCurrentServerEntry().address.equals("2b2t.org"))){
				KeyBound.LOGGER.info("Storing owner uuid in file (and sending to remote server): "+ownerUUID);
				FileIO.appendToFile(LOCAL_DB_FILENAME, keyUUID, ownerUUID);//on-disk
				uuidCacheRemoteServer.putIfAbsent(keyUUID, ownerUUID);//in-mem
				final int command = Commands.EPEARL_OWNER_STORE + (USE_COORDS ? Commands.EPEARL_SXZ : Commands.EPEARL_U);
				KeyBound.remoteSender.sendBotMessage(command, PacketHelper.toByteArray(keyUUID, ownerUUID));//remote-server
			}
		}
		return usernameCacheMojang.get(ownerUUID);
	}
}