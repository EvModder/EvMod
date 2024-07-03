package net.evmodder;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import net.fabricmc.loader.api.FabricLoader;

public final class FileIO{
	static public String DIR = FabricLoader.getInstance().getConfigDir().toString()+"/";

	public static String loadFile(String filename, InputStream defaultValue){
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

	public static String loadFile(String filename, String defaultContent/*, boolean exactContent*/){
		BufferedReader reader;
		try{reader = new BufferedReader(new FileReader(DIR+filename));}
		catch(FileNotFoundException e){
			if(defaultContent == null || defaultContent.isEmpty()) return defaultContent;

			//Create Directory
			final File dir = new File(DIR);
			if(!dir.exists())dir.mkdir();

			//Create the file
			File conf = new File(DIR+filename);
			try{
				conf.createNewFile();
				BufferedWriter writer = new BufferedWriter(new FileWriter(conf));
				writer.write(defaultContent);
				writer.close();
			}
			catch(IOException e1){e1.printStackTrace();}
			reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(defaultContent.getBytes())));
		}
		final StringBuilder file = new StringBuilder();
		if(reader != null){
			try{
				String line;
				while((line = reader.readLine()) != null){
					line = line.trim().replace("//", "#");
					int cut = line.indexOf('#');
					if(cut == -1) file.append('\n').append(line);
					else if(cut > 0) file.append('\n').append(line.substring(0, cut).trim());
				}
				reader.close();
			}catch(IOException e){}
		}
		return file.length() == 0 ? "" : file.substring(1);//Hmm; return "" or defaultContent
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

	public static boolean deleteFile(String filename){
		return new File(DIR+filename).delete();
	}

	public static boolean moveFile(String oldName, String newName){
		return new File(DIR+oldName).renameTo(new File(DIR+newName));
	}

	public static String loadResource(Object pl, String filename, String defaultContent){
		try{
			InputStream inputStream = pl.getClass().getResourceAsStream("/"+filename);
			if(inputStream == null) inputStream = pl.getClass().getClassLoader().getResourceAsStream("/"+filename);
			final BufferedReader reader = new BufferedReader(inputStream == null
					? new InputStreamReader(new ByteArrayInputStream(defaultContent.getBytes()))
					: new InputStreamReader(inputStream));

			final StringBuilder file = new StringBuilder();
			String line;
			while((line = reader.readLine()) != null){
				line = line.trim().replace("//", "#");
				int cut = /*keepComments ? -1 :*/ line.indexOf('#');
				if(cut == -1) file.append('\n').append(line);
				else if(cut > 0) file.append('\n').append(line.substring(0, cut).trim());
			}
			reader.close();
			return file.substring(1);
		}
		catch(IOException ex){ex.printStackTrace();}
		return defaultContent;
	}
}