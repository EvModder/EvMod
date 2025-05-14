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
import net.evmodder.EvLib.TextUtils;
import net.evmodder.KeyBound.mixin.AccessorProjectileEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;

public final class EpearlLookup{
	private static final MinecraftClient client = MinecraftClient.getInstance();

	private static final String NAME_404 = "n[404]", NAME_U_404 = "u[404]", NAME_LOADING = "Loading name...", NAME_U_LOADING = "Loading UUID...";
	private static final UUID UUID_404 = null, UUID_LOADING = new UUID(114141414, 282828282);
	private static final PearlDataClient PD_404 = new PearlDataClient(UUID_404, 0, 0, 0), PD_LOADING = new PearlDataClient(UUID_LOADING, 0, 0, 0);
	//private static final boolean ONLY_FOR_2b2t = true;
	private static final String DB_FILENAME_UUID = "epearl_cache_uuid";
	private static final String DB_FILENAME_XZ = "epearl_cache_xz";

	private final HashMap<Integer, UUID> updateKeyXZ; // Map of epearl.id -> epearl.pos (x.Double, z.Double, concatenated as UUID)
	private final HashMap<UUID, XYZ> idToPos; // Map of epearl.uuid -> epearl.pos
	private final HashMap<UUID, Long> requestStartTimes;

	private final long FETCH_TIMEOUT = 5*1000, STORE_TIMEOUT = 15*1000;
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
			Main.LOGGER.debug("fetch ownerUUID called for pearlUUID: "+key+" at "+idToPos.get(key));
			if(Main.remoteSender == null){
				Main.LOGGER.info("Remote server offline. Returning "+NAME_U_404);
				return PD_404;
			}

