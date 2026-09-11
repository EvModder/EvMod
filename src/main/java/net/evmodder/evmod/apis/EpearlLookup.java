package net.evmodder.evmod.apis;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.slf4j.Logger;
import net.evmodder.EvLib.util.Command;
import net.evmodder.EvLib.util.FileIO;
import net.evmodder.EvLib.util.LoadingCache;
import net.evmodder.EvLib.util.PacketCodec;
import static net.evmodder.evmod.apis.MojangProfileLookupConstants.*;

public abstract class EpearlLookup{
	public record XYZ(int x, int y, int z){}
	// Unlike the WYXZ dimension int, world includes the server/save too; null cannot authorize cleanup.
	public record PearlDataClient(UUID owner, int x, int y, int z, UUID world){
		public PearlDataClient(UUID owner, int x, int y, int z){this(owner, x, y, z, null);}
	}
	private record LookupPosition(int x, int y, int z, UUID world, RemoteServerSender.ServerDescriptor server){}

	private final Logger LOGGER;
	private final Path cacheDirectory;
	private static final Object clientFileLock = new Object(); // Also serialize multiple lookup instances in one JVM.
	private final HashSet<String> worldFiles = new HashSet<>(); // Files whose row format has been checked this session.

	private static final PearlDataClient PDC_404 = new PearlDataClient(UUID_404, 0, 0, 0);
	private static final PearlDataClient PDC_LOADING = new PearlDataClient(UUID_LOADING, 0, 0, 0);
	private static final byte[] REMOTE_UNAVAILABLE = "unavailable".getBytes(StandardCharsets.UTF_8);
	private static final byte[] REMOTE_CONFLICT = "conflict".getBytes(StandardCharsets.UTF_8);
	private static final long STORE_PENDING = Long.MAX_VALUE, STORE_CONFLICT = Long.MIN_VALUE, STORE_RETRY_DELAY_MILLIS = 30_000;

	private static final String DB_FILENAME_UUID = "epearl_cache_uuid";
	private static final String DB_FILENAME_XZ = "epearl_cache_wyxz";

	private final RemoteServerSender remoteSender;
	protected final ConcurrentHashMap<UUID, Long> requestStartTimes;
	private UUID worldId;
	private int dimensionId;
	private record Anchor(double x, double z, int y, long expires){}
	private final HashMap<UUID, Anchor> anchors = new HashMap<>();
	private volatile RemoteServerSender.ServerDescriptor server;
	private static final long FILE_HEADER = 0x4556504541524c31L; // EVPEARL1: 60-byte rows, including world identity.

	private static UUID scopeHash(String value){
		try{
			final ByteBuffer bb = ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
			return new UUID(bb.getLong(), bb.getLong());
		}
		catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
	}
	/** The local identity must distinguish singleplayer save directories, not merely display names. */
	protected final synchronized void setWorldScope(RemoteServerSender.ServerDescriptor server, String localIdentity, String dimensionName){
		final UUID world = localIdentity == null || dimensionName == null ? null : scopeHash(localIdentity+'\0'+dimensionName);
		if(Objects.equals(worldId, world)) return;
		resetCleanup();
		anchors.clear();
		worldId = world;
		dimensionId = dimensionName == null ? 0 : (int)(scopeHash(dimensionName).getMostSignificantBits()>>>32);
		this.server = server;
		if(world == null) return;
		if(enableKeyUUID()) loadEpearlCacheUUID();
		if(enableKeyXZ()) loadEpearlCacheXZ();
		resetCleanup();
	}
	// Fabric/ZP adapt their unrelated Entity APIs here; preserve fractional XZ, not just block coordinates.
	protected final UUID toKeyXZ(UUID pearl, double x, double y, double z, PearlPositionKey.Blocks blocks){
		final long now = System.nanoTime()/1_000_000;
		Anchor anchor = anchors.get(pearl);
		if(anchor == null || anchor.x() != x || anchor.z() != z || now >= anchor.expires()){
			final int ay = PearlPositionKey.findAnchor(x, y, z, blocks);
			if(ay == PearlPositionKey.PENDING_Y) return null; // Never turn unloaded geometry or piston motion into an unknown-key write.
			anchors.put(pearl, anchor = new Anchor(x, z, ay, now+500));
		}
		return PearlPositionKey.encode(dimensionId, x, anchor.y(), z);
	}

