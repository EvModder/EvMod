package net.evmodder.evmod.apis;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.evmodder.EvLib.util.Command;
import net.evmodder.EvLib.util.PacketHelper;
import net.evmodder.EvLib.util.PacketHelper.MessageReceiver;

public final class RemoteServerSender{
	public static final int DEFAULT_PORT = 14441;

	private final Logger LOGGER;
	private final Supplier<Integer> CURR_SERVER_HASHCODE;

	private final LinkedList<byte[]> tcpPackets, udpPackets;
	private final LinkedList<MessageReceiver> tcpReceivers, udpReceivers;

	private String REMOTE_ADDR;
	private InetAddress addrResolved;
	private int PORT;

	private int CLIENT_ID;
	private String CLIENT_KEY;

	private final void resolveAddress(){
		if(REMOTE_ADDR == null) addrResolved = null;
		else try{addrResolved = InetAddress.getByName(REMOTE_ADDR);}
		catch(UnknownHostException e){LOGGER.warn("Server not found: "+REMOTE_ADDR);}
	}

	public final void setConnectionDetails(final String addr, final int port, final int clientId, final String clientKey){
		REMOTE_ADDR = addr; resolveAddress();
		PORT = port;
		CLIENT_ID = clientId;
		CLIENT_KEY = clientKey;
	}

	public RemoteServerSender(final Logger logger, final Supplier<Integer> serverAddrGetter){
		LOGGER = logger;
		CURR_SERVER_HASHCODE = serverAddrGetter;

		tcpPackets = new LinkedList<>();
		udpPackets = new LinkedList<>();
		tcpReceivers = new LinkedList<>();
		udpReceivers = new LinkedList<>();
	}

	// Returns a `4+message.length+16`-byte packet
	private final byte[] packageAndEncryptMessage(final Command command, final byte[/*16*n*/] message){
		final ByteBuffer bb1 = ByteBuffer.allocate(16+message.length);
		bb1.putInt(CLIENT_ID);
		bb1.putInt(command.ordinal());
		bb1.putInt(CURR_SERVER_HASHCODE.get());
		bb1.putInt((int)System.currentTimeMillis());//Truncate, since we assume ping < Integer.MAX anyway
		bb1.put(message);
		final byte[] encryptedMessage = PacketHelper.encrypt(bb1.array(), CLIENT_KEY);

		final ByteBuffer bb2 = ByteBuffer.allocate(4+encryptedMessage.length);
		bb2.putInt(CLIENT_ID);
		bb2.put(encryptedMessage);
		return bb2.array();
	}

	private final String formatTimeMillis(final long latency){
		// Output: 2s734ms
//		return TextUtils.formatTime(latency, false, "", 2, new long[]{1000, 1}, new char[]{'s', ' '}).stripTrailing()+"ms";
		// Output: 2734ms
		return latency+"ms";
	}
	private final void sendPacketSequence(final boolean udp, final long timeout){
		final LinkedList<byte[]> packetList = (udp ? udpPackets : tcpPackets);
		final LinkedList<MessageReceiver> recvList = (udp ? udpReceivers : tcpReceivers);
		final long startTs = System.currentTimeMillis();
		synchronized(packetList){
			final byte[] packet = packetList.peek();
			final MessageReceiver recv = recvList.peek();
//			LOGGER.warn("RMS: sendingPacket with len="+packet.length+", waitForReply="+(recv!=null));
			PacketHelper.sendPacket(addrResolved, PORT, udp, timeout, /*waitForReply=*/recv != null, packet, reply->{
				final long latency = System.currentTimeMillis()-startTs;
				if(latency >= timeout && reply == null) LOGGER.info("RemoteServerSender "+(udp?"UDP":"TCP")+" request timed out");
				else LOGGER.info("RMS: got "+(udp?"UDP":"TCP")+" reply (in "+formatTimeMillis(latency)+"): "
						+(reply == null ? "null" : new String(reply)+" [len="+reply.length+"]"));
				if(recv != null) recv.receiveMessage(reply);
				synchronized(packetList){
					packetList.remove();
					recvList.remove();
					if(!packetList.isEmpty()) sendPacketSequence(udp, timeout);
				}
			});
		}
	}
	public final void sendBotMessage(final Command command, final boolean udp, final long timeout, final byte[] message, final MessageReceiver recv){
		final byte[] packet = packageAndEncryptMessage(command, message);
		if(addrResolved == null) resolveAddress();
		if(addrResolved == null){LOGGER.warn("RemoteSender address could not be resolved!: "+REMOTE_ADDR); return;}
		LOGGER.warn("RMS: queuingPacket for cmd: "+command+", len="+packet.length);
		final LinkedList<byte[]> packetList = (udp ? udpPackets : tcpPackets);
		final LinkedList<MessageReceiver> recvList = (udp ? udpReceivers : tcpReceivers);
		synchronized(packetList){
			packetList.add(packet);
			recvList.add(recv);
			if(packetList.size() == 1) sendPacketSequence(udp, timeout);
		}
		resolveAddress();
	}

	public final static void main(String... args) throws IOException{
		UUID pearlUUID = UUID.fromString("a8c5dd6e-5f95-4875-9494-7c1d519ba8c8");
		UUID ownerUUID = UUID.fromString("34471e8d-d0c5-47b9-b8e1-b5b9472affa4");
//		UUID loc = new UUID(Double.doubleToRawLongBits(x), Double.doubleToRawLongBits(z));

		RemoteServerSender rss = new RemoteServerSender(LoggerFactory.getLogger("RMS"), ()->0);
		rss.setConnectionDetails("localhost", DEFAULT_PORT, 1, "some_unique_key");

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