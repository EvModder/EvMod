package net.evmodder.evmod.apis;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Shared whisper syntax; adapters resolve the target and verify its pearl/trigger before acting. */
public record PearlWhisperRequest(String target, boolean confirmed, String command){
	private static final Pattern CONFIRMATION = Pattern.compile("(?i)\\s+-f[.!?]?$");
	private static final Pattern NAME = Pattern.compile("(?<!\\S)[a-zA-Z0-9_]{1,16}(?=\\s|[.!?]?$)");
	private static final Set<String> SELF_WORDS = Set.of("me", "pls", "plse", "please", "pleas", "plz", "ready", "rdy");

	public static PearlWhisperRequest parse(final Pattern trigger, final String message, final String sender){
		String command = message.trim();
		final var confirmation = CONFIRMATION.matcher(command);
		final boolean confirmed = confirmation.find();
		if(confirmed) command = command.substring(0, confirmation.start()).trim();
		final boolean matches = trigger.matcher(command).matches();
		String target = null;
		final var names = NAME.matcher(command);
		while(names.find()){
			final String word = names.group().toLowerCase(Locale.ROOT);
			if(names.start() == 0 || word.equals("me") || matches && SELF_WORDS.contains(word)) continue;
			// Accept the configured full syntax, or extend legacy "tp" / "tp me" regexes without changing their config.
			final String before = command.substring(0, names.start()).stripTrailing(), after = command.substring(names.end());
			if(!matches && !trigger.matcher(before+after).matches() && !trigger.matcher(before+" me"+after).matches()) continue;
			if(target != null) return null;  // Do not guess between multiple named players.
			target = names.group();
		}
		return target != null ? new PearlWhisperRequest(target, confirmed, command)
				: matches ? new PearlWhisperRequest(sender, confirmed, command) : null;
	}

	public String confirmationPrompt(final String name){return "[AutoPearl] To pull "+name+", repeat: "+command+" -f";}
}