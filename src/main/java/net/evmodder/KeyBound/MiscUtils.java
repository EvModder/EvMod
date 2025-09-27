package net.evmodder.KeyBound;

import net.minecraft.entity.Entity;

public class MiscUtils{
	public static final boolean hasMoved(Entity entity){
		return entity.lastX != entity.getX() || entity.lastY != entity.getY() || entity.lastZ != entity.getZ();
	}
}