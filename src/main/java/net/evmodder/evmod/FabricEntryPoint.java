package net.evmodder.evmod;

import net.evmodder.EvLib.util.FileIO;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public class FabricEntryPoint implements ClientModInitializer{
	Main evMod;

	@Override public void onInitializeClient(){
		FileIO.DIR = FabricLoader.getInstance().getConfigDir().toString()+"/"+Main.MOD_ID+"/";
		Main.LOGGER.info("temptemptemptemptemptemptemp: config dir: "+FileIO.DIR);
		evMod = new Main();
	}
}