	private final long FETCH_TIMEOUT = 5_000, STORE_TIMEOUT = 15_000;
	private long lastServerTick, lastServerUpdate, serverTickLead;
	private boolean serverReady;
	public final void resetConnectionReadiness(){lastServerUpdate = serverTickLead = 0; serverReady = false; resetCleanup();}
	public final void onServerTime(long gameTime){
		final long now = System.nanoTime()/1_000_000;
		// Accumulate accelerated server ticks: draining queued time packets is not evidence of being caught up.
		serverTickLead = Math.max(0, serverTickLead+(gameTime-lastServerTick)*50-(now-lastServerUpdate));
		if(lastServerUpdate == 0 || now-lastServerUpdate > 5_000 || gameTime <= lastServerTick || serverTickLead > 1_000)
			resetConnectionReadiness();
		else serverReady = true;
		lastServerTick = gameTime;
		lastServerUpdate = now;
	}
	protected final boolean isConnectionReady(long now){return serverReady && now-lastServerUpdate <= 5_000;}

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

	/** Atomically replaces a binary cache without changing its format (including an empty replacement). */
	private boolean saveFileBytesAtomic(String filename, byte[] data, int length){
		Path temporary = null;
		try{
			final Path target = cacheDirectory.resolve(filename);
			temporary = Files.createTempFile(target.getParent(), target.getFileName().toString()+".", ".tmp");
			try(FileOutputStream fos = new FileOutputStream(temporary.toFile())){
				fos.write(data, 0, length);
				fos.getFD().sync();
			}
			Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			return true;
		}
		catch(IOException e){e.printStackTrace(); return false;}
		finally{
			if(temporary != null) try{Files.deleteIfExists(temporary);}
			catch(IOException e){e.printStackTrace();}
		}
	}

	// Lock a stable sidecar: replacing the data file must not invalidate another process's lock.
	private FileChannel lockClientFile(String filename) throws IOException{
		final Path file = cacheDirectory.resolve(".locks").resolve(filename);
		Files.createDirectories(file.getParent());
		final FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
		try{channel.lock(); return channel;}
		catch(IOException | RuntimeException e){channel.close(); throw e;}
	}
	private byte[] readClientBytes(String filename) throws IOException{
		try{return Files.readAllBytes(cacheDirectory.resolve(filename));}
		catch(NoSuchFileException e){return null;}
	}
	private record FileStamp(Object key, long size, FileTime modified){}
	private FileStamp fileStamp(String filename) throws IOException{
		try{
			final var a = Files.readAttributes(cacheDirectory.resolve(filename), BasicFileAttributes.class);
			return new FileStamp(a.fileKey(), a.size(), a.lastModifiedTime());
		}
		catch(NoSuchFileException e){return null;}
	}
	private byte[] readClientFile(String filename){
		synchronized(clientFileLock){
			try(FileChannel _ = lockClientFile(filename)){return readClientBytes(filename);}
			catch(IOException e){LOGGER.error("[EPL] Unable to read cache "+filename, e); return null;}
		}
	}
	private final void appendToClientFile(String filename, UUID pearlUUID, PearlDataClient pdc){
		synchronized(clientFileLock){
			try(FileChannel _ = lockClientFile(filename)){
				// Validate once per session; historical files must be converted offline, never guessed here.
				if(!worldFiles.contains(filename)){
					final byte[] data = readClientBytes(filename);
					if(data == null){
						final ByteBuffer out = ByteBuffer.allocate(8+60).putLong(FILE_HEADER);
						writeClientRow(out, pearlUUID, pdc);
						if(saveFileBytesAtomic(filename, out.array(), out.position())) worldFiles.add(filename);
						else LOGGER.error("[EPL] Unable to save cache "+filename);
						return;
					}
					if(!validClientFile(data)){LOGGER.error("[EPL] Invalid cache; refusing to append "+filename); return;}
					worldFiles.add(filename);
				}
				final ByteBuffer bb = ByteBuffer.allocate(60);
				writeClientRow(bb, pearlUUID, pdc);
				try(FileOutputStream fos = new FileOutputStream(cacheDirectory.resolve(filename).toFile(), /*append=*/true)){
					fos.write(bb.array());
				}
			}
			catch(IOException e){LOGGER.error("[EPL] Unable to append cache "+filename, e);}
		}
	}
	private static boolean validClientFile(byte[] data){
		return data.length >= 8 && ByteBuffer.wrap(data).getLong() == FILE_HEADER && (data.length-8)%60 == 0;
	}
	private static void writeClientRow(ByteBuffer bb, UUID pearlUUID, PearlDataClient pdc){
		bb.putLong(pearlUUID.getMostSignificantBits());
		bb.putLong(pearlUUID.getLeastSignificantBits());
		bb.putLong(pdc.owner().getMostSignificantBits());
		bb.putLong(pdc.owner().getLeastSignificantBits());
		bb.putInt(pdc.x()).putInt(pdc.y()).putInt(pdc.z());
		bb.putLong(pdc.world() == null ? 0 : pdc.world().getMostSignificantBits());
		bb.putLong(pdc.world() == null ? 0 : pdc.world().getLeastSignificantBits());
	}

