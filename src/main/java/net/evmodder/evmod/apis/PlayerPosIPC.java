package net.evmodder.evmod.apis;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.function.Consumer;

public final class PlayerPosIPC{
	private static final class Holder{private static final PlayerPosIPC INSTANCE = new PlayerPosIPC();}
	public static final PlayerPosIPC getInstance(){return Holder.INSTANCE;}

	// Hopefully nobody is running more than this many Minecraft accounts on 1 device...
	private static final int MAX_SLOTS = 64;
	// UUID + serverHashCode + worldHashCode + x + y + z
	public static final int DATA_SIZE = 16 + 8 + 8 + 8 + 4 + 4; //=48
	// PID + TS + version + data
	private static final int SLOT_SIZE = 8 + 8 + 8 + DATA_SIZE;
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

	private static final long myPID = ProcessHandle.current().pid();
	private final long[] lastReadVersions = new long[MAX_SLOTS]; // Can't be static (static variables are shared across the entire JVM)
	private final byte[] data = new byte[DATA_SIZE];
	private final MappedByteBuffer buffer;
	private int mySlot;

	private final void freeSlot(final long owner, final int base){
		if(base == LAST_SLOT_BASE) LONG_HANDLE.compareAndSet(buffer, base, owner, 0l);
		else if((long)LONG_HANDLE.getVolatile(buffer, base + SLOT_SIZE) != 0l) LONG_HANDLE.compareAndSet(buffer, base, owner, -1l);
		else if(LONG_HANDLE.compareAndSet(buffer, base, owner, -2l)){
			final long clearedVal = (long)LONG_HANDLE.getVolatile(buffer, base + SLOT_SIZE) == 0l ? 0l : -1l;
			LONG_HANDLE.compareAndSet(buffer, base, -2l, clearedVal);
		}
	}

	private PlayerPosIPC(){
		final File file = new File(System.getProperty("java.io.tmpdir"), "minecraft_player_pos.bin");
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
	}

	private final int claimSlot(){
		final long now = System.nanoTime();
		for(int j=0; j<CLAIM_LOOP_MAX_ATTEMPTS; ++j){
			for(int i=0; i<MAX_SLOTS; ++i){
				final int base = i*SLOT_SIZE;
				final long owner = (long)LONG_HANDLE.getVolatile(buffer, base);
				if(owner > 0l && now - (long)LONG_HANDLE.getVolatile(buffer, base + TIME_OFFSET) < TIMEOUT_NS) continue;
				LONG_HANDLE.setVolatile(buffer, base + TIME_OFFSET, now); // Update ts (reduces contention fighting for this slot)
				if(LONG_HANDLE.compareAndSet(buffer, base, owner, myPID)) return i; // Nice, we snagged this slot!
//				i=-1; // Another PID grabbed the slot before us; start again from i=0.
			}
		}
		assert false : "shared-mem table is out of slots!";
		return -1; // Failed to acquire a slot!!
	}

	public final void postData(final byte[] data){
		assert data.length == DATA_SIZE;
		// Ensure my slot is still valid (and update it if not).
		if(myPID != (long)LONG_HANDLE.getVolatile(buffer, mySlot*SLOT_SIZE) && (mySlot=claimSlot()) == -1) return;
		final int base = mySlot*SLOT_SIZE;
		final long currentVer = (long)LONG_HANDLE.get(buffer, base + VERSION_OFFSET); // Get current version
		final long nextEvenVer = (currentVer+1l)&-2l;
		LONG_HANDLE.setRelease(buffer, base + VERSION_OFFSET, nextEvenVer); // Move current version to EVEN (signals write in-progress)
		LONG_HANDLE.setVolatile(buffer, base + TIME_OFFSET, System.nanoTime()); // Heartbeat
		buffer.put(base + DATA_OFFSET, data); // Publish data
		LONG_HANDLE.setRelease(buffer, base + VERSION_OFFSET, nextEvenVer + 1l); // Move current version to ODD (signals write completed)
	}

	public final void readData(final Consumer<byte[]> consumer){
		final long now = System.nanoTime(), prevOwner;
		for(int i=0; i<MAX_SLOTS; ++i){
			final int base = i*SLOT_SIZE;
			final long owner = (long)LONG_HANDLE.getOpaque(buffer, base);
			if(owner < 0l || owner == myPID) continue;
			if(owner == 0l){
				// Attempt cleanup of trailing -1/-2 for the last slot before 0
				if(i > 0 && (prevOwner=(long)LONG_HANDLE.getVolatile(buffer, base-SLOT_SIZE)) < 0l) freeSlot(prevOwner, base-SLOT_SIZE);
				return;
			}
			if(now - (long)LONG_HANDLE.getVolatile(buffer, base + TIME_OFFSET) > TIMEOUT_NS){freeSlot(owner, base); continue;}
//			while(((long)LONG_HANDLE.getAcquire(buffer, base + VERSION_OFFSET)&1) == 0) Thread.onSpinWait();
			final long version = (long)LONG_HANDLE.getAcquire(buffer, base + VERSION_OFFSET);
			if((version&1l) == 0l || version == lastReadVersions[i]) continue; // Skip busy or stale
			VarHandle.fullFence();
			buffer.get(base + DATA_OFFSET, data); // Read data
			if(version == (long)LONG_HANDLE.getVolatile(buffer, base + VERSION_OFFSET)){ // Verify data wasn't altered mid-read
				consumer.accept(data);
				lastReadVersions[i] = version;
			}
		}
	}
}