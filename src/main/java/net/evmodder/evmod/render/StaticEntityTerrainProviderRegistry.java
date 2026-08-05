package net.evmodder.evmod.render;

import net.minecraft.client.multiplayer.ClientLevel;

final class StaticEntityTerrainProviderRegistry{
	private static final StaticEntityTerrainProvider[] PROVIDERS = {
			// Vanilla chunks plus full-chunk replacements retained by mods such as Bobby/Farsight.
			(level, blockX, blockZ, _/*cameraX*/, _/*cameraZ*/) -> level.getChunkSource().getChunk(blockX >> 4, blockZ >> 4, false) != null,
			new StaticEntityTerrainProviderVoxy()
	};

	private StaticEntityTerrainProviderRegistry(){}

	static final void tick(){
		for(final StaticEntityTerrainProvider provider : PROVIDERS) provider.tick();
	}

	static final boolean hasTerrainAt(
			final ClientLevel level, final int blockX, final int blockZ,
			final double cameraX, final double cameraZ){
		for(final StaticEntityTerrainProvider provider : PROVIDERS){
			if(provider.hasTerrainAt(level, blockX, blockZ, cameraX, cameraZ)) return true;
		}
		return false;
	}

	static final boolean hasExtendedTerrainAt(
			final ClientLevel level, final int blockX, final int blockZ,
			final double cameraX, final double cameraZ){
		boolean hasExtendedTerrain = false;
		for(final StaticEntityTerrainProvider provider : PROVIDERS){
			if(!provider.hasTerrainAt(level, blockX, blockZ, cameraX, cameraZ)) continue;
			// Ordinary/full-chunk terrain must retain Minecraft/Sodium's own section visibility result.
			if(!provider.extendsVanillaSectionVisibility()) return false;
			hasExtendedTerrain = true;
		}
		return hasExtendedTerrain;
	}
}