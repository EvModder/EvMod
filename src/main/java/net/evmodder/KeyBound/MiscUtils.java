package net.evmodder.KeyBound;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.UUID;
import net.evmodder.EvLib.Command;
import net.evmodder.EvLib.PacketHelper;
import net.evmodder.KeyBound.keybinds.Keybind;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;

public class MiscUtils{
	public static final boolean hasMoved(Entity entity){
		return entity.prevX != entity.getX() || entity.prevY != entity.getY() || entity.prevZ != entity.getZ();
	}

	private static final String getRealServerAddress(String address){
		switch(address){ // TODO: remove all this hacky BS
			case "2b2t.org":
			case "connect.2b2t.org":
			case "152.228.212.15:25572": // 0crit
			case "152.228.212.15:25566": // Minuscul
			case "152.228.212.15:25567": // JalvaBot
			case "152.228.212.15:25565": // Jalvaviel
			case "209.44.205.3:14445": // EvMD
			case "104.128.56.167:14445": // EvModder
				return "2b2t.org";
			default:
				final int i = address.lastIndexOf(':');
				try{
					final InetAddress addr = InetAddress.getByName(i == -1 ? address : address.substring(0, i));
					// Use canonical host name if available, otherwise use input hostname (I think it will be the same as `address`, but not sure)
					return addr.getCanonicalHostName().equals(addr.getHostAddress()) ? addr.getHostName() : addr.getCanonicalHostName();
				}
				catch(UnknownHostException e){
					Main.LOGGER.warn("Server not found: "+address);
				}
				return address;
		}
	}

	public static final int getCurrentServerAddressHashCode(){
		MinecraftClient client = MinecraftClient.getInstance();
		if(client == null || client.getCurrentServerEntry() == null) return 0;
		// TODO: if connected to a proxy, figure out the server IP
		return getRealServerAddress(client.getCurrentServerEntry().address.toLowerCase()).hashCode();
	}

	private static Command parseCommand(String str){
		try{return Command.valueOf(str);}
		catch(IllegalArgumentException e){return null;}
	}
	public static final void registerRemoteMsgKeybinds(HashMap<String, String> messages){
		if(messages != null) messages.forEach((key, message) -> {
			String[] arr = message.split(",");
			Command command = parseCommand(arr[0].toUpperCase());
			if(command == null){
				Main.LOGGER.error("Undefined command in config keybound.txt: "+arr[0]);
			}
			else{
				final byte[] byteMsg = PacketHelper.toByteArray(Arrays.stream(Arrays.copyOfRange(arr, 1, arr.length)).map(UUID::fromString).toArray(UUID[]::new));
				new Keybind(key, ()->Main.remoteSender.sendBotMessage(command, /*udp=*/true, /*timeout=*/5000, byteMsg, /*recv=*/null));
			}
		});
	}
}