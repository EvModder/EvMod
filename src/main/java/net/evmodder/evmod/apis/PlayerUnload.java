package net.evmodder.evmod.apis;

import java.util.ArrayList;
import java.util.function.Consumer;
import net.evmodder.evmod.Main;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Runs listeners on the client thread right before a disconnect or reconfiguration clears the player and level.
 * Fabric's ClientPlayConnectionEvents.DISCONNECT usually fires later, on the Netty thread, and vanilla drops
 * client tasks queued while disconnecting; use this for anything that reads or resets client-thread state.
 */
public final class PlayerUnload{
	private static final ArrayList<Consumer<LocalPlayer>> listeners = new ArrayList<>();

	private PlayerUnload(){}

	public static void register(final Consumer<LocalPlayer> listener){listeners.add(listener);}

	/** For listeners: false when the player is only unloading for a reconfiguration, such as a proxy switching backends. */
	public static boolean isDisconnect(final LocalPlayer player){return !player.connection.isAcceptingMessages();}

	// Called by: MixinClientPacketListenerUnload
	public static void fire(){
		final LocalPlayer player = Minecraft.getInstance().player;
		if(player == null) return;  // Already unloaded: a disconnect after reconfiguration clears the level twice.
		for(final Consumer<LocalPlayer> listener : listeners){
			try{listener.accept(player);}
			catch(RuntimeException e){Main.LOGGER.error("PlayerUnload listener failed", e);}  // Never interrupt vanilla's teardown.
		}
	}
}