	private final HashMap<UUID, PearlDataClient> loadFromClientFile(String filename){
		final byte[] data = readClientFile(filename);
		return decodeClientFile(data);
	}
	private HashMap<UUID, PearlDataClient> decodeClientFile(byte[] data){
		if(data == null) return new HashMap<>(0);
		if(!validClientFile(data)){
			LOGGER.error("[EpearlLookup] Corrupted/invalid ePearlDB file! (A)");
			return new HashMap<>();
		}
		final int numRows = (data.length-8)/60;
		final ByteBuffer bb = ByteBuffer.wrap(data);
		bb.position(8);
		final HashMap<UUID, PearlDataClient> entries = new HashMap<>(numRows);
		for(int i=0; i<numRows; ++i){
			final UUID pearl = new UUID(bb.getLong(), bb.getLong());
			final UUID owner = new UUID(bb.getLong(), bb.getLong());
			final int x = bb.getInt(), y = bb.getInt(), z = bb.getInt();
			final UUID world = new UUID(bb.getLong(), bb.getLong());
			entries.put(pearl, new PearlDataClient(owner, x, y, z, (world.getMostSignificantBits() != 0 || world.getLeastSignificantBits() != 0) ? world : null));
		}
		return entries;
	}

	private final int removeFromClientFile(String filename, HashSet<UUID> keysToRemove, FileStamp expected){
		synchronized(clientFileLock){
			try(FileChannel _ = lockClientFile(filename)){
				// Compare under the same lock as the rewrite; another account may have refreshed this pearl.
				if(!Objects.equals(expected, fileStamp(filename))) return -1;
				final byte[] data = readClientBytes(filename);

				if(data == null) return 0;
				if(!validClientFile(data)){
					LOGGER.error("[EpearlLookup] Corrupted/invalid ePearlDB file! (B)");
					return -1;
				}
				final ByteBuffer bbIn = ByteBuffer.wrap(data);
				int end = 8;
				int removed = 0;
				for(int offset=8; offset<data.length; offset+=60){
					final UUID key = new UUID(bbIn.getLong(offset), bbIn.getLong(offset+8));
					if(!keysToRemove.contains(key)){
						if(end != offset) System.arraycopy(data, offset, data, end, 60);
						end += 60;
					}
					else ++removed;
				}
				if(removed == 0) return 0;
				assert end == data.length - removed*60;
				if(!saveFileBytesAtomic(filename, data, end)){
					LOGGER.error("[EpearlLookup] Error occurred while trying to update the local cache");
					return -1;
				}
				return removed;
			}
			catch(IOException e){LOGGER.error("[EPL] Unable to update cache "+filename, e); return -1;}
		}
	}


	protected abstract boolean enableRemoteDbUUID();
	protected abstract boolean enableRemoteDbXZ();
	protected abstract boolean enableKeyUUID();
	protected abstract boolean enableKeyXZ();

