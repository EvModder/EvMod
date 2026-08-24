package net.evmodder.evmod.onTick;

import net.evmodder.evmod.Configs;
import net.evmodder.evmod.apis.TickListener;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;

public final class MuteMusicOnMenuScreen implements TickListener{
	private boolean musicMuted;

	@Override public void onTickEnd(final Minecraft client){
		if(Configs.Generic.MUTE_MUSIC_ON_MENU_SCREEN.getBooleanValue() && client.level == null){
			client.getSoundManager().updateCategoryVolume(SoundSource.MUSIC, 0F);
			musicMuted = true;
		}
		else if(musicMuted){
			client.getSoundManager().updateCategoryVolume(SoundSource.MUSIC, client.options.getSoundSourceVolume(SoundSource.MUSIC));
			musicMuted = false;
		}
	}
}