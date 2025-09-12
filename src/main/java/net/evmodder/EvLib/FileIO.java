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
			tempDir = fabricLoader.getClass().getMethod("getConfigDir").invoke(fabricLoader).toString()+"/"+
						Class.forName("net.evmodder.KeyBound.Main").getField("MOD_ID").get(null)+"/";
		}
		catch(IllegalArgumentException | NoSuchFieldException e){e.printStackTrace();}
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
	public static final byte[] loadFileBytes(String filename){
		try{
			final FileInputStream fis = new FileInputStream(FileIO.DIR+filename);
			final byte[] data = fis.readAllBytes();
			fis.close();
			return data;
		}
		catch(FileNotFoundException e){return null;}
		catch(IOException e){e.printStackTrace(); return null;}
	}
	public static final boolean saveFileBytes(String filename, byte[] data){
		File file = new File(FileIO.DIR+filename);
		FileOutputStream fos;
		try{
			try{fos = new FileOutputStream(file);}
			catch(FileNotFoundException e){
				file.createNewFile();
				fos = new FileOutputStream(file);
			}
			fos.write(data);
			fos.close();
		}
		catch(IOException e){e.printStackTrace(); return false;}
		return true;
	}

	public static boolean saveFile(String filename, String content, boolean append){
		if(content == null || content.isEmpty()) return new File(DIR+filename).delete();
		try{
			final BufferedWriter writer = new BufferedWriter(new FileWriter(DIR+filename, append));
			writer.write(content); writer.close();
			return true;
		}
		catch(IOException e){return false;}
	}
	public static boolean saveFile(String filename, String content){
		return saveFile(filename, content, /*append=*/false);
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

	public static final synchronized boolean appendToClientFile(String filename, UUID pearlUUID, PearlDataClient pdc){
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
			bb.putLong(pdc.owner().getMostSignificantBits());
			bb.putLong(pdc.owner().getLeastSignificantBits());
			bb.putInt(pdc.x()).putInt(pdc.y()).putInt(pdc.z());
			fos.write(bb.array());
			fos.close();
			LOGGER.fine("saved pearlUUID->ownerUUID to file: "+pearlUUID+"->"+pdc.owner());
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

	public static final synchronized HashSet<UUID> removeMissingFromClientFile(String filename, int playerX, int playerY, int playerZ,
			double affectedDistSq, HashSet<UUID> keep){
		FileInputStream is = null;
		try{is = new FileInputStream(FileIO.DIR+filename);}
		catch(FileNotFoundException e){/*e.printStackTrace(); */return null;}
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

			final double diffX = playerX-x, diffY = playerY-y, diffZ = playerZ-z; // Intentional use of double (to avoid overflow)
			final double distSq = diffX*diffX + diffY*diffY + diffZ*diffZ;
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