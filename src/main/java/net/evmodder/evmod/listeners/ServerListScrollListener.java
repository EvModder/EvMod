package net.evmodder.evmod.listeners;

import java.util.function.DoubleConsumer;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.evmodder.evmod.mixin.AccessorJoinMultiplayerScreen;

public final class ServerListScrollListener{
	private double scroll;
	private ServerSelectionList activeList;
	private final DoubleConsumer save;
	private final BooleanSupplier enabled;

	private void capture(){if(activeList != null && enabled.getAsBoolean()) scroll = activeList.scrollAmount();}
	private void persist(){
		if(activeList != null && enabled.getAsBoolean()){
			scroll = activeList.scrollAmount();
			save.accept(scroll);
		}
		activeList = null;
	}

	public ServerListScrollListener(double initialScroll, DoubleConsumer save, BooleanSupplier enabled){
		scroll = initialScroll;
		this.save = save;
		this.enabled = enabled;
		// Capture before resize/reinitialization can clamp or replace the old list.
		ScreenEvents.BEFORE_INIT.register((mc, screen, width, height) -> capture());
		ScreenEvents.AFTER_INIT.register((mc, screen, width, height) -> {
			if(!(screen instanceof JoinMultiplayerScreen)) return;
			activeList = ((AccessorJoinMultiplayerScreen)screen).getServerSelectionList();
			if(enabled.getAsBoolean()) activeList.setScrollAmount(scroll);
			ScreenEvents.remove(screen).register(closed -> persist());
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> persist());
	}
}