	private class RSLoadingCache extends LoadingCache<UUID, PearlDataClient>{
		final String DB_FILENAME;
		final Command DB_FETCH_COMMAND;
		final Supplier<Boolean> USE_REMOTE_DB;
		RemoteServerSender.ServerDescriptor remoteServer(){return server;}
		final ConcurrentHashMap<UUID, LookupPosition> positions = new ConcurrentHashMap<>();
		final ConcurrentHashMap<UUID, UUID> updateKeyXZ = new ConcurrentHashMap<>();
		// Pending/conflicting stores and misses last for the session, never across restarts.
		final ConcurrentHashMap<UUID, Long> storeRetryAfter = new ConcurrentHashMap<>();
		final HashSet<UUID> deleting = new HashSet<>();
		final HashMap<UUID, Long> deleteRetryAfter = new HashMap<>();
		final HashMap<UUID, Long> missingSince = new HashMap<>();
		final HashSet<UUID> remoteNeedsStore = new HashSet<>();
		private HashMap<UUID, PearlDataClient> diskEntries;
		private FileStamp diskStamp = new FileStamp(null, -1, null);
		private long nextDiskCheck;
		/** Lazy, at most one stat per second; reread only changed files, including atomic replacements. */
		synchronized void refreshDisk(){
			final long now = System.nanoTime();
			if(now < nextDiskCheck) return;
			nextDiskCheck = now+1_000_000_000L;
			synchronized(clientFileLock){
				try{
					if(Objects.equals(diskStamp, fileStamp(DB_FILENAME))) return;
					try(FileChannel _ = lockClientFile(DB_FILENAME)){
						final FileStamp stamp = fileStamp(DB_FILENAME);
						final byte[] bytes = readClientBytes(DB_FILENAME);
						if(bytes != null && !validClientFile(bytes)) throw new IOException("Invalid shared pearl cache");
						final HashMap<UUID, PearlDataClient> entries = decodeClientFile(bytes);
						for(UUID key : diskEntries.keySet()) if(!entries.containsKey(key)){
							remove(key);
							remoteNeedsStore.remove(key); // Reobserving an uncached owner will submit it again.
						}
						entries.forEach((key, value)->{
							if(!value.equals(diskEntries.get(key))){
								put(key, value); // Also replaces a session-cached miss.
								storeRetryAfter.remove(key, STORE_CONFLICT);
							}
						});
						diskEntries = entries;
						diskStamp = stamp;
						missingSince.clear(); // Conservatively restart absence after any concurrent disk write.
					}
				}
				catch(IOException e){missingSince.clear(); LOGGER.error("[EPL] Unable to refresh shared cache "+DB_FILENAME, e);}
			}
		}
		RSLoadingCache(final String dbFilename, final Command fetchCommand, final Supplier<Boolean> dbEnabledCheck){
			super(loadFromClientFile(dbFilename), PDC_LOADING);
			DB_FILENAME = dbFilename;
			DB_FETCH_COMMAND = fetchCommand;
			diskEntries = new HashMap<>(getCache());
			USE_REMOTE_DB = () -> {
				final var target = remoteServer();
				return target != null && !target.singleplayer() && dbEnabledCheck.get();
			};
		}
		@Override protected PearlDataClient load(UUID key){
			LOGGER.debug("[EpearlLookup] Fetch ownerUUID called for pearlUUID: "+key+" at "+positions.get(key));
			final LookupPosition xyz = positions.get(key); // The caller captured both world and server before starting the loader thread.
			if(xyz == null || xyz.server() == null || xyz.server().singleplayer()){
				LOGGER.info("[EpearlLookup] Database server is disabled (A). Returning "+NAME_U_404);
				return PDC_404;
			}
			assert remoteSender != null : "[EPL] Caller indicated RemoteDB enabled, but remoteSender is null";
			if(remoteSender == null){
				LOGGER.info("[EpearlLookup] Database server is disabled (B). Returning "+NAME_U_404);
				return PDC_404;
			}

			// Request UUID of epearl for <Server>,<ePearlPosEncrypted>
			requestStartTimes.put(key, System.currentTimeMillis());
			remoteSender.sendBotMessage(xyz.server(), DB_FETCH_COMMAND, /*udp=*/true, FETCH_TIMEOUT, PacketCodec.toByteArray(key),
				msg->{
					synchronized(this){
						positions.remove(key, xyz);
						final PearlDataClient pdc;
						if(msg == null || msg.length != 16){
							if(msg == null) LOGGER.warn("[EpearlLookup] Fetch ownerUUID timed out");
							else if(msg.length == 1 && msg[0] == 0){
								LOGGER.info("[EpearlLookup] Server does not know ownerUUID for pearlUUID: "+key+" at "+xyz);
							}
							else LOGGER.error("[EpearlLookup] Invalid server response: "+new String(msg)+" ["+msg.length+"]");
							pdc = PDC_404;
						}
						else{
							final ByteBuffer bb = ByteBuffer.wrap(msg);
							final UUID fetchedUUID = new UUID(bb.getLong(), bb.getLong());
							assert !fetchedUUID.equals(UUID_404);
							LOGGER.info("[EpearlLookup] Got ownerUUID for pearlUUID: "+key+" at "+xyz+", appending to clientFile");
							pdc = new PearlDataClient(fetchedUUID, xyz.x(), xyz.y(), xyz.z(), xyz.world());
						}
						final PearlDataClient oldPdc = getCached(key);
						if(oldPdc != null){
							LOGGER.warn("[EpearlLookup] Owner UUID was already added to prior to receiving DB response! owner="+oldPdc.owner);
							if(pdc.owner() != UUID_404 && !oldPdc.owner().equals(pdc.owner())){
								LOGGER.error("[EpearlLookup] Remote owner disagrees with local owner for "+key
										+": remote="+pdc.owner()+", local="+oldPdc.owner());
							}
						}
						else if(putIfAbsent(key, pdc) == null && pdc.owner() != UUID_404){
							appendToClientFile(DB_FILENAME, key, pdc);
						}
						requestStartTimes.remove(key);
					}
				}
			);
			return PDC_LOADING;
		}
	}
	private RSLoadingCache cacheByUUID, cacheByXZ;
	private RSLoadingCache cacheFor(boolean uuid){
		if(uuid){if(cacheByUUID == null) loadEpearlCacheUUID(); cacheByUUID.refreshDisk(); return cacheByUUID;}
		if(cacheByXZ == null) loadEpearlCacheXZ(); cacheByXZ.refreshDisk(); return cacheByXZ;
	}

