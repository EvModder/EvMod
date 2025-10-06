package net.evmodder.EvLib;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLConnection;

public class WebUtils{
	public static final String getReadURL(String post){
		try{
			URLConnection connection = URI.create(post).toURL().openConnection();
			//conn.setRequestMethod("GET");
			connection.setUseCaches(false);
			connection.setDoOutput(true);
			connection.setDoInput(true);

			BufferedReader rd = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			StringBuilder resp = new StringBuilder();
			String line = null;
			while ((line=rd.readLine()) != null) resp.append(line.replace(" ", ""));
			rd.close();
			return resp.length() > 0 ? resp.substring(1) : null;
		}
		catch(IOException e){
			e.printStackTrace();
			return null;
		}
	}
}