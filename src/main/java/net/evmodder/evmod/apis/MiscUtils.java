package net.evmodder.evmod.apis;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.text.Normalizer;
import java.util.UUID;
import net.evmodder.EvLib.util.PacketHelper;
import net.evmodder.evmod.Main;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.entity.Entity;
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

	private static final String ADDRESS_2B2T = "2b2t.org"; // TODO: make EvMod more server-independent 
	public static final int HASHCODE_2B2T = ADDRESS_2B2T.hashCode(); // -437714968;
//	private static final boolean USE_CANONICAL_IP = false;
	public static final String getServerAddress(ServerInfo serverInfo, boolean USE_CANONICAL_IP){
		if(serverInfo == null) return null;
		final String name = Normalizer.normalize(serverInfo.name, Normalizer.Form.NFKD).toLowerCase().replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", "");
		// TODO: Sync with proxy via some API, and have it tell us what server the backend is connecting to?
		if(name.contains("2b2tproxy")) return "2b2t.org";

		String address = serverInfo.address.toLowerCase();
		switch(address){
			case "2b2t.org":
			case "connect.2b2t.org":
				return ADDRESS_2B2T;
			default:
				if(USE_CANONICAL_IP){
					final int i = address.lastIndexOf(':');
					try{
						final InetAddress addr = InetAddress.getByName(i == -1 ? address : address.substring(0, i));
						// Use canonical host name if available, otherwise use input hostname (I think it will be the same as `address`, but not sure)
						return (addr.getCanonicalHostName().equals(addr.getHostAddress()) ? addr.getHostName() : addr.getCanonicalHostName());
					}
					catch(UnknownHostException e){
						Main.LOGGER.warn("Server not found: "+address);
					}
				}
				return address;
		}
	}
	public static final String getServerAddress(){ // Will never return null
		MinecraftClient client = MinecraftClient.getInstance();
		if(client == null) return "null0";
		if(client.getCurrentServerEntry() == null){
			return client.getServer() == null ? "null1" : client.getServer().getSaveProperties().getLevelName();
		}
		return getServerAddress(client.getCurrentServerEntry(), /*useCanonical=*/false);
	}

	public static final int getServerAddressHashCode(ServerInfo serverInfo){
		return serverInfo == null ? 0 : getServerAddress(serverInfo, /*useCanonical=*/true).hashCode();
	}
	public static final int getCurrentServerAddressHashCode(){
		MinecraftClient client = MinecraftClient.getInstance();
		if(client == null || client.getCurrentServerEntry() == null) return 0;
		return getServerAddress(client.getCurrentServerEntry(), /*useCanonical=*/true).hashCode();
	}

	// TODO: on db-side, create a function that can reverse uuid -> username
	private static final UUID encodeAsUUID(final String str){
		assert str.length() <= 16;
		assert str.length() == str.getBytes().length;
		final byte[] bytes = (str+" ".repeat(16-str.length())).getBytes();
		assert bytes.length == 16;
		final ByteBuffer bb = ByteBuffer.wrap(bytes);
		return new UUID(bb.getLong(), bb.getLong());
	}

	public static final byte[] getEncodedPlayerIds(MinecraftClient client){
		final String sessionName = client.getSession().getUsername(), playerName = client.player.getGameProfile().getName();
		final UUID sessionUUID = client.getSession().getUuidOrNull(), playerUUID = client.player.getGameProfile().getId();
		final UUID usableSessionUUID = sessionUUID != null ? sessionUUID : MiscUtils.encodeAsUUID(sessionName);
		return sessionName.equals(playerName) ? PacketHelper.toByteArray(usableSessionUUID) : PacketHelper.toByteArray(usableSessionUUID, playerUUID);
	}
}