	protected final void resetCleanup(){
		for(RSLoadingCache cache : new RSLoadingCache[]{cacheByUUID, cacheByXZ}){
			if(cache != null) synchronized(cache){cache.missingSince.clear();}
		}
	}
	/** Caller checks loaded chunks and nearby tracking; absence must then remain continuous for another minute. */
	protected final void cleanupPearls(Set<UUID> seenUUID, Set<UUID> seenXZ, Predicate<PearlDataClient> observable, long now){
		anchors.keySet().retainAll(seenUUID);
		if(cacheByXZ != null) cacheByXZ.updateKeyXZ.keySet().retainAll(seenUUID);
		if(enableKeyUUID()) cleanupPearls(cacheByUUID, seenUUID, observable, now);
		else if(cacheByUUID != null) synchronized(cacheByUUID){cacheByUUID.missingSince.clear();}
		if(enableKeyXZ()) cleanupPearls(cacheByXZ, seenXZ, observable, now);
		else if(cacheByXZ != null) synchronized(cacheByXZ){cacheByXZ.missingSince.clear();}
	}
	private void cleanupPearls(RSLoadingCache cache, Set<UUID> seen, Predicate<PearlDataClient> observable, long now){
		if(cache == null) return;
		cache.refreshDisk();
		synchronized(cache){
			int budget = 16; // Bound network bursts and local disk rewrites per scan, per key type.
			for(Entry<UUID, PearlDataClient> entry : cache.getCache().entrySet()){
				final UUID key = entry.getKey();
				final PearlDataClient value = entry.getValue();
				if(value.owner() == UUID_404 || value.owner() == UUID_LOADING || value.world() == null || !value.world().equals(worldId)
						|| seen.contains(key) || !observable.test(value)){
					cache.missingSince.remove(key);
					continue;
				}
				final Long since = cache.missingSince.putIfAbsent(key, now);
				if(since == null || now-since < 60_000 || budget == 0 || cache.storeRetryAfter.containsKey(key)
						|| now < cache.deleteRetryAfter.getOrDefault(key, 0L) || !cache.deleting.add(key)) continue;
				--budget;
				final FileStamp expected = cache.diskStamp;
				final var deletionServer = cache.remoteServer();
				final boolean remoteDelete = remoteSender != null && cache.USE_REMOTE_DB.get()
						&& cache.DB_FETCH_COMMAND == Command.DB_PEARL_FETCH_BY_UUID;
				try{
					if(!Objects.equals(expected, fileStamp(cache.DB_FILENAME))){cache.missingSince.clear(); cache.deleting.remove(key); return;}
				}
				catch(IOException e){cache.missingSince.clear(); cache.deleting.remove(key); return;}
				final Consumer<byte[]> done = msg -> {
					synchronized(cache){
						cache.deleting.remove(key);
						cache.deleteRetryAfter.put(key, now+30_000);
						if(msg == null || msg.length != 1 || (msg[0] != 0 && msg[0] != (byte)255)) return;
						// A pearl seen again while deletion was in flight must be re-stored, even if locally cached.
						cache.remoteNeedsStore.add(key);
						// A world switch or renewed observation invalidates this client's pending cleanup.
						final HashSet<UUID> keys = new HashSet<>(); keys.add(key);
						if(!Objects.equals(cache.missingSince.get(key), since) || cache.getCached(key) != value || removeFromClientFile(cache.DB_FILENAME, keys, expected) < 0){
							cache.missingSince.remove(key);
							// A remote delete may already have succeeded before another account's newer write was noticed.
							if(remoteDelete){
								final PearlDataClient latest = loadFromClientFile(cache.DB_FILENAME).get(key);
								if(latest != null) remoteSender.sendBotMessage(deletionServer, Command.DB_PEARL_STORE_BY_UUID,
										true, STORE_TIMEOUT, PacketCodec.toByteArray(key, latest.owner()), reply->{
											if(reply == null || reply.length != 1 || (reply[0] != 0 && reply[0] != (byte)255))
												LOGGER.warn("[EPL] Unable to repair concurrent pearl deletion: "+key);
										});
							}
							return;
						}
						cache.remove(key);
						cache.missingSince.remove(key);
						cache.deleteRetryAfter.remove(key);
						cache.remoteNeedsStore.remove(key);
						LOGGER.info("[EPL] Removed absent pearl from local world cache: "+key);
					}
				};
				// Shared/unknown anchors can still alias owners; never remotely delete WYXZ by inferred absence.
				if(!remoteDelete) done.accept(new byte[]{0});
				else remoteSender.sendBotMessage(deletionServer,
						Command.DB_PEARL_STORE_BY_UUID, true, STORE_TIMEOUT, PacketCodec.toByteArray(key), done);
			}
		}
	}

