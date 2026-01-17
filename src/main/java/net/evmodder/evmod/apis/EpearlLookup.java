package net.evmodder.evmod.apis;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;
import net.evmodder.EvLib.util.Command;
import net.evmodder.EvLib.util.FileIO;
import net.evmodder.EvLib.util.LoadingCache;
import net.evmodder.EvLib.util.PacketHelper;
import static net.evmodder.evmod.apis.MojangProfileLookupConstants.*;

public abstract class EpearlLookup{
	public record XYZ(int x, int y, int z){}
	public record PearlDataClient(UUID owner, int x, int y, int z){} // TODO: xyz + WORLD

	private final Logger LOGGER;

	private static final PearlDataClient PDC_404 = new PearlDataClient(UUID_404, 0, 0, 0);
	private static final PearlDataClient PDC_LOADING = new PearlDataClient(UUID_LOADING, 0, 0, 0);

	private static final String DB_FILENAME_UUID = "epearl_cache_uuid";
	private static final String DB_FILENAME_XZ = "epearl_cache_xz";

	private final RemoteServerSender remoteSender;
	private HashMap<Integer, UUID> updateKeyXZ; // Map of epearl.id -> keyXZ
	private final HashMap<UUID, XYZ> idToPosTemp; // Map of epearl.uuid -> epearl.pos
	protected final HashMap<UUID, Long> requestStartTimes;

	private final long FETCH_TIMEOUT = 5_000, STORE_TIMEOUT = 15_000;

	// element size = 16+16+4+4 = 40
	/*public static final Tuple3<UUID, Integer, Integer> lookupInClientFile(String filename, UUID pearlUUID){
		FileInputStream is = null;
		try{is = new FileInputStream(FileIO.DIR+filename);}
		catch(FileNotFoundException e){return null;}
		final byte[] data;
		try{data = is.readAllBytes(); is.close();}
		catch(IOException e){e.printStackTrace(); return null;}
		if(data.length % 40 != 0){
			LOGGER.severe("[EpearlLookup] Corrupted/invalid ePearlDB file!");
			return null;
		}
		final long mostSig = pearlUUID.getMostSignificantBits(), leastSig = pearlUUID.getLeastSignificantBits();
		final ByteBuffer bb = ByteBuffer.wrap(data);
		int i = 0; while(i < data.length && bb.getLong(i) != mostSig && bb.getLong(i+8) != leastSig) i += 40;
//		int lo = 0, hi = data.length/40;
//		while(hi-lo > 1){
//			int m = (lo + hi)/2;
//			long v = bb.getLong(m*40);
//			if(v > mostSig || (v == mostSig && bb.getLong(m*40+8) > pearlUUID.getLeastSignificantBits())) hi = m;
//			else lo = m;
//		}
//		final int i = lo*40;
//		final UUID keyUUID = new UUID(bb.getLong(i), bb.getLong(i+8));
//		if(!keyUUID.equals(pearlUUID)){
		if(i >= data.length){
			LOGGER.fine("[EpearlLookup] pearlUUID not found in localDB file: "+pearlUUID);
			return null;
		}
		final UUID ownerUUID = new UUID(bb.getLong(i+16), bb.getLong(i+24));
		final int x = bb.getInt(i+32), z = bb.getInt(i+36);
		return new Tuple3<>(ownerUUID, x, z);
	}*/

	private final synchronized boolean appendToClientFile(String filename, UUID pearlUUID, PearlDataClient pdc){
		File file = new File(FileIO.DIR+filename);
		try{
			FileOutputStream fos = null;
			try{fos = new FileOutputStream(file, true);}
			catch(FileNotFoundException e){
				LOGGER.info("[EpearlLookup] DB file not found, creating one");
				file.createNewFile();
				fos = new FileOutputStream(file, true);
			}
			ByteBuffer bb = ByteBuffer.allocate(16+16+4+4+4);
			bb.putLong(pearlUUID.getMostSignificantBits());
			bb.putLong(pearlUUID.getLeastSignificantBits());
			bb.putLong(pdc.owner().getMostSignificantBits());
			bb.putLong(pdc.owner().getLeastSignificantBits());
			bb.putInt(pdc.x()).putInt(pdc.y()).putInt(pdc.z());
			fos.write(bb.array());
			fos.close();
			LOGGER.trace("[EpearlLookup] Saved pearlUUID->ownerUUID to file: "+pearlUUID+"->"+pdc.owner());
		}
		catch(IOException e){e.printStackTrace();return false;}
		return true;
	}

