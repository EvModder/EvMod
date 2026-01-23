package net.evmodder.evmod;

import java.nio.ByteBuffer;
import java.util.Arrays;
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
		ClickUtils.refreshLimits(Configs.Generic.CLICK_LIMIT_COUNT.getIntegerValue(), Configs.Generic.CLICK_LIMIT_DURATION.getIntegerValue());
	}

	static final void refreshRemoteServerSender(RemoteServerSender rms){
		assert rms != null;
		String fullAddress = Configs.Database.ADDRESS.getStringValue();
		final int sep = fullAddress.indexOf(':');
		final String addr;
		final int port;
		if(sep == -1){addr = fullAddress; port = RemoteServerSender.DEFAULT_PORT;}
		else{addr = fullAddress.substring(0, sep).trim(); port = Integer.parseInt(fullAddress.substring(sep+1).trim());}
		final int clientId = Configs.Database.CLIENT_ID.getIntegerValue();
		rms.setConnectionDetails(addr, port, clientId, Configs.Database.CLIENT_KEY.getStringValue());

		Main.LOGGER.info("RMS settings updated: "+addr+":"+port+", id="+clientId);
		if(clientId != DUMMY_CLIENT_ID)
			rms.sendBotMessage(Command.PING, /*udp=*/false, /*timeout=*/5000, /*msg=*/new byte[0],
				msg->Main.LOGGER.info("RMS responded to ping: "+(msg == null ? null : new String(msg))));
	}

	static final int DUMMY_CLIENT_ID = 67;
	private static boolean requestedKey = false;
	static final boolean checkValidClientKeyAndRequestIfNot(RemoteServerSender rms, Configs configs){
		if(Configs.Database.CLIENT_ID.getIntegerValue() != DUMMY_CLIENT_ID) return true;
		if(requestedKey) return false;
		requestedKey = true;
		Main.LOGGER.info("Missing valid CLIENT_ID for Database, requesting one from RMS");

		Session session = MinecraftClient.getInstance().getSession();
		UUID uuid = session.getUuidOrNull() != null ? session.getUuidOrNull() : UUID.nameUUIDFromBytes(session.getUsername().getBytes());
		byte[] msg = PacketHelper.toByteArray(uuid);
		rms.sendBotMessage(Command.REQUEST_CLIENT_KEY, /*udp=*/false, /*timeout=*/5000, msg, (response)->{
			if(response == null || response.length <= 4+8){
				Main.LOGGER.info("ClientAuth: null/invalid response from RMS for REQUEST_CLIENT_KEY: "+(msg == null ? null : new String(msg)));
				return;
			}
			ByteBuffer bb = ByteBuffer.wrap(response);
			final int clientId = bb.getInt();
			assert clientId != DUMMY_CLIENT_ID;
			final byte[] strBytes = new byte[bb.remaining()]; bb.get(strBytes);
			final String clientKey = new String(strBytes);

			MinecraftClient.getInstance().executeSync(()->{
				Main.LOGGER.info("ClientAuth: server granted credentials! id="+clientId+", key="+clientKey);
				// TODO: these changes do not seem to be propogating properly
				Configs.Database.CLIENT_ID.setIntegerValue(clientId);
				Configs.Database.CLIENT_KEY.setValueFromString(clientKey);
				configs.save();
//				refreshRemoteServerSender(rms); // Should get triggered automatically by KeyCallbacks onValueChanged()
				Main.LOGGER.info("ClientAuth: credentials sanity check clientId: "+Configs.Database.CLIENT_ID.getIntegerValue());
			});
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