	public final synchronized void loadEpearlCacheUUID(){
		if(cacheByUUID == null){
			cacheByUUID = new RSLoadingCache(DB_FILENAME_UUID, Command.DB_PEARL_FETCH_BY_UUID, this::enableRemoteDbUUID);
			LOGGER.info("[EpearlLookup] stored by UUID: "+cacheByUUID.size());
		}
	}
	public final synchronized void loadEpearlCacheXZ(){
		if(cacheByXZ == null){
			cacheByXZ = new RSLoadingCache(DB_FILENAME_XZ, Command.DB_PEARL_FETCH_BY_WYXZ, this::enableRemoteDbXZ);
			LOGGER.info("[EpearlLookup] stored by XZ: "+cacheByXZ.size());
		}
	}

	public EpearlLookup(RemoteServerSender rms, Logger logger){this(rms, logger, Paths.get(FileIO.DIR));}
	protected EpearlLookup(RemoteServerSender rms, Logger logger, Path directory){
		// Share the directory (including .locks), not individual files: atomic replacement breaks file symlinks.
		try{Files.createDirectories(directory); cacheDirectory = directory.toRealPath();}
		catch(IOException e){throw new UncheckedIOException("Unable to open pearl cache directory", e);}
		remoteSender = rms;
		LOGGER = logger;
		requestStartTimes = new ConcurrentHashMap<>();

		if(enableKeyUUID()) loadEpearlCacheUUID();
		if(enableKeyXZ()) loadEpearlCacheXZ();
	}

