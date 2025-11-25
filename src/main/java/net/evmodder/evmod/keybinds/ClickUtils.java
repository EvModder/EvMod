package net.evmodder.evmod.keybinds;

import java.util.Arrays;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.evmodder.EvLib.util.TextUtils_New;
import net.evmodder.evmod.Main;
import net.evmodder.evmod.mixin.AccessorPlayerListHud;
import net.minecraft.client.MinecraftClient;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

public class ClickUtils{
	public record ClickEvent(int slotId, int button, SlotActionType actionType){}

	public final int MAX_CLICKS;
	private final int[] tickDurationArr;
	private int tickDurIndex, sumClicksInDuration;
	private long lastTick;
	private final int OUTTA_CLICKS_COLOR = 15764490, SYNC_ID_CHANGED_COLOR = 16733525;
//	private final double C_PER_T;
	public static long TICK_DURATION = 51l; // LOL!! TODO: estimate base off TPS/ping

	private boolean thisClickIsBotted;
	public boolean isThisClickBotted(/*MixinClientPlayerInteractionManager.Friend friend*/){return thisClickIsBotted;}

	public ClickUtils(final int MAX_CLICKS, int FOR_TICKS){
		if(MAX_CLICKS >= 100_000 || MAX_CLICKS <= 0){
			if(MAX_CLICKS != 0) Main.LOGGER.error("InventoryUtils() initialized with "
					+(MAX_CLICKS < 0 ? "invalid" : "insanely-large")+" click-limit: "+MAX_CLICKS+", treating it as limitless");
			this.MAX_CLICKS = Integer.MAX_VALUE;
//			C_PER_T = Double.MAX_VALUE;
			tickDurationArr = null;
			return;
		}
		assert FOR_TICKS > 0;
		if(FOR_TICKS > 72_000){//1hr irl
			Main.LOGGER.error("InventoryUtils() initialized with insanely-large tick-limiter duration: "+FOR_TICKS+", ignoring and using 72k instead");
			FOR_TICKS = 72_000;
		}
		this.MAX_CLICKS = MAX_CLICKS;
//		C_PER_T = (double)MAX_CLICKS/(double)FOR_TICKS;
		tickDurationArr = new int[FOR_TICKS];
//		lastTick = System.currentTimeMillis()/TICK_DURATION; // Get recomputed by calcAvailableClicks() anyway
	}

//	private long getPing(){
//		ServerInfo serverInfo = MinecraftClient.getInstance().getCurrentServerEntry();
//		return serverInfo == null ? 0 : serverInfo.ping; 
//	}

	public int calcAvailableClicks(){
		if(tickDurationArr == null) return MAX_CLICKS;
		final long curTick = System.currentTimeMillis()/TICK_DURATION;
		if(curTick != lastTick){
//			final long pingTicks = (long)Math.ceil(getPing()/(double)TICK_DURATION);
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
		}
		return MAX_CLICKS - sumClicksInDuration;
	}
	public void addClick(SlotActionType type){ // TODO: friend MixinClientPlayerInteractionManager?
		assert type != null; //TODO: type is unused
		if(tickDurationArr == null) return;
//		calcAvailableClicks(); // TODO: anywhere addClick() is called, calcAvailableClicks() MUST be called immediately before
		++tickDurationArr[tickDurIndex];
		++sumClicksInDuration;
	}

	private void adjustTickRate(long msPerTick){
		// If TPS is degrading, don't clear old tick data (TODO: this isn't a perfect solution by any means)
		if(msPerTick > TICK_DURATION) lastTick = System.currentTimeMillis()/TICK_DURATION;
		else calcAvailableClicks();
		TICK_DURATION = msPerTick;
		lastTick = System.currentTimeMillis()/TICK_DURATION;
	}

	private int calcRemainingTicks(int clicksToExecute){
//		final int unusedCapacity = calcAvailableClicks();
//		final double C_PER_T = (double)(MAX_CLICKS-unusedCapacity)/(double)tickDurationArr.length;
//		int ticksLeft = 0;
//		int simTickDurIndex = tickDurIndex;
//		for(int i=0; clicksToExecute > 0; ++i){ // Do 1 loop around the array
//			if(++simTickDurIndex == tickDurationArr.length) simTickDurIndex = 0;
//			clicksToExecute -= tickDurationArr[simTickDurIndex];
//			++ticksLeft;
//		}
//		ticksLeft += Math.ceil(clicksToExecute/C_PER_T);
//		return ticksLeft;
		int ticksIntoFuture;
		for(ticksIntoFuture = 1; clicksToExecute > 0; ++ticksIntoFuture){
			clicksToExecute -= tickDurationArr[(tickDurIndex + ticksIntoFuture) % tickDurationArr.length];
		}
		return ticksIntoFuture;
	}

