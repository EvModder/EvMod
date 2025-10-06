package net.evmodder.KeyBound;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import net.evmodder.EvLib.Command;
import net.evmodder.EvLib.FileIO;
import net.evmodder.EvLib.LoadingCache;
import net.evmodder.EvLib.PacketHelper;
import net.evmodder.EvLib.PearlDataClient;
import net.evmodder.EvLib.TextUtils;
import net.evmodder.KeyBound.mixin.AccessorProjectileEntity;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;

public final class EpearlLookup{
	public record XYZ(int x, int y, int z){}

	private static final MinecraftClient client = MinecraftClient.getInstance();

	private static final PearlDataClient PD_404 = new PearlDataClient(MojangProfileLookup.UUID_404, 0, 0, 0);
	private static final PearlDataClient PD_LOADING = new PearlDataClient(MojangProfileLookup.UUID_LOADING, 0, 0, 0);
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
		RSLoadingCache(final String dbFilename, final Command fetchCommand){
			super(FileIO.loadFromClientFile(dbFilename), PD_404, PD_LOADING);
			DB_FILENAME = dbFilename;
			DB_FETCH_COMMAND = fetchCommand;
		}
		@Override public PearlDataClient load(UUID key){
			Main.LOGGER.debug("fetch ownerUUID called for pearlUUID: "+key+" at "+idToPos.get(key));
			if(Main.remoteSender == null){
				Main.LOGGER.info("Remote server offline. Returning "+MojangProfileLookup.NAME_U_404);
				return PD_404;
			}

			//Request UUID of epearl for <Server>,<ePearlPosEncrypted>
			requestStartTimes.put(key, System.currentTimeMillis());
			Main.remoteSender.sendBotMessage(DB_FETCH_COMMAND, /*udp=*/true, FETCH_TIMEOUT, PacketHelper.toByteArray(key),
				msg->{
					final XYZ xyz = idToPos.get(key);
					final PearlDataClient pdc;
					if(msg == null || msg.length != 16){
						if(msg == null) Main.LOGGER.warn("ePearlOwnerFetch: Timed out");
						else if(msg.length == 1 && msg[0] == 0){
							Main.LOGGER.info("ePearlOwnerFetch: Server does not know ownerUUID for pearlUUID: "+key+(xyz==null ? "" : " at "+xyz));
						}
						else Main.LOGGER.error("ePearlOwnerFetch: Invalid server response: "+new String(msg)+" ["+msg.length+"]");
						pdc = PD_404;
					}
					else{
						final ByteBuffer bb = ByteBuffer.wrap(msg);
						final UUID fetchedUUID = new UUID(bb.getLong(), bb.getLong());
						assert !fetchedUUID.equals(MojangProfileLookup.UUID_404);
						if(xyz == null){
							Main.LOGGER.warn("ePearlOwnerFetch: Unable to find XZ of epearl for given key!: "+key);
							pdc = new PearlDataClient(fetchedUUID, 0, 0, 0);
						}
						else{
							Main.LOGGER.info("ePearlOwnerFetch: Got ownerUUID for pearlUUID: "+key+" at "+xyz+", appending to clientFile");
							pdc = new PearlDataClient(fetchedUUID, xyz.x(), xyz.x(), xyz.z());
							FileIO.appendToClientFile(DB_FILENAME, key, pdc);
						}
					}
					putIfAbsent(key, pdc);
					requestStartTimes.remove(key);
				}
			);
			return PD_LOADING;
		}
	}
	private final RSLoadingCache cacheByUUID, cacheByXZ;

	private boolean recentChunkLoad = true;
	private void runEpearlRemovalChecker(){
//		ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {});
		ClientChunkEvents.CHUNK_LOAD.register((_0, _1) -> recentChunkLoad=true);//TODO: will epearl entities be loaded at this stage?

		//TODO: also do removal for pearl landing event?
		if(removalChecker != null) return;
		removalChecker = new Timer(/*isDaemon=*/true);
		removalChecker.scheduleAtFixedRate(new TimerTask(){
			@Override public void run(){
				if(client.player == null || client.world == null) return;
				if(recentChunkLoad){recentChunkLoad=false; return;}

				HashSet<UUID> pearlsSeen1 = new HashSet<>(), pearlsSeen2 = new HashSet<>();
				double maxDistSq = 30*30; // Min render distance is 2 chunks
				final int playerX = client.player.getBlockX(), playerY = client.player.getBlockY(), playerZ = client.player.getBlockZ();
//				for(Entity e : client.world.getEntities()){
				for(Entity e : client.world.getEntitiesByType(EntityType.ENDER_PEARL, client.player.getBoundingBox().expand(64, 128, 64), _0->true)){
//					if(e.getType() != EntityType.ENDER_PEARL) continue;
					if(USE_DB_UUID) pearlsSeen1.add(e.getUuid());
					if(USE_DB_XZ) pearlsSeen2.add(new UUID(Double.doubleToRawLongBits(e.getX()), Double.doubleToRawLongBits(e.getZ())));
//					final double dx = e.getBlockX()-playerX, dz = e.getBlockZ()-playerZ;
//					final double distSq = dx*dx + dz*dz; // ommission of Y intentional
//					if(distSq > maxDistSq) maxDistSq = distSq;
				}
				synchronized(idToPos){ // Really this is just here to synchonize access to FileDBs
					if(USE_DB_UUID){
						HashSet<UUID> deletedKeys = FileIO.removeMissingFromClientFile(DB_FILENAME_UUID, playerX, playerY, playerZ, maxDistSq, pearlsSeen1);
						if(deletedKeys == null) Main.LOGGER.error("!! Delete failed because FileDB is null: "+DB_FILENAME_UUID);
						else if(!deletedKeys.isEmpty()){
							Main.LOGGER.error("Num ePearls deleted from FileDB: "+deletedKeys.size()+" (current seen: "+pearlsSeen1+")");
							for(UUID deletedKey : deletedKeys){
								cacheByUUID.remove(deletedKey);
								Main.remoteSender.sendBotMessage(Command.DB_PEARL_STORE_BY_UUID, /*udp=*/true, STORE_TIMEOUT, PacketHelper.toByteArray(deletedKey),
								msg->{
									if(msg != null && msg.length > 0 && msg[0] != 0) Main.LOGGER.info("Removed pearl UUID from remote DB!");
									else Main.LOGGER.info("Failed to remove pearl UUID from remote DB!");
								});
								Main.LOGGER.info("Deleted ePearl owner stored for UUID: "+deletedKey);
							}
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
		}, 1L, 10_000L); // Runs every 10s
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
				cacheByUUID = new RSLoadingCache(DB_FILENAME_UUID, Command.DB_PEARL_FETCH_BY_UUID);
				Main.LOGGER.info("Epearls stored by UUID: "+cacheByUUID.size());
			}
			else cacheByUUID = null;
			if(USE_DB_XZ){
				cacheByXZ = new RSLoadingCache(DB_FILENAME_XZ, Command.DB_PEARL_FETCH_BY_XZ);
				Main.LOGGER.info("Epearls stored by XZ: "+cacheByXZ.size());
			}
			else cacheByXZ = null;
		}
	}

	private final String getDynamicUsername(UUID owner, UUID key){
		if(owner == null ? MojangProfileLookup.UUID_404 == null : owner.equals(MojangProfileLookup.UUID_404)) return MojangProfileLookup.NAME_U_404;
		if(owner == null ? MojangProfileLookup.UUID_LOADING == null : owner.equals(MojangProfileLookup.UUID_LOADING)){
			Long startTs = requestStartTimes.get(key);
			if(startTs == null) return MojangProfileLookup.NAME_U_LOADING+" ERROR";
			return MojangProfileLookup.NAME_U_LOADING+" "+TextUtils.formatTime(System.currentTimeMillis()-startTs);
		}
		return MojangProfileLookup.nameLookup.get(owner, /*callback=*/null);
	}

	public final boolean isLoadedOwnerName(String ownerName){ // TODO: make private (only called by CommandAssignPearl)
		return !ownerName.equals(MojangProfileLookup.NAME_404) && !ownerName.equals(MojangProfileLookup.NAME_U_404)
			&& !ownerName.startsWith(MojangProfileLookup.NAME_LOADING) && !ownerName.startsWith(MojangProfileLookup.NAME_U_LOADING);
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
			if(ownerUUID != null){
				final PearlDataClient pdc = new PearlDataClient(ownerUUID, epearl.getBlockX(), epearl.getBlockY(), epearl.getBlockZ());
				if(cacheByUUID.putIfAbsent(key, pdc)){
					Main.LOGGER.info("Sending STORE_OWNER '"+ownerName+"' for pearl at "+epearl.getBlockX()+","+epearl.getBlockZ());
					Main.remoteSender.sendBotMessage(Command.DB_PEARL_STORE_BY_UUID, /*udp=*/true, STORE_TIMEOUT, PacketHelper.toByteArray(key, ownerUUID), msg->{
						if(msg != null && msg.length == 1){
							if(msg[0] != 0) Main.LOGGER.info("Added pearl UUID to remote DB!");
							else Main.LOGGER.info("Remote DB already contains pearl UUID");
							FileIO.appendToClientFile(DB_FILENAME_UUID, key, pdc);
						}
						else Main.LOGGER.info("Unexpected/Invalid response from RMS for DB_PEARL_STORE_BY_UUID: "+msg);
					});
				}
			}
			else{
				PearlDataClient pd = cacheByUUID.get(key, /*callback=*/null);
				if(pd != null){
					ownerUUID = pd.owner();
					ownerName = getDynamicUsername(ownerUUID, key);
				}
			}
		}
		if(USE_DB_XZ){
//			byte[] bytes = address.getBytes();
//			ByteBuffer bb = ByteBuffer.allocate(bytes.length + 16).put(bytes).putDouble(epearl.getX()).putDouble(epearl.getZ());
//			final UUID key = UUID.nameUUIDFromBytes(bb.array());
			final UUID key = new UUID(Double.doubleToRawLongBits(epearl.getX()), Double.doubleToRawLongBits(epearl.getZ()));
			if(ownerUUID == null || ownerUUID.equals(MojangProfileLookup.UUID_404) || ownerUUID.equals(MojangProfileLookup.UUID_LOADING)){
				PearlDataClient pd = cacheByXZ.get(key, /*callback=*/null);
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
							if(msg != null && msg.length == 1){
								if(msg[0] != 0) Main.LOGGER.info("Added pearl XZ to remote DB!");
								else Main.LOGGER.info("Remote DB already contains pearl XZ (or rejected it for other reasons)");
								FileIO.appendToClientFile(DB_FILENAME_XZ, key, pdc);
							}
							else Main.LOGGER.info("Unexpected/Invalid response from RMS for DB_PEARL_STORE_BY_XZ: "+msg);
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