package net.evmodder.KeyBound.listeners;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public class WhisperPlaySound{
	//WhisperPlaySound(SoundEvent sound, SoundCategory category, float volume, float pitch){
	public WhisperPlaySound(final String soundData){
		if(soundData.lastIndexOf('{') != 0 || soundData.indexOf('}') != soundData.length()-1) return;
		final String[] parts = soundData.split(",");
		if(parts.length == 0 || parts.length > 4) return;
		int idx;
		final String soundName =
				((idx=parts[1].indexOf(':')) == -1 ? parts[0] : parts[0].substring(idx+1)).trim();
		final String categoryName = parts.length < 2 ? SoundCategory.PLAYERS.name() :
				((idx=parts[1].indexOf(':')) == -1 ? parts[1] : parts[1].substring(idx+1)).trim();
		final float volume = parts.length < 3 ? 1 : Float.parseFloat(
				((idx=parts[2].indexOf(':')) == -1 ? parts[2] : parts[2].substring(idx+1)).trim());
		final float pitch = parts.length < 4 ? 1 : Float.parseFloat(
				((idx=parts[3].indexOf(':')) == -1 ? parts[3] : parts[3].substring(idx+1)).trim());

		final SoundEvent sound = SoundEvent.of(Identifier.of(soundName));
		final SoundCategory category = SoundCategory.valueOf(categoryName);

		ClientReceiveMessageEvents.GAME.register((msg, overlay) -> {
			if(overlay) return;
			final String literal = msg.getString();
			if(literal == null || !literal.matches("\\w+ whispers: .+")) return;
			MinecraftClient.getInstance().player.playSoundToPlayer(sound, category, volume, pitch);
		});
	}
}