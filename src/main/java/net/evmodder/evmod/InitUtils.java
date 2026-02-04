package net.evmodder.evmod;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import net.evmodder.EvLib.util.Command;
import net.evmodder.EvLib.util.PacketHelper;
import net.evmodder.evmod.apis.ClickUtils;
import net.evmodder.evmod.apis.RemoteServerSender;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;
import net.minecraft.entity.player.PlayerModelPart;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

final class InitUtils{
//	public static final String MOD_ID;
//	public static final String MOD_NAME;
//	static{
//		String fabricModJsonStr = FileIO.loadResource(FabricEntryPoint.class, "fabric.mod.json", null);
//		JsonObject fabricModJsonObj = JsonParser.parseString(fabricModJsonStr).getAsJsonObject();
//		InputStreamReader inputStreamReader = new InputStreamReader(Main.class.getResourceAsStream("/fabric.mod.json"));
//		JsonObject fabricModJsonObj = JsonParser.parseReader(inputStreamReader).getAsJsonObject();
//		LoggerFactory.getLogger("test").info("fabric mod json: "+fabricModJsonObj.toString());
//		MOD_ID = fabricModJsonObj.get("id").getAsString();
//		MOD_NAME = fabricModJsonObj.get("name").getAsString();
//	}
	static final String getModId(){
		// Get the path of the current class
		String classPath = Main.class.getName().replace('.', '/') + ".class";
		String modId = FabricLoader.getInstance().getAllMods().stream()
				.filter(container -> container.findPath(classPath).isPresent())
				.map(container -> container.getMetadata().getId())
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("Could not find current mod ID!"));

