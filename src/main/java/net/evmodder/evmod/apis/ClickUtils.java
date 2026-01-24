package net.evmodder.evmod.apis;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.evmodder.EvLib.util.TextUtils_New;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.Main;
import net.evmodder.evmod.mixin.AccessorPlayerListHud;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.BundleItemSelectedC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

public final class ClickUtils{
	public enum ActionType{
		CLICK(SlotActionType.PICKUP),
		SHIFT_CLICK(SlotActionType.QUICK_MOVE),
		HOTBAR_SWAP(SlotActionType.SWAP),
		THROW(SlotActionType.THROW),
		BUNDLE_SELECT(null);

		SlotActionType action;
		ActionType(SlotActionType a){action = a;}
	}
	public record InvAction(int slot, int button, ActionType action){}

	private static int MAX_CLICKS; public static int getMaxClicks(){return MAX_CLICKS;}
	private static int[] tickDurationArr;
	private static int tickDurIndex, sumClicksInDuration;
	private static long lastTick;
	private static final int OUTTA_CLICKS_COLOR = 15777300, SYNC_ID_CHANGED_COLOR = 16733525;
//	private static final double C_PER_T;
	public static long TICK_DURATION = 51l; // In millis

	private static boolean thisClickIsBotted;
	public static boolean isThisClickBotted(/*MixinClientPlayerInteractionManager.Friend friend*/){return thisClickIsBotted;}

	public static void refreshLimits(final int MAX_CLICKS, int FOR_TICKS){
		if(clickOpOngoing){
			Main.LOGGER.error("ClickUtils.refreshLimits() called DURING AN ACTIVE CLICK-OPERATION!! May cause crash or incorrect result");
		}
		lastTick = tickDurIndex = sumClicksInDuration = 0;
		if(MAX_CLICKS >= 100_000 || MAX_CLICKS <= 0){
			if(MAX_CLICKS != 0) Main.LOGGER.error("InventoryUtils() initialized with "
					+(MAX_CLICKS < 0 ? "invalid" : "insanely-large")+" click-limit: "+MAX_CLICKS+", treating it as limitless");
			ClickUtils.MAX_CLICKS = Integer.MAX_VALUE;
//			C_PER_T = Double.MAX_VALUE;
			tickDurationArr = null;
			return;
		}
		assert FOR_TICKS > 0;
		if(FOR_TICKS > 72_000){//1hr irl
			Main.LOGGER.error("InventoryUtils() initialized with insanely-large tick-limiter duration: "+FOR_TICKS+", ignoring and using 72k instead");
			FOR_TICKS = 72_000;
		}
		ClickUtils.MAX_CLICKS = MAX_CLICKS;
//		C_PER_T = (double)MAX_CLICKS/(double)FOR_TICKS;
		tickDurationArr = new int[FOR_TICKS];
//		lastTick = System.currentTimeMillis()/TICK_DURATION; // Get recomputed by calcAvailableClicks() anyway
	}

//	private long getPing(){
//		ServerInfo serverInfo = MinecraftClient.getInstance().getCurrentServerEntry();
//		return serverInfo == null ? 0 : serverInfo.ping; 
//	}

	private static void updateAvailableClicks(){
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
	}
	public static int calcAvailableClicks(){
		if(tickDurationArr == null) return MAX_CLICKS;
		synchronized(tickDurationArr){
			updateAvailableClicks();
			return MAX_CLICKS - sumClicksInDuration;
		}
	}
	public static boolean addClick(/*SlotActionType type*/){ // TODO: friend MixinClientPlayerInteractionManager?
//		assert type != null; // unused

		synchronized(tickDurationArr){
			if(tickDurationArr == null) return true;
			updateAvailableClicks();
			if(sumClicksInDuration >= MAX_CLICKS) return false;
			++tickDurationArr[tickDurIndex];
			++sumClicksInDuration;
			return true;
		}
	}