	final Pattern tpsPattern = Pattern.compile("(\\d{1,2}(?:\\.\\d+))\\s?tps", Pattern.CASE_INSENSITIVE);
	private long /*getTPS*/getMillisPerTick(MinecraftClient client){
		// Alternative: client.getNetworkHandler().onPlayerListHeader(PlayerListHeaderS2CPacket plhp)

		final AccessorPlayerListHud playerListHudAccessor = (AccessorPlayerListHud)client.inGameHud.getPlayerListHud();
		final Text footerText = playerListHudAccessor.getFooter();
		if(footerText == null) return TICK_DURATION;
		final MutableText text = Text.empty(); footerText.withoutStyle().forEach(text::append);
		final String footerStr = TextUtils_New.stripColorAndFormats(text.getString());
		//§819.90 tps — 692 players online — 92 ping
		final Matcher matcher = tpsPattern.matcher(footerStr);
		if(!matcher.find()) return TICK_DURATION;
		final double tps = Double.parseDouble(matcher.group(1));
//		Main.LOGGER.info("ClickUtils: got TPS from playerListTab: "+tps);
		final long msPerTick = (long)Math.ceil(1000d/tps);
		return Math.max(50, msPerTick); // Even if TPS>20, let's play it safe since packet-limiters might use real-time
	}

	private boolean clickOpOngoing/*, waitedForClicks*/;
	private int estimatedMsLeft;
	public final boolean hasOngoingClicks(){return clickOpOngoing;}
	public final void executeClicks(Queue<ClickEvent> clicks, Function<ClickEvent, Boolean> canProceed, Runnable onComplete){
		final MinecraftClient client = MinecraftClient.getInstance();
		if(clickOpOngoing){
			Main.LOGGER.warn("executeClicks() already has an ongoing operation");
			client.player.sendMessage(Text.literal("Clicks cancelled: current operation needs to finish before starting a new one"), true);
			onComplete.run();
			return;
		}
		final int syncId = client.player.currentScreenHandler.syncId;
		if(clicks.isEmpty()){
			Main.LOGGER.warn("executeClicks() called with an empty ClickEvent list");
			onComplete.run();
			return;
		}
		if(tickDurationArr != null){
			final long msPerTick = getMillisPerTick(client);
			if(msPerTick != TICK_DURATION) adjustTickRate(msPerTick);
		}

		estimatedMsLeft = Integer.MAX_VALUE;
		clickOpOngoing = true;
		new Timer().schedule(new TimerTask(){@Override public void run(){
			if(client.player == null){
				Main.LOGGER.error("executeClicks() failed due to null player! num clicks in arr: "+sumClicksInDuration);
				cancel(); clickOpOngoing=false; onComplete.run(); return;
			}
			if(client.player.currentScreenHandler.syncId != syncId){
				Main.LOGGER.error("executeClicks() failed due to syncId changing mid-operation ("+syncId+" -> "+client.player.currentScreenHandler.syncId+")");
				client.player.sendMessage(Text.literal("Clicks cancelled: container ID changed").withColor(SYNC_ID_CHANGED_COLOR), true);
				cancel(); clickOpOngoing=false; onComplete.run(); return;
			}
			if(clicks.isEmpty()){
				if(estimatedMsLeft != Integer.MAX_VALUE) client.player.sendMessage(Text.literal("Clicks finished early!"), true);
				cancel(); clickOpOngoing=false; onComplete.run(); return;
			}
			client.executeSync(()->{
				while(calcAvailableClicks() > 0 && !clicks.isEmpty() && canProceed.apply(clicks.peek())){
//					if(!canProceed.apply(clicks.peek())) break;//{waitedForClicks = true; return;}
					if(calcAvailableClicks() <= 0){
						Main.LOGGER.error("executeClicks() lost available click mid-op, seemingly due to click(s) occuring during check of canProceed()!");
						break;
					}
					ClickEvent click = clicks.remove();
					try{
						//Main.LOGGER.info("Executing click: "+click.syncId+","+click.slotId+","+click.button+","+click.actionType);
						thisClickIsBotted = true;
						client.interactionManager.clickSlot(syncId, click.slotId, click.button, click.actionType, client.player);
						thisClickIsBotted = false;
					}
					catch(NullPointerException e){
						Main.LOGGER.error("executeClicks() failed due to null client. Clicks left: "+clicks.size()+", sumClicksInDuration: "+sumClicksInDuration);
						clicks.clear();
					}
				}
				if(clicks.isEmpty()){
					cancel();
					if(clickOpOngoing){
						clickOpOngoing=false;
						if(estimatedMsLeft != Integer.MAX_VALUE) client.player.sendMessage(Text.translatable(Main.MOD_ID+".clickutils.clicksDone"), true);
						onComplete.run();
					}
					return;
				}
				if(tickDurationArr != null){
					// +1000 so it always says at least "1s left" and not "0s left"
					final int msLeft = 1000 + calcRemainingTicks(clicks.size())*(int)TICK_DURATION;
					estimatedMsLeft = Math.min(estimatedMsLeft, msLeft);
//					StringUtils.translate("");
					client.player.sendMessage(
						Text.translatable(
								Main.MOD_ID+".clickutils.waitingForClicks",
								clicks.size(), TextUtils_New.formatTime(estimatedMsLeft)
						).withColor(OUTTA_CLICKS_COLOR), true);
//					client.player.sendMessage(
//						Text.literal(
////							"Waiting for available clicks... ("
//							+clicks.size()+", ~"+TextUtils.formatTime(estimatedMsLeft)+") "
//							+", ticksleft="+calcRemainingTicks(clicks.size())+",msLeft="+msLeft+", "
//							+String.format("%02d", tickDurIndex)
//						).withColor(OUTTA_CLICKS_COLOR), false);
				}
			});
		}}, 0l, 23l);//51l = just over a tick, 23l=just under half a tick
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