package net.evmodder;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

interface PacketHelper{
	private static Cipher getCipher(String keyString, int mode) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException{
		// setup AES cipher in CBC mode with PKCS #5 padding
		// Actually, since we encoding 16 bytes (or multiple or 16), use ECB/NoPadding
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
		try{
			Cipher cipher = getCipher(keyString, Cipher.ENCRYPT_MODE);
			return cipher.doFinal(data);
		}
		catch(Exception e){
			e.printStackTrace();
			KeyBound.LOGGER.warn(e.getMessage());
			return null;
		}
	}
	public static byte[] decrypt(byte[] data, String keyString){
		try{
			Cipher cipher = getCipher(keyString, Cipher.DECRYPT_MODE);
			return cipher.doFinal(data);
		}
		catch(Exception e){
			e.printStackTrace();
			KeyBound.LOGGER.warn(e.getMessage());
			return null;
		}
	}

	public static boolean sendPacket(byte[] msg){
		try{
			DatagramSocket socket = new DatagramSocket();
			socket.send(new DatagramPacket(msg, msg.length, InetAddress.getByName("altcraft.net"), 14441));
			socket.close();
			//KeyBound.LOGGER.info("packet was sent!:\n"+new String(msg)+"\n"+msg.toString());
			return true;
		}
		catch(IOException e){
			e.printStackTrace();
			return false;
		}
	}
}