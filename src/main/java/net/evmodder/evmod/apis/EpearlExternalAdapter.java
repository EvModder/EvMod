package net.evmodder.evmod.apis;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.MinecraftClient;

public final class EpearlExternalAdapter{
	private EpearlExternalAdapter(){}
	private static final HttpClient HTTP = HttpClient.newHttpClient();

	private static final String stripTrailingSlashes(final String s){
		int end = s.length();
		while(end > 0 && s.charAt(end - 1) == '/') --end;
		return s.substring(0, end);
	}
	private static final String normalizeUrl(final String baseUrl){
		final String url = stripTrailingSlashes(baseUrl.trim());
		return url.startsWith("http://") || url.startsWith("https://") ? url : "http://" + url;
	}
	private static final CompletableFuture<String> post(final String baseUrl, final String token, final String path, final String body){
		assert token != null && !token.isBlank() : "Bad token";
		assert baseUrl != null : "Null url";
		final String cleanUrl = normalizeUrl(baseUrl);
		assert !cleanUrl.isBlank() : "Bad url";

		final HttpRequest req = HttpRequest.newBuilder(URI.create(cleanUrl + path))
				.timeout(Duration.ofSeconds(10))
				.header("Content-Type", "application/json")
				.header("User-Agent", "Apparate/1.0")
				.header("Authorization", token.trim())
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.build();

		return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply(res -> {
			if(res.statusCode() / 100 != 2) throw new RuntimeException("EpearlExternal HTTP " + res.statusCode() + ": " + res.body());
			return res.body();
		});
	}

	private static final String firstPearl(final String json){
		final int arrayStart = json.indexOf('[', json.indexOf("\"pearls\""));
		final int start = json.indexOf('"', arrayStart) + 1;
		final int end = json.indexOf('"', start);
		if(arrayStart < 0 || start < 1 || end < 0) throw new RuntimeException("No pearl in response: " + json);
		return json.substring(start, end);
	}

	public static final CompletableFuture<Void> trigger(final String baseUrl, final String token, final String playerName, final String pearlId){
		assert playerName != null && playerName.matches("[A-Za-z0-9_]{2,16}") : "Bad playerName";
		assert pearlId != null && pearlId.matches("[A-Za-z0-9_-]{1,64}") : "Bad pearlId";

		return post(baseUrl, token, "/pearlplus/load", "{\"playerName\":\"" + playerName + "\",\"pearlId\":\"" + pearlId + "\"}")
				.thenAccept(_0 -> {});
	}

	public static final CompletableFuture<Void> trigger(final String baseUrl, final String token, final String playerName){
		assert playerName != null && playerName.matches("[A-Za-z0-9_]{2,16}") : "Bad playerName";

		return post(baseUrl, token, "/pearlplus/status", "{\"playerName\":\"" + playerName + "\"}")
				.thenApply(EpearlExternalAdapter::firstPearl)
				.thenCompose(pearlId -> trigger(baseUrl, token, playerName, pearlId));
	}

	public static final CompletableFuture<Void> trigger(final String baseUrl, final String token){
		return trigger(baseUrl, token, MinecraftClient.getInstance().player.getGameProfile().getName());
	}
}