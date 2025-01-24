package net.evmodder.KeyBound;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import com.mojang.authlib.yggdrasil.ProfileResult;
import net.evmodder.EvLib.Commands;
import net.evmodder.EvLib.FileIO;
import net.evmodder.EvLib.LoadingCache;
import net.evmodder.EvLib.PacketHelper;
import net.evmodder.EvLib.PearlDataClient;
import net.evmodder.KeyBound.mixin.ProjectileEntityAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;

public final class EpearlLookup{
	private static final MinecraftClient client = MinecraftClient.getInstance();

	private static final String NAME_404 = "[404]", NAME_LOADING = "Loading...";
	private static final UUID UUID_404 = null, UUID_LOADING = new UUID(114141414, 282828282);
	private static final PearlDataClient PD_404 = new PearlDataClient(UUID_404, 0, 0, 0), PD_LOADING = new PearlDataClient(UUID_LOADING, 0, 0, 0);
	private static final boolean ONLY_FOR_2b2t = true;
	private static final String DB_FILENAME_UUID = "keybound-epearl_cache_uuid";
	private static final String DB_FILENAME_XZ = "keybound-epearl_cache_xz";

	private final HashMap<UUID, UUID> updateXZ;
	private final HashMap<UUID, XYZ> epearlLocs;

	private final boolean USE_REMOTE_DB, SAVE_BY_UUID, SAVE_BY_XZ;
	private static Timer removalChecker = null;

	private void runEpearlRemovalChecker(){
		if(removalChecker != null) return;
		removalChecker = new Timer(/*isDaemon=*/true);
		removalChecker.scheduleAtFixedRate(new TimerTask(){//TODO: change to onChunkLoadEvent?
			@Override public void run(){
				HashSet<UUID> pearlsSeen1 = new HashSet<>(), pearlsSeen2 = new HashSet<>();
				MinecraftClient instance = MinecraftClient.getInstance();
				double maxDistSq = 32*32; // Min render distance is 2 chunks
				final int playerX = instance.player.getBlockX(), playerY = instance.player.getBlockY(), playerZ = instance.player.getBlockZ();
				for(Entity e : instance.player.clientWorld.getEntities()){
					if(e.getType() != EntityType.ENDER_PEARL) continue;
					if(SAVE_BY_UUID) pearlsSeen1.add(e.getUuid());
					if(SAVE_BY_XZ) pearlsSeen2.add(new UUID(Double.doubleToRawLongBits(e.getX()), Double.doubleToRawLongBits(e.getZ())));
					final double dx = e.getBlockX()-playerX, dz = e.getBlockZ()-playerZ;
					final double distSq = dx*dx + dz*dz; // ommission of Y intentional
					if(distSq > maxDistSq) maxDistSq = distSq;
				}
				synchronized(epearlLocs){ // Really this is just here to synchonize access to clientFileDB
				if(SAVE_BY_UUID){
					for(UUID deletedKey : FileIO.removeMissingFromClientFile(DB_FILENAME_UUID, playerX, playerY, playerZ, maxDistSq, pearlsSeen1)){
						Main.remoteSender.sendBotMessage(Commands.EPEARL_OWNER_STORE + Commands.EPEARL_UUID, PacketHelper.toByteArray(deletedKey), false);
						Main.LOGGER.info("Deleted ePearl owner stored for UUID: "+deletedKey);
					}
				}
				if(SAVE_BY_XZ){
					for(UUID deletedKey : FileIO.removeMissingFromClientFile(DB_FILENAME_XZ, playerX, playerY, playerZ, maxDistSq, pearlsSeen1)){
						Main.remoteSender.sendBotMessage(Commands.EPEARL_OWNER_STORE + Commands.EPEARL_XZ, PacketHelper.toByteArray(deletedKey), false);
						Main.LOGGER.info("Deleted ePearl owner stored for XZ: "
								+ Double.longBitsToDouble(deletedKey.getMostSignificantBits())+","
								+ Double.longBitsToDouble(deletedKey.getLeastSignificantBits()));
					}
				}
				}
			}
		}, 1L, 15_000L); // Runs every 15s
	}

