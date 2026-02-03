package net.evmodder.evmod.listeners;

import net.evmodder.evmod.Configs;
import net.evmodder.evmod.Main;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public class WhisperPlaySound{
	record SoundData(SoundEvent sound, SoundCategory category, float volume, float pitch){}
	private SoundData whisperSound, whisperSoundUnfocused;

	private final SoundData parseSoundData(final String soundData){
		if(soundData == null || soundData.isBlank()) return null;
		if(soundData.lastIndexOf('{') != 0 || soundData.indexOf('}') != soundData.length()-1){
			Main.LOGGER.warn("Unrecognized sound format (not wrapped in {}): "+soundData+"");
			return null;
		}
		final String[] parts = soundData.substring(1, soundData.length()-1).split(",");
		if(parts.length == 0 || parts.length > 4){Main.LOGGER.warn("Unrecognized sound format (parts.length != 4): "+soundData); return null;}
		int idx;
		final String soundName =
				((idx=parts[0].indexOf(':')) == -1 ? parts[0] : parts[0].substring(idx+1)).trim();
		final SoundEvent sound = SoundEvent.of(Identifier.of(soundName));
		final String categoryName = parts.length < 2 ? SoundCategory.PLAYERS.name() :
				((idx=parts[1].indexOf(':')) == -1 ? parts[1] : parts[1].substring(idx+1)).trim();
		final SoundCategory category = SoundCategory.valueOf(categoryName);
		final float volume = parts.length < 3 ? 1 : Float.parseFloat(
				((idx=parts[2].indexOf(':')) == -1 ? parts[2] : parts[2].substring(idx+1)).trim());
		final float pitch = parts.length < 4 ? 1 : Float.parseFloat(
				((idx=parts[3].indexOf(':')) == -1 ? parts[3] : parts[3].substring(idx+1)).trim());
		if(sound == null || category == null || volume == 0) return null;
		return new SoundData(sound, category, volume, pitch);
	}

	public final void recomputeSound(){whisperSound = parseSoundData(Configs.Generic.WHISPER_PLAY_SOUND.getStringValue());}
	public final void recomputeSoundUnfocused(){whisperSoundUnfocused = parseSoundData(Configs.Generic.WHISPER_PLAY_SOUND_UNFOCUSED.getStringValue());}


	public WhisperPlaySound(){recomputeSound(); recomputeSoundUnfocused();}

	public void playSound(){
		final MinecraftClient client = MinecraftClient.getInstance();
		final SoundData data = client.isWindowFocused() ? whisperSound : whisperSoundUnfocused;
		if(data == null) return;
		client.player.playSoundToPlayer(data.sound, data.category, data.volume, data.pitch);
	}
}