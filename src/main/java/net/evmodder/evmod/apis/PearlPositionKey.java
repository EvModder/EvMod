package net.evmodder.evmod.apis;

import java.util.UUID;

/** Shared Fabric/ZP encoding and conservative, bounded chamber detection; no Minecraft API dependencies. */
public final class PearlPositionKey{
	private PearlPositionKey(){}

	public static final int UNKNOWN_Y = Short.MIN_VALUE, PENDING_Y = Integer.MIN_VALUE;
	private static final int SEARCH_HEIGHT = 128;
	public enum Block{PASSABLE, BUBBLE, SLIME, UP_PISTON, UP_HEAD, MOVING, SOLID, UNLOADED}
	@FunctionalInterface public interface Blocks{Block get(int x, int y, int z);}

	/** WYXZ: high word W16/Y8/X40, low word W16/Y8/Z40; X/Z are signed 27 whole + 13 fractional bits. */
	public static UUID encode(int dimension, double x, int anchorY, double z){
		checkXZ(x, z);
		if(anchorY < Short.MIN_VALUE || anchorY > Short.MAX_VALUE) throw new IllegalArgumentException("Invalid pearl anchor");
		final long fx = (long)Math.floor(x*0x1p13), fz = (long)Math.floor(z*0x1p13);
		return new UUID(((long)(dimension>>>16)<<48) | ((long)((anchorY>>>8)&255)<<40) | (fx&0xffffffffffL),
				((long)(dimension&65535)<<48) | ((long)(anchorY&255)<<40) | (fz&0xffffffffffL));
	}
	private static void checkXZ(double x, double z){
		if(!Double.isFinite(x) || !Double.isFinite(z) || x < -0x1p26 || x >= 0x1p26 || z < -0x1p26 || z >= 0x1p26)
			throw new IllegalArgumentException("Pearl XZ outside fixed-point range");
	}
	public static boolean sameAnchor(UUID a, UUID b){
		return a.getMostSignificantBits()>>>40 == b.getMostSignificantBits()>>>40
				&& a.getLeastSignificantBits()>>>40 == b.getLeastSignificantBits()>>>40;
	}

	/** Inspect the pearl's 0.25-block-wide footprint, not neighboring chambers through walls. */
	public static int findAnchor(double x, double y, double z, Blocks blocks){
		checkXZ(x, z);
		if(!Double.isFinite(y) || y <= Short.MIN_VALUE || y > Short.MAX_VALUE) return UNKNOWN_Y;
		int anchor = UNKNOWN_Y;
		for(int bx=(int)Math.floor(x-.125); bx<=(int)Math.floor(x+.125-1e-7); ++bx){
			for(int bz=(int)Math.floor(z-.125); bz<=(int)Math.floor(z+.125-1e-7); ++bz){
				final int candidate = findColumn(bx, (int)Math.floor(y), bz, blocks);
				if(candidate == PENDING_Y) return PENDING_Y;
				if(candidate == UNKNOWN_Y) continue;
				if(anchor != UNKNOWN_Y && anchor != candidate) return UNKNOWN_Y;
				anchor = candidate;
			}
		}
		return anchor;
	}
	private static int findColumn(int x, int y, int z, Blocks blocks){
		for(int by=y; by>=y-SEARCH_HEIGHT && by>Short.MIN_VALUE; --by){
			final Block block = blocks.get(x, by, z);
			if(block == Block.UNLOADED || block == Block.MOVING) return PENDING_Y;
			if(block == Block.BUBBLE){
				for(int top=by; top<by+SEARCH_HEIGHT && top<=Short.MAX_VALUE; ++top){
					final Block above = blocks.get(x, top+1, z);
					if(above == Block.UNLOADED || above == Block.MOVING) return PENDING_Y;
					if(above != Block.BUBBLE) return top;
				}
				return UNKNOWN_Y; // No verified surface within the bounded search.
			}
			if(block == Block.SLIME){
				// The stationary base stays a piston (extended is a state); head/slime may become moving_piston.
				for(int dy=1; dy<=2; ++dy){
					final Block below = blocks.get(x, by-dy, z);
					if(below == Block.UNLOADED) return PENDING_Y;
					if(below == Block.UP_PISTON) return by-dy > Short.MIN_VALUE ? by-dy : UNKNOWN_Y;
					if(dy == 1 && below != Block.MOVING && below != Block.UP_HEAD) break;
				}
				return UNKNOWN_Y;
			}
			if(block != Block.PASSABLE) return UNKNOWN_Y;
		}
		return UNKNOWN_Y;
	}
}