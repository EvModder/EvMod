package net.evmodder.evmod.apis;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.UUID;
import net.evmodder.EvLib.util.Command;
import net.evmodder.EvLib.util.PacketHelper;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.Main;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerModelPart;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Vec3d;

public class MiscUtils{
	public static final boolean hasMoved(Entity entity){
		return entity.prevX != entity.getX() || entity.prevY != entity.getY() || entity.prevZ != entity.getZ();
	}

	public static final boolean isLookingAt(Entity entity, Entity player){
		Vec3d vec3d = player.getRotationVec(1.0F).normalize();
		Vec3d vec3d2 = new Vec3d(entity.getX() - player.getX(), entity.getEyeY() - player.getEyeY(), entity.getZ() - player.getZ());
		double d = vec3d2.length();
		vec3d2 = new Vec3d(vec3d2.x / d, vec3d2.y / d, vec3d2.z / d);//normalize
		double e = vec3d.dotProduct(vec3d2);
		return e > 1.0D - 0.03D / d ? /*client.player.canSee(entity)*/true : false;
	}

	/*public static String getModVersionString(String modId){
		for(ModContainer container : FabricLoader.getInstance().getAllMods()){
			if(container.getMetadata().getId().equals(modId)){
				return container.getMetadata().getVersion().getFriendlyString();
			}
		}
		return "?";
	}*/

	public static final int HASHCODE_2B2T = -437714968;//"2b2t.org".hashCode() // TODO: make mod more server-independent 
	private static final int getRealServerAddressHashCode(String address){
		switch(address){
			case "2b2t.org":
			case "connect.2b2t.org":
				return HASHCODE_2B2T;
			default:
				final int i = address.lastIndexOf(':');
				try{
					final InetAddress addr = InetAddress.getByName(i == -1 ? address : address.substring(0, i));
					// Use canonical host name if available, otherwise use input hostname (I think it will be the same as `address`, but not sure)
					return (addr.getCanonicalHostName().equals(addr.getHostAddress()) ? addr.getHostName() : addr.getCanonicalHostName()).hashCode();
				}
				catch(UnknownHostException e){
					Main.LOGGER.warn("Server not found: "+address);
				}
				return address.hashCode();
		}
	}

	public static final int getServerAddressHashCode(ServerInfo serverInfo){
		if(serverInfo == null) return 0;
		final String name = Normalizer.normalize(serverInfo.name, Normalizer.Form.NFKD).toLowerCase().replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", "");
		// TODO: if connected to a proxy, figure out the server IP
		if(name.contains("2b2tproxy")) return HASHCODE_2B2T;
		return getRealServerAddressHashCode(serverInfo.address.toLowerCase());
	}
	public static final int getCurrentServerAddressHashCode(){
		MinecraftClient client = MinecraftClient.getInstance();
		if(client == null || client.getCurrentServerEntry() == null) return 0;
		return getServerAddressHashCode(client.getCurrentServerEntry());
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