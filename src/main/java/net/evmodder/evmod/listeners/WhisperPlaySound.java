package net.evmodder.evmod.listeners;

import net.evmodder.evmod.Configs;
import net.evmodder.evmod.Main;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public class WhisperPlaySound{
	private float volume, pitch;
	private SoundEvent sound;
	private SoundCategory category;

//	public void recomputeSound(String soundData){
	public void recomputeSound(){
		final String soundData = Configs.Generic.WHISPER_PLAY_SOUND.getStringValue();
		if(soundData == null || soundData.isBlank()) return;
		if(soundData.lastIndexOf('{') != 0 || soundData.indexOf('}') != soundData.length()-1){
			Main.LOGGER.warn("Unrecognized sound format: "+soundData+" (not wrapped in {})");
			return;
		}
		final String[] parts = soundData.substring(1, soundData.length()-1).split(",");
		if(parts.length == 0 || parts.length > 4){Main.LOGGER.warn("Unrecognized sound format"); return;}
		int idx;
		final String soundName =
				((idx=parts[0].indexOf(':')) == -1 ? parts[0] : parts[0].substring(idx+1)).trim();
		sound = SoundEvent.of(Identifier.of(soundName));
		final String categoryName = parts.length < 2 ? SoundCategory.PLAYERS.name() :
				((idx=parts[1].indexOf(':')) == -1 ? parts[1] : parts[1].substring(idx+1)).trim();
		category = SoundCategory.valueOf(categoryName);
		volume = parts.length < 3 ? 1 : Float.parseFloat(
				((idx=parts[2].indexOf(':')) == -1 ? parts[2] : parts[2].substring(idx+1)).trim());
		pitch = parts.length < 4 ? 1 : Float.parseFloat(
				((idx=parts[3].indexOf(':')) == -1 ? parts[3] : parts[3].substring(idx+1)).trim());
	}

	public WhisperPlaySound(){recomputeSound();}

	public void playSound(){
		if(sound == null || category == null || volume == 0) return;
		MinecraftClient client = MinecraftClient.getInstance();
		if(Configs.Generic.WHISPER_PLAY_SOUND_UNFOCUSED_ONLY.getBooleanValue() && client.isWindowFocused()) return;
		client.player.playSound(sound, /*TODO:category,*/ volume, pitch);
	}
}