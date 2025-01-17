package net.evmodder;

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
import net.evmodder.EvLib.Tuple3;
import net.evmodder.mixin.ProjectileEntityAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.util.Pair;

public final class EpearlLookup{
	private static final MinecraftClient client = MinecraftClient.getInstance();

	private static final String NAME_404 = "[404]", NAME_LOADING = "Loading...";
	private static final UUID UUID_404 = null, UUID_LOADING = new UUID(114141414, 282828282);
	private static final boolean ONLY_FOR_2b2t = true;
	private static final String DB_FILENAME_UUID = "keybound-epearl_cache_uuid";
	private static final String DB_FILENAME_XZ = "keybound-epearl_cache_xz";

	private final HashMap<UUID, UUID> updateXZ;
	private final HashMap<UUID, Pair<Integer, Integer>> epearlLocs;

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
				final int playerX = instance.player.getBlockX(), playerZ = instance.player.getBlockZ();
				for(Entity e : instance.player.clientWorld.getEntities()){
					if(e.getType() != EntityType.ENDER_PEARL) continue;
					if(SAVE_BY_UUID) pearlsSeen1.add(e.getUuid());
					if(SAVE_BY_XZ) pearlsSeen2.add(new UUID(Double.doubleToRawLongBits(e.getX()), Double.doubleToRawLongBits(e.getZ())));
					final double dx = e.getBlockX()-playerX, dz = e.getBlockZ()-playerZ;
					final double distSq = dx*dx + dz*dz;
					if(distSq > maxDistSq) maxDistSq = distSq;
				}
				if(SAVE_BY_UUID){
					for(UUID deletedKey : FileIO.removeMissingFromClientFile(DB_FILENAME_UUID, playerX, playerZ, maxDistSq, pearlsSeen1)){
						KeyBound.remoteSender.sendBotMessage(Commands.EPEARL_OWNER_STORE + Commands.EPEARL_UUID, PacketHelper.toByteArray(deletedKey), false);
						KeyBound.LOGGER.info("Deleted ePearl owner stored for UUID: "+deletedKey);
					}
				}
				if(SAVE_BY_XZ){
					for(UUID deletedKey : FileIO.removeMissingFromClientFile(DB_FILENAME_XZ, playerX, playerZ, maxDistSq, pearlsSeen1)){
						KeyBound.remoteSender.sendBotMessage(Commands.EPEARL_OWNER_STORE + Commands.EPEARL_XZ, PacketHelper.toByteArray(deletedKey), false);
						KeyBound.LOGGER.info("Deleted ePearl owner stored for XZ: "
								+ Double.longBitsToDouble(deletedKey.getMostSignificantBits())+","
								+ Double.longBitsToDouble(deletedKey.getLeastSignificantBits()));
					}
				}
			}
		}, 1000L, 5000L); // Runs every 5s
	}

	EpearlLookup(boolean uuidDb, boolean coordsDb){
		SAVE_BY_UUID = uuidDb; SAVE_BY_XZ = coordsDb;
		USE_REMOTE_DB = SAVE_BY_UUID || SAVE_BY_XZ;
		updateXZ = SAVE_BY_XZ ? new HashMap<>() : null;
		if(!USE_REMOTE_DB) epearlLocs = null;
		else{
			epearlLocs = new HashMap<>();
			runEpearlRemovalChecker();
			if(SAVE_BY_UUID) KeyBound.LOGGER.info("Epearls stored by UUID: "+FileIO.readAllClientEntries(DB_FILENAME_UUID).size());
			if(SAVE_BY_XZ) KeyBound.LOGGER.info("Epearls stored by XZ: "+FileIO.readAllClientEntries(DB_FILENAME_XZ).size());
		}
	}

	private static final LoadingCache<UUID, String> usernameCacheMojang = new LoadingCache<>(NAME_404, NAME_LOADING){@Override public String load(UUID key){
			KeyBound.LOGGER.info("fetch name called for uuid: "+key);
			MinecraftClient client = MinecraftClient.getInstance();
			ClientPlayNetworkHandler networkHandler = client.getNetworkHandler();
			if(networkHandler != null){
				KeyBound.LOGGER.info("ez, player list :D");
				PlayerListEntry entry = networkHandler.getPlayerListEntry(key);
				if(entry != null) return entry.getProfile().getName();
			}
			KeyBound.LOGGER.info("oof, web request D:");
			ProfileResult pr = client.getSessionService().fetchProfile(key, false);
			if(pr == null || pr.profile() == null || pr.profile().getName() == null) return NAME_404;
			else return pr.profile().getName();
	}};
	static{
		usernameCacheMojang.putIfAbsent(UUID_404, NAME_404);
		usernameCacheMojang.putIfAbsent(UUID_LOADING, NAME_LOADING);
	}

	private class RSLoadingCache extends LoadingCache<UUID, UUID>{
		final String DB_FILENAME;
		final int DB_FETCH_COMMAND;
		RSLoadingCache(String dbFilename, int cmdVariant){
			super(UUID_404, UUID_LOADING);
			DB_FILENAME = dbFilename;
			DB_FETCH_COMMAND = Commands.EPEARL_OWNER_FETCH + cmdVariant;
		}
		@Override public UUID load(UUID key){
			KeyBound.LOGGER.info("fetch ownerUUID called for pearlUUID: "+key);
			final Tuple3<UUID, Integer, Integer> ownerAndXZ = FileIO.lookupInClientFile(DB_FILENAME, key);
			if(ownerAndXZ != null) return ownerAndXZ.a;
			if(KeyBound.remoteSender == null) return UUID_404;
			//Request UUID of epearl for <Server>,<ePearlPosEncrypted>
			KeyBound.remoteSender.sendBotMessage(DB_FETCH_COMMAND, PacketHelper.toByteArray(key), false,
				(msg)->{
					if(msg == null || msg.length != 16){
						KeyBound.LOGGER.error("Got invalid response from remote server for ePearlOwnerFetch: "+(msg == null ? null : new String(msg)));
						putIfAbsent(key, UUID_404);
						return;
					}
					final ByteBuffer bb = ByteBuffer.wrap(msg);
					final UUID fetchedUUID = new UUID(bb.getLong(), bb.getLong());
					if(!fetchedUUID.equals(UUID_404) && !fetchedUUID.equals(UUID_LOADING)){
						Pair<Integer, Integer> xz = epearlLocs.get(key);
						if(xz == null) KeyBound.LOGGER.error("Unable to find XZ of epearl for given key!: "+key);
						else FileIO.appendToClientFile(DB_FILENAME, key, fetchedUUID, xz.getLeft(), xz.getRight());
					}
					putIfAbsent(key, fetchedUUID);
				}
			);
			return UUID_LOADING;
		}
	}

	private final RSLoadingCache cacheByUUID = new RSLoadingCache(DB_FILENAME_UUID, Commands.EPEARL_UUID);
	private final RSLoadingCache cacheByXZ = new RSLoadingCache(DB_FILENAME_XZ, Commands.EPEARL_XZ);

	private String currentServerAddrOrNull(){
		return client != null && client.getCurrentServerEntry() != null ? client.getCurrentServerEntry().address : null;
	}

	private void sendToRemoteDatabaseIfNew(RSLoadingCache cache, String filename, int cmdVariant, UUID keyUUID, UUID ownerUUID){
		if(!cache.containsKey(keyUUID) && FileIO.lookupInClientFile(filename, keyUUID) == null){
			KeyBound.LOGGER.info("Storing ownerUUID in file (and sending to remote server): "+ownerUUID);
			Pair<Integer, Integer> xz = epearlLocs.get(keyUUID);
			if(xz == null) KeyBound.LOGGER.error("Unable to find XZ of epearl for given key!: "+keyUUID);
			else FileIO.appendToClientFile(filename, keyUUID, ownerUUID, xz.getLeft(), xz.getRight());//on-disk
			cache.putIfAbsent(keyUUID, ownerUUID);//in-mem
			KeyBound.remoteSender.sendBotMessage(Commands.EPEARL_OWNER_STORE + cmdVariant, PacketHelper.toByteArray(keyUUID, ownerUUID), false);//remote-server
		}
	}

	private void updatePearlLocMap(UUID key, int x, int z){
		Pair<Integer, Integer> oldXZ = epearlLocs.get(key);
		if(oldXZ == null || oldXZ.getLeft() != x || oldXZ.getRight() != z) epearlLocs.put(key, new Pair<>(x, z));
	}

	public String getOwnerName(Entity epearl){
		//if(epearl.getType() != EntityType.ENDER_PEARL) throw IllegalArgumentException();
		UUID ownerUUID = ((ProjectileEntityAccessor)epearl).getOwnerUUID();
		final String address;
		if(USE_REMOTE_DB && epearl.getVelocity().x == 0d && epearl.getVelocity().z == 0d && (address=currentServerAddrOrNull()) != null){
			if(SAVE_BY_UUID){
				final UUID key = epearl.getUuid();
				updatePearlLocMap(key, epearl.getBlockX(), epearl.getBlockZ());
				if(ownerUUID == null) ownerUUID = cacheByUUID.get(key);
				else if(!ONLY_FOR_2b2t || "2b2t.org".equals(address)){
					sendToRemoteDatabaseIfNew(cacheByUUID, DB_FILENAME_UUID, Commands.EPEARL_UUID, key, ownerUUID);
				}
			}
			if(SAVE_BY_XZ){
//				byte[] bytes = address.getBytes();
//				ByteBuffer bb = ByteBuffer.allocate(bytes.length + 16).put(bytes).putDouble(epearl.getX()).putDouble(epearl.getZ());
//				final UUID key = UUID.nameUUIDFromBytes(bb.array());
				final UUID key = new UUID(Double.doubleToRawLongBits(epearl.getX()), Double.doubleToRawLongBits(epearl.getZ()));
				updatePearlLocMap(key, epearl.getBlockX(), epearl.getBlockZ());
				if(ownerUUID == null || ownerUUID.equals(UUID_404) || ownerUUID.equals(UUID_LOADING)) ownerUUID = cacheByXZ.get(key);
				else if(!ONLY_FOR_2b2t || "2b2t.org".equals(address)){
					final UUID oldKey = updateXZ.get(epearl.getUuid());
					if(oldKey == null) sendToRemoteDatabaseIfNew(cacheByXZ, DB_FILENAME_XZ, Commands.EPEARL_XZ, key, ownerUUID);
					else if(!oldKey.equals(key)){
						KeyBound.remoteSender.sendBotMessage(Commands.EPEARL_OWNER_STORE + Commands.EPEARL_XZ_KEY_UPDATE,
								PacketHelper.toByteArray(oldKey, key), false);
					}
					updateXZ.put(epearl.getUuid(), key);
				}
			}//SAVE_BY_XZ
		}//USE_REMOTE_DB && velocityXZ==0 && serverIP!=null
		return usernameCacheMojang.get(ownerUUID);
	}
}