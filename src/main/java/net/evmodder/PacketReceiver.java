package net.evmodder;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Arrays;
import java.util.UUID;

public class PacketReceiver extends Thread{
	private DatagramSocket socket;
	private boolean running;
	private byte[] buf = new byte[256];

	public PacketReceiver() throws SocketException{
		socket = new DatagramSocket(14441);
	}

	public void run(){
		running = true;
		while(running){
			DatagramPacket packet = new DatagramPacket(buf, buf.length);
			try{socket.receive(packet);}
			catch(IOException e){e.printStackTrace();}

			final byte[] data = Arrays.copyOfRange(packet.getData(), 0, packet.getLength());
			String received = new String(data);
			System.out.println("received: "+received+" (len: "+data.length+")");

			short my_id = (short) (data[0]<<8 | data[1] & 0xFF);
			System.out.println("my_id: "+my_id);

			byte[] uuidBytes = PacketHelper.decrypt(Arrays.copyOfRange(data, 2, data.length), "unique_client_key");
			DataInputStream dis = new DataInputStream(new ByteArrayInputStream(uuidBytes));
			try{
				long uuid_p1 = dis.readLong(), uuid_p2 = dis.readLong();
				UUID uuid = new UUID(uuid_p1, uuid_p2);
				System.out.println("decrypted uuid: "+uuid.toString());
			}
			catch(IOException e){e.printStackTrace();}

			InetAddress address = packet.getAddress();
			int port = packet.getPort();
			System.out.println("from: "+address.toString()+" : "+port);

//			// echo it back to sender
//			packet = new DatagramPacket(buf, buf.length, address, port);
//			try{socket.send(packet);}
//			catch(IOException e){e.printStackTrace();}
		}
		socket.close();
	}

	public static void main(String... arg){
		System.out.println("Listening...");
		try{
			PacketReceiver pc = new PacketReceiver();
			pc.run();
		}
		catch(SocketException e){e.printStackTrace();}
	}
}