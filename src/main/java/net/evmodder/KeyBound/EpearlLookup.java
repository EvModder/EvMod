package net.evmodder.KeyBound;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import com.mojang.authlib.yggdrasil.ProfileResult;
import net.evmodder.EvLib.Command;
import net.evmodder.EvLib.FileIO;
import net.evmodder.EvLib.LoadingCache;
import net.evmodder.EvLib.PacketHelper;
import net.evmodder.EvLib.PearlDataClient;
import net.evmodder.KeyBound.mixin.AccessorProjectileEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;

public final class EpearlLookup{
	private static final MinecraftClient client = MinecraftClient.getInstance();

	private static final String NAME_404 = "n[404]", NAME_LOADING = "Loading...";
	private static final UUID UUID_404 = null, UUID_LOADING = new UUID(114141414, 282828282);
	private static final PearlDataClient PD_404 = new PearlDataClient(UUID_404, 0, 0, 0), PD_LOADING = new PearlDataClient(UUID_LOADING, 0, 0, 0);
	//private static final boolean ONLY_FOR_2b2t = true;
	private static final String DB_FILENAME_UUID = "keybound-epearl_cache_uuid";
	private static final String DB_FILENAME_XZ = "keybound-epearl_cache_xz";

	private final HashMap<Integer, UUID> updateKeyXZ; // Map of epearl.id -> epearl.pos (x.Double, z.Double, concatenated as UUID)
	private final HashMap<UUID, XYZ> idToPos; // Map of epearl.uuid -> epearl.pos

	private final boolean USE_DB_UUID, USE_DB_XZ;
	private long lastUpdateXZ;
	private static Timer removalChecker = null;

