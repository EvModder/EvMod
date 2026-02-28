package net.evmodder.evmod.apis;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.function.Consumer;
import net.evmodder.evmod.Main;

public final class PlayerPosIPC{
	private static final class Holder{private static final PlayerPosIPC INSTANCE = new PlayerPosIPC();}
	public static final PlayerPosIPC getInstance(){return Holder.INSTANCE;}

	// All these are static-final to take advantage of Constant Folding
	private static final int MAX_SLOTS = 64;
	// PID + TS + version + data
	public static final int METADATA_SIZE = 8 + 8 + 8; //=24
	// UUID + serverHashCode + worldHashCode + x + y + z + yaw + pitch + headYaw + velX + velY + velZ + pose (+ health?)
	public static final int DATA_SIZE = 16 + 4 + 4 + 8 + 8 + 8 + 4 + 4 + 4 + 8 + 8 + 8 + 4; //=92
	public static final int CPU_CACHE_LINE_SIZE = 128;
	private static final int SLOT_SIZE = Math.ceilDiv(METADATA_SIZE + DATA_SIZE, CPU_CACHE_LINE_SIZE) * CPU_CACHE_LINE_SIZE;
	// Treat PID as "dead" if no update for > 15s
	private static final long TIMEOUT_NS = 15_000l * 1000000l;

	private static final int CLAIM_LOOP_MAX_ATTEMPTS = 3;
	private static final int LAST_SLOT_BASE = (MAX_SLOTS-1)*SLOT_SIZE; // Cached compuation

	// Offsets within a slot
	private static final int TIME_OFFSET = 8;
	private static final int VERSION_OFFSET = 16;
	private static final int DATA_OFFSET = 24;

//	private static final String MEM_TABLE_FILENAME = "minecraft_player_pos.bin";
	private static final VarHandle LONG_HANDLE = MethodHandles.byteBufferViewVarHandle(long[].class, ByteOrder.nativeOrder());

	private static final long myPID = ProcessHandle.current().pid(); // Static for Constant Propagation (not Constant Folding)
	private final long[] lastReadVersions; // No benefit to making these static (memory addresses to dynamic values)
	private final byte[] data;
	private final MappedByteBuffer buffer;

	private int mySlot, readCount;

	private static final File getFastestTempDir() {
		final String os = System.getProperty("os.name").toLowerCase();
		if(os.contains("linux")){
			// /dev/shm is a tmpfs (RAM). It is significantly faster than /tmp.
			final File shm = new File("/dev/shm");
			if(shm.exists() && shm.canWrite()) return shm;
		}
		// For Windows/Mac, java.io.tmpdir is the standard path.
		// On Windows, there is no native "RAM-disk" guaranteed to exist.
		return new File(System.getProperty("java.io.tmpdir"));
	}

	private final void freeSlot(final long owner, final int base){
		if(base == LAST_SLOT_BASE) LONG_HANDLE.compareAndSet(buffer, base, owner, 0l);
		else if((long)LONG_HANDLE.getAcquire(buffer, base + SLOT_SIZE) != 0l) LONG_HANDLE.compareAndSet(buffer, base, owner, -1l);
		else if(LONG_HANDLE.compareAndSet(buffer, base, owner, -2l)){
			final long clearedVal = (long)LONG_HANDLE.getAcquire(buffer, base + SLOT_SIZE) == 0l ? 0l : -1l;
			if(clearedVal == 0l){
				final long version = (long)LONG_HANDLE.get(buffer, base + VERSION_OFFSET);
//				final long nextEvenVer = (version + 1l) & -2l;
				LONG_HANDLE.setRelease(buffer, base + VERSION_OFFSET, (version + 1l) & -2l);
			}
			LONG_HANDLE.compareAndSet(buffer, base, -2l, clearedVal);
		}
	}


