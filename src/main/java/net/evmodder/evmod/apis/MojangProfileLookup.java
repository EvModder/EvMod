package net.evmodder.evmod.apis;

import java.io.IOException;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.google.gson.JsonParser;
import net.evmodder.EvLib.util.LoadingCache;
import net.evmodder.EvLib.util.WebHook;
import net.evmodder.EvLib.util.WebHook.ReadResponse;
import net.evmodder.evmod.Main;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;

import static net.evmodder.evmod.apis.MojangProfileLookupConstants.*;

public class MojangProfileLookup{
	private static final Minecraft client = Minecraft.getInstance();
	private static final long PROFILE_REFRESH_MILLIS = 24*60*60*1000L, RETRY_MILLIS = 30_000, MAX_RETRY_MILLIS = 60*60*1000L, RETRY_SPACING_MILLIS = 500;

	// When a cached name is next due a lookup. Names with no entry (cached from a config file) are refreshed on first use.
	private static final ConcurrentHashMap<UUID, Long> refreshAt = new ConcurrentHashMap<>();

	public static final LoadingCache<UUID, String> nameLookup = new LoadingCache<>(NAME_LOADING){
		// Transient failures pause every lookup: they are rarely specific to one UUID.
		private volatile long pausedUntil;
		private int failures, slots;
		@Override protected String loadSyncOrNull(UUID key){
			final long now = System.currentTimeMillis();
			ClientPacketListener networkHandler = client.getConnection();
			if(networkHandler != null){
				PlayerInfo entry = networkHandler.getPlayerInfo(key);
				if(entry != null){
					if(now >= refreshAt.getOrDefault(key, 0L)) refreshAt.put(key, now+PROFILE_REFRESH_MILLIS);
					return entry.getProfile().name();
				}
			}
			// Refresh only when accessed after expiry; keep displaying the cached name/status meanwhile.
			if(now >= refreshAt.getOrDefault(key, 0L) && getCached(key) != null) refresh(key);
			return null;
		}
		/** When the caller may retry. Slots are spaced out so that retries do not all arrive as the pause ends. */
		private synchronized long retrySlot(final boolean failed){
			final long now = System.currentTimeMillis();
			if(failed && now >= pausedUntil){  // A request sent before the pause began does not extend it.
				pausedUntil = now+Math.min(RETRY_MILLIS << Math.min(failures++, 16), MAX_RETRY_MILLIS);
				slots = 0;
			}
			return pausedUntil+RETRY_SPACING_MILLIS*slots++;
		}
		@Override protected String load(UUID key){
			long retryAt;
			if(System.currentTimeMillis() < pausedUntil) retryAt = retrySlot(/*failed=*/false);
			else try{
				// Authlib caches failed fetchProfile(false) results for six hours, obscuring transient errors.
				final ReadResponse response = WebHook.getReadResponse("https://api.minecraftservices.com/minecraft/profile/lookup/"+key, 5_000);
				final int status = response.statusCode();
				if(status == 200){
					// Not checked against naming rules: some legacy names break them.
					final String name = JsonParser.parseString(response.body()).getAsJsonObject().get("name").getAsString();
					synchronized(this){failures = 0;}
					refreshAt.put(key, System.currentTimeMillis()+PROFILE_REFRESH_MILLIS);
					uuidLookup.put(name.toLowerCase(Locale.ROOT), key);
					return name;
				}
				// Other HTTP errors (such as 404) are specific to this UUID and unlikely to change soon.
				retryAt = status == 408 || status == 429 || status >= 500 ? retrySlot(/*failed=*/true) : System.currentTimeMillis()+PROFILE_REFRESH_MILLIS;
				Main.LOGGER.warn("Mojang name lookup failed for {} (HTTP {})", key, status);
			}
			catch(IOException | RuntimeException e){
				retryAt = retrySlot(/*failed=*/true);
				Main.LOGGER.warn("Mojang name lookup failed for {}: {}", key, e.toString());
			}
			refreshAt.put(key, retryAt);
			final String cached = getCached(key);
			return cached == null ? NAME_404 : cached;  // Keep showing a name that is already known.
		}
	};
	public static final LoadingCache<String, UUID> uuidLookup = new LoadingCache<>(UUID_LOADING){
		@Override protected UUID loadSyncOrNull(String key){
			//Main.LOGGER.info("fetch name called for uuid: "+key);
			ClientPacketListener networkHandler = client.getConnection();
			if(networkHandler != null){
				for(PlayerInfo entry : networkHandler.getListedOnlinePlayers()){
					if(entry.getProfile().name().equalsIgnoreCase(key)) return entry.getProfile().id();
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
			final int uuidEnd = data.indexOf("\"", uuidStart+2);  // +2 instead of +1 because we assume name field is non-empty (has at least 1 char)
			if(uuidStart == -1 || uuidEnd == -1) return UUID_404;  // No account found for this UUI
			String uuidStr = data.substring(uuidStart, uuidEnd);
			if(uuidStr.indexOf('-') == -1) uuidStr = uuidStr.replaceFirst(
					"(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5");
			final UUID uuid = UUID.fromString(uuidStr);
			final int nameStart = data.indexOf("\"name\":\"")+8;
			final int nameEnd = data.indexOf("\"", nameStart+2);  // +2 instead of +1 because we assume name field is non-empty (has at least 1 char)
			if(nameStart == -1 || nameEnd == -1){Main.LOGGER.error("Unexpected API response (no 'name' field): "+data); return uuid;}
			nameLookup.put(uuid, data.substring(nameStart, nameEnd));
			refreshAt.put(uuid, System.currentTimeMillis()+PROFILE_REFRESH_MILLIS);
			return uuid;
		}
	};
	static{
		nameLookup.put(UUID_404, NAME_U_404);
		refreshAt.put(UUID_404, Long.MAX_VALUE);
		uuidLookup.put(NAME_U_404, UUID_404);
	}

	public static final void prefetchName(UUID uuid){if(!nameLookup.contains(uuid)) nameLookup.get(uuid, null);}
	/** For log output: never triggers a lookup or refresh. */
	public static final String cachedNameOrUUID(UUID uuid){
		final String name = nameLookup.getCached(uuid);
		return name == null || name == NAME_404 ? uuid.toString() : name;
	}
	public static final String nameOrUUID(UUID uuid){
		final String name = nameLookup.get(uuid, null);
		return name == NAME_404 || name == NAME_LOADING ? uuid.toString() : name;
	}
}