		assert classPath.contains("/"+modId+"/") : "Class path does not contain mod id! "+classPath;
		assert classPath.equals("net/evmodder/"+modId+"/Main.class");
		return modId;
	}

	static final void refreshClickLimits(){
		ClickUtils.refreshLimits(Configs.Generic.CLICK_LIMIT_COUNT.getIntegerValue(), Configs.Generic.CLICK_LIMIT_WINDOW.getIntegerValue());
	}

	private static Timer clickRenderTimer;
	private static boolean lastClickRenderWasMax;
	static final void refreshClickRenderer(){
		if(clickRenderTimer != null) clickRenderTimer.cancel();
		if(!Configs.Generic.CLICK_DISPLAY_AVAILABLE_PERSISTENT.getBooleanValue()) return;
		clickRenderTimer = new Timer(/*isDaemon=*/true);
		clickRenderTimer.scheduleAtFixedRate(new TimerTask(){@Override public void run(){
			MinecraftClient client = MinecraftClient.getInstance();
			if(client.player == null) return;
			if(ClickUtils.hasOngoingClicks()) return; // Don't stomp actionbar statuses from click-ops
			final int clicks = ClickUtils.calcAvailableClicks();
			if(clicks == ClickUtils.getMaxClicks()){
				if(lastClickRenderWasMax) return;
				lastClickRenderWasMax = true;
			}
			else lastClickRenderWasMax = false;
			client.player.sendMessage(Text.literal("Clicks available: "+clicks+"/"+ClickUtils.getMaxClicks()).withColor(15777300), true);
		}}, 1l, 50l); // Runs every tick
	}

	static final int DUMMY_CLIENT_ID = 67; // Accessor: Configs.java (for setting default value)
	private static final String DUMMY_CLIENT_KEY = "yesyesyes";
	private static boolean sendPingRequest = true;
	static final void refreshRemoteServerSender(RemoteServerSender rms){
		assert rms != null;
		final String fullAddress = Configs.Database.ADDRESS.getStringValue();
		final int sep = fullAddress.indexOf(':');
		final String addr;
		final int port;
		if(sep == -1){addr = fullAddress.isBlank() ? null : fullAddress; port = RemoteServerSender.DEFAULT_PORT;}
		else{addr = fullAddress.substring(0, sep).trim(); port = Integer.parseInt(fullAddress.substring(sep+1).trim());}
		final int clientId = Configs.Database.CLIENT_ID.getIntegerValue();
		final String clientKey = clientId == DUMMY_CLIENT_ID ? DUMMY_CLIENT_KEY : Configs.Database.CLIENT_KEY.getStringValue();
		rms.setConnectionDetails(addr, port, clientId, clientKey);
		if(addr == null) return;

		Main.LOGGER.info("RMS settings updated: "+addr+":"+port+", id="+clientId+", key="+clientKey.charAt(0)+"..."+clientKey.charAt(clientKey.length()-1));
		if(clientId != DUMMY_CLIENT_ID && sendPingRequest){
			sendPingRequest = false;
			rms.sendBotMessage(Command.PING, /*udp=*/false, /*timeout=*/5000, /*msg=*/new byte[0],
				msg->{
					Main.LOGGER.info("RMS responded to ping: "+(msg == null ? null : new String(msg)));
					sendPingRequest = true;
				});
		}
	}

	private static boolean requestedKey = false;
	static final boolean checkValidClientKeyAndRequestIfNot(RemoteServerSender rms, Configs configs){
		if(Configs.Database.ADDRESS.getStringValue().isBlank()) return false;
		if(Configs.Database.CLIENT_ID.getIntegerValue() != DUMMY_CLIENT_ID) return true;
		if(requestedKey) return false;
		requestedKey = true;
		Main.LOGGER.info("Missing valid CLIENT_ID for Database, requesting one from RMS");

		final Session session = MinecraftClient.getInstance().getSession();
		final UUID uuid = session.getUuidOrNull() != null ? session.getUuidOrNull() : UUID.nameUUIDFromBytes(session.getUsername().getBytes());
		final byte[] msg = PacketHelper.toByteArray(uuid);
		rms.sendBotMessage(Command.REQUEST_CLIENT_KEY, /*udp=*/false, /*timeout=*/5000, msg, reply->{
			if(reply == null || reply.length != 20){
				Main.LOGGER.info("ClientAuth: Invalid response from RMS for REQUEST_CLIENT_KEY: "+(reply == null ? null : new String(reply)+",len="+reply.length));
				return;
			}
			final ByteBuffer bb = ByteBuffer.wrap(reply);
			final int clientId = bb.getInt();
			assert clientId != DUMMY_CLIENT_ID;
			final byte[] strBytes = new byte[bb.remaining()]; bb.get(strBytes);
			final String clientKey = new String(strBytes);

//			MinecraftClient.getInstance().executeSync(()->{
				Main.LOGGER.info("ClientAuth: Server granted credentials! id="+clientId+", key="+clientKey);
				Configs.Database.CLIENT_ID.setIntegerValue(clientId);
				Configs.Database.CLIENT_KEY.setValueFromString(clientKey);
				configs.save();
//				refreshRemoteServerSender(rms); // Gets triggered automatically by KeyCallbacks onValueChanged()
//				Main.LOGGER.info("ClientAuth: credentials sanity check clientId: "+Configs.Database.CLIENT_ID.getIntegerValue());
//			});
		});
		return false;
	}

	static final void sendRemoteMsg(RemoteServerSender rms, String msg){ // Accessor: KeybindCallbacks
		String[] arr = msg.split(",");
		if(arr.length < 2){
			Main.LOGGER.error("Invalid remote msg syntax, expected 'COMMAND,UUID...' got: "+msg);
			return;
		}
		final Command command;
		try{command = Command.valueOf(arr[0].toUpperCase());}
		catch(IllegalArgumentException e){
			Main.LOGGER.error("Invalid remote msg syntax, undefined command: "+arr[0]);
			return;
		}
		final byte[] byteMsg;
		try{byteMsg = PacketHelper.toByteArray(Arrays.stream(Arrays.copyOfRange(arr, 1, arr.length)).map(UUID::fromString).toArray(UUID[]::new));}
		catch(IllegalArgumentException e){
			Main.LOGGER.error("Invalid remote msg syntax, unable to parse UUID(s): "+msg.substring(msg.indexOf(',')+1));
			return;
		}
		rms.sendBotMessage(command, /*udp=*/true, /*timeout=*/5000, byteMsg, /*recv=*/null);
	}

	static final void sendChatMsg(String msg){ // Accessor: KeybindCallbacks
		if(msg.isBlank()) return;
		MinecraftClient mc = MinecraftClient.getInstance();
		if(msg.charAt(0) == '/') mc.player.networkHandler.sendChatCommand(msg.substring(1));
		else mc.player.networkHandler.sendChatMessage(msg);
	}

	static final void toggleSkinLayer(PlayerModelPart part){ // Accessor: KeybindCallbacks
		final MinecraftClient client = MinecraftClient.getInstance();
		if(Configs.Hotkeys.SYNC_CAPE_WITH_ELYTRA.getBooleanValue() && part == PlayerModelPart.CAPE
				&& client.player != null && client.options.isPlayerModelPartEnabled(part)){
			ItemStack chestItem = client.player.getInventory().getArmorStack(2);
			// Don't disable cape if we just switched to an elytra
			if(Registries.ITEM.getId(chestItem.getItem()).getPath().equals("elytra")) return;
		}
		client.options.setPlayerModelPart(part, !client.options.isPlayerModelPartEnabled(part));
		client.options.sendClientSettings();
	}
}
