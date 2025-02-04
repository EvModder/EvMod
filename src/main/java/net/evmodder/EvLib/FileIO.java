package net.evmodder.EvLib;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;
import java.util.logging.Logger;

public final class FileIO{
	private static final Logger LOGGER;
	public static final String DIR;//= FabricLoader.getInstance().getConfigDir().toString()+"/";
	static{
		String tempDir = "./";
		try{
			Object fabricLoader = Class.forName("net.fabricmc.loader.api.FabricLoader").getMethod("getInstance").invoke(null);
			tempDir = fabricLoader.getClass().getMethod("getConfigDir").invoke(fabricLoader).toString()+"/";
		}
		catch(ClassNotFoundException | IllegalAccessException | InvocationTargetException | NoSuchMethodException | SecurityException e){}
		DIR = tempDir;

		Logger tempLogger = Logger.getLogger("EvLibMod-FileIO");
		try{tempLogger = (Logger)Class.forName("net.evmodder.ServerMain").getField("LOGGER").get(null);}
		catch(IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException | ClassNotFoundException e){}
		LOGGER = tempLogger;
	}

	public static final String loadFile(String filename, String defaultValue){
		BufferedReader reader = null;
		try{reader = new BufferedReader(new FileReader(DIR+filename));}
		catch(FileNotFoundException e){
			if(defaultValue == null) return null;

			//Create Directory
			final File dir = new File(DIR);
			if(!dir.exists())dir.mkdir();

			//Create the file
			final File conf = new File(DIR+filename);
			try{
				conf.createNewFile();
				BufferedWriter writer = new BufferedWriter(new FileWriter(conf));
				writer.write(defaultValue); writer.close();
				reader = new BufferedReader(new FileReader(DIR+filename));
			}
			catch(IOException e1){e1.printStackTrace();}
		}
		final StringBuilder file = new StringBuilder();
		if(reader != null){
			try{
				String line = reader.readLine();
				while(line != null){
					line = line.trim().replace("//", "#");
					int cut = line.indexOf('#');
					if(cut == -1) file.append('\n').append(line);
					else if(cut > 0) file.append('\n').append(line.substring(0, cut).trim());
					line = reader.readLine();
				}
				reader.close();
			}catch(IOException e){}
		}
		return file.length() == 0 ? "" : file.substring(1);
	}
	public static final String loadFile(String filename, InputStream defaultValue){
		BufferedReader reader = null;
		try{reader = new BufferedReader(new FileReader(DIR+filename));}
		catch(FileNotFoundException e){
			if(defaultValue == null) return null;

			//Create Directory
			final File dir = new File(DIR);
			if(!dir.exists())dir.mkdir();

			//Create the file
			final File conf = new File(DIR+filename);
			try{
				conf.createNewFile();
				reader = new BufferedReader(new InputStreamReader(defaultValue));

				String line = reader.readLine();
				StringBuilder builder = new StringBuilder(line);
				while((line = reader.readLine()) != null) builder.append('\n').append(line);
				reader.close();

				BufferedWriter writer = new BufferedWriter(new FileWriter(conf));
				writer.write(builder.toString()); writer.close();
				reader = new BufferedReader(new FileReader(DIR+filename));
			}
			catch(IOException e1){e1.printStackTrace();}
		}
		final StringBuilder file = new StringBuilder();
		if(reader != null){
			try{
				String line = reader.readLine();
				while(line != null){
					line = line.trim().replace("//", "#");
					int cut = line.indexOf('#');
					if(cut == -1) file.append('\n').append(line);
					else if(cut > 0) file.append('\n').append(line.substring(0, cut).trim());
					line = reader.readLine();
				}
				reader.close();
			}catch(IOException e){}
		}
		return file.length() == 0 ? "" : file.substring(1);
	}

