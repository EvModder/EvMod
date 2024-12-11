package net.evmodder;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.UUID;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;

final class RemoteServerSender{
	private final static String REMOTE_MSG_CATEGORY = "key.categories."+KeyBound.MOD_ID+".remote_messages";

	private final String ADDR;
	private final int PORT;
	private InetAddress addrResolved;

	private final int CLIENT_ID;
	private final String CLIENT_KEY;

	private void resolveAddress(){
		try{addrResolved = InetAddress.getByName(ADDR);}
		catch(UnknownHostException e){KeyBound.LOGGER.warn("Server not found: "+ADDR);}
	}

	void sendBotMessage(UUID uuid, UUID message){
		ByteBuffer bb1 = ByteBuffer.allocate(16+16);
		bb1.putLong(uuid.getMostSignificantBits());
		bb1.putLong(uuid.getLeastSignificantBits());
		bb1.putLong(message.getMostSignificantBits());
		bb1.putLong(message.getLeastSignificantBits());
		byte[] encryptedUUID = PacketHelper.encrypt(bb1.array(), CLIENT_KEY);
		//LOGGER.info("bytes length: "+encryptedUUID.length);

		ByteBuffer bb2 = ByteBuffer.allocate(4+32);
		bb2.putInt(CLIENT_ID);
		bb2.put(encryptedUUID);
		if(addrResolved == null) resolveAddress();
		PacketHelper.sendPacket(addrResolved, PORT, bb2.array());
		resolveAddress();
	}

	RemoteServerSender(String addr, int port, int clientId, String clientKey, HashMap<String, UUID> messages){
		ADDR = addr;
		PORT = port;
		resolveAddress();

		CLIENT_ID = clientId;
		CLIENT_KEY = clientKey;

		messages.forEach((key, message) -> {
			KeyBindingHelper.registerKeyBinding(new AbstractKeybind("key."+KeyBound.MOD_ID+key, InputUtil.Type.KEYSYM, -1, REMOTE_MSG_CATEGORY){
				@Override public void onPressed(){
					MinecraftClient instance = MinecraftClient.getInstance();
					sendBotMessage(instance.player.getUuid(), message);
				}
			});
		});
	}
}