	private final synchronized HashMap<UUID, PearlDataClient> loadFromClientFile(String filename){
		final byte[] data;
		try(FileInputStream fis = new FileInputStream(FileIO.DIR+filename)){
			data = fis.readAllBytes();
			fis.close();
		}
		catch(FileNotFoundException e){return new HashMap<>();}
		catch(IOException e){e.printStackTrace(); return new HashMap<>();}

		if(data.length % 40 == 0){
			final int numRows = data.length/40;
			final ByteBuffer bb = ByteBuffer.wrap(data);
			HashMap<UUID, PearlDataClient> entries = new HashMap<>(numRows);
			for(int i=0; i<numRows; ++i){
				UUID pearl = new UUID(bb.getLong(), bb.getLong());
				UUID owner = new UUID(bb.getLong(), bb.getLong());
				int x = bb.getInt(), z = bb.getInt();
				entries.put(pearl, new PearlDataClient(owner, x, -999, z));
			}
			return entries;
		}
		if(data.length % 44 != 0){
			LOGGER.error("[EpearlLookup] Corrupted/invalid ePearlDB file! (A)");
			return new HashMap<>();
		}
		final int numRows = data.length/44;
		final ByteBuffer bb = ByteBuffer.wrap(data);
		HashMap<UUID, PearlDataClient> entries = new HashMap<>(numRows);
		for(int i=0; i<numRows; ++i){
			UUID pearl = new UUID(bb.getLong(), bb.getLong());
			UUID owner = new UUID(bb.getLong(), bb.getLong());
			int x = bb.getInt(), y = bb.getInt(), z = bb.getInt();
			entries.put(pearl, new PearlDataClient(owner, x, y, z));
		}
		return entries;
	}

	private final synchronized int removeFromClientFile(String filename, HashSet<UUID> keysToRemove){
		final byte[] data;
		try(FileInputStream fis = new FileInputStream(FileIO.DIR+filename)){
			data = fis.readAllBytes();
			fis.close();
		}
		catch(FileNotFoundException e){return 0;}
		catch(IOException e){e.printStackTrace(); return -1;}

		if(data.length % 44 != 0){
			LOGGER.error("[EpearlLookup] Corrupted/invalid ePearlDB file! (B)");
			return -1;
		}
		final ByteBuffer bbIn = ByteBuffer.wrap(data);
		final ByteBuffer bbOut = ByteBuffer.allocate(data.length);
		int removed = 0;
		while(bbIn.hasRemaining()){
			final long k1 = bbIn.getLong(), k2 = bbIn.getLong();//16
			final long o1 = bbIn.getLong(), o2 = bbIn.getLong();//16
			final int x = bbIn.getInt(), y = bbIn.getInt(), z = bbIn.getInt();//4+4+4
			final UUID key = new UUID(k1, k2);
			if(!keysToRemove.contains(key)) bbOut.putLong(k1).putLong(k2).putLong(o1).putLong(o2).putInt(x).putInt(y).putInt(z);
			else ++removed;
		}
		if(removed == 0) return 0;

		assert bbOut.limit() == data.length - removed*44;
		try(FileOutputStream fos = new FileOutputStream(FileIO.DIR+filename)){
			fos.write(bbOut.array(), 0, bbOut.limit());
			fos.close();
		}
		catch(IOException e){e.printStackTrace();}
		return removed;
	}


	protected abstract boolean enableRemoteDbUUID();
	protected abstract boolean enableRemoteDbXZ();
	protected abstract boolean enableKeyUUID();
	protected abstract boolean enableKeyXZ();

