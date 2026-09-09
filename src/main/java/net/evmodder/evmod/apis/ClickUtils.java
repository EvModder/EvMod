package net.evmodder.evmod.apis;

import static net.evmodder.evmod.compat.MinecraftCompat.tabList;
import static net.evmodder.evmod.compat.MinecraftCompat.sendOverlay;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.evmodder.EvLib.util.TextUtils_New;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.Main;
import net.evmodder.evmod.mixin.AccessorPlayerListHud;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ServerboundSelectBundleItemPacket;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;

public final class ClickUtils{
	public enum ActionType{
		CLICK(ContainerInput.PICKUP),
		SHIFT_CLICK(ContainerInput.QUICK_MOVE),
		HOTBAR_SWAP(ContainerInput.SWAP),
		THROW(ContainerInput.THROW),
		QUICK_CRAFT(ContainerInput.QUICK_CRAFT),
		BUNDLE_SELECT(null);

		ContainerInput action;
		ActionType(ContainerInput a){action = a;}
	}
	public record InvAction(int slot, int button, ActionType action){
		@Override public InvAction clone(){return new InvAction(slot, button, action);}
	}

	private static volatile int MAX_CLICKS; public static final int getMaxClicks(){return MAX_CLICKS;}
	private static volatile int[] tickDurationArr;
	private static final Object CLICK_LOCK = new Object();
	private static int tickDurIndex, sumClicksInDuration;
	private static long lastTick;
	private static final int OUTTA_CLICKS_COLOR = 15777300, SYNC_ID_CHANGED_COLOR = 16733525;
//	private static final double C_PER_T;
	public static volatile long TICK_DURATION_NANOS = 50_100_000l;
	private static boolean IS_NEW_TICK; // TODO: friend MixinClientPlayerInteractionManager?

	private static boolean thisClickIsBotted;
	public static final boolean isThisClickBotted(/*MixinClientPlayerInteractionManager.Friend friend*/){return thisClickIsBotted;}

	public static final void refreshLimits(final int MAX_CLICKS, int FOR_TICKS){
		synchronized(CLICK_LOCK){
			if(clickOpOngoing){
				Main.LOGGER.error("ClickUtils.refreshLimits() called DURING AN ACTIVE CLICK-OPERATION!! May cause crash or incorrect result");
			}
			lastTick = tickDurIndex = sumClicksInDuration = 0;
			if(MAX_CLICKS >= 100_000 || MAX_CLICKS <= 0){
				if(MAX_CLICKS != 0) Main.LOGGER.error("InventoryUtils() initialized with "
						+(MAX_CLICKS < 0 ? "invalid" : "insanely-large")+" click-limit: "+MAX_CLICKS+", treating it as limitless");
				ClickUtils.MAX_CLICKS = Integer.MAX_VALUE;
//				C_PER_T = Double.MAX_VALUE;
				tickDurationArr = null;
				return;
			}
			assert FOR_TICKS > 0;
			if(FOR_TICKS > 72_000){//1hr irl
				Main.LOGGER.error("InventoryUtils() initialized with insanely-large tick-limiter duration: "+FOR_TICKS+", ignoring and using 72k instead");
				FOR_TICKS = 72_000;
			}
			ClickUtils.MAX_CLICKS = MAX_CLICKS;
//			C_PER_T = (double)MAX_CLICKS/(double)FOR_TICKS;
			tickDurationArr = new int[FOR_TICKS];
//			lastTick = System.nanoTime()/TICK_DURATION_NANOS; // Get recomputed by calcAvailableClicks() anyway
		}
	}

//	private long getPing(){
//		ServerInfo serverInfo = MinecraftClient.getInstance().getCurrentServerEntry();
//		return serverInfo == null ? 0 : serverInfo.ping; 
//	}

