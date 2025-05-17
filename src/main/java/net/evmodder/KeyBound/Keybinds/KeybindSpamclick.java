package net.evmodder.KeyBound.Keybinds;

import net.evmodder.KeyBound.Main;
import net.evmodder.KeyBound.Keybinds.ClickUtils.ClickEvent;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import java.util.ArrayDeque;

public final class KeybindSpamclick{

	private boolean ongoingSpamClick;
	private long lastPress;
	private final long keybindCooldown = 5*1000;

	private long numClicks = 0, numTicks = 0;

	public KeybindSpamclick(){
		MinecraftClient client = MinecraftClient.getInstance();

		KeyBindingHelper.registerKeyBinding(new EvKeybind("incr_clicks", ()->{
			//if(client.currentScreen instanceof InventoryScreen == false){Main.LOGGER.warn("SpamClickIncrClick: not in InventoryScreen"); return;}
			if(numClicks >= 1000){numClicks = 0; numTicks = 0;}//<<<<<<<<<<<<<<<<<
			client.player.sendMessage(Text.literal("SpamClick: numClicks="+(numClicks+=(Screen.hasShiftDown() ? 1 : 20))+" numTicks="+numTicks), /*overlay=*/false);
		}));

		KeyBindingHelper.registerKeyBinding(new EvKeybind("incr_ticks", ()->{
			//if(client.currentScreen instanceof InventoryScreen == false){Main.LOGGER.warn("SpamClickIncrTicks: not in InventoryScreen"); return;}
			client.player.sendMessage(Text.literal("SpamClick: numClicks="+numClicks+" numTicks="+(numTicks+=(Screen.hasShiftDown() ? 20 : 1))), /*overlay=*/false);
		}));

		KeyBindingHelper.registerKeyBinding(new EvKeybind("spam_click", ()->{
			if(ongoingSpamClick) return;
			if(numClicks == 0 || numTicks == 0) return;
			if(client.currentScreen instanceof InventoryScreen == false){Main.LOGGER.warn("SpamClick: not in InventoryScreen"); return;}
			final long ts = System.currentTimeMillis();
			if(ts - lastPress < keybindCooldown){Main.LOGGER.warn("SpamClick: In cooldown, lastPress:"+lastPress+", ts:"+ts+", ts-lp:"+(ts-lastPress)); return;}
			else lastPress = ts;
			ongoingSpamClick = true;

			client.player.sendMessage(Text.literal("SpamClick: executing "+numClicks+"c in "+numTicks+"t"), /*overlay=*/false);

			ArrayDeque<ClickEvent> clicks = new ArrayDeque<>();
			for(int i=0; i<numClicks; ++i){
				int a = i%4;
				//clicks.add(new ClickEvent(i%2 == 0 ? PlayerScreenHandler.HOTBAR_START : PlayerScreenHandler.INVENTORY_START, 0, SlotActionType.QUICK_MOVE));
				clicks.add(new ClickEvent(a==0||a==3 ? PlayerScreenHandler.HOTBAR_START : PlayerScreenHandler.HOTBAR_START+1, 0, SlotActionType.PICKUP));
			}
			final int msPerClick = (int)((numTicks*50l)/numClicks);
			if(numTicks*50l % numClicks != 0){
				client.player.sendMessage(Text.literal("SpamClick: ms per click is not exact: "+((numTicks*50d)/numClicks)).copy().withColor(16763080), /*overlay=*/false);
			}
			ClickUtils.executeClicksLEGACY(client, clicks, msPerClick, /*MAX_CLICKS_PER_SECOND=*/Integer.MAX_VALUE, _0->true, ()->{
				ongoingSpamClick = false;
				//Main.LOGGER.info("SpamClick: DONE");
				if(client.player != null) client.player.sendMessage(Text.literal("SpamClick: DONE"), /*overlay=*/false);
			});
		}, s->s instanceof InventoryScreen));
	}
}