	private class RSLoadingCache extends LoadingCache<UUID, PearlDataClient>{
		final String DB_FILENAME;
		final Command DB_FETCH_COMMAND;
		final Supplier<Boolean> USE_REMOTE_DB;
		RSLoadingCache(final String dbFilename, final Command fetchCommand, final Supplier<Boolean> dbEnabledCheck){
			super(loadFromClientFile(dbFilename), PDC_404, PDC_LOADING);
			DB_FILENAME = dbFilename;
			DB_FETCH_COMMAND = fetchCommand;
			USE_REMOTE_DB = dbEnabledCheck;
		}
		@Override protected PearlDataClient load(UUID key){
			LOGGER.debug("[EpearlLookup] Fetch ownerUUID called for pearlUUID: "+key+" at "+idToPosTemp.get(key));
			if(!USE_REMOTE_DB.get()){
				LOGGER.info("[EpearlLookup] Database server is disabled (A). Returning "+NAME_U_404);
				return PDC_404;
			}
			assert remoteSender != null : "[EPL] Caller indicated RemoteDB enabled, but remoteSender is null";
			if(remoteSender == null){
				LOGGER.info("[EpearlLookup] Database server is disabled (B). Returning "+NAME_U_404);
				return PDC_404;
			}

			//Request UUID of epearl for <Server>,<ePearlPosEncrypted>
			requestStartTimes.put(key, System.currentTimeMillis());
			remoteSender.sendBotMessage(DB_FETCH_COMMAND, /*udp=*/true, FETCH_TIMEOUT, PacketHelper.toByteArray(key),
				msg->{
					final XYZ xyz = idToPosTemp.remove(key);
					assert xyz != null;
					final PearlDataClient pdc;
					if(msg == null || msg.length != 16){
						if(msg == null) LOGGER.warn("[EpearlLookup] Fetch ownerUUID timed out");
						else if(msg.length == 1 && msg[0] == 0){
							LOGGER.info("[EpearlLookup] Server does not know ownerUUID for pearlUUID: "+key+(xyz==null ? "" : " at "+xyz));
						}
						else LOGGER.error("[EpearlLookup] Invalid server response: "+new String(msg)+" ["+msg.length+"]");
						pdc = PDC_404;
					}
					else{
						final ByteBuffer bb = ByteBuffer.wrap(msg);
						final UUID fetchedUUID = new UUID(bb.getLong(), bb.getLong());
						assert !fetchedUUID.equals(UUID_404);
						if(xyz == null){
							LOGGER.error("[EpearlLookup] Unable to find XZ of epearl for given key!: "+key);
							if(DB_FETCH_COMMAND == Command.DB_PEARL_FETCH_BY_XZ){ // Don't bother storing owner-by-XZ if XZ changed during lookup
								requestStartTimes.remove(key);
								return;
							}
							pdc = new PearlDataClient(fetchedUUID, 0, 0, 0);
						}
						else{
							LOGGER.info("[EpearlLookup] Got ownerUUID for pearlUUID: "+key+" at "+xyz+", appending to clientFile");
							pdc = new PearlDataClient(fetchedUUID, xyz.x(), xyz.x(), xyz.z());
							appendToClientFile(DB_FILENAME, key, pdc);
						}
					}
					putIfAbsent(key, pdc);
					requestStartTimes.remove(key);
				}
			);
			return PDC_LOADING;
		}
	}
	private RSLoadingCache cacheByUUID, cacheByXZ;

	private final void removeEpearls(final RSLoadingCache cache, final String DB_FILENAME, final HashSet<UUID> keysToRemove){
		final int numToRemove = keysToRemove.size();
		assert numToRemove > 0;
		final int oldCacheSize = cache.size();
		keysToRemove.forEach(cache::remove); // Remove from in-Mem
		final int removedInMem = oldCacheSize - cache.size();
		if(removedInMem != numToRemove){
			LOGGER.error("[EPL] Unable to delete ePearls from MemDB! got: "+removedInMem+"/"+numToRemove);
		}
		final int removedInFile = removeFromClientFile(DB_FILENAME, keysToRemove);
		if(removedInFile != keysToRemove.size()){
			LOGGER.error("[EPL] Unable to delete ePearls from FileDB! got: "+removedInFile+"/"+numToRemove);
		}
		else LOGGER.info("[EPL] Removed "+removedInFile+" ePearls from MemDB/FileDB");

		if(remoteSender == null || !cache.USE_REMOTE_DB.get()) return;
		keysToRemove.forEach(key->{
			remoteSender.sendBotMessage(
				DB_FILENAME == DB_FILENAME_UUID ? Command.DB_PEARL_STORE_BY_UUID : Command.DB_PEARL_STORE_BY_XZ,
				/*udp=*/true, STORE_TIMEOUT, PacketHelper.toByteArray(key),
				msg->{
					if(msg != null && msg.length > 0 && msg[0] != 0) LOGGER.info("[EPL] RemoteDB reported pearl removed");
					else LOGGER.warn("[EPL] Remote DB was unable to remove pearl! key="+key);
				}
			);
		});
	}
	protected final void runRemovalCheckUUID(Function<Entry<UUID, PearlDataClient>, Boolean> shouldRemove){
		assert enableKeyUUID();
//		if(!Configs.Database.EPEARL_OWNERS_BY_UUID.getBooleanValue()) return;
		HashSet<UUID> keysToRemove = new HashSet<>();
		cacheByUUID.getCache().entrySet().forEach(entry -> {
			if(shouldRemove.apply(entry)) keysToRemove.add(entry.getKey());
		});
		if(!keysToRemove.isEmpty()){
			removeEpearls(cacheByUUID, DB_FILENAME_UUID, keysToRemove); // Remove from inMemDB, FileDB, and RemoteDB
		}
	}
	protected final void runRemovalCheckXZ(Function<Entry<UUID, PearlDataClient>, Boolean> shouldRemove){
		assert enableKeyXZ();
		HashSet<UUID> keysToRemove = new HashSet<>();
		cacheByUUID.getCache().entrySet().forEach(entry -> {
			if(shouldRemove.apply(entry)) keysToRemove.add(entry.getKey());
		});
		if(!keysToRemove.isEmpty()){
			removeEpearls(cacheByXZ, DB_FILENAME_XZ, keysToRemove); // Remove from inMemDB, FileDB, and RemoteDB
		}
	}