	private PlayerPosIPC(){
		final File file = new File(getFastestTempDir(), "minecraft_player_pos.bin");
		try(final RandomAccessFile raf = new RandomAccessFile(file, "rw")){
			final long TOTAL_SIZE = (long) MAX_SLOTS*SLOT_SIZE;
			if(raf.length() < TOTAL_SIZE) raf.setLength(TOTAL_SIZE);
			buffer = raf.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, TOTAL_SIZE);
		}
		catch(IOException e){
			System.err.println("[EvMod] CRITICAL: Failed to initialize shared memory file");
			throw new ExceptionInInitializerError(e);
		}
		Runtime.getRuntime().addShutdownHook(new Thread(()->{
			// Mark as dead so others can take this slot immediately (rather than waiting for heartbeat)
			final int base = mySlot*SLOT_SIZE;
			if(buffer != null && myPID == (long)LONG_HANDLE.getVolatile(buffer, base)) freeSlot(myPID, base);
		}));
		// Non-shared variables, used exclusively by this PID
		lastReadVersions = new long[MAX_SLOTS];
		data = new byte[DATA_SIZE];
		Arrays.fill(lastReadVersions, -1l);
		claimSlot(); // Not strictly-necessary to call this prior to postData()
	}

	private final int claimSlot(){
		for(int j=0; j<CLAIM_LOOP_MAX_ATTEMPTS; ++j){
			final long now = System.nanoTime();
			for(int i=0; i<MAX_SLOTS; ++i){
				final int base = i*SLOT_SIZE;
				final long owner = (long)LONG_HANDLE.getVolatile(buffer, base);
				if(owner > 0l && now - (long)LONG_HANDLE.getAcquire(buffer, base + TIME_OFFSET) < TIMEOUT_NS) continue;
				LONG_HANDLE.setOpaque(buffer, base + TIME_OFFSET, now); // Update heartbeat (reduces contention fighting for this slot)
				if(LONG_HANDLE.compareAndSet(buffer, base, owner, myPID)){
					Main.LOGGER.info("[EvMod] Claimed IPC slot "+i);
					return i; // Nice, we snagged this slot!
				}
//				i=-1; // Another PID grabbed the slot before us; start again from i=0.
			}
		}
		assert false : "shared-mem table is out of slots!";
		return -1; // Failed to acquire a slot!!
	}

	private final void reapZombies() {
		final long now = System.nanoTime();
		for(int i=0; i<MAX_SLOTS; ++i){
			final int base = i*SLOT_SIZE;
//			final int base = i << 7;// Assumes `SLOT_SIZE == 128`!
			final long owner = (long) LONG_HANDLE.getOpaque(buffer, base);
			if(owner == myPID) continue; // Given its own line / branch prediction here, unlike in readData()
			if(owner <= 0l){
				if(owner == 0l) return;
				continue;
			}
			if(now - (long)LONG_HANDLE.getAcquire(buffer, base + TIME_OFFSET) > TIMEOUT_NS) freeSlot(owner, base);
		}
	}

	public final void postData(final byte[] data){
		assert data.length == DATA_SIZE;
		// Proactive heartbeat (even if mySlot is expired and got claimed by someone else)
		LONG_HANDLE.setOpaque(buffer, mySlot*SLOT_SIZE + TIME_OFFSET, System.nanoTime());
		// Ensure slot ownership, and claim a new slot if necessary
		if(myPID != (long)LONG_HANDLE.getVolatile(buffer, mySlot*SLOT_SIZE) && (mySlot=claimSlot()) == -1) return;
		final int base = mySlot*SLOT_SIZE;
		final long currentVer = (long)LONG_HANDLE.get(buffer, base + VERSION_OFFSET); // Get current version
		final long nextEvenVer = (currentVer+1l)&-2l;
		LONG_HANDLE.setRelease(buffer, base + VERSION_OFFSET, nextEvenVer); // Move current version to EVEN (signals write in-progress)
		buffer.put(base + DATA_OFFSET, data); // Publish data
		final long nextOddVer = nextEvenVer + 1l;
		lastReadVersions[mySlot] = nextOddVer; // Ensure I don't read my own data
		LONG_HANDLE.setRelease(buffer, base + VERSION_OFFSET, nextOddVer); // Move current version to ODD (signals write completed)
	}

	public final void readData(final Consumer<byte[]> consumer){
		if((++readCount & 1023) == 0) reapZombies(); // Only call every 1024 cycles
		for(int i=0; i<MAX_SLOTS; ++i){
			final int base = i*SLOT_SIZE;
//			final int base = i << 7; // Assumes `SLOT_SIZE == 128`!
			final long version = (long)LONG_HANDLE.getAcquire(buffer, base + VERSION_OFFSET);
			if(version <= lastReadVersions[i]) continue;
			final long owner = (long)LONG_HANDLE.getOpaque(buffer, base);
			if(owner <= 0l){
				if(owner == 0l) return;
				continue;
			}
			if((version & 1l) != 0l){ // Only read if non-busy
				buffer.get(base + DATA_OFFSET, data); // Read data
				VarHandle.loadLoadFence(); // Explicitly prevent the next load from occurring before the 'get' finishes
				if(version == (long)LONG_HANDLE.getOpaque(buffer, base + VERSION_OFFSET)){ // Verify data wasn't altered mid-read
					consumer.accept(data);
					lastReadVersions[i] = version;
				}
			}
		}
	}
}