	private class RSLoadingCache extends LoadingCache<UUID, PearlDataClient>{
		final String DB_FILENAME;
		final Command DB_FETCH_COMMAND;
		RSLoadingCache(HashMap<UUID, PearlDataClient> initMap, String dbFilename, boolean uuidOrXZ){
			super(initMap, PD_404, PD_LOADING);
			DB_FILENAME = dbFilename;
			DB_FETCH_COMMAND = uuidOrXZ ? Command.DB_PEARL_FETCH_BY_UUID : Command.DB_PEARL_FETCH_BY_XZ;
		}
		@Override public PearlDataClient load(UUID key){
			Main.LOGGER.info("fetch ownerUUID called for pearlUUID: "+key);
			if(Main.remoteSender == null) return PD_404;
			//Request UUID of epearl for <Server>,<ePearlPosEncrypted>
			Main.remoteSender.sendBotMessage(DB_FETCH_COMMAND, PacketHelper.toByteArray(key), /*udp=*/true,
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
						XYZ xyz = idToPos.get(key);
						if(xyz == null) Main.LOGGER.error("Unable to find XZ of epearl for given key!: "+key);
						else FileIO.appendToClientFile(DB_FILENAME, key, fetchedUUID, xyz.x(), xyz.x(), xyz.z());
					}
					putIfAbsent(key, new PearlDataClient(fetchedUUID, x, y, z));
				}
			);
			return PD_LOADING;
		}
	}
	private final RSLoadingCache cacheByUUID, cacheByXZ;

	private void runEpearlRemovalChecker(){
		if(removalChecker != null) return;
		removalChecker = new Timer(/*isDaemon=*/true);
		removalChecker.scheduleAtFixedRate(new TimerTask(){//TODO: change to onChunkLoadEvent?
			@Override public void run(){
				if(client.player == null) return;
				HashSet<UUID> pearlsSeen1 = new HashSet<>(), pearlsSeen2 = new HashSet<>();
				double maxDistSq = 32*32; // Min render distance is 2 chunks
				final int playerX = client.player.getBlockX(), playerY = client.player.getBlockY(), playerZ = client.player.getBlockZ();
				for(Entity e : client.player.clientWorld.getEntities()){
					if(e.getType() != EntityType.ENDER_PEARL) continue;
					if(USE_DB_UUID) pearlsSeen1.add(e.getUuid());
					if(USE_DB_XZ) pearlsSeen2.add(new UUID(Double.doubleToRawLongBits(e.getX()), Double.doubleToRawLongBits(e.getZ())));
					final double dx = e.getBlockX()-playerX, dz = e.getBlockZ()-playerZ;
					final double distSq = dx*dx + dz*dz; // ommission of Y intentional
					if(distSq > maxDistSq) maxDistSq = distSq;
				}
				synchronized(idToPos){ // Really this is just here to synchonize access to FileDBs
					if(USE_DB_UUID){
						HashSet<UUID> deletedKeys = FileIO.removeMissingFromClientFile(DB_FILENAME_UUID, playerX, playerY, playerZ, maxDistSq, pearlsSeen1);
						if(deletedKeys == null) Main.LOGGER.error("!! Delete failed because FileDB is null: "+DB_FILENAME_UUID);
						else for(UUID deletedKey : deletedKeys){
							cacheByUUID.remove(deletedKey);
							Main.remoteSender.sendBotMessage(Command.DB_PEARL_STORE_BY_UUID, PacketHelper.toByteArray(deletedKey), /*udp=*/true, (msg)->{
								if(msg != null && msg.length > 0 && msg[0] != 0) Main.LOGGER.info("Removed pearl UUID from remote DB!");
								else Main.LOGGER.info("Failed to remove pearl UUID from remote DB!");
							});
							Main.LOGGER.info("Deleted ePearl owner stored for UUID: "+deletedKey);
						}
					}
					if(USE_DB_XZ){
						HashSet<UUID> deletedKeys = FileIO.removeMissingFromClientFile(DB_FILENAME_XZ, playerX, playerY, playerZ, maxDistSq, pearlsSeen1);
						if(deletedKeys == null) Main.LOGGER.error("!! Delete failed because FileDB is null: "+DB_FILENAME_XZ);
						else for(UUID deletedKey : deletedKeys){
							cacheByXZ.remove(deletedKey);
							Main.remoteSender.sendBotMessage(Command.DB_PEARL_STORE_BY_XZ, PacketHelper.toByteArray(deletedKey), /*udp=*/true, (msg)->{
								if(msg != null && msg.length > 0 && msg[0] != 0) Main.LOGGER.info("Removed pearl XZ from remote DB!");
								else Main.LOGGER.info("Failed to remove pearl XZ from remote DB!");
							});
							Main.LOGGER.info("Deleted ePearl owner stored for XZ: "
									+ Double.longBitsToDouble(deletedKey.getMostSignificantBits())+","
									+ Double.longBitsToDouble(deletedKey.getLeastSignificantBits()));
						}
					}
				}
			}
		}, 1L, 15_000L); // Runs every 15s
	}

	EpearlLookup(boolean uuidDb, boolean coordsDb){
		USE_DB_UUID = uuidDb; USE_DB_XZ = coordsDb;
		updateKeyXZ = USE_DB_XZ ? new HashMap<>() : null;
		if(!USE_DB_UUID && !USE_DB_XZ){idToPos = null; cacheByUUID = cacheByXZ = null;}
		else{
			idToPos = new HashMap<>();
			runEpearlRemovalChecker();
			if(USE_DB_UUID){
				HashMap<UUID, PearlDataClient> localData = FileIO.loadFromClientFile(DB_FILENAME_UUID);
				cacheByUUID = new RSLoadingCache(localData, DB_FILENAME_UUID, /*uuidOrXZ=*/true);
				Main.LOGGER.info("Epearls stored by UUID: "+localData.size());
			}
			else cacheByUUID = null;
			if(USE_DB_XZ){
				HashMap<UUID, PearlDataClient> localData = FileIO.loadFromClientFile(DB_FILENAME_XZ);
				cacheByXZ = new RSLoadingCache(localData, DB_FILENAME_XZ, /*uuidOrXZ=*/false);
				Main.LOGGER.info("Epearls stored by XZ: "+localData.size());
			}
			else cacheByXZ = null;
		}
	}

	private static final LoadingCache<UUID, String> usernameCacheMojang = new LoadingCache<>(NAME_404, NAME_LOADING){
		@Override public String loadSyncOrNull(UUID key){
			//KeyBound.LOGGER.info("fetch name called for uuid: "+key);
			ClientPlayNetworkHandler networkHandler = client.getNetworkHandler();
			if(networkHandler != null){
				PlayerListEntry entry = networkHandler.getPlayerListEntry(key);
				if(entry != null){
					//KeyBound.LOGGER.info("ez, player list :D");
					return entry.getProfile().getName();
				}
			}
			return null;
		}
		@Override public String load(UUID key){
			//KeyBound.LOGGER.info("oof, web request D:");
			ProfileResult pr = client.getSessionService().fetchProfile(key, false);
			if(pr == null || pr.profile() == null || pr.profile().getName() == null) return NAME_404;
			else return pr.profile().getName();
		}
	};
	static{
		usernameCacheMojang.putIfAbsent(UUID_404, "u[404]");
		usernameCacheMojang.putIfAbsent(UUID_LOADING, NAME_LOADING);
	}

	public String getOwnerName(Entity epearl){
		//if(epearl.getType() != EntityType.ENDER_PEARL) throw IllegalArgumentException();
		UUID ownerUUID = ((AccessorProjectileEntity)epearl).getOwnerUUID();
		if((!USE_DB_UUID && !USE_DB_XZ) || epearl.getVelocity().x != 0d || epearl.getVelocity().z != 0d) return usernameCacheMojang.get(ownerUUID);
		final String address = client != null && client.getCurrentServerEntry() != null ? client.getCurrentServerEntry().address : null;
		if(address == null) return usernameCacheMojang.get(ownerUUID);

		idToPos.put(epearl.getUuid(), new XYZ(epearl.getBlockX(), epearl.getBlockY(), epearl.getBlockZ()));

		if(USE_DB_UUID){
			final UUID key = epearl.getUuid();
			if(ownerUUID == null){
				PearlDataClient pd = cacheByUUID.get(key);
				if(pd != null) ownerUUID = pd.owner();
			}
			else/* if(!ONLY_FOR_2b2t || "2b2t.org".equals(address))*/{
				cacheByUUID.putIfAbsent(key, new PearlDataClient(ownerUUID, epearl.getBlockX(), epearl.getBlockY(), epearl.getBlockZ()));
				Main.remoteSender.sendBotMessage(Command.DB_PEARL_STORE_BY_UUID, PacketHelper.toByteArray(key, ownerUUID), /*udp=*/true, (msg)->{
					if(msg != null && msg.length > 0 && msg[0] != 0) Main.LOGGER.info("Added pearl UUID to remote DB!");
					else Main.LOGGER.info("Failed to add pearl UUID to remote DB!");
				});
			}
		}
		if(USE_DB_XZ){
//			byte[] bytes = address.getBytes();
//			ByteBuffer bb = ByteBuffer.allocate(bytes.length + 16).put(bytes).putDouble(epearl.getX()).putDouble(epearl.getZ());
//			final UUID key = UUID.nameUUIDFromBytes(bb.array());
			final UUID key = new UUID(Double.doubleToRawLongBits(epearl.getX()), Double.doubleToRawLongBits(epearl.getZ()));
			if(ownerUUID == null/* || ownerUUID.equals(UUID_404) || ownerUUID.equals(UUID_LOADING)*/){
				PearlDataClient pd = cacheByXZ.get(key);
				if(pd != null) ownerUUID = pd.owner();
			}
			else/* if(!ONLY_FOR_2b2t || "2b2t.org".equals(address))*/{
				final UUID oldKey = updateKeyXZ.get(epearl.getId());
				if(oldKey == null){
					cacheByXZ.putIfAbsent(key, new PearlDataClient(ownerUUID, epearl.getBlockX(), epearl.getBlockY(), epearl.getBlockZ()));
					Main.remoteSender.sendBotMessage(Command.DB_PEARL_STORE_BY_XZ, PacketHelper.toByteArray(key, ownerUUID), /*udp=*/true, (msg)->{
						if(msg != null && msg.length > 0 && msg[0] != 0) Main.LOGGER.info("Added pearl XZ to remote DB!");
						else Main.LOGGER.info("Failed to add pearl XZ to remote DB!");
					});
				}
				else if(!oldKey.equals(key)){
					// Don't spam the server with XZ updates, especially because of risk they arrive in wrong order with UDP
					final long currentTime = System.currentTimeMillis();
					if(currentTime - lastUpdateXZ < 7000l) return usernameCacheMojang.get(ownerUUID);
					lastUpdateXZ = currentTime;
					Main.remoteSender.sendBotMessage(Command.DB_PEARL_XZ_KEY_UPDATE, PacketHelper.toByteArray(oldKey, key), /*udp=*/true, (msg)->{
						if(msg != null && msg.length > 0 && msg[0] != 0) Main.LOGGER.info("Updated pearl XZ in remote DB!");
						else Main.LOGGER.info("Failed to update pearl XZ in remote DB!");
					});
				}
				updateKeyXZ.put(epearl.getId(), key);
			}
		}//USE_DB_XZ
		return usernameCacheMojang.get(ownerUUID);
	}
}