	public final void loadEpearlCacheUUID(){
		if(cacheByUUID == null){
			cacheByUUID = new RSLoadingCache(DB_FILENAME_UUID, Command.DB_PEARL_FETCH_BY_UUID, this::enableRemoteDbUUID);
			LOGGER.info("[EpearlLookup] stored by UUID: "+cacheByUUID.size());
		}
	}
	public final void loadEpearlCacheXZ(){
		if(cacheByXZ == null){
			updateKeyXZ = new HashMap<Integer, UUID>();
			cacheByXZ = new RSLoadingCache(DB_FILENAME_XZ, Command.DB_PEARL_FETCH_BY_XZ, this::enableRemoteDbXZ);
			LOGGER.info("[EpearlLookup] stored by XZ: "+cacheByXZ.size());
		}
	}

	public EpearlLookup(RemoteServerSender rms, Logger logger){
		remoteSender = rms;
		LOGGER = logger;
		if(rms != null){idToPosTemp = new HashMap<>(); requestStartTimes = new HashMap<>();}
		else{idToPosTemp = null; requestStartTimes = null;}

		if(enableKeyUUID()) loadEpearlCacheUUID();
		if(enableKeyXZ()) loadEpearlCacheXZ();
	}

	protected final void putPearlOwner(final UUID key, final PearlDataClient pdc, final boolean keyIsUUID){
		assert key != null && pdc != null;
		assert pdc.owner != null && pdc.owner != UUID_404 && pdc.owner != UUID_LOADING;
		final RSLoadingCache cache = keyIsUUID ? cacheByUUID : cacheByXZ;
		assert cache != null;
		if(!cache.putIfAbsent(key, pdc)){ // Owner already stored
			assert cache.getSync(key).owner.equals(pdc.owner);
			return;
		}
		final String DB_FILENAME = (keyIsUUID ? DB_FILENAME_UUID : DB_FILENAME_XZ);
		if(remoteSender == null || !cache.USE_REMOTE_DB.get()){
			appendToClientFile(DB_FILENAME, key, pdc);
			return;
		}
//		Main.LOGGER.debug("[EpearlLookup] Sending STORE_OWNER("+keyIsUUID+") '"+ownerName+"' for pearl at "+pdc.x+","+pdc.z);
		final Command cmd = keyIsUUID ? Command.DB_PEARL_STORE_BY_UUID : Command.DB_PEARL_STORE_BY_XZ;
		remoteSender.sendBotMessage(
				cmd, /*udp=*/true, STORE_TIMEOUT, PacketHelper.toByteArray(key, pdc.owner()),
				msg->{
					if(msg != null && msg.length == 1){
						if(msg[0] != 0) LOGGER.info("[EPL] Added pearl UUID to remote DB!");
						else LOGGER.info("[EPL] Remote DB already contains pearl UUID");
						appendToClientFile(DB_FILENAME, key, pdc);
					}
					else LOGGER.info("[EPL] Unexpected response from RMS for "+cmd.name()+": "+msg);
				}
		);
	}

	protected final PearlDataClient getPearlOwner(final UUID key, final int pearlId, final int x, final int y, final int z, final boolean keyIsUUID){
		final RSLoadingCache cache = (keyIsUUID ? cacheByUUID : cacheByXZ);
		if(!keyIsUUID){
			final UUID oldKey = updateKeyXZ.get(pearlId);
			if(oldKey != null && !oldKey.equals(key)){
				LOGGER.warn("[EPL] Detected that a pearl has changed position! Updating owner-by-XZ");
				if(cache.contains(oldKey)){
					final UUID owner = cache.getSync(oldKey).owner;
					assert owner != UUID_LOADING;
					if(owner != UUID_404) putPearlOwner(key, new PearlDataClient(owner, x, y, z), /*keyIsUUID=*/false);
				}
			}
		}
		if(cache.contains(key)) return cache.getSync(key);
		if(idToPosTemp != null) idToPosTemp.put(key, new XYZ(x, y, z)); // equivalent: if(DB_ENABLED)
		return cache.get(key, pdc->{
			if(!keyIsUUID && pdc.owner != UUID_404 && pdc.owner != UUID_LOADING){
				updateKeyXZ.put(pearlId, key);
			}
		});
	}
}