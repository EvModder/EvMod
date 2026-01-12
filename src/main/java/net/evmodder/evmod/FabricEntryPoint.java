package net.evmodder.evmod;

import net.fabricmc.api.ClientModInitializer;

public class FabricEntryPoint implements ClientModInitializer{
	Main evMod;

	@Override public void onInitializeClient(){
		evMod = new Main();
	}
}