	/*public static final UUID lookupInFile(String filename, UUID pearlUUID){
		FileInputStream is = null;
		try{is = new FileInputStream(FileIO.DIR+filename);}
		catch(FileNotFoundException e){return null;}
		final byte[] data;
		try{data = is.readAllBytes(); is.close();}
		catch(IOException e){e.printStackTrace(); return null;}
		if(data.length % 32 != 0){
			LOGGER.severe("Corrupted/invalid ePearlDB file!");
			return null;
		}
		final ByteBuffer bb = ByteBuffer.wrap(data);
		int lo = 0, hi = data.length/32;
		final long k = pearlUUID.getMostSignificantBits();
		while(hi-lo > 1){
			int m = (lo + hi)/2;
			long v = bb.getLong(m*32);
			if(v > k) hi = m;
			else lo = m;
		}
		final UUID keyUUID = new UUID(bb.getLong(lo*32), bb.getLong(lo*32+8));
		if(!keyUUID.equals(pearlUUID)){
			LOGGER.fine("pearlUUID not found in localDB file: "+pearlUUID);
			return null;
		}
		return new UUID(bb.getLong(lo*32+16), bb.getLong(lo*32+24));
	}*/

	public static final boolean saveToServerFile(String filename, HashMap<UUID/*pearl*/, PearlData> data){
		File file = new File(FileIO.DIR+filename);
		try{
			FileOutputStream fos = null;
			try{fos = new FileOutputStream(file);}
			catch(FileNotFoundException e){
				LOGGER.info("ePearlDB file not found, creating it");
				file.createNewFile();
				fos = new FileOutputStream(file, true);
			}
			ByteBuffer bb = ByteBuffer.allocate(16+16+4+8+8);
			for(var e : data.entrySet()){
				bb.clear();
				bb.putLong(e.getKey().getMostSignificantBits());
				bb.putLong(e.getKey().getLeastSignificantBits());
				bb.putLong(e.getValue().owner().getMostSignificantBits());
				bb.putLong(e.getValue().owner().getLeastSignificantBits());
				bb.putInt(e.getValue().submittedBy());
				bb.putLong(e.getValue().created());
				bb.putLong(e.getValue().lastAccessed());
				fos.write(bb.array());
			}
			fos.close();
		}
		catch(IOException e){e.printStackTrace();return false;}
		return true;
	}

	public static final HashMap<UUID, PearlData> loadFromServerFile(String filename){
		final byte[] data;
		try{
			FileInputStream fis = new FileInputStream(FileIO.DIR+filename);
			data = fis.readAllBytes();
			fis.close();
		}
		//catch(FileNotFoundException e){return new HashMap<>();}
		catch(IOException e){
			LOGGER.warning("DB file not found");
			e.printStackTrace();
			return new HashMap<>();
		}
		if(data.length % 52 != 0){
			LOGGER.severe("Corrupted/invalid ePearlDB file!");
			return new HashMap<>();
		}
		final int numRows = data.length/52;
		final ByteBuffer bb = ByteBuffer.wrap(data);
		HashMap<UUID, PearlData> entries = new HashMap<>(numRows);
		for(int i=0; i<numRows; ++i){
			UUID pearlKey = new UUID(bb.getLong(), bb.getLong());
			UUID owningPlayer = new UUID(bb.getLong(), bb.getLong());
			int submittedByClientId = bb.getInt();
			long createdTs = bb.getLong();
			long accessedTs = bb.getLong();
			entries.put(pearlKey, new PearlData(owningPlayer, submittedByClientId, createdTs, accessedTs));
		}
		return entries;
	}

	// element size = 16+16+4+4 = 40
	/*public static final Tuple3<UUID, Integer, Integer> lookupInClientFile(String filename, UUID pearlUUID){
		FileInputStream is = null;
		try{is = new FileInputStream(FileIO.DIR+filename);}
		catch(FileNotFoundException e){return null;}
		final byte[] data;
		try{data = is.readAllBytes(); is.close();}
		catch(IOException e){e.printStackTrace(); return null;}
		if(data.length % 40 != 0){
			LOGGER.severe("Corrupted/invalid ePearlDB file!");
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
			LOGGER.fine("pearlUUID not found in localDB file: "+pearlUUID);
			return null;
		}
		final UUID ownerUUID = new UUID(bb.getLong(i+16), bb.getLong(i+24));
		final int x = bb.getInt(i+32), z = bb.getInt(i+36);
		return new Tuple3<>(ownerUUID, x, z);
	}*/

