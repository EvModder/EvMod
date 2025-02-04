package net.evmodder.KeyBound;

import java.time.Duration;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import net.evmodder.EvLib.FileIO;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;

final class ChatBroadcaster{
	private int msgIndex = 0;
	private final int evt_hr;
	private final long evt_ts;

	ChatBroadcaster(long unix_evt_ts, String[] evt_msgs){
		evt_ts = unix_evt_ts*1000L;
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("CST"));
		cal.setTimeInMillis(evt_ts);
		evt_hr = cal.get(Calendar.HOUR_OF_DAY);
		if(System.currentTimeMillis() > evt_ts) return;
		msgIndex = Integer.parseInt(FileIO.loadFile("temp_event_index", "0"));

		Main.LOGGER.info("Chat Broadcaster initialized");

		new Timer(/*isDaemon=*/true).scheduleAtFixedRate(new TimerTask(){
			@Override public void run(){
				Main.LOGGER.info("chat msg gogogo");
				final Calendar cal = Calendar.getInstance();
				final int hr = cal.get(Calendar.HOUR_OF_DAY);
				if(cal.get(Calendar.MINUTE) % 30 == 0) return; // Advertise once per 1/2 hour
				if(hr > evt_hr || evt_hr - hr > 5) return; // Advertise in the 5 hrs leading up the event, on preceeding days

				long ts = System.currentTimeMillis();
				if(ts > evt_ts) return; // Event has passed

				if(msgIndex >= evt_msgs.length) msgIndex = 0;
				long hrsTillEvent = Duration.ofMillis(evt_ts-System.currentTimeMillis()).toHours();
				long daysTillEvent = hrsTillEvent/24;
				hrsTillEvent %= 24;
				String timeStr = (daysTillEvent != 0 ? daysTillEvent+"d" : "") + (hrsTillEvent != 0 ? hrsTillEvent+"h" : "");
				if(timeStr.isEmpty()){
					Main.LOGGER.error("Timeleft till event is 0??");
					timeStr = "...soon(tm)";
				}

				String msg = evt_msgs[msgIndex].replace("{time}", timeStr).replace("{t}", timeStr);
				Main.LOGGER.info("msg to send: "+msg);

				MinecraftClient instance = MinecraftClient.getInstance();
				ClientPlayNetworkHandler handler = instance.getNetworkHandler();
				if(handler == null) Main.LOGGER.warn("Player appears to be offline (or otherwise unable to send chats)");
				else handler.sendChatMessage(msg);
				++msgIndex;
			}
		}, 1L, 15_000L); // Runs every 15s
	}
}