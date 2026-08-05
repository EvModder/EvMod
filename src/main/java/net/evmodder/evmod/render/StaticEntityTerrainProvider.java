package net.evmodder.evmod.render;

import net.minecraft.client.multiplayer.ClientLevel;

/** Internal seam between the entity cache and terrain-retention/rendering implementations. */
interface StaticEntityTerrainProvider{
	default void tick(){}

	boolean hasTerrainAt(
			final ClientLevel level, final int blockX, final int blockZ,
			final double cameraX, final double cameraZ);

	default boolean extendsVanillaSectionVisibility(){return false;}
}