	private class RSLoadingCache extends LoadingCache<UUID, PearlDataClient>{
		final String DB_FILENAME;
		final int DB_FETCH_COMMAND;
		RSLoadingCache(HashMap<UUID, PearlDataClient> initMap, String dbFilename, int cmdVariant){
			super(initMap, PD_404, PD_LOADING);
			DB_FILENAME = dbFilename;
			DB_FETCH_COMMAND = Commands.EPEARL_OWNER_FETCH + cmdVariant;
		}
		@Override public PearlDataClient load(UUID key){
			Main.LOGGER.info("fetch ownerUUID called for pearlUUID: "+key);
			if(Main.remoteSender == null) return PD_404;
			//Request UUID of epearl for <Server>,<ePearlPosEncrypted>
			Main.remoteSender.sendBotMessage(DB_FETCH_COMMAND, PacketHelper.toByteArray(key), false,
				(msg)->{
					if(msg == null || msg.length != 16){
						Main.LOGGER.error("Got invalid response from remote server for ePearlOwnerFetch: "+(msg == null ? null : new String(msg)));
						putIfAbsent(key, PD_404);
						return;
					}
					final ByteBuffer bb = ByteBuffer.wrap(msg);
					final UUID fetchedUUID = new UUID(bb.getLong(), bb.getLong());
					int x=0,y=0,z=0;
					if(!fetchedUUID.equals(UUID_404) && !fetchedUUID.equals(UUID_LOADING)){
						XYZ xyz = epearlLocs.get(key);
						if(xyz == null) Main.LOGGER.error("Unable to find XZ of epearl for given key!: "+key);
						else{x=xyz.x(); y=xyz.y(); z=xyz.z();}
						FileIO.appendToClientFile(DB_FILENAME, key, fetchedUUID, x, y, z);
					}
					putIfAbsent(key, new PearlDataClient(fetchedUUID, x, y, z));
				}
			);
			return PD_LOADING;
		}
	}
	private final RSLoadingCache cacheByUUID, cacheByXZ;

	EpearlLookup(boolean uuidDb, boolean coordsDb){
		SAVE_BY_UUID = uuidDb; SAVE_BY_XZ = coordsDb;
		USE_REMOTE_DB = SAVE_BY_UUID || SAVE_BY_XZ;
		updateXZ = SAVE_BY_XZ ? new HashMap<>() : null;
		if(!USE_REMOTE_DB){epearlLocs = null; cacheByUUID = cacheByXZ = null;}
		else{
			epearlLocs = new HashMap<>();
			runEpearlRemovalChecker();
			if(SAVE_BY_UUID){
				HashMap<UUID, PearlDataClient> localData = FileIO.loadFromClientFile(DB_FILENAME_UUID);
				cacheByUUID = new RSLoadingCache(localData, DB_FILENAME_UUID, Commands.EPEARL_UUID);
				Main.LOGGER.info("Epearls stored by UUID: "+localData.size());
			}
			else cacheByUUID = null;
			if(SAVE_BY_XZ){
				HashMap<UUID, PearlDataClient> localData = FileIO.loadFromClientFile(DB_FILENAME_XZ);
				cacheByXZ = new RSLoadingCache(localData, DB_FILENAME_XZ, Commands.EPEARL_XZ);
				Main.LOGGER.info("Epearls stored by XZ: "+localData.size());
			}
			else cacheByXZ = null;
		}
	}

	private static final LoadingCache<UUID, String> usernameCacheMojang = new LoadingCache<>(NAME_404, NAME_LOADING){@Override public String load(UUID key){
			//KeyBound.LOGGER.info("fetch name called for uuid: "+key);
			MinecraftClient client = MinecraftClient.getInstance();
			ClientPlayNetworkHandler networkHandler = client.getNetworkHandler();
			if(networkHandler != null){
				//KeyBound.LOGGER.info("ez, player list :D");
				PlayerListEntry entry = networkHandler.getPlayerListEntry(key);
				if(entry != null) return entry.getProfile().getName();
			}
			//KeyBound.LOGGER.info("oof, web request D:");
			ProfileResult pr = client.getSessionService().fetchProfile(key, false);
			if(pr == null || pr.profile() == null || pr.profile().getName() == null) return NAME_404;
			else return pr.profile().getName();
	}};
	static{
		usernameCacheMojang.putIfAbsent(UUID_404, NAME_404);
		usernameCacheMojang.putIfAbsent(UUID_LOADING, NAME_LOADING);
	}

