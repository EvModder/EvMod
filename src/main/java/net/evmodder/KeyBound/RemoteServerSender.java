package net.evmodder.KeyBound;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.evmodder.EvLib.Command;
import net.evmodder.EvLib.PacketHelper;
import net.evmodder.EvLib.PacketHelper.MessageReceiver;
import net.evmodder.EvLib.TextUtils;

public final class RemoteServerSender{
	public static final int DEFAULT_PORT = 14441;
	private final Logger LOGGER;

	private final String REMOTE_ADDR;
	private final Supplier<Integer> CURR_SERVER_ADDR_HASHCODE;
	private final int PORT;
	private InetAddress addrResolved;

	private final int CLIENT_ID;
	private final String CLIENT_KEY;

	private void resolveAddress(){
		try{addrResolved = InetAddress.getByName(REMOTE_ADDR);}
		catch(UnknownHostException e){LOGGER.warn("Server not found: "+REMOTE_ADDR);}
	}

	// Returns a `4+message.length+16`-byte packet
	private byte[] packageAndEncryptMessage(final Command command, final byte[/*16*n*/] message){
		ByteBuffer bb1 = ByteBuffer.allocate(16+message.length);
		bb1.putInt(CLIENT_ID);
		bb1.putInt(command.ordinal());
		final int addressCode = CURR_SERVER_ADDR_HASHCODE.get();
		bb1.putInt(addressCode);
		bb1.putInt((int)System.currentTimeMillis());//Truncate, since we assume ping < Integer.MAX anyway
		bb1.put(message);
		byte[] encryptedMessage = PacketHelper.encrypt(bb1.array(), CLIENT_KEY);

		ByteBuffer bb2 = ByteBuffer.allocate(4+encryptedMessage.length);
		bb2.putInt(CLIENT_ID);
		bb2.put(encryptedMessage);
		return bb2.array();
	}

	private final LinkedList<byte[]> tcpPackets, udpPackets;
	private final LinkedList<MessageReceiver> tcpReceivers, udpReceivers;
	private void sendPacketSequence(final boolean udp, final long timeout){
		final LinkedList<byte[]> packetList = (udp ? udpPackets : tcpPackets);
		final LinkedList<MessageReceiver> recvList = (udp ? udpReceivers : tcpReceivers);
		final long startTs = System.currentTimeMillis();
		synchronized(packetList){
			final byte[] packet = packetList.peek();
			final MessageReceiver recv = recvList.peek();
			//Main.LOGGER.info("calling sendPacket with waitForReply="+(recv!=null));
			LOGGER.warn("RMS: sendingPacket with len="+packet.length);
			PacketHelper.sendPacket(addrResolved, PORT, udp, timeout, /*waitForReply=*/recv != null, packet, reply->{
				final long latency = System.currentTimeMillis()-startTs;
				if(latency >= timeout && reply == null) LOGGER.info("RemoteServerSender "+(udp?"UDP":"TCP")+" request timed out");
				else LOGGER.info("RMS: got "+(udp?"UDP":"TCP")+" reply (in "+TextUtils.formatTime(latency)+
						") from RS: "+(reply == null ? "null" : new String(reply)+" ["+reply.length+"]"));
				if(recv != null) recv.receiveMessage(reply);
				synchronized(packetList){
					packetList.remove();
					recvList.remove();
					if(!packetList.isEmpty()) sendPacketSequence(udp, timeout);
				}
			});
		}
	}
	public void sendBotMessage(final Command command, final boolean udp, final long timeout, final byte[] message, final MessageReceiver recv){
		final byte[] packet = packageAndEncryptMessage(command, message);
		if(addrResolved == null) resolveAddress();
		if(addrResolved == null) LOGGER.warn("RemoteSender address could not be resolved!: "+REMOTE_ADDR);
		else{
			LOGGER.warn("RMS: queuingPacket for cmd: "+command);
			final LinkedList<byte[]> packetList = (udp ? udpPackets : tcpPackets);
			final LinkedList<MessageReceiver> recvList = (udp ? udpReceivers : tcpReceivers);
			synchronized(packetList){
				packetList.add(packet);
				recvList.add(recv);
				if(packetList.size() == 1) sendPacketSequence(udp, timeout);
			}
			resolveAddress();
		}
	}

	RemoteServerSender(Logger logger, String addr, int port, int clientId, String clientKey, Supplier<Integer> serverAddrGetter){
		LOGGER = logger;
		REMOTE_ADDR = addr;
		PORT = port;
		CURR_SERVER_ADDR_HASHCODE = serverAddrGetter;
		resolveAddress();

		tcpPackets = new LinkedList<>();
		udpPackets = new LinkedList<>();
		tcpReceivers = new LinkedList<>();
		udpReceivers = new LinkedList<>();

		CLIENT_ID = clientId;
		CLIENT_KEY = clientKey;

		sendBotMessage(Command.PING, /*udp=*/false, /*timeout=*/5000, /*msg=*/new byte[0], msg->{
			LOGGER.info("Remote server responded to ping: "+(msg == null ? null : new String(msg)));
		});
	}

	public static void main(String... args) throws IOException{
		UUID pearlUUID = UUID.fromString("a8c5dd6e-5f95-4875-9494-7c1d519ba8c8");
		UUID ownerUUID = UUID.fromString("34471e8d-d0c5-47b9-b8e1-b5b9472affa4");
//		UUID loc = new UUID(Double.doubleToRawLongBits(x), Double.doubleToRawLongBits(z));

		RemoteServerSender rss = new RemoteServerSender(LoggerFactory.getLogger("RMS"), "localhost", 14441, 1, "some_unique_key", ()->0);

		byte[] storePearlOwnerMsg = ByteBuffer.allocate(32)
				.putLong(pearlUUID.getMostSignificantBits()).putLong(pearlUUID.getLeastSignificantBits())
				.putLong(ownerUUID.getMostSignificantBits()).putLong(ownerUUID.getLeastSignificantBits()).array();
		rss.sendBotMessage(Command.DB_PEARL_STORE_BY_UUID, /*udp=*/true, /*timeout=*/5000, storePearlOwnerMsg, /*recv=*/null);

		rss.sendBotMessage(Command.DB_PEARL_FETCH_BY_UUID, /*udp=*/true, /*timeout=*/5000, PacketHelper.toByteArray(pearlUUID), new MessageReceiver(){
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