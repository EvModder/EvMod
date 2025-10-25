package net.evmodder.EvLib;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.ConnectException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.UUID;
import java.util.logging.Logger;
import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import jdk.net.ExtendedSocketOptions;

public final class PacketHelper{
	private static final int MAX_PACKET_SIZE_SEND = 4+16+128*128; //=16384. 2nd biggest: 4 + [4+4+8+16+16]
	private static final int MAX_PACKET_SIZE_RECV = 16;
//	private static final int BIND_ATTEMPTS = 5;
//	private static final int BIND_REATTEMPT_DELAY = 100;
	private static Socket socketTCP;
	private static DatagramSocket socketUDP;
	private static int lastPortTCP, lastPortUDP;
	private static long lastTimeoutTCP, lastTimeoutUDP;
	private static byte[] replyUDP/*, replyTCP*/;

	private static final Logger LOGGER = Logger.getLogger("EvLibMod-PacketHelper");
//	static{
//		try{
//			Object logger = Class.forName("org.slf4j.LoggerFactory").getMethod("getLogger", String.class).invoke(null, "EvLibMod");
//		}
//		catch(ClassNotFoundException | IllegalAccessException | InvocationTargetException | NoSuchMethodException | SecurityException e){}
//	}

	private static Cipher getCipher(String keyString, int mode)
			throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException{
		// setup AES cipher in CBC mode with PKCS #5 padding
		// Actually, since we encode 16 bytes (or multiples or 16), use ECB/NoPadding
		Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");

//		// setup an IV (initialization vector) that should be
//		// randomly generated for each input that's encrypted
//		byte[] iv = new byte[cipher.getBlockSize()];
//		new SecureRandom().nextBytes(iv);
//		IvParameterSpec ivSpec = new IvParameterSpec(iv);

		// hash keyString with SHA-256 and crop the output to 128-bit for key
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		digest.update(keyString.getBytes());
		byte[] key = new byte[16];
		System.arraycopy(digest.digest(), 0, key, 0, key.length);
		SecretKeySpec keySpec = new SecretKeySpec(key, "AES");

		cipher.init(mode, keySpec/*, ivSpec*/);
		return cipher;
	}
	public static byte[] encrypt(byte[] data, String keyString){
		if(data.length % 16 != 0){
			LOGGER.severe("Invalid message length, must be multiple of 16, got: "+data.length);
			return null;
		}
		try{
			Cipher cipher = getCipher(keyString, Cipher.ENCRYPT_MODE);
			return cipher.doFinal(data);
		}
		catch(Exception e){
			e.printStackTrace();
			LOGGER.warning(e.getMessage());
			return null;
		}
	}
	public static byte[] decrypt(byte[] data, String keyString){
		if(data.length % 16 != 0){
			LOGGER.warning("Invalid message length, must be multiple of 16, got: "+data.length);
			return null;
		}
		try{
			Cipher cipher = getCipher(keyString, Cipher.DECRYPT_MODE);
			return cipher.doFinal(data);
		}
		catch(Exception e){
			e.printStackTrace();
			LOGGER.warning(e.getMessage());
			return null;
		}
	}

	public static final void writeShort(OutputStream out, short s) throws IOException{
		out.write((byte)((s >> 8) & 0xff));
		out.write((byte)(s & 0xff));
		//out.write(ByteBuffer.allocate(2).putShort((short)msg.length).array());
	}
	public static final short readShort(InputStream is) throws IOException{
		return (short)(((is.read() & 0xFF) << 8) | (is.read() & 0xFF));
		//return ByteBuffer.wrap(is.readNBytes(2)).getShort();
	}

	public interface MessageReceiver{void receiveMessage(byte[] message);}

