package net.evmodder.evmod.apis;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

public interface TickListener{
	public default void onTickStart(MinecraftClient client){}
	public default void onTickEnd(MinecraftClient client){}

	public static void register(TickListener tickListener){
		try{
			if(!tickListener.getClass().getMethod("onTickStart").getDeclaringClass().equals(TickListener.class))
				ClientTickEvents.START_CLIENT_TICK.register(tickListener::onTickStart);
			if(!tickListener.getClass().getMethod("onTickEnd").getDeclaringClass().equals(TickListener.class))
				ClientTickEvents.END_CLIENT_TICK.register(tickListener::onTickEnd);
		}
		catch(NoSuchMethodException | SecurityException e){
			e.printStackTrace();
		}
	}

	/*public static List<TickListener> tickStartListeners = new ArrayList<>(), tickEndListeners = new ArrayList<>();
	public static boolean addToRegistrationList(TickListener tickListener){
		boolean added = false;
		try{
			if(!tickListener.getClass().getMethod("onTickStart").getDeclaringClass().equals(TickListener.class)) added |= tickStartListeners.add(tickListener);
			if(!tickListener.getClass().getMethod("onTickEnd").getDeclaringClass().equals(TickListener.class)) added |= tickEndListeners.add(tickListener);
		}
		catch(NoSuchMethodException | SecurityException e){
			e.printStackTrace();
		}
		return added;
	}

	public static void registerAll(){
		if(tickStartListeners.isEmpty() && tickEndListeners.isEmpty()){
			// Unreachable, assuming good callers
			throw new RuntimeException("TickListener.registerAll() called without first specifying tick listeners!");
		}
		ClientTickEvents.START_CLIENT_TICK.register(client -> tickStartListeners.forEach(l -> l.onTickStart(client)));
		ClientTickEvents.END_CLIENT_TICK.register(client -> tickEndListeners.forEach(l -> l.onTickStart(client)));
	}*/
}