	protected final void putPearlOwner(final UUID key, final PearlDataClient pdc, final boolean keyIsUUID){
		if(pdc.world() == null && worldId != null){
			putPearlOwner(key, new PearlDataClient(pdc.owner(), pdc.x(), pdc.y(), pdc.z(), worldId), keyIsUUID); return;
		}
		assert key != null && pdc != null;
		assert key != UUID_404 && key != UUID_LOADING;
		assert pdc.owner != null && pdc.owner != UUID_404 && pdc.owner != UUID_LOADING;
		final RSLoadingCache cache = cacheFor(keyIsUUID);
		if(cache == null) return; // No world is active yet.
		synchronized(cache){
			cache.missingSince.remove(key);
			final PearlDataClient oldPdc = cache.getCached(key);
			if(oldPdc != null && oldPdc.owner() != UUID_404){
//			LOGGER.info("Currently stored owner: "+key+" <- "+MojangProfileLookup.nameOrUUID(cache.getSync(key).owner));
//			LOGGER.info("Requested update owner: "+key+" <- "+MojangProfileLookup.nameOrUUID(pdc.owner));
				if(!oldPdc.owner.equals(pdc.owner)){
					if(cache.storeRetryAfter.putIfAbsent(key, STORE_CONFLICT) == null)
						LOGGER.warn("[EPL] Refusing conflicting local pearl owner for "+key);
					return;
				}
				if(!oldPdc.equals(pdc)){
					if(remoteSender != null && cache.USE_REMOTE_DB.get() && !Objects.equals(oldPdc.world(), pdc.world())) cache.remoteNeedsStore.add(key);
					if(oldPdc.x() != pdc.x() || oldPdc.z() != pdc.z() || !Objects.equals(oldPdc.world(), pdc.world())) appendToClientFile(cache.DB_FILENAME, key, pdc);
					cache.put(key, pdc); // Y bobbing does not append every movement.
				}
				if(!cache.remoteNeedsStore.contains(key)) return;
			}
			final String DB_FILENAME = cache.DB_FILENAME;
			if(remoteSender == null || !cache.USE_REMOTE_DB.get()){
				appendToClientFile(DB_FILENAME, key, pdc);
				cache.put(key, pdc);
				return;
			}
			// Local persistence confirms the DB was informed; failed/unacknowledged stores must retry after restart.
//		Main.LOGGER.debug("[EpearlLookup] Sending STORE_OWNER("+keyIsUUID+") '"+ownerName+"' for pearl at "+pdc.x+","+pdc.z);
			final Command cmd = keyIsUUID ? Command.DB_PEARL_STORE_BY_UUID : Command.DB_PEARL_STORE_BY_WYXZ;
			final ConcurrentHashMap<UUID, Long> storeRetryAfter = cache.storeRetryAfter;
			final long now = System.currentTimeMillis();
			while(true){
				final Long retryAt = storeRetryAfter.putIfAbsent(key, STORE_PENDING);
				if(retryAt == null) break;
				if(retryAt == STORE_PENDING || retryAt == STORE_CONFLICT || now < retryAt) return;
				if(storeRetryAfter.replace(key, retryAt, STORE_PENDING)) break;
			}
			remoteSender.sendBotMessage(
					cache.remoteServer(), cmd, /*udp=*/true, STORE_TIMEOUT, PacketCodec.toByteArray(key, pdc.owner()),
					msg->{
						synchronized(cache){
							if(msg != null && msg.length == 1 && (msg[0] == 0 || msg[0] == (byte)255)){
								storeRetryAfter.remove(key, STORE_PENDING);
								final PearlDataClient current = cache.getCached(key);
								if(current != null && current.owner() != UUID_404 && !Objects.equals(current.world(), pdc.world())) return;
								cache.remoteNeedsStore.remove(key);
								if(msg[0] != 0) LOGGER.info("[EPL] Added pearl UUID to remote DB!");
								else LOGGER.info("[EPL] Remote DB already contains pearl UUID");
								// Keep the latest locally observed position; an acknowledgement may describe an older one.
								if(current == null || current.owner() == UUID_404){
									appendToClientFile(DB_FILENAME, key, pdc);
									cache.put(key, pdc);
								}
							}
							else if(Arrays.equals(msg, REMOTE_CONFLICT)){
								storeRetryAfter.replace(key, STORE_PENDING, STORE_CONFLICT);
								LOGGER.error("[EPL] Remote DB rejected conflicting owner for "+key+"; not saving the proposed owner");
							}
							else{
								final long retryAt = System.currentTimeMillis()+STORE_RETRY_DELAY_MILLIS;
								storeRetryAfter.replace(key, STORE_PENDING, retryAt);
								CompletableFuture.delayedExecutor(STORE_RETRY_DELAY_MILLIS, TimeUnit.MILLISECONDS)
										.execute(()->storeRetryAfter.remove(key, retryAt));
								if(Arrays.equals(msg, REMOTE_UNAVAILABLE)) LOGGER.warn("[EPL] Mojang profile validation is temporarily unavailable; pearl store will retry");
								else LOGGER.info("[EPL] Unexpected response from RMS for "+cmd.name()+": "+msg);
							}
						}
					}
			);
		}
	}