	private static final void sendPacket(final InetAddress addr, final int port, final boolean udp, final long timeout,
			final boolean waitForReply, final byte[] msg, final MessageReceiver callback, final boolean isFallbackPort){
		if(callback == null && waitForReply){
			LOGGER.severe("PacketHelper: waitForReply=true but recv is null!");
		}

		if(udp){
			if(addr.isLoopbackAddress()){
				LOGGER.severe("UDP does not work on a local address due to feedback loops");
				if(callback != null) callback.receiveMessage(null);
				return;
			}
			if(socketUDP == null || socketUDP.isClosed() || lastPortUDP != port){
				lastPortUDP = port;
				try{
					socketUDP = new DatagramSocket(port);
					socketUDP.setBroadcast(false);
					socketUDP.setTrafficClass(/*IPTOS_LOWDELAY=*/0x10);//socket.setOption(StandardSocketOptions.IP_TOS, 0x10);
					socketUDP.setSendBufferSize(MAX_PACKET_SIZE_SEND);
					socketUDP.setReceiveBufferSize(MAX_PACKET_SIZE_RECV); // Minimum it allows is 1024 bytes. Putting any value below (like 64) still gives 1024
				}
				catch(BindException e){
					if(isFallbackPort){
						LOGGER.warning("Fallback port is also unavailable! (UDP)");
						if(callback != null) callback.receiveMessage(null);
					}
					else{
						LOGGER.warning("Port "+port+" is unavailable! (UDP)");
						sendPacket(addr, port+1, udp, timeout, waitForReply, msg, callback, /*isFallbackPort=*/true);
					}
					return;
				}
				catch(SocketException e){e.printStackTrace(); return;}
				try{socketUDP.setOption(ExtendedSocketOptions.IP_DONTFRAGMENT, true);}
				catch(IOException e){e.printStackTrace(); return;}
			}
			if(lastTimeoutUDP != timeout){
				lastTimeoutUDP = timeout;
				try{socketUDP.setSoTimeout((int)timeout);}
				catch(SocketException e){e.printStackTrace(); return;}
			}
			try{socketUDP.send(new DatagramPacket(msg, msg.length, addr, isFallbackPort ? port-1 : port));}
			catch(IOException e){e.printStackTrace(); return;}

			if(!waitForReply || callback == null){
				if(isFallbackPort) socketUDP.close();
				if(callback != null) callback.receiveMessage(null);
				return;
			}
			//LOGGER.info("sendPacket() is waiting for UDP reply");
			new Thread(()->{
				if(replyUDP == null) replyUDP = new byte[MAX_PACKET_SIZE_RECV];
				final DatagramPacket packet = new DatagramPacket(replyUDP, MAX_PACKET_SIZE_RECV);
				try{socketUDP.receive(packet);}
				catch(IOException e){
					if(e instanceof SocketTimeoutException){
						LOGGER.warning("Waiting for UDP response timed out");
						callback.receiveMessage(null);
					}
					else e.printStackTrace();
					return;
				}
				finally{
					if(isFallbackPort) socketUDP.close();
				}
				//LOGGER.info("got UDP reply: "+new String(replyUDP)+", len="+packet.getLength());
				callback.receiveMessage(Arrays.copyOf(replyUDP, packet.getLength()));
			}).start();
		}
		else{
			if(msg.length > Short.MAX_VALUE){LOGGER.severe("sendPacket() called with invalid message (length > Short.MAX_VALUE)!"); return;}
			if(socketTCP == null || socketTCP.isClosed() || !socketTCP.isConnected()){
				try{
					socketTCP = new Socket();
					socketTCP.setPerformancePreferences(2, 1, 0);//TODO: Java standard library has not implemented this yet???
					socketTCP.setTrafficClass(/*IPTOS_LOWDELAY=*/0x10);
//					socketTCP.setTcpNoDelay(true);

					//socketTCP.setOption(ExtendedSocketOptions.IP_DONTFRAGMENT, true);//java.lang.UnsupportedOperationException
//					socketTCP.setSendBufferSize(64);   //TODO: find a way to resize BEFORE connect, not after, without having it overridden by server socket
//					socketTCP.setReceiveBufferSize(64);//TODO: find a way to resize BEFORE connect, not after, without having it overridden by server socket
				}
				catch(SocketException e){e.printStackTrace(); return;}
			}

			if(lastTimeoutTCP != timeout){
				lastTimeoutTCP = timeout;
//				try{socketTCP.setSoTimeout((int)timeout);}
//				catch(SocketException e){e.printStackTrace(); return;}
			}

			new Thread(()->{
				final long stopWaitingTs = timeout == 0 ? Long.MAX_VALUE : System.currentTimeMillis() + timeout;
				if(socketTCP.isClosed() || !socketTCP.isConnected() || lastPortTCP != port || socketTCP.isOutputShutdown()){
					lastPortTCP = port;
					try{socketTCP.connect(new InetSocketAddress(addr, port), (int)timeout);}
					catch(ConnectException e){LOGGER.severe("Failed to connect to RemoteServer"); return;}
					catch(IOException e){e.printStackTrace(); return;}

					// These need to be called AFTER socket.connect(), apparently.
					try{
						socketTCP.setSendBufferSize(MAX_PACKET_SIZE_SEND);
						socketTCP.setReceiveBufferSize(MAX_PACKET_SIZE_RECV);// Minimum it allows is 1024 bytes. Putting any value below (like 64) still gives 1024
					}
					catch(SocketException e){e.printStackTrace(); return;}
				}
				try{
					OutputStream out = socketTCP.getOutputStream();
					writeShort(out, (short)msg.length);
//					out.write((msg.length>>8) & 0xff); out.write(msg.length & 0xff);
					out.write(msg);
					out.flush();
				}
				catch(IOException e){
					e.printStackTrace();
					try{socketTCP.close();}catch(IOException e1){e1.printStackTrace();} socketTCP = null; // Reset socket
					return;
				}

				if(!waitForReply || callback == null){if(callback != null) callback.receiveMessage(null); return;}
				//LOGGER.info("sendPacket() is waiting for TCP reply");
				//if(replyTCP == null) replyTCP = new byte[MAX_PACKET_SIZE_RECV];
				byte[] reply = null;
				try{
					final InputStream is = socketTCP.getInputStream();
					while(!socketTCP.isClosed() && is.available() < 2 && !Thread.currentThread().isInterrupted() && System.currentTimeMillis() < stopWaitingTs)
						/*...wait...*/;//Thread.yield();
					if(is.available() < 2){
						if(System.currentTimeMillis() > stopWaitingTs) LOGGER.warning("Waiting for TCP response timed out (BEFORE receiving response len)");
						else if(socketTCP.isClosed()) LOGGER.warning("Socket closed while waiting for TCP response (BEFORE receiving response len)");
						else LOGGER.warning("Socket read interrupted while waiting for TCP response (BEFORE receiving response len)");
						callback.receiveMessage(null);
						if(!socketTCP.isClosed()) socketTCP.close();
						socketTCP = null;
						return;
					}
					final short len = readShort(is);
					if(len == 0){
						LOGGER.warning("Got TCP empty reply (this shouldn't be reachable!)");
						callback.receiveMessage(null);
						return;
					}
					if(len > socketTCP.getReceiveBufferSize()){
						LOGGER.warning("Length of incoming response is too big, adjusting buffer size");
						socketTCP.setReceiveBufferSize(len);
					}
					while(!socketTCP.isClosed() && is.available() < len && !Thread.currentThread().isInterrupted() && System.currentTimeMillis() < stopWaitingTs)
						/*...wait...*/;//Thread.yield();
					if(is.available() >= len){
						if(is.available() > len){LOGGER.severe("TCP response is too long! Expected:"+len+", Got:"+is.available());}
						//is.read(replyTCP, /*off=*/0, len);
						reply = is.readNBytes(len);
					}
				}
				catch(IOException e){e.printStackTrace(); return;}
				if(reply == null){
					if(System.currentTimeMillis() > stopWaitingTs) LOGGER.warning("Waiting for TCP response timed out (AFTER receiving response len)");
					else if(socketTCP.isClosed()) LOGGER.warning("Socket closed while waiting for TCP response (AFTER receiving response len)");
					else LOGGER.warning("Socket read interrupted while waiting for TCP response (AFTER receiving response len)");
				}
				//else LOGGER.info("Got TCP reply: "+new String(reply)+", len="+reply.length+", in "+TextUtils.formatTime(System.currentTimeMillis()-startTime));
				callback.receiveMessage(reply);
			}).start();
		}
	}
	public static final void sendPacket(final InetAddress addr, final int port, final boolean udp, final long timeout,
			final boolean waitForReply, final byte[] msg, final MessageReceiver callback){
		sendPacket(addr, port, udp, timeout, waitForReply, msg, callback, /*isFallbackPort=*/false);
	}

	public static byte[] toByteArray(UUID... uuids){
		ByteBuffer bb = ByteBuffer.allocate(uuids.length*16);
		for(UUID uuid : uuids) bb.putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits());
		return bb.array();
	}
}