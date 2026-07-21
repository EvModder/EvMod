package net.evmodder.evmod.apis;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.text.Normalizer;
import java.util.UUID;
import net.evmodder.EvLib.util.PacketHelper;
import net.evmodder.evmod.Main;
import net.evmodder.evmod.mixin.AccessorProjectileEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class MiscUtils{
	public static final boolean hasMoved(final Entity entity){
		return entity.xo != entity.getX() || entity.yo != entity.getY() || entity.zo != entity.getZ();
	}

	public static final boolean isLookingAt(final Entity entity, final Entity player){
		final Vec3 vec3d = player.getViewVector(1f).normalize();
		Vec3 vec3d2 = new Vec3(entity.getX() - player.getX(), entity.getEyeY() - player.getEyeY(), entity.getZ() - player.getZ());
		final double d = vec3d2.length();
		vec3d2 = new Vec3(vec3d2.x / d, vec3d2.y / d, vec3d2.z / d);//normalize
		final double e = vec3d.dot(vec3d2);
		return e > 1.0D - 0.03D / d ? /*client.player.canSee(entity)*/true : false;
	}

	public static final byte getDimensionId(final Level world){
		if(world == null) return -1;
		else if(world.dimension() == Level.OVERWORLD) return 0;
		else if(world.dimension() == Level.NETHER) return 1;
		else if(world.dimension() == Level.END) return 2;
		else return 3;
	}

	public static final long toggleLSB(final long input){return input ^ 1l;}
	public static final long setLSB(final long input, final boolean set){return (input & ~1l) | (set ? 1l : 0l);}
//	public static final long setMSB(final long input, final boolean set){return (input & ~(1l << 63)) | (set ? 1l << 63 : 0l);}

	private static final String ADDRESS_2B2T = "2b2t.org"; // TODO: make EvMod more server-independent 
	public static final int HASHCODE_2B2T = ADDRESS_2B2T.hashCode(); // -437714968;
	private static final String getServerAddress(final ServerData serverInfo, final boolean USE_CANONICAL_IP){
//		if(serverInfo == null) return null;
		final String name = Normalizer.normalize(serverInfo.name, Normalizer.Form.NFKD).toLowerCase().replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", "");
		// TODO: Sync with proxy via some API, and have it tell us what server the backend is connecting to?
		if(name.contains("2b2tproxy")) return ADDRESS_2B2T;

		final String address = serverInfo.ip.toLowerCase();
		switch(address){
			case ADDRESS_2B2T: // "2b2t.org"
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
	private static final String getServerAddress(final boolean USE_CANONICAL_IP){
		final Minecraft client = Minecraft.getInstance();
//		if(client == null) return "null0";
		assert client != null;
		final ServerData serverInfo = client.getCurrentServer();
		return serverInfo != null ? getServerAddress(serverInfo, USE_CANONICAL_IP)
			: client.getSingleplayerServer() != null ? client.getSingleplayerServer().getWorldData().getLevelName() : null;
	}
	public static final String getServerAddress(){return getServerAddress(/*useCanonical=*/false);}

	public static final int getServerAddressHashCode(){
		final String address = getServerAddress(/*useCanonical=*/true);
		return switch(address){
			case null -> 0;
			case ADDRESS_2B2T -> HASHCODE_2B2T; // Tiny optimization (no need to compute hash), since we store it anyway
			default -> address.hashCode();
		};
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

	public static final byte[] getEncodedPlayerIds(final Minecraft client){
		final String sessionName = client.getUser().getName(), playerName = client.player.getGameProfile().name();
		final UUID sessionUUID = client.getUser().getProfileId(), playerUUID = client.player.getGameProfile().id();
		final UUID usableSessionUUID = sessionUUID != null ? sessionUUID : MiscUtils.encodeAsUUID(sessionName);
		return sessionName.equals(playerName) ? PacketHelper.toByteArray(usableSessionUUID) : PacketHelper.toByteArray(usableSessionUUID, playerUUID);
	}

	public static final UUID getPearlUUID(final ThrownEnderpearl epearl){
		var ref = ((AccessorProjectileEntity)epearl).getOwnerReference();
		return ref == null ? null : ref.getUUID();
	}
	public static final void setPearlUUID(final ThrownEnderpearl epearl, final UUID owner){
		((AccessorProjectileEntity)epearl).setOwnerReference(EntityReference.of(owner));
	}
}