package net.evmodder.KeyBound.apis;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.UUID;
import net.evmodder.EvLib.Command;
import net.evmodder.EvLib.PacketHelper;
import net.evmodder.KeyBound.Main;
import net.evmodder.KeyBound.config.Configs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerModelPart;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

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
			case "104.128.56.167:14445": // EvMod
			case "104.128.56.146:14446": // Vr
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

	public static final int getServerAddressHashCode(ServerInfo serverInfo){
		if(serverInfo == null) return 0;
		return getRealServerAddress(serverInfo.address.toLowerCase()).hashCode();
	}
	public static final int getCurrentServerAddressHashCode(){
		MinecraftClient client = MinecraftClient.getInstance();
		if(client == null || client.getCurrentServerEntry() == null) return 0;
		// TODO: if connected to a proxy, figure out the server IP
		return getRealServerAddress(client.getCurrentServerEntry().address.toLowerCase()).hashCode();
	}
	public static byte[] getCurrentServerAndPlayerData(){
		UUID playerUUID = MinecraftClient.getInstance().player.getUuid();
		return PacketHelper.toByteArray(
			UUID.nameUUIDFromBytes(
				ByteBuffer.allocate(16 + Integer.BYTES)
					.putLong(playerUUID.getMostSignificantBits()).putLong(playerUUID.getLeastSignificantBits())
					.putInt(getCurrentServerAddressHashCode()).array()
			)
		);
	}

	public static final void sendChatMsg(String msg){
		if(msg.isBlank()) return;
		MinecraftClient mc = MinecraftClient.getInstance();
		if(msg.charAt(0) == '/') mc.player.networkHandler.sendChatCommand(msg.substring(1));
		else mc.player.networkHandler.sendChatMessage(msg);
	}

	public static final void sendRemoteMsg(String msg){
		String[] arr = msg.split(",");
		if(arr.length < 2){
			Main.LOGGER.error("Invalid remote msg syntax, expected 'COMMAND,UUID...' got: "+msg);
			return;
		}
		final Command command;
		try{command = Command.valueOf(arr[0].toUpperCase());}
		catch(IllegalArgumentException e){
			Main.LOGGER.error("Invalid remote msg syntax, undefined command: "+arr[0]);
			return;
		}
		final byte[] byteMsg;
		try{byteMsg = PacketHelper.toByteArray(Arrays.stream(Arrays.copyOfRange(arr, 1, arr.length)).map(UUID::fromString).toArray(UUID[]::new));}
		catch(IllegalArgumentException e){
			Main.LOGGER.error("Invalid remote msg syntax, unable to parse UUID(s): "+msg.substring(msg.indexOf(',')+1));
			return;
		}
		Main.remoteSender.sendBotMessage(command, /*udp=*/true, /*timeout=*/5000, byteMsg, /*recv=*/null);
	}

	public static final void toggleSkinLayer(PlayerModelPart part){
		final MinecraftClient client = MinecraftClient.getInstance();
		if(Configs.Hotkeys.SYNC_CAPE_WITH_ELYTRA.getBooleanValue() && part == PlayerModelPart.CAPE
				&& client.player != null && client.options.isPlayerModelPartEnabled(part)){
			ItemStack chestItem = client.player.getInventory().getArmorStack(2);
			// Don't disable cape if we just switched to an elytra
			if(Registries.ITEM.getId(chestItem.getItem()).getPath().equals("elytra")) return;
		}
		client.options.setPlayerModelPart(part, !client.options.isPlayerModelPartEnabled(part));
		client.options.sendClientSettings();
	}
}