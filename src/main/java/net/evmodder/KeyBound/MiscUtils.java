package net.evmodder.KeyBound;

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

	public static final int getCurrentServerAddressHashCode(){
		MinecraftClient client = MinecraftClient.getInstance();
		return client == null || client.getCurrentServerEntry() == null ? 0 : client.getCurrentServerEntry().address.hashCode();
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