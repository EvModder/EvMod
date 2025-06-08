package net.evmodder.KeyBound.Keybinds;

import java.util.Arrays;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Function;
import net.evmodder.KeyBound.Main;
import net.minecraft.client.MinecraftClient;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

public class ClickUtils{
	public record ClickEvent(int slotId, int button, SlotActionType actionType){}
//	public record ClickEvent(int syncId, int slotId, int button, SlotActionType actionType){
//		ClickEvent(int slotId, int button, SlotActionType actionType){this(0, slotId, button, actionType);}
//	}

	public final int MAX_CLICKS;
	private final int[] tickDurationArr;
	private int tickDurIndex, sumClicksInDuration;
	private long lastTick;
	private final int OUTTA_CLICKS_COLOR = 15764490;

	public ClickUtils(final int MAX_CLICKS, int FOR_TICKS){
		if(MAX_CLICKS > 100_000){
			Main.LOGGER.error("InventoryUtils() initialized with insanely-large click-limit: "+MAX_CLICKS+", treating it as limitless");
			this.MAX_CLICKS = Integer.MAX_VALUE;
			tickDurationArr = null;
			return;
		}
		if(FOR_TICKS > 72_000){//1hr irl
			Main.LOGGER.error("InventoryUtils() initialized with insanely-large tick-limiter duration: "+FOR_TICKS+", ignoring and using 72k instead");
			FOR_TICKS = 72_000;
		}
		this.MAX_CLICKS = MAX_CLICKS;
		tickDurationArr = new int[FOR_TICKS];
		lastTick = System.currentTimeMillis()/50l;
	}

	public int addClick(SlotActionType type){
		if(tickDurationArr == null) return 0;
		final long curTick = System.currentTimeMillis()/50l;
		if(curTick - lastTick >= tickDurationArr.length){
			lastTick = curTick;
			Arrays.fill(tickDurationArr, 0);
			sumClicksInDuration = 0;
		}
		while(lastTick != curTick){
			if(++tickDurIndex == tickDurationArr.length) tickDurIndex = 0;
			sumClicksInDuration -= tickDurationArr[tickDurIndex];
			tickDurationArr[tickDurIndex] = 0;
			++lastTick;
		}
		if(type != null){// null is a special flag to update/remove old clicks without adding a new click
			++tickDurationArr[tickDurIndex];
			++sumClicksInDuration;
		}
		return sumClicksInDuration;
	}

	private static boolean waitedForClicks;
	public void executeClicks(Queue<ClickEvent> clicks, Function<ClickEvent, Boolean> canProceed, Runnable onComplete){
		waitedForClicks = false;
		final int syncId = MinecraftClient.getInstance().player.currentScreenHandler.syncId;
		if(clicks.isEmpty()){
			Main.LOGGER.warn("executeClicks() called with an empty ClickEvent list");
			onComplete.run();
			return;
		}
		new Timer().schedule(new TimerTask(){
			@Override public void run(){
				final MinecraftClient client = MinecraftClient.getInstance();
				if(client.player == null){
					Main.LOGGER.error("executeClicks() failed due to null player");
					cancel(); onComplete.run(); return;
				}
				if(client.player.currentScreenHandler.syncId != syncId){
					Main.LOGGER.error("executeClicks() failed due to syncId changing mid-operation ("+syncId+" -> "+client.player.currentScreenHandler.syncId+")");
					cancel(); onComplete.run(); return;
				}
				//final int availableClicks = MAX_CLICKS - addClick(null);
				//for(int i=0; i<availableClicks; ++i){
				while(addClick(null) < MAX_CLICKS){
					if(!canProceed.apply(clicks.peek())) return;
					ClickEvent click = clicks.remove();
					try{
						//Main.LOGGER.info("Executing click: "+click.syncId+","+click.slotId+","+click.button+","+click.actionType);
						client.interactionManager.clickSlot(syncId, click.slotId, click.button, click.actionType, client.player);
					}
					catch(NullPointerException e){
						Main.LOGGER.error("executeClicks() failed due to null client. Clicks left: "+clicks.size()+", sumClicksInDuration: "+sumClicksInDuration);
						clicks.clear();
					}
					if(clicks.isEmpty()){
						if(waitedForClicks) client.player.sendMessage(Text.literal("Clicks done!"), true);
						cancel(); onComplete.run(); return;
					}
				}
				client.player.sendMessage(Text.literal("Waiting for available clicks...").withColor(OUTTA_CLICKS_COLOR), true);
				waitedForClicks = true;
			}
		}, 0l, 51l);
	}


	public static void executeClicksLEGACY(
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
		final int syncId = MinecraftClient.getInstance().player.currentScreenHandler.syncId;
		if(MILLIS_BETWEEN_CLICKS == 0){
			new Timer().schedule(new TimerTask(){
				int clicksInLastSecond = 0;
				int[] clicksInLastSecondArr = new int[20];
				int clicksInLastSecondArrIndex = 0;
				@Override public void run(){
					int clicksThisStep = 0;
					while(clicksInLastSecond < MAX_CLICKS_PER_SECOND && canProceed.apply(clicks.peek())){
						ClickEvent click = clicks.remove();
						try{
							client.interactionManager.clickSlot(syncId, click.slotId, click.button, click.actionType, client.player);
						}
						catch(NullPointerException e){
							Main.LOGGER.error("executeClicks()-MODE:c/ms(array) failure due to null client. Clicks left: "+clicks.size());
							clicks.clear();
						}
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
				try{
					client.interactionManager.clickSlot(syncId, click.slotId, click.button, click.actionType, client.player);
				}
				catch(NullPointerException e){
					Main.LOGGER.error("executeClicks()-MODE:c/ms(simple) failure due to null client. Clicks left: "+clicks.size());
					clicks.clear();
				}
			}}, 0l, MILLIS_BETWEEN_CLICKS);
		}
		else new Timer().schedule(new TimerTask(){
			int clicksInLastSecond = 0;
			boolean[] clicksInLastSecondArr = new boolean[Math.ceilDiv(1000, MILLIS_BETWEEN_CLICKS)];
			int clicksInLastSecondArrIndex = 0;
			@Override public void run(){
				if(clicksInLastSecond < MAX_CLICKS_PER_SECOND && canProceed.apply(clicks.peek())){
					ClickEvent click = clicks.remove();
					//Main.LOGGER.info("click: "+click.syncId+","+click.slotId+","+click.button+","+click.actionType);
					try{
						client.interactionManager.clickSlot(syncId, click.slotId, click.button, click.actionType, client.player);
					}
					catch(NullPointerException e){
						Main.LOGGER.error("executeClicks()-MODE:ms/c failure due to null client. Clicks left: "+clicks.size());
						clicks.clear();
					}
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