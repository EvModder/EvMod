package net.evmodder.EvLib;

import java.io.IOException;
import java.io.InputStream;
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

	public interface MessageReceiver{void receiveMessage(byte[] message);}

	public static void sendPacket(InetAddress addr, int port, boolean udp, byte[] msg, MessageReceiver recv, long timeout){
		if(udp){//synchronized(socketUDP){
			if(socketUDP == null){
//				new Thread(()->{
//					for(int i=0; i<BIND_ATTEMPTS && socketUDP == null; ++i){
//						try{socketUDP = new DatagramSocket(port);}
//						catch(/*BindException | */SocketException e){
//							try{Thread.sleep(BIND_REATTEMPT_DELAY);}
//							catch(InterruptedException ex){ex.printStackTrace();}
//						}
//						//catch(SocketException e){e.printStackTrace(); break;}
//					}
//					if(socketUDP == null){
//						LOGGER.severe("Failed to bind to socket (UDP) !!");
//						return;
//					}
					try{
						socketUDP = new DatagramSocket(port);
						socketUDP.setBroadcast(false);
						socketUDP.setTrafficClass(/*IPTOS_LOWDELAY=*/0x10);//socket.setOption(StandardSocketOptions.IP_TOS, 0x10);
						socketUDP.setSendBufferSize(MAX_PACKET_SIZE_SEND);
						socketUDP.setReceiveBufferSize(MAX_PACKET_SIZE_RECV); // Minimum it allows is 1024 bytes. Putting any value below (like 64) still gives 1024
					}
					catch(SocketException e){e.printStackTrace(); return;}
					try{
						socketUDP.setOption(ExtendedSocketOptions.IP_DONTFRAGMENT, true);
//						socketUDP.send(new DatagramPacket(msg, msg.length, addr, port));
					}
					catch(/*SocketException*/IOException e){e.printStackTrace(); return;}
//					sendPacket(addr, port, udp, msg, recv, timeout);
//				}).start();
//				return;
			}
			try{socketUDP.send(new DatagramPacket(msg, msg.length, addr, port));}
			catch(IOException e){e.printStackTrace(); return;}

			if(recv != null) new Thread(()->{
				byte[] reply = new byte[MAX_PACKET_SIZE_RECV];
				try{
					socketUDP.setSoTimeout((int)timeout);
					socketUDP.receive(new DatagramPacket(reply, reply.length));
				}
				catch(IOException e){
					if(e instanceof SocketTimeoutException) LOGGER.warning("Waiting for UDP response timed out");
					else e.printStackTrace();
					reply = null;
				}
				//LOGGER.info("Roundtrip delay (UDP): "+(System.currentTimeMillis()-startTime));
				recv.receiveMessage(reply);
				//try{socketUDP.disconnect();}catch(UncheckedIOException e){e.printStackTrace(); socketUDP=null;}
			}).start();
		}//}
		else{
//			if(socketTCP == null || socketTCP.isClosed() || !socketTCP.isConnected()){
				try{
					socketTCP = new Socket();
					socketTCP.setPerformancePreferences(2, 1, 0);//TODO: Java standard library has not implemented this yet???
					socketTCP.setTrafficClass(/*IPTOS_LOWDELAY=*/0x10);
					socketTCP.setTcpNoDelay(true);
					//socketTCP.setOption(ExtendedSocketOptions.IP_DONTFRAGMENT, true);//java.lang.UnsupportedOperationException
//					socketTCP.setSendBufferSize(64);   //TODO: find a way to resize BEFORE connect, not after, without having it overridden by server socket
//					socketTCP.setReceiveBufferSize(64);//TODO: find a way to resize BEFORE connect, not after, without having it overridden by server socket
				}
				catch(SocketException e){e.printStackTrace(); return;}
//			}
			new Thread(()->{
				final long startTime = System.currentTimeMillis();
				byte[] reply = null;
				try{
					socketTCP.connect(new InetSocketAddress(addr, port), (int)timeout);
					socketTCP.setSendBufferSize(MAX_PACKET_SIZE_SEND);
					socketTCP.setReceiveBufferSize(MAX_PACKET_SIZE_RECV);// Minimum it allows is 1024 bytes. Putting any value below (like 64) still gives 1024
	
					socketTCP.getOutputStream().write(msg);
					if(recv == null){socketTCP.close(); return;}
	
					final InputStream is = socketTCP.getInputStream();
					while(!socketTCP.isClosed() && is.available() == 0 && System.currentTimeMillis() - startTime < timeout
							&& !Thread.currentThread().isInterrupted()){/*wait for reply*/};
					if(is.available() > 0) reply = is.readAllBytes();
					socketTCP.close();
				}
				catch(ConnectException e){LOGGER.severe("Failed to connect to RemoteServer"); return;}
				catch(IOException e){e.printStackTrace(); return;}
				//LOGGER.info("Roundtrip delay (TCP): "+(System.currentTimeMillis()-startTime));
				recv.receiveMessage(reply);
			}).start();
		}
	}

	public static byte[] toByteArray(UUID... uuids){
		ByteBuffer bb = ByteBuffer.allocate(uuids.length*16);
		for(UUID uuid : uuids){
			bb.putLong(uuid.getMostSignificantBits());
			bb.putLong(uuid.getLeastSignificantBits());
		}
		return bb.array();
	}
}