package net.evmodder.KeyBound;

import java.time.Duration;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import net.evmodder.EvLib.FileIO;
import net.evmodder.KeyBound.config.Configs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;

public class ChatBroadcaster{
	private static Timer timer;
	private static int msgIndex;

	public final static void refreshBroadcast(){
		if(timer != null) timer.cancel();
		final long unix_evt_ts = Long.parseLong(Configs.Misc.TEMP_BROADCAST_TIMESTAMP.getStringValue());
		List<String> evt_msgs = Configs.Misc.TEMP_BROADCAST_MSGS.getStrings();
		final String username = MinecraftClient.getInstance().getSession().getUsername();
		if(unix_evt_ts*1000L > System.currentTimeMillis() && !evt_msgs.isEmpty() && username.equalsIgnoreCase(Configs.Misc.TEMP_BROADCAST_ACCOUNT.getStringValue())){
			final long evt_ts = unix_evt_ts*1000L;
			Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("CST"));
			cal.setTimeInMillis(evt_ts);
			final long evt_hr = cal.get(Calendar.HOUR_OF_DAY);
			if(System.currentTimeMillis() > evt_ts) return;
			msgIndex = Integer.parseInt(FileIO.loadFile("temp_event_index", "0"));

			Main.LOGGER.info("Chat Broadcaster initialized");

			timer = new Timer(/*isDaemon=*/true);
			timer.scheduleAtFixedRate(new TimerTask(){
				@Override public void run(){
					Main.LOGGER.info("chat msg gogogo");
					final Calendar cal = Calendar.getInstance();
					final int hr = cal.get(Calendar.HOUR_OF_DAY);
					if(cal.get(Calendar.MINUTE) % 30 == 0) return; // Advertise once per 1/2 hour
					if(hr > evt_hr || evt_hr - hr > 5) return; // Advertise in the 5 hrs leading up the event, on preceeding days
	
					long ts = System.currentTimeMillis();
					if(ts > evt_ts) return; // Event has passed
	
					if(msgIndex >= evt_msgs.size()) msgIndex = 0;
					long hrsTillEvent = Duration.ofMillis(evt_ts-System.currentTimeMillis()).toHours();
					long daysTillEvent = hrsTillEvent/24;
					hrsTillEvent %= 24;
					String timeStr = (daysTillEvent != 0 ? daysTillEvent+"d" : "") + (hrsTillEvent != 0 ? hrsTillEvent+"h" : "");
					if(timeStr.isEmpty()){
						Main.LOGGER.error("Timeleft till event is 0??");
						timeStr = "...soon(tm)";
					}
	
					String msg = evt_msgs.get(msgIndex).replace("{time}", timeStr).replace("{t}", timeStr);
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
}