	protected final PearlDataClient getPearlOwner(final UUID key, final UUID pearlId, final int x, final int y, final int z, final boolean keyIsUUID){
		assert key != null && key != UUID_404 && key != UUID_LOADING;
		final RSLoadingCache cache = cacheFor(keyIsUUID);
		if(cache == null) return PDC_LOADING;
		final ConcurrentHashMap<UUID, UUID> updateKeyXZ = cache.updateKeyXZ;
		if(!keyIsUUID){
			final UUID oldKey = updateKeyXZ.get(pearlId);
			if(oldKey != null && !oldKey.equals(key)){
				LOGGER.warn("[EPL] Detected that a pearl has changed position! Updating owner-by-XZ");
				final PearlDataClient old = cache.getCached(oldKey);
				if(old != null && PearlPositionKey.sameAnchor(oldKey, key)){
					final UUID owner = old.owner;
					assert owner != UUID_LOADING;
					if(owner != UUID_404) putPearlOwner(key, new PearlDataClient(owner, x, y, z), /*keyIsUUID=*/false);
				}
				updateKeyXZ.put(pearlId, key);
			}
		}
		PearlDataClient pdc = cache.getCached(key);
		if(pdc != null){
			if(pdc.owner() != UUID_404 && pdc.owner() != UUID_LOADING && (pdc.x() != x || pdc.y() != y || pdc.z() != z || !Objects.equals(pdc.world(), worldId)))
				putPearlOwner(key, pdc = new PearlDataClient(pdc.owner(), x, y, z, worldId), keyIsUUID);
			synchronized(cache){
				cache.missingSince.remove(key);
				if(cache.remoteNeedsStore.contains(key)) putPearlOwner(key, pdc, keyIsUUID);
			}
			if(!keyIsUUID && pdc.owner() != UUID_404 && pdc.owner() != UUID_LOADING) updateKeyXZ.put(pearlId, key);
			return pdc;
		}
		if(remoteSender == null || !cache.USE_REMOTE_DB.get()) return new PearlDataClient(UUID_404, x, y, z);
		cache.positions.putIfAbsent(key, new LookupPosition(x, y, z, worldId, cache.remoteServer()));
		return cache.get(key, loaded->{
			if(!keyIsUUID && loaded.owner() != UUID_404 && loaded.owner() != UUID_LOADING){
				updateKeyXZ.put(pearlId, key);
			}
		});
	}
}