package net.evmodder.evmod.apis;

import java.time.Duration;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import net.evmodder.EvLib.util.FileIO;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.Main;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;

public final class ChatBroadcaster{
	private static Timer timer;
	private static int msgIndex;

	public final static void refreshBroadcast(){
		if(timer != null) timer.cancel();
		final long unix_evt_ts = Long.parseLong(Configs.Generic.TEMP_BROADCAST_TIMESTAMP.getStringValue());
		final List<String> evt_msgs = Configs.Generic.TEMP_BROADCAST_MSGS.getStrings();
		final String username = MinecraftClient.getInstance().getSession().getUsername();
		final long ts = System.currentTimeMillis();
		if(unix_evt_ts*1000L <= ts || evt_msgs.isEmpty() || !username.equalsIgnoreCase(Configs.Generic.TEMP_BROADCAST_ACCOUNT.getStringValue())) return;

		final long evt_ts = unix_evt_ts*1000L;
		final Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("CST"));
		cal.setTimeInMillis(evt_ts);
		final long evt_hr = cal.get(Calendar.HOUR_OF_DAY);
		if(ts > evt_ts) return;
		msgIndex = Integer.parseInt(FileIO.loadFile("temp_event_index", "-1"));

		Main.LOGGER.info("Chat Broadcaster initialized");

		timer = new Timer(/*isDaemon=*/true);
		timer.scheduleAtFixedRate(new TimerTask(){
			@Override public void run(){
				final Calendar cal = Calendar.getInstance();
				final int hr = cal.get(Calendar.HOUR_OF_DAY);
				if(cal.get(Calendar.MINUTE) % 30 == 0) return; // Advertise once per 1/2 hour
				if(hr > evt_hr || evt_hr - hr > 5) return; // Advertise in the 5 hrs leading up the event, on preceeding days

				final long ts = System.currentTimeMillis();
				if(ts > evt_ts) return; // Event has passed

				final ClientPlayNetworkHandler handler = MinecraftClient.getInstance().getNetworkHandler();
				if(handler == null){
					Main.LOGGER.warn("Player appears to be offline (or otherwise unable to send chats)");
					return;
				}

				if(++msgIndex >= evt_msgs.size()) msgIndex = 0;
				FileIO.saveFile("temp_event_index", ""+msgIndex);

				long hrsTillEvent = Duration.ofMillis(evt_ts-System.currentTimeMillis()).toHours();
				final long daysTillEvent = hrsTillEvent/24;
				hrsTillEvent %= 24;
				String timeStr = (daysTillEvent != 0 ? daysTillEvent+"d" : "") + (hrsTillEvent != 0 ? hrsTillEvent+"h" : "");
				if(timeStr.isEmpty()){
					Main.LOGGER.error("Timeleft till event is 0??");
					timeStr = "...soon(tm)";
				}

				final String msg = evt_msgs.get(msgIndex).replace("{time}", timeStr).replace("{t}", timeStr);
				Main.LOGGER.info("sending msg: "+msg);
				handler.sendChatMessage(msg);
			}
		}, 1L, 15_000L); // Runs every 15s
	}
}