	private static void adjustTickRate(long msPerTick){
		// If TPS is degrading, don't clear old tick data (this isn't a perfect solution by any means)
		if(msPerTick > TICK_DURATION) lastTick = System.currentTimeMillis()/TICK_DURATION;
		else calcAvailableClicks();
		TICK_DURATION = msPerTick;
		lastTick = System.currentTimeMillis()/TICK_DURATION;
	}

	private static int calcRemainingTicks(int clicksToExecute){
		synchronized(tickDurationArr){
			updateAvailableClicks();
			final int availableNow = MAX_CLICKS - sumClicksInDuration;
			if(availableNow >= clicksToExecute) return 0;
			clicksToExecute -= availableNow;
			tickDurationArr[tickDurIndex] += availableNow;
	
			int ticksIntoFuture;
			for(ticksIntoFuture = 1; clicksToExecute > 0; ++ticksIntoFuture){
				clicksToExecute -= tickDurationArr[(tickDurIndex + ticksIntoFuture) % tickDurationArr.length];
			}
			tickDurationArr[tickDurIndex] -= availableNow;
			return ticksIntoFuture;
		}
	}

	private static final Pattern tpsPattern = Pattern.compile("(\\d{1,2}(?:\\.\\d+))\\s?tps", Pattern.CASE_INSENSITIVE);
	private static long /*getTPS*/getMillisPerTick(MinecraftClient client){
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

	private static boolean clickOpOngoing/*, waitedForClicks*/;
	private static int estimatedMsLeft;
	public static boolean hasOngoingClicks(){return clickOpOngoing;}
	public static void executeClicks(Function<InvAction, Boolean> canProceed, Runnable onComplete, Queue<InvAction> clicks){
		if(clicks.isEmpty()){
			Main.LOGGER.warn("executeClicks() called with an empty ClickEvent list");
			onComplete.run();
			return;
		}

		final MinecraftClient client = MinecraftClient.getInstance();
		synchronized(tickDurationArr){
			if(clickOpOngoing){
				Main.LOGGER.warn("executeClicks() already has an ongoing operation");
				client.player.sendMessage(Text.literal("Clicks cancelled: current operation needs to finish before starting a new one"), true);
				onComplete.run();
				return;
			}
			clickOpOngoing = true;
		}
		if(Configs.Generic.CLICK_LIMIT_ADJUST_FOR_TPS.getBooleanValue() && tickDurationArr != null){
			final long msPerTick = getMillisPerTick(client);
			if(msPerTick != TICK_DURATION) adjustTickRate(msPerTick);
		}

		final int syncId = client.player.currentScreenHandler.syncId;
		estimatedMsLeft = Integer.MAX_VALUE;

		new Timer().schedule(new TimerTask(){
			private void stopTask(){
				synchronized(tickDurationArr){
					cancel();
					clickOpOngoing=false;
					onComplete.run();
				}
			}
			@Override public void run(){
				if(client.player == null){
					Main.LOGGER.error("executeClicks() failed due to null player! num clicks in arr: "+sumClicksInDuration);
					stopTask(); return;
				}
				if(client.player.currentScreenHandler.syncId != syncId){
					Main.LOGGER.error("executeClicks() failed due to syncId changing mid-operation ("+syncId+" -> "+client.player.currentScreenHandler.syncId+")");
					client.player.sendMessage(Text.literal("Clicks cancelled: container ID changed").withColor(SYNC_ID_CHANGED_COLOR), true);
					stopTask(); return;
				}
				if(clicks.isEmpty()){
					if(estimatedMsLeft != Integer.MAX_VALUE) client.player.sendMessage(Text.literal("Clicks finished early!"), true);
					stopTask(); return;
				}
				client.executeSync(()->{
					while(calcAvailableClicks() > 0 && !clicks.isEmpty() && canProceed.apply(clicks.peek())){
//						if(!canProceed.apply(clicks.peek())) break;//{waitedForClicks = true; return;}
						if(calcAvailableClicks() <= 0){
							Main.LOGGER.error("executeClicks() lost available click mid-op, seemingly due to click(s) occuring during check of canProceed()!");
							break;
						}
						InvAction click = clicks.remove();
						try{
//							Main.LOGGER.info("Executing click: "+click.slot+","+click.button+","+click.action+" | available="+calcAvailableClicks());
							thisClickIsBotted = true;
							if(click.action == ActionType.BUNDLE_SELECT){
								client.player.networkHandler.sendPacket(new BundleItemSelectedC2SPacket(click.slot, click.button));
							}
							else client.interactionManager.clickSlot(syncId, click.slot, click.button, click.action.action, client.player);
							thisClickIsBotted = false;
						}
						catch(NullPointerException e){
							Main.LOGGER.error("executeClicks() failed due to null client. Clicks left: "+clicks.size()+", sumClicksInDuration: "+sumClicksInDuration);
							clicks.clear();
						}
					}
					if(clicks.isEmpty()){
						stopTask();
						if(estimatedMsLeft != Integer.MAX_VALUE) client.player.sendMessage(Text.translatable(Main.MOD_ID+".clickutils.clicksDone"), true);
						return;
					}
					if(tickDurationArr != null){
						// +1000 so it always says at least "1s left" and not "0s left"
						final int msLeft = 1000 + calcRemainingTicks(clicks.size())*(int)TICK_DURATION;
						estimatedMsLeft = Math.min(estimatedMsLeft, msLeft);
//						StringUtils.translate("");
						client.player.sendMessage(
							Text.translatable(
									Main.MOD_ID+".clickutils.waitingForClicks",
									clicks.size(), TextUtils_New.formatTime(estimatedMsLeft)
							).withColor(OUTTA_CLICKS_COLOR), true);
//						client.player.sendMessage(
//							Text.literal(
////							"Waiting for available clicks... ("
//								+clicks.size()+", ~"+TextUtils.formatTime(estimatedMsLeft)+") "
//								+", ticksleft="+calcRemainingTicks(clicks.size())+",msLeft="+msLeft+", "
//								+String.format("%02d", tickDurIndex)
//							).withColor(OUTTA_CLICKS_COLOR), false);
					}
				});
			}
		}, 0l, 23l);//51l = just over a tick, 23l=just under half a tick
	}
	public static void executeClicks(Function<InvAction, Boolean> canProceed, Runnable onComplete, InvAction... clicks){
		executeClicks(canProceed, onComplete, new ArrayDeque<>(List.of(clicks)));
	}

	public static void executeClicksLEGACY(
			MinecraftClient client,
			Queue<InvAction> clicks, final int MILLIS_BETWEEN_CLICKS, final int MAX_CLICKS_PER_SECOND,
			Function<InvAction, Boolean> canProceed, Runnable onComplete)
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
						InvAction click = clicks.remove();
						try{
							if(click.action == ActionType.BUNDLE_SELECT){
								client.player.networkHandler.sendPacket(new BundleItemSelectedC2SPacket(click.slot, click.button));
							}
							else client.interactionManager.clickSlot(syncId, click.slot, click.button, click.action.action, client.player);
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
				InvAction click = clicks.remove();
				try{
					if(click.action == ActionType.BUNDLE_SELECT){
						client.player.networkHandler.sendPacket(new BundleItemSelectedC2SPacket(click.slot, click.button));
					}
					else client.interactionManager.clickSlot(syncId, click.slot, click.button, click.action.action, client.player);
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
					InvAction click = clicks.remove();
					//Main.LOGGER.info("click: "+click.syncId+","+click.slotId+","+click.button+","+click.actionType);
					try{
						if(click.action == ActionType.BUNDLE_SELECT){
							client.player.networkHandler.sendPacket(new BundleItemSelectedC2SPacket(click.slot, click.button));
						}
						else client.interactionManager.clickSlot(syncId, click.slot, click.button, click.action.action, client.player);
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