	public static final synchronized boolean appendToClientFile(String filename, UUID pearlUUID, UUID ownerUUID, int x, int y, int z){
		File file = new File(FileIO.DIR+filename);
		try{
			FileOutputStream fos = null;
			try{fos = new FileOutputStream(file, true);}
			catch(FileNotFoundException e){
				LOGGER.info("ePearlDB file not found, creating one");
				file.createNewFile();
				fos = new FileOutputStream(file, true);
			}
			ByteBuffer bb = ByteBuffer.allocate(16+16+4+4+4);
			bb.putLong(pearlUUID.getMostSignificantBits());
			bb.putLong(pearlUUID.getLeastSignificantBits());
			bb.putLong(ownerUUID.getMostSignificantBits());
			bb.putLong(ownerUUID.getLeastSignificantBits());
			bb.putInt(x).putInt(y).putInt(z);
			fos.write(bb.array());
			fos.close();
			LOGGER.fine("saved ownerUUID to file: "+ownerUUID);
		}
		catch(IOException e){e.printStackTrace();return false;}
		return true;
	}

	public static final synchronized HashMap<UUID, PearlDataClient> loadFromClientFile(String filename){
		final byte[] data;
		try{
			FileInputStream fis = new FileInputStream(FileIO.DIR+filename);
			data = fis.readAllBytes();
			fis.close();
		}
		catch(FileNotFoundException e){
			LOGGER.warning("DB file not found, attempting to create it");
			try{new File(filename).createNewFile();} catch(IOException e1){e1.printStackTrace();}
			return new HashMap<>();
		}
		catch(IOException e){
			e.printStackTrace();
			return new HashMap<>();
		}
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
			LOGGER.severe("Corrupted/invalid ePearlDB file!");
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

	public static final synchronized HashSet<UUID> removeMissingFromClientFile(String filename, int playerX, int playerY, int playerZ, double affectedDistSq, HashSet<UUID> keep){
		FileInputStream is = null;
		try{is = new FileInputStream(FileIO.DIR+filename);}
		catch(FileNotFoundException e){e.printStackTrace(); return null;}
		final byte[] data;
		try{data = is.readAllBytes(); is.close();}
		catch(IOException e){e.printStackTrace(); return null;}
		if(data.length % 44 != 0){
			LOGGER.severe("Corrupted/invalid ePearlDB file!");
			return null;
		}
		final ByteBuffer bbIn = ByteBuffer.wrap(data);
		final ByteBuffer bbOut = ByteBuffer.allocate(data.length);
		final HashSet<UUID> deletedKeys = new HashSet<>();
		int kept = 0;
		while(bbIn.hasRemaining()){
			final long k1 = bbIn.getLong(), k2 = bbIn.getLong();//16
			final long o1 = bbIn.getLong(), o2 = bbIn.getLong();//16
			final int x = bbIn.getInt(), y = bbIn.getInt(), z = bbIn.getInt();//4+4+4

			final double distSq = (playerX-x)*(playerX-x) + (playerY-y)*(playerY-y) + (playerZ-z)*(playerZ-z);
			if(distSq < affectedDistSq){
				final UUID key = new UUID(k1, k2);
				if(!keep.contains(key)){deletedKeys.add(key); continue;}
			}
			//else
			++kept;
			bbOut.putLong(k1).putLong(k2).putLong(o1).putLong(o2).putInt(x).putInt(z);
		}
		if(kept*44 == data.length) return deletedKeys; // Nothing was deleted

		final byte[] rowsLeft = new byte[kept*44];
		bbOut.get(0, rowsLeft);
		try{
			FileOutputStream fos = new FileOutputStream(FileIO.DIR+filename);
			fos.write(rowsLeft);
			fos.close();
		}
		catch(IOException e){e.printStackTrace(); deletedKeys.clear();}
		return deletedKeys;
	}
}