	private String currentServerAddrOrNull(){
		return client != null && client.getCurrentServerEntry() != null ? client.getCurrentServerEntry().address : null;
	}

	private void sendToRemoteDatabaseIfNew(RSLoadingCache cache, String filename, int cmdVariant, UUID keyUUID, UUID ownerUUID){
		if(!cache.containsKey(keyUUID)){
			//new Thread(()->{
				Main.LOGGER.info("Storing ownerUUID in file (and sending to remote server): "+ownerUUID);
				int x=0, y=0, z=0;
				XYZ xyz = epearlLocs.get(keyUUID);
				if(xyz == null) Main.LOGGER.error("Unable to find XYZ of epearl for given key!: "+keyUUID);
				else{x=xyz.x(); y=xyz.y(); z=xyz.z();}
				cache.putIfAbsent(keyUUID, new PearlDataClient(ownerUUID, x, y, z));
				Main.remoteSender.sendBotMessage(Commands.EPEARL_OWNER_STORE + cmdVariant, PacketHelper.toByteArray(keyUUID, ownerUUID), true);//remote-server
			//}).start();
		}
	}

	private void updatePearlLocMap(UUID key, int x, int y, int z){
		XYZ xyz = epearlLocs.get(key);
		if(xyz == null || xyz.x() != x || xyz.z() != z || Math.abs(xyz.y()-y) > 3) epearlLocs.put(key, new XYZ(x, y, z));
	}

	public String getOwnerName(Entity epearl){
		//if(epearl.getType() != EntityType.ENDER_PEARL) throw IllegalArgumentException();
		UUID ownerUUID = ((ProjectileEntityAccessor)epearl).getOwnerUUID();
		final String address;
		if(USE_REMOTE_DB && epearl.getVelocity().x == 0d && epearl.getVelocity().z == 0d && (address=currentServerAddrOrNull()) != null){
			if(SAVE_BY_UUID){
				final UUID key = epearl.getUuid();
				updatePearlLocMap(key, epearl.getBlockX(), epearl.getBlockY(), epearl.getBlockZ());
				if(ownerUUID == null){PearlDataClient pd = cacheByUUID.get(key); if(pd != null)ownerUUID = pd.owner();}
				else if(!ONLY_FOR_2b2t || "2b2t.org".equals(address)){
					sendToRemoteDatabaseIfNew(cacheByUUID, DB_FILENAME_UUID, Commands.EPEARL_UUID, key, ownerUUID);
				}
			}
			if(SAVE_BY_XZ){
//				byte[] bytes = address.getBytes();
//				ByteBuffer bb = ByteBuffer.allocate(bytes.length + 16).put(bytes).putDouble(epearl.getX()).putDouble(epearl.getZ());
//				final UUID key = UUID.nameUUIDFromBytes(bb.array());
				final UUID key = new UUID(Double.doubleToRawLongBits(epearl.getX()), Double.doubleToRawLongBits(epearl.getZ()));
				updatePearlLocMap(key, epearl.getBlockX(), epearl.getBlockY(), epearl.getBlockZ());
				if(ownerUUID == null || ownerUUID.equals(UUID_404) || ownerUUID.equals(UUID_LOADING)){
					PearlDataClient pd = cacheByXZ.get(key);
					if(pd != null) ownerUUID = pd.owner();
				}
				else if(!ONLY_FOR_2b2t || "2b2t.org".equals(address)){
					final UUID oldKey = updateXZ.get(epearl.getUuid());
					if(oldKey == null) sendToRemoteDatabaseIfNew(cacheByXZ, DB_FILENAME_XZ, Commands.EPEARL_XZ, key, ownerUUID);
					else if(!oldKey.equals(key)){
						Main.remoteSender.sendBotMessage(Commands.EPEARL_OWNER_STORE + Commands.EPEARL_XZ_KEY_UPDATE,
								PacketHelper.toByteArray(oldKey, key), false);
					}
					updateXZ.put(epearl.getUuid(), key);
				}
			}//SAVE_BY_XZ
		}//USE_REMOTE_DB && velocityXZ==0 && serverIP!=null
		return usernameCacheMojang.get(ownerUUID);
	}
}