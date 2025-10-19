package net.evmodder.KeyBound.listeners;

import net.evmodder.KeyBound.EpearlActivator;
import net.evmodder.KeyBound.config.Configs;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;

public class WhisperListener{
	private final String MSG_MATCH_END = "";//"( .*)?"
	private final EpearlActivator pearlActivator;

	public WhisperListener(){
		pearlActivator = new EpearlActivator();

		ClientReceiveMessageEvents.GAME.register((msg, overlay) -> {
			if(overlay) return;
			final String literal = msg.getString();
			if(literal == null) return;
			if(!literal.matches("\\w+ whispers: ")) return; // TODO: per-server format support

			WhisperPlaySound.playSound();


			final String name = literal.substring(0, literal.indexOf(' '));
			if(literal.substring(name.length()+" whispers: ".length()).matches(Configs.Misc.WHISPER_PEARL_PULL+MSG_MATCH_END)){
				pearlActivator.triggerPearl(name);
			}
		});
	}
}