			//Request UUID of epearl for <Server>,<ePearlPosEncrypted>
			Main.remoteSender.sendBotMessage(DB_FETCH_COMMAND, /*udp=*/true, FETCH_TIMEOUT, PacketHelper.toByteArray(key),
				msg->{
					//Main.LOGGER.info("got response: "+msg+(msg == null ? "" : " ["+msg.length+"]"));
					final PearlDataClient pdc;
					if(msg == null || msg.length != 16){
						if(msg == null) Main.LOGGER.warn("ePearlOwnerFetch: Timed out");
						else if(msg.length == 1 && msg[0] == 0) Main.LOGGER.info("ePearlOwnerFetch: Server does not know ownerUUID for pearlUUID: "+key+" (1)");
						else Main.LOGGER.error("ePearlOwnerFetch: Invalid server response: "+new String(msg)+" ["+msg.length+"]");
						pdc = PD_404;
					}
					else{
						final ByteBuffer bb = ByteBuffer.wrap(msg);
						final UUID fetchedUUID = new UUID(bb.getLong(), bb.getLong());
						if(fetchedUUID.equals(UUID_404)){
							pdc = PD_404;
							Main.LOGGER.info("ePearlOwnerFetch: Server does not know ownerUUID for pearlUUID: "+key+" (2)");
						}
						else{
							final XYZ xyz = idToPos.get(key);
							if(xyz == null){
								Main.LOGGER.error("Unable to find XZ of epearl for given key!: "+key);
								pdc = new PearlDataClient(fetchedUUID, 0, 0, 0);
							}
							else{
								Main.LOGGER.info("ePearlOwnerFetch: Got ownerUUID for pearlUUID: "+key+" at "+idToPos.get(key)+", appending to clientFile");
								pdc = new PearlDataClient(fetchedUUID, xyz.x(), xyz.x(), xyz.z());
								FileIO.appendToClientFile(DB_FILENAME, key, pdc);
							}
						}
					}
					putIfAbsent(key, pdc);
					requestStartTimes.remove(key);
				}
			);
			requestStartTimes.put(key, System.currentTimeMillis());
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
							Main.remoteSender.sendBotMessage(Command.DB_PEARL_STORE_BY_UUID, /*udp=*/true, STORE_TIMEOUT, PacketHelper.toByteArray(deletedKey), msg->{
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
							Main.remoteSender.sendBotMessage(Command.DB_PEARL_STORE_BY_XZ, /*udp=*/true, STORE_TIMEOUT, PacketHelper.toByteArray(deletedKey), msg->{
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
		if(!USE_DB_UUID && !USE_DB_XZ){idToPos = null; cacheByUUID = cacheByXZ = null; requestStartTimes = null;}
		else{
			idToPos = new HashMap<>();
			requestStartTimes = new HashMap<>();
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
			ProfileResult pr = client.getSessionService().fetchProfile(key, /*requireSecure=*/false);
			if(pr == null || pr.profile() == null || pr.profile().getName() == null){
				Main.LOGGER.error("Unable to find name for player UUID: "+key.toString());
				return NAME_404;
			}
			else return pr.profile().getName();
		}
	};
	static{//TODO: this block can be commented out now :)
		usernameCacheMojang.putIfAbsent(UUID_404, NAME_U_404);
		usernameCacheMojang.putIfAbsent(UUID_LOADING, NAME_U_LOADING);
	}
	private final String getDynamicUsername(UUID owner, UUID key){
		if(owner == null ? UUID_404 == null : owner.equals(UUID_404)) return NAME_U_404;
		if(owner == null ? UUID_LOADING == null : owner.equals(UUID_LOADING)){
			Long startTs = requestStartTimes.get(key);
			if(startTs == null) return NAME_U_LOADING+" ERROR";
			return NAME_U_LOADING+" "+TextUtils.formatTime(System.currentTimeMillis()-startTs);
		}
		return usernameCacheMojang.get(owner);
	}

	private final boolean isLoadedOwnerName(String ownerName){
		return !ownerName.equals(NAME_404) && !ownerName.equals(NAME_U_404) && !ownerName.startsWith(NAME_LOADING) && !ownerName.startsWith(NAME_U_LOADING);
	}

	public String getOwnerName(Entity epearl){
		//if(epearl.getType() != EntityType.ENDER_PEARL) throw IllegalArgumentException();
		UUID ownerUUID = ((AccessorProjectileEntity)epearl).getOwnerUUID();
		String ownerName = getDynamicUsername(ownerUUID, epearl.getUuid());
		if((!USE_DB_UUID && !USE_DB_XZ) || epearl.getVelocity().x != 0d || epearl.getVelocity().z != 0d) return getDynamicUsername(ownerUUID, epearl.getUuid());
		final String address = client != null && client.getCurrentServerEntry() != null ? client.getCurrentServerEntry().address : null;
		if(address == null) return ownerName;

		idToPos.put(epearl.getUuid(), new XYZ(epearl.getBlockX(), epearl.getBlockY(), epearl.getBlockZ()));

		if(USE_DB_UUID){
			final UUID key = epearl.getUuid();
			if(ownerUUID == null){
				PearlDataClient pd = cacheByUUID.get(key);
				if(pd != null){
					ownerUUID = pd.owner();
					ownerName = getDynamicUsername(ownerUUID, key);
				}
			}
			else if(isLoadedOwnerName(ownerName)){
				final PearlDataClient pdc = new PearlDataClient(ownerUUID, epearl.getBlockX(), epearl.getBlockY(), epearl.getBlockZ());
				if(cacheByUUID.putIfAbsent(key, pdc)){
					Main.LOGGER.info("Sending STORE_OWNER '"+ownerName+"' for pearl at "+epearl.getBlockX()+","+epearl.getBlockZ());
					Main.remoteSender.sendBotMessage(Command.DB_PEARL_STORE_BY_UUID, /*udp=*/true, STORE_TIMEOUT, PacketHelper.toByteArray(key, ownerUUID), msg->{
						if(msg != null && msg.length == 1){
							if(msg[0] != 0){
								Main.LOGGER.info("Added pearl UUID to remote DB!");
								FileIO.appendToClientFile(DB_FILENAME_UUID, key, pdc);
							}
							// AlreadyStored/AlreadyDeleted/InvalidOwnerUUID
							else Main.LOGGER.info("Remote DB already contains pearl UUID (or rejected it for other reasons)");
						}
						else Main.LOGGER.info("Unexpected/Invalid response from RMS for DB_PEARL_STORE_BY_UUID: "+msg);
					});
				}
			}
		}
		if(USE_DB_XZ){
//			byte[] bytes = address.getBytes();
//			ByteBuffer bb = ByteBuffer.allocate(bytes.length + 16).put(bytes).putDouble(epearl.getX()).putDouble(epearl.getZ());
//			final UUID key = UUID.nameUUIDFromBytes(bb.array());
			final UUID key = new UUID(Double.doubleToRawLongBits(epearl.getX()), Double.doubleToRawLongBits(epearl.getZ()));
			if(ownerUUID == null || ownerUUID.equals(UUID_404) || ownerUUID.equals(UUID_LOADING)){
				PearlDataClient pd = cacheByXZ.get(key);
				if(pd != null){
					ownerUUID = pd.owner();
					ownerName = getDynamicUsername(ownerUUID, key);
				}
			}
			else if(isLoadedOwnerName(ownerName)){
				final UUID oldKey = updateKeyXZ.get(epearl.getId());
				if(oldKey == null){
					final PearlDataClient pdc = new PearlDataClient(ownerUUID, epearl.getBlockX(), epearl.getBlockY(), epearl.getBlockZ());
					if(cacheByXZ.putIfAbsent(key, pdc)){
						Main.remoteSender.sendBotMessage(Command.DB_PEARL_STORE_BY_XZ, /*udp=*/true, STORE_TIMEOUT, PacketHelper.toByteArray(key, ownerUUID), msg->{
							if(msg != null && msg.length > 0 && msg[0] != 0){
								Main.LOGGER.info("Added pearl XZ to remote DB!");
								FileIO.appendToClientFile(DB_FILENAME_XZ, key, pdc);
							}
							else Main.LOGGER.info("Failed to add pearl XZ to remote DB!");
						});
					}
				}
				else if(!oldKey.equals(key)){
					final long currentTime = System.currentTimeMillis();
					// Don't spam the server with XZ updates, especially because of risk they arrive in wrong order with UDP
					if(currentTime - lastUpdateXZ < 7000l) return getDynamicUsername(ownerUUID, key);
					lastUpdateXZ = currentTime;
					Main.remoteSender.sendBotMessage(Command.DB_PEARL_XZ_KEY_UPDATE, /*udp=*/true, STORE_TIMEOUT, PacketHelper.toByteArray(oldKey, key), msg->{
						if(msg != null && msg.length > 0 && msg[0] != 0) Main.LOGGER.info("Updated pearl XZ in remote DB!");
						else Main.LOGGER.info("Failed to update pearl XZ in remote DB!");
					});
				}
				updateKeyXZ.put(epearl.getId(), key);
			}
		}//USE_DB_XZ
		return ownerName;
	}
}