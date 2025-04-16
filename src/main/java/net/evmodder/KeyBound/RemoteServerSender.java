package net.evmodder.KeyBound;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.UUID;
import net.evmodder.EvLib.Command;
import net.evmodder.EvLib.PacketHelper;
import net.evmodder.EvLib.PacketHelper.MessageReceiver;
import net.evmodder.KeyBound.Keybinds.EvKeybind;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;

public final class RemoteServerSender{
	//private static final String REMOTE_MSG_CATEGORY = "key.categories."+KeyBound.MOD_ID+".remote_messages";

	private static final MinecraftClient client = MinecraftClient.getInstance();

	private final String ADDR;
	private final int PORT;
	private InetAddress addrResolved;

	private final int CLIENT_ID;
	private final String CLIENT_KEY;

	private void resolveAddress(){
		try{addrResolved = InetAddress.getByName(ADDR);}
		catch(UnknownHostException e){Main.LOGGER.warn("Server not found: "+ADDR);}
	}

	// Returns a `4+message.length+16`-byte packet
	private byte[] packageAndEncryptMessage(Command command, byte[/*16*n*/] message){
		ByteBuffer bb1 = ByteBuffer.allocate(16+message.length);
		bb1.putInt(CLIENT_ID);
		bb1.putInt(command.ordinal());
		int addressCode = (client == null || client.getCurrentServerEntry() == null) ? 0 : client.getCurrentServerEntry().address.hashCode();
		bb1.putInt(addressCode);
		bb1.putInt((int)System.currentTimeMillis());//Truncate, since we assume ping < Integer.MAX anyway
		bb1.put(message);
		byte[] encryptedMessage = PacketHelper.encrypt(bb1.array(), CLIENT_KEY);

		ByteBuffer bb2 = ByteBuffer.allocate(4+encryptedMessage.length);
		bb2.putInt(CLIENT_ID);
		bb2.put(encryptedMessage);
		return bb2.array();
	}

	public void sendBotMessage(Command command, byte[] message, boolean udp, MessageReceiver recv){
		final byte[] packet = packageAndEncryptMessage(command, message);

//		// 4=id, 16=data=encrypted{4=id,4=cmd,4=server,4=ts}
//		switch(packet.length){
//			//20, 36, 52
//			case 4 + 16*1: // id + data
//			case 4 + 16*2: // id + data + uuid1
//			case 4 + 16*3: // id + data + uuid1 + uuid2
//			case 4 + 16*1 + 128*128: // id + data + map_colors
//				break;
//			default:
//				Main.LOGGER.error("Sending an invalid packet! length="+packet.length);
//		}
		if(addrResolved == null) resolveAddress();
		if(addrResolved == null) Main.LOGGER.warn("RemoteSender address could not be resolved!: "+ADDR);
		else{
			PacketHelper.sendPacket(addrResolved, PORT+(udp?0:1), udp, packet, recv, /*timeout=*/1000*5);
			resolveAddress();
		}
	}

	private Command parseCommand(String str){
		try{return Command.valueOf(str);}
		catch(IllegalArgumentException e){}
		return null;
	}

	RemoteServerSender(String addr, int port, int clientId, String clientKey, HashMap<String, String> messages){
		ADDR = addr;
		PORT = port;
		resolveAddress();

		CLIENT_ID = clientId;
		CLIENT_KEY = clientKey;

		if(messages != null) messages.forEach((key, message) -> {
			String[] arr = message.split(",");
			Command command = parseCommand(arr[0].toUpperCase());
			if(command == null){
				Main.LOGGER.error("Undefined command in config keybound.txt: "+arr[0]);
			}
			else{
				final byte[] byteMsg = PacketHelper.toByteArray(Arrays.stream(Arrays.copyOfRange(arr, 1, arr.length)).map(UUID::fromString).toArray(UUID[]::new));
				KeyBindingHelper.registerKeyBinding(new EvKeybind(key, ()->sendBotMessage(command, byteMsg, true, null)));
			}
		});

		sendBotMessage(Command.PING, new byte[0], false, (msg)->{
			Main.LOGGER.info("Remote server responded to ping: "+(msg == null ? null : new String(msg)));
		});
	}

	public static void main(String... args) throws IOException{
		UUID pearlUUID = UUID.fromString("a8c5dd6e-5f95-4875-9494-7c1d519ba8c8");
		UUID ownerUUID = UUID.fromString("34471e8d-d0c5-47b9-b8e1-b5b9472affa4");
//		UUID loc = new UUID(Double.doubleToRawLongBits(x), Double.doubleToRawLongBits(z));

		RemoteServerSender rss = new RemoteServerSender("localhost", 14441, 1, "some_unique_key", /*botMsgKeybinds=*/null);

		rss.sendBotMessage(Command.DB_PEARL_STORE_BY_UUID, ByteBuffer.allocate(32)
				.putLong(pearlUUID.getMostSignificantBits()).putLong(pearlUUID.getLeastSignificantBits())
				.putLong(ownerUUID.getMostSignificantBits()).putLong(ownerUUID.getLeastSignificantBits()).array(), true, null);

		rss.sendBotMessage(Command.DB_PEARL_FETCH_BY_UUID, PacketHelper.toByteArray(pearlUUID), true, new MessageReceiver(){
			@Override public void receiveMessage(byte[] msg){
				if(msg == null){System.err.println("Expected msg non-null !");return;}
				if(msg.length != 16){System.err.println("Expected msg size == 16 !!!");return;}
				ByteBuffer byteBuffer = ByteBuffer.wrap(msg);
				long high = byteBuffer.getLong();
				long low = byteBuffer.getLong();
				UUID ownerUUID = new UUID(high, low);
				System.out.println("owner uuid: "+ownerUUID.toString());
			}
		});
	}
}