package net.evmodder.evmod.apis;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;

public final class JavaIPC{
	private static final class Holder{private static final JavaIPC INSTANCE = new JavaIPC();}
	public static final JavaIPC getInstance(){return Holder.INSTANCE;}

	// Hopefully nobody is running more than this many Minecraft accounts on 1 device...
	private static final int MAX_SLOTS = 512;
	// UUID + serverHashCode + worldHashCode + x + y + z
	private static final int DATA_SIZE = 16 + 8 + 8 + 8 + 4 + 4;
	// PID + TS + lock + data
	private static final int SLOT_SIZE = 8 + 4 + 4 + DATA_SIZE;
	// 15 seconds to consider an instance "dead"
	private static final int TIMEOUT_MS = 15_000;

	// Offsets within a slot
	private static final int PID_OFFSET = 0;
	private static final int TIME_OFFSET = 4;
	private static final int VERSION_OFFSET = 8;
	private static final int DATA_OFFSET = 12;

	private static final VarHandle LONG_HANDLE = MethodHandles.byteBufferViewVarHandle(long[].class, ByteOrder.nativeOrder());
	private static final VarHandle INT_HANDLE = MethodHandles.byteBufferViewVarHandle(int[].class, ByteOrder.nativeOrder());

	private static final long myPID = ProcessHandle.current().pid();
	private static final ByteBuffer bb = ByteBuffer.allocate(DATA_SIZE);
	private final MappedByteBuffer buffer;
	private int mySlot;

	private JavaIPC(){
		final File file = new File(System.getProperty("java.io.tmpdir"), "minecraft_player_pos.dat");
		MappedByteBuffer temp = null;
		try(final RandomAccessFile raf = new RandomAccessFile(file, "rw")){
			final long TOTAL_SIZE = (long) MAX_SLOTS*SLOT_SIZE;
			if(raf.length() < TOTAL_SIZE) raf.setLength(TOTAL_SIZE);
			temp = raf.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, TOTAL_SIZE);
		}
		catch(IOException e){
			System.err.println("[EvMod] CRITICAL: Failed to initialize shared memory file");
			e.printStackTrace();
			temp = null;
			return;
		}
		finally{buffer = temp;}
		Runtime.getRuntime().addShutdownHook(new Thread(()->{
			// Mark owner PID as -1 so others can take it immediately (rather than waiting for heartbeat)
			if(buffer != null && myPID == (long)LONG_HANDLE.getVolatile(buffer, mySlot*SLOT_SIZE + PID_OFFSET)){
				LONG_HANDLE.compareAndSet(buffer, mySlot*SLOT_SIZE + PID_OFFSET, myPID, -1l);
			}
		}));
	}

	private record PosData(int server, int world, double x, double y, double z){}
	public final ArrayList<PosData> readPeerData(){
		final ArrayList<PosData> peerData = new ArrayList<>();
		final int now = (int)System.currentTimeMillis();
//		int idx = 0;
		for(int i=0; i<MAX_SLOTS; ++i){
			final int base = i*SLOT_SIZE;
			final long owner = (long)LONG_HANDLE.getVolatile(buffer, base + PID_OFFSET);
			if(owner < 0 || owner == myPID) continue;
			if(owner == 0) return peerData;
			if(now - (int)INT_HANDLE.getVolatile(buffer, base + TIME_OFFSET) > TIMEOUT_MS){
				LONG_HANDLE.compareAndSet(buffer, base + PID_OFFSET, owner, -1l);
				continue;
			}
//			while(((long)LONG_HANDLE.getAcquire(buffer, base + VERSION_OFFSET)&1) == 0) Thread.onSpinWait();
			final long version = (long)LONG_HANDLE.getAcquire(buffer, base + VERSION_OFFSET); // Snapshot the data version
			if((version&1) == 0) continue; // Even = data is actively being written (not safe to read) 
			buffer.get(base + DATA_OFFSET, bb.array()); // Read data
			if((long)LONG_HANDLE.getVolatile(buffer, base + VERSION_OFFSET) == version){ // Ensure data wasn't changed mid-read
//				peerData[idx++] = data;
				peerData.add(new PosData(bb.getInt(), bb.getInt(), bb.getDouble(), bb.getDouble(), bb.getDouble()));
				bb.rewind();
			}
		}
//		// peerData is usually returned far earlier, due to encountering any ownerPID==0.
//		return idx == MAX_SLOTS ? peerData : Arrays.copyOfRange(peerData, 0, idx);
		return peerData;
	}

	private final int claimSlot(){
		final int now = (int)System.currentTimeMillis();
		for(int i=0; i<MAX_SLOTS; ++i){
			final int base = i*SLOT_SIZE;
			final long owner = (long)LONG_HANDLE.getVolatile(buffer, base + PID_OFFSET);
			final int lastHeartbeat = (int)INT_HANDLE.getVolatile(buffer, base + TIME_OFFSET);
			if(owner > 0 && now - lastHeartbeat < TIMEOUT_MS) continue;
			LONG_HANDLE.setVolatile(buffer, base + TIME_OFFSET, now); // Update ts (reduces contention fighting for this slot)
			if(LONG_HANDLE.compareAndSet(buffer, base + PID_OFFSET, owner, myPID)) return i; // Nice, we snagged this slot!
			i=-1; // Another PID grabbed the slot before us; start again from i=0.
		}
		assert false : "shared-mem table is out of slots!";
		return -1; // Failed to acquire a slot!!
	}

	public final void postMyData(final byte[] data){
		assert data.length == DATA_SIZE;
		// Ensure my slot is still valid (and update it if not).
		if(myPID != (long)LONG_HANDLE.getVolatile(buffer, mySlot*SLOT_SIZE + PID_OFFSET) && (mySlot=claimSlot()) == -1) return;
		final int base = mySlot*SLOT_SIZE;
		final long currentVer = (long)LONG_HANDLE.get(buffer, base + VERSION_OFFSET); // Get current version
		final long nextEvenVer = (currentVer+1)&-2;
		LONG_HANDLE.setRelease(buffer, base + VERSION_OFFSET, nextEvenVer); // Move current version to EVEN (signals write in-progress)
		LONG_HANDLE.setVolatile(buffer, base + TIME_OFFSET, System.currentTimeMillis()); // Heartbeat
		buffer.put(base + DATA_OFFSET, data); // Publish data
//		buffer.position(base + DATA_OFFSET).put(data); // Publish data
		LONG_HANDLE.setRelease(buffer, base + VERSION_OFFSET, nextEvenVer + 1); // Move current version to ODD (signals write completed)
	}
}