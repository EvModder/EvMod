package net.evmodder.KeyBound.Keybinds;

import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Function;
import net.evmodder.KeyBound.Main;
import net.minecraft.client.MinecraftClient;
import net.minecraft.screen.slot.SlotActionType;

public class InventoryUtils{
	record ClickEvent(int syncId, int slotId, int button, SlotActionType actionType){
		ClickEvent(int slotId, int button, SlotActionType actionType){this(0, slotId, button, actionType);}
	}

	public static void executeClicks(
			MinecraftClient client,
			Queue<ClickEvent> clicks, final int MILLIS_BETWEEN_CLICKS, final int MAX_CLICKS_PER_SECOND,
			Function<ClickEvent, Boolean> canProceed, Runnable onComplete)
	{
		if(clicks.isEmpty()){
			Main.LOGGER.warn("executeClicks() called with an empty ClickEvent list");
			onComplete.run();
			return;
		}
		if(MAX_CLICKS_PER_SECOND < 1 || MILLIS_BETWEEN_CLICKS < 0){
			Main.LOGGER.error("Invalid settings! clicks_per_second cannot be < 1 and millis_between clicks cannot be < 0");
			return;
		}
		if(MILLIS_BETWEEN_CLICKS == 0){
			new Timer().schedule(new TimerTask(){
				int clicksInLastSecond = 0;
				int[] clicksInLastSecondArr = new int[20];
				int clicksInLastSecondArrIndex = 0;
				@Override public void run(){
					int clicksThisStep = 0;
					while(clicksInLastSecond < MAX_CLICKS_PER_SECOND && canProceed.apply(clicks.peek())){
						ClickEvent click = clicks.remove();
						client.interactionManager.clickSlot(click.syncId, click.slotId, click.button, click.actionType, client.player);
						if(clicks.isEmpty()){cancel(); onComplete.run(); return;}
						++clicksThisStep;
						++clicksInLastSecond;
					}
					clicksInLastSecondArr[clicksInLastSecondArrIndex] = clicksThisStep;
					if(++clicksInLastSecondArrIndex == clicksInLastSecondArr.length) clicksInLastSecondArrIndex = 0;
					clicksInLastSecond -= clicksInLastSecondArr[clicksInLastSecondArrIndex];
				}
			}, 0l, 50l);
		}
		else if(MILLIS_BETWEEN_CLICKS > 1000){
			new Timer().schedule(new TimerTask(){@Override public void run(){
				if(clicks.isEmpty()){cancel(); onComplete.run(); return;}
				if(!canProceed.apply(clicks.peek())) return;
				ClickEvent click = clicks.remove();
				client.interactionManager.clickSlot(click.syncId, click.slotId, click.button, click.actionType, client.player);
			}}, 0l, MILLIS_BETWEEN_CLICKS);
		}
		else new Timer().schedule(new TimerTask(){
			int clicksInLastSecond = 0;
			boolean[] clicksInLastSecondArr = new boolean[Math.ceilDiv(1000, MILLIS_BETWEEN_CLICKS)];
			int clicksInLastSecondArrIndex = 0;
			@Override public void run(){
				if(clicksInLastSecond < MAX_CLICKS_PER_SECOND && canProceed.apply(clicks.peek())){
					ClickEvent click = clicks.remove();
					client.interactionManager.clickSlot(click.syncId, click.slotId, click.button, click.actionType, client.player);
					if(clicks.isEmpty()){cancel(); onComplete.run(); return;}
					++clicksInLastSecond;
					clicksInLastSecondArr[clicksInLastSecondArrIndex] = true;
				}
				if(++clicksInLastSecondArrIndex == clicksInLastSecondArr.length) clicksInLastSecondArrIndex = 0;
				if(clicksInLastSecondArr[clicksInLastSecondArrIndex]) --clicksInLastSecond;
			}
		}, 0l, MILLIS_BETWEEN_CLICKS);
	}
}