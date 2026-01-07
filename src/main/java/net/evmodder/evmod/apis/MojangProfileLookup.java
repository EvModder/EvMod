package net.evmodder.evmod.apis;

import java.util.UUID;
import com.mojang.authlib.yggdrasil.ProfileResult;
import net.evmodder.EvLib.util.LoadingCache;
import net.evmodder.EvLib.util.WebHook;
import net.evmodder.evmod.Main;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;

public class MojangProfileLookup{
	public static final String NAME_404 = "n[404]", NAME_U_404 = "u[404]", NAME_LOADING = "Loading name...", NAME_U_LOADING = "Loading UUID...";
	public static final UUID UUID_404 = null, UUID_LOADING = new UUID(114141414, 282828282);

	private static final MinecraftClient client = MinecraftClient.getInstance();

	public static final LoadingCache<UUID, String> nameLookup = new LoadingCache<>(NAME_404, NAME_LOADING){
		@Override protected String loadSyncOrNull(UUID key){
			ClientPlayNetworkHandler networkHandler = client.getNetworkHandler();
			if(networkHandler != null){
				PlayerListEntry entry = networkHandler.getPlayerListEntry(key);
				if(entry != null) return entry.getProfile().getName();
			}
			return null;
		}
		@Override protected String load(UUID key){
			//Main.LOGGER.info("oof, web request D:");
			ProfileResult pr = client.getSessionService().fetchProfile(key, /*requireSecure=*/false);
			if(pr == null || pr.profile() == null || pr.profile().getName() == null){
				Main.LOGGER.error("Unable to find name for player UUID: "+key.toString());
				return NAME_404;
			}
			else{
				uuidLookup.putIfAbsent(pr.profile().getName().toLowerCase(), key);
				return pr.profile().getName();
			}
		}
	};
	public static final LoadingCache<String, UUID> uuidLookup = new LoadingCache<>(UUID_404, UUID_LOADING){
		@Override protected UUID loadSyncOrNull(String key){
			//Main.LOGGER.info("fetch name called for uuid: "+key);
			ClientPlayNetworkHandler networkHandler = client.getNetworkHandler();
			if(networkHandler != null){
				for(PlayerListEntry entry : networkHandler.getPlayerList()){
					if(entry.getProfile().getName().equalsIgnoreCase(key)) return entry.getProfile().getId();
				}
			}
			return null;
		}
		@Override protected UUID load(String key){
			if(key.isBlank()/*!key.matches("[a-zA-Z0-9]+")*/){
				Main.LOGGER.error("MojangProfileLookup: UUID load() called for an invalid username!");
				return UUID_404;
			}
			String data = WebHook.getReadURL("https://api.mojang.com/users/profiles/minecraft/"+key);
			if(data == null) return UUID_404;
			final int uuidStart = data.indexOf("\"id\":\"")+6;
			final int uuidEnd = data.indexOf("\"", uuidStart+2); // +2 instead of +1 because we assume name field is non-empty (has at least 1 char)
			if(uuidStart == -1 || uuidEnd == -1) return UUID_404; // No account found for this UUI
			String uuidStr = data.substring(uuidStart, uuidEnd);
			if(uuidStr.indexOf('-') == -1) uuidStr = uuidStr.replaceFirst(
					"(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5");
			final UUID uuid = UUID.fromString(uuidStr);
			final int nameStart = data.indexOf("\"name\":\"")+8;
			final int nameEnd = data.indexOf("\"", nameStart+2); // +2 instead of +1 because we assume name field is non-empty (has at least 1 char)
			if(nameStart == -1 || nameEnd == -1){Main.LOGGER.error("Unexpected API response (no 'name' field): "+data); return uuid;}
			nameLookup.putIfAbsent(uuid, data.substring(nameStart, nameEnd));
			return uuid;
		}
	};
	static{
		nameLookup.putIfAbsent(UUID_404, NAME_U_404);
		nameLookup.putIfAbsent(UUID_LOADING, NAME_U_LOADING);
	}

	public static final void prefetchName(UUID uuid){nameLookup.get(uuid, null);}
	public static final String nameOrUUID(UUID uuid){
		final String name = nameLookup.get(uuid, null);
		return name == NAME_404 || name == NAME_LOADING ? uuid.toString() : name;
	}
}