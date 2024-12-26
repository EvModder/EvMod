package net.evmodder;

import java.nio.ByteBuffer;
import java.util.UUID;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.mojang.authlib.yggdrasil.ProfileResult;
import net.evmodder.PacketHelper.MessageReceiver;
import net.evmodder.mixin.ProjectileEntityAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;

public final class EpearlLookup{
	private static final MinecraftClient client = MinecraftClient.getInstance();
	private static final boolean USE_COORDS = false;
	private static final String LOADING_NAME = "Loading...";
	private static final String LOCAL_DB_FILENAME = "keybound-epearl_cache_"+(USE_COORDS ? "sxz" : "u");

	private final boolean USE_REMOTE_DB;
	EpearlLookup(boolean useDB){USE_REMOTE_DB = useDB;}

	private static final LoadingCache<UUID, String> usernameCacheMojang = CacheBuilder.newBuilder().build(new CacheLoader<>(){@Override public String load(UUID key){
			if(key != null) new Thread(()->{
				KeyBound.LOGGER.info("fetch name called for uuid: "+key);
				ProfileResult pr = MinecraftClient.getInstance().getSessionService().fetchProfile(key, false);
				if(pr == null || pr.profile() == null || pr.profile().getName() == null) usernameCacheMojang.put(key, "[404]");
				else usernameCacheMojang.put(key, pr.profile().getName());
			}).start();
			return LOADING_NAME;
	}});

	final private static UUID LOADING_UUID = UUID.randomUUID();
	static{usernameCacheMojang.put(LOADING_UUID, LOADING_NAME);}

	private static final LoadingCache<UUID, UUID> uuidCacheRemoteServer = CacheBuilder.newBuilder().build(new CacheLoader<>(){@Override public UUID load(UUID key){
			final UUID ownerUUID = FileIO.lookupInFile(LOCAL_DB_FILENAME, key);
			if(ownerUUID != null) uuidCacheRemoteServer.put(key, ownerUUID);
			else if(KeyBound.remoteSender != null) new Thread(()->{
				KeyBound.LOGGER.info("fetch ownerUUID called for pearlUUID: "+key);
				//Request UUID of epearl for <Server>,<ePearlPosEncrypted>
				KeyBound.remoteSender.sendBotMessage(Commands.EPEARL_OWNER_FETCH_U, PacketHelper.toByteArray(key), new MessageReceiver(){
					public void receiveMessage(byte[] msg){
						if(msg.length != 16){uuidCacheRemoteServer.put(key, null); return;}
						ByteBuffer bb = ByteBuffer.wrap(msg);
						UUID ownerUUID = new UUID(bb.getLong(), bb.getLong());
						FileIO.appendToFile(LOCAL_DB_FILENAME, key, ownerUUID);
						uuidCacheRemoteServer.put(key, ownerUUID);
					}
				});
			}).start();
			return LOADING_UUID;
	}});

	private UUID getEpearlKey(Entity epearl){
		if(USE_COORDS){
			KeyBound.LOGGER.info("current server: "+client.getCurrentServerEntry().address);
			KeyBound.LOGGER.info("epearl pos: "+epearl.getPos().toString());
			//Double.doubleToRawLongBits(epearl.getX());
			byte[] addr = client.getCurrentServerEntry().address.getBytes();
			ByteBuffer bb = ByteBuffer.allocate(addr.length + 16);
			bb.put(addr).putDouble(epearl.getX()).putDouble(epearl.getZ());
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
				ownerUUID = uuidCacheRemoteServer.getUnchecked(keyUUID);
				KeyBound.LOGGER.info("fetched owner uuid: "+ownerUUID);
			}
			else if(uuidCacheRemoteServer.getIfPresent(keyUUID) == null && FileIO.lookupInFile(LOCAL_DB_FILENAME, keyUUID) == null){
				KeyBound.LOGGER.info("Storing owner uuid in file (and sending to remote server): "+ownerUUID);
				FileIO.appendToFile(LOCAL_DB_FILENAME, keyUUID, ownerUUID);//on-disk
				uuidCacheRemoteServer.put(keyUUID, ownerUUID);//in-mem
				KeyBound.remoteSender.sendBotMessage(Commands.EPEARL_OWNER_STORE_U, PacketHelper.toByteArray(keyUUID, ownerUUID));//remote-server
			}
		}
		return usernameCacheMojang.getUnchecked(ownerUUID);
	}
}