	private static final void updateAvailableClicks(){
		final long curTick = System.nanoTime()/TICK_DURATION_NANOS;
		IS_NEW_TICK = curTick != lastTick;
		if(IS_NEW_TICK){
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
	public static final int calcAvailableClicks(){
		if(tickDurationArr == null) return Integer.MAX_VALUE;
		synchronized(CLICK_LOCK){
			updateAvailableClicks();
			return MAX_CLICKS - sumClicksInDuration;
		}
	}
	/** Count permitted user clicks even over budget, so automation waits for that debt. */
	public static final boolean addClick(/*SlotActionType type, */final boolean allowOverLimit){ // friend MixinClientPlayerInteractionManager?
//		assert type != null; // unused

		if(tickDurationArr == null) return true;
		synchronized(CLICK_LOCK){
			updateAvailableClicks();
			final boolean withinLimit = sumClicksInDuration < MAX_CLICKS;
			if(withinLimit || allowOverLimit){
				++tickDurationArr[tickDurIndex];
				++sumClicksInDuration;
			}
			return withinLimit;
		}
	}
	public static final boolean addClick(/*SlotActionType type*/){return addClick(/*type, */false);}
	public static final boolean isNewTick(){return IS_NEW_TICK;} // friend MixinClientPlayerInteractionManager?

	private static final void adjustTickRate(final long msPerTick){
		synchronized(CLICK_LOCK){
			// If TPS is degrading, don't clear old tick data (this isn't a perfect solution by any means)
			if(msPerTick > TICK_DURATION_NANOS/1_000_000l) lastTick = System.nanoTime()/TICK_DURATION_NANOS;
			else updateAvailableClicks();
			TICK_DURATION_NANOS = msPerTick*1_000_000l;
			lastTick = System.nanoTime()/TICK_DURATION_NANOS;
		}
	}

	private static final int calcRemainingTicks(int clicksToExecute){
		if(clicksToExecute == 0) return 0;
		synchronized(CLICK_LOCK){
			updateAvailableClicks();
			final int availableNow = MAX_CLICKS - sumClicksInDuration;
			if(availableNow >= clicksToExecute) return 0;
			clicksToExecute -= Math.max(0, availableNow); // Only actual free capacity can be used now.
			final int fullWindows = (clicksToExecute-1)/MAX_CLICKS;
			// Greedy refill repeats every window. Existing debt must expire before any refill.
			int expirationsNeeded = (clicksToExecute-1)%MAX_CLICKS + 1 + Math.max(0, -availableNow);
			for(int ticks=1; ticks<tickDurationArr.length; ++ticks){
				expirationsNeeded -= tickDurationArr[(tickDurIndex+ticks)%tickDurationArr.length];
				if(expirationsNeeded <= 0) return fullWindows*tickDurationArr.length + ticks;
			}
			return (fullWindows+1)*tickDurationArr.length;
		}
	}

	private static final Pattern tpsPattern = Pattern.compile("(\\d{1,2}(?:\\.\\d+))\\s?tps", Pattern.CASE_INSENSITIVE);
	private static final long /*getTPS*/getMillisPerTick(Minecraft client){
		// Alternative: client.getNetworkHandler().onPlayerListHeader(PlayerListHeaderS2CPacket plhp)

		final AccessorPlayerListHud playerListHudAccessor = (AccessorPlayerListHud)tabList(client);
		final Component footerText = playerListHudAccessor.getFooter();
		if(footerText == null) return TICK_DURATION_NANOS/1_000_000l;
		final MutableComponent text = Component.empty(); footerText.toFlatList().forEach(text::append);
		final String footerStr = TextUtils_New.stripColorAndFormats(text.getString());
		//§819.90 tps — 692 players online — 92 ping
		final Matcher matcher = tpsPattern.matcher(footerStr);
		if(!matcher.find()) return TICK_DURATION_NANOS/1_000_000l;
		final double tps = Double.parseDouble(matcher.group(1));
//		Main.LOGGER.info("ClickUtils: got TPS from playerListTab: "+tps);
		final long msPerTick = (long)Math.ceil(1000d/tps);
		return Math.max(50, msPerTick); // Even if TPS>20, let's play it safe since packet-limiters might use real-time
	}

	private static volatile boolean clickOpOngoing/*, waitedForClicks*/;
	private static Runnable cancelClicks; // Client-thread owned, like the queue and completion callback.
	static{
		ClientPlayConnectionEvents.DISCONNECT.register((_, client)->client.executeIfPossible(()->{
			// Disconnect can discard a pending client task before its scheduled flag is cleared.
			if(cancelClicks != null) cancelClicks.run();
		}));
	}
	public static final boolean hasOngoingClicks(){return clickOpOngoing;}
	public static final void executeClicks(final Function<InvAction, Boolean> canProceed, final Runnable onComplete, final Queue<InvAction> clicks){
		// Ensure this is called from the client's main thread
		final Minecraft client = Minecraft.getInstance();
		if(!client.isSameThread()){
			client.executeIfPossible(()->executeClicks(canProceed, onComplete, clicks));
			return;
		}
		if(clicks.isEmpty()){
			Main.LOGGER.warn("executeClicks() called with an empty ClickEvent list");
			onComplete.run();
			return;
		}

		if(client.player == null || client.gameMode == null || !client.player.connection.isAcceptingMessages()){
			Main.LOGGER.warn("executeClicks() called while player is unloaded");
			onComplete.run();
			return;
		}
		if(clickOpOngoing){
			Main.LOGGER.warn("executeClicks() already has an ongoing operation");
			sendOverlay(client.player, Component.literal("Clicks cancelled: current operation needs to finish before starting a new one"));
			onComplete.run();
			return;
		}
		clickOpOngoing = true;
		if(Configs.Generic.CLICK_LIMIT_ADJUST_FOR_TPS.getBooleanValue() && tickDurationArr != null){
			final long msPerTick = getMillisPerTick(client);
			if(msPerTick != TICK_DURATION_NANOS/1_000_000l) adjustTickRate(msPerTick);
		}

		final AbstractContainerMenu menu = client.player.containerMenu;
		final int syncId = menu.containerId;

		class ClickTask extends TimerTask{
			private Timer timer;
			private AtomicBoolean scheduled;
			private int estimatedMsLeft = Integer.MAX_VALUE;
			private boolean stopped; // Accessed only on the client thread.
			private final void stopTask(){
				if(stopped) return;
				stopped = true;
				if(timer != null) timer.cancel();
				cancelClicks = null;
				clickOpOngoing = false;
				onComplete.run();
			}
			private void executeBatch(){
				if(stopped) return;
				if(client.player == null || client.gameMode == null || !client.player.connection.isAcceptingMessages()){
					Main.LOGGER.info("executeClicks() cancelled: no active player/connection");
					stopTask(); return;
				}
				if(client.player.containerMenu != menu){
					Main.LOGGER.error("executeClicks() failed due to container changing mid-operation ("+syncId+" -> "+client.player.containerMenu.containerId+")");
					sendOverlay(client.player, Component.literal("Clicks cancelled: container changed").withColor(SYNC_ID_CHANGED_COLOR));
					stopTask(); return;
				}
				if(clicks.isEmpty()){
					if(estimatedMsLeft != Integer.MAX_VALUE) sendOverlay(client.player, Component.literal("Clicks finished early!"));
					stopTask(); return;
				}
				while(!clicks.isEmpty() && (clicks.peek().action == ActionType.BUNDLE_SELECT || calcAvailableClicks() > 0)
						&& canProceed.apply(clicks.peek())){
//					if(!canProceed.apply(clicks.peek())) break;//{waitedForClicks = true; return;}
					if(clicks.peek().action != ActionType.BUNDLE_SELECT && calcAvailableClicks() <= 0){
						Main.LOGGER.error("executeClicks() lost available click mid-op, seemingly due to click(s) occuring during check of canProceed()!");
						break;
					}
					// Bundle selection is a separate packet, not part of the container-click budget.
					final InvAction click = clicks.remove();
					try{
//						Main.LOGGER.info("Executing click: "+click.slot+","+click.button+","+click.action+" | available="+calcAvailableClicks());
						thisClickIsBotted = true;
						if(click.action == ActionType.BUNDLE_SELECT){
							client.player.connection.send(new ServerboundSelectBundleItemPacket(click.slot, click.button));
						}
						else client.gameMode.handleContainerInput(syncId, click.slot, click.button, click.action.action, client.player);
					}
					catch(NullPointerException e){
						Main.LOGGER.error("executeClicks() failed. Clicks left: "+clicks.size(), e);
						clicks.clear();
					}
					finally{thisClickIsBotted = false;}
				}
				if(clicks.isEmpty()){
					if(estimatedMsLeft != Integer.MAX_VALUE) sendOverlay(client.player, Component.translatable(Main.MOD_ID+".clickutils.clicksDone"));
					stopTask();
					return;
				}
				if(tickDurationArr != null){
					// +1000 so it always says at least "1s left" and not "0s left"
					int limitedClicks = 0;
					for(InvAction click : clicks) if(click.action != ActionType.BUNDLE_SELECT) ++limitedClicks;
					estimatedMsLeft = 1000 + calcRemainingTicks(limitedClicks)*(int)(TICK_DURATION_NANOS/1_000_000l);
//					StringUtils.translate("");
					sendOverlay(client.player,
						Component.translatable(
								Main.MOD_ID+".clickutils.waitingForClicks",
								clicks.size(), TextUtils_New.formatTime(estimatedMsLeft)
						).withColor(OUTTA_CLICKS_COLOR));
//					client.player.sendMessage(
//						Text.literal(
////						"Waiting for available clicks... ("
//							+clicks.size()+", ~"+TextUtils.formatTime(estimatedMsLeft)+") "
//							+", ticksleft="+calcRemainingTicks(clicks.size())+",msLeft="+msLeft+", "
//							+String.format("%02d", tickDurIndex)
//						).withColor(OUTTA_CLICKS_COLOR), false);
				}
				if(timer == null){
					scheduled = new AtomicBoolean();
					timer = new Timer("EvMod-clicks", /*isDaemon=*/true);
					timer.schedule(this, 23l, 23l); // Allocate a scheduler only when the first synchronous pass must wait.
				}
			}
			private void executeNow(){
				try{executeBatch();}
				catch(RuntimeException e){stopTask(); throw e;}
			}
			@Override public final void run(){
				if(!scheduled.compareAndSet(false, true)) return;
				client.executeIfPossible(()->{
					try{executeNow();}
					finally{scheduled.set(false);}
				});
			}
		}
		final ClickTask task = new ClickTask();
		cancelClicks = task::stopTask;
		task.executeNow();
	}
	public static final void executeClicks(final Function<InvAction, Boolean> canProceed, final Runnable onComplete, final InvAction... clicks){
		executeClicks(canProceed, onComplete, new ArrayDeque<>(List.of(clicks)));
	}

	public static final void executeClicksLEGACY(
			Minecraft client,
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
		final int syncId = Minecraft.getInstance().player.containerMenu.containerId;
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
								client.player.connection.send(new ServerboundSelectBundleItemPacket(click.slot, click.button));
							}
							else client.gameMode.handleContainerInput(syncId, click.slot, click.button, click.action.action, client.player);
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
						client.player.connection.send(new ServerboundSelectBundleItemPacket(click.slot, click.button));
					}
					else client.gameMode.handleContainerInput(syncId, click.slot, click.button, click.action.action, client.player);
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
							client.player.connection.send(new ServerboundSelectBundleItemPacket(click.slot, click.button));
						}
						else client.gameMode.handleContainerInput(syncId, click.slot, click.button, click.action.action, client.player);
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