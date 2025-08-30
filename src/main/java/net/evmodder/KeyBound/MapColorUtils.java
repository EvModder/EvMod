package net.evmodder.KeyBound;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.stream.IntStream;
import net.minecraft.block.MapColor;
import net.minecraft.block.MapColor.Brightness;

public abstract class MapColorUtils{
	public static final boolean isTransparentOrStone(final byte[] colors){
		assert colors != null && colors.length == 128*128;
		// This is faster apparently (less branching beats short-circuit)
		byte anyColor = 0;
		for(byte b : colors) anyColor |= b;
		return anyColor == 0 || anyColor == 45;//11*4+1 = flat stone
	}
	public static final boolean isMonoColor(final byte[] colors){
		assert colors != null && colors.length == 128*128;
		for(int i=1; i<colors.length; ++i) if(colors[i] != colors[i-1]) return false;
		return true;
	}

	private static final boolean isCarpetColor(MapColor color){
//		switch(color.id){
//			case ORANGE:
//			case MAGENTA:
//			case YELLOW:
//			case LIME:
//			case PINK:
//			case GRAY:
//			case LIGHT_GRAY:
//			case CYAN:
//			case PURPLE:
//			case BLUE:
//			case BROWN:
//			case GREEN:
//			case RED:
//			case BLACK:
//				return true;
//			default:
//				return false;
//		}
		return color.id >= 15 && color.id <= 29
				|| color.id == 0; // Also include transparency hmm
	}
	private static final boolean isPistonClearableColor(MapColor color){
		switch(color.id){
			case 2: // BIRCH
			case 3: // CANDLE
			case 6: // IRON
			case 7: // PLANT
			case 8: // WHITE
			case 10: // JUNGLE
			case 11: // STONE
			case 12: // WATER
			case 13: // OAK
			case 14: // PALE_OAK
			//15-29: // CARPET
			case 30: // GOLD
			case 34: // SPRUCE
			case 35: // NETHER
			case 36: // CHERRY
			case 37: // RESIN
			case 48: // DRIPSTONE
			case 49: // CLOSED_EYEBLOSSOM
			case 50: // DECORATED_POT
			case 53: // CRIMSON_STEM
			case 56: // WARPED_STEM
			case 61: // GLOW_LICHEN
				return true;
			default:
				return isCarpetColor(color);
		}
	}
	private static final HashSet<Byte>
			transparentColors = new HashSet<>(),
			carpetColors = new HashSet<>(),
			pistonColors = new HashSet<>(),
			northLower = new HashSet<>(),
			northHigher = new HashSet<>();
	static{
		try{
			Field f = MapColor.class.getDeclaredField("COLORS");
			f.setAccessible(true);
			MapColor[] MAP_COLORS = (MapColor[])f.get(null);
			for(MapColor color : MAP_COLORS){
				if(color.id == 0){
					carpetColors.add(color.getRenderColorByte(Brightness.LOW));
					carpetColors.add(color.getRenderColorByte(Brightness.NORMAL));
					carpetColors.add(color.getRenderColorByte(Brightness.HIGH));
				}
				if(isCarpetColor(color)){
					carpetColors.add(color.getRenderColorByte(Brightness.LOW)); // Staircased carpet
					carpetColors.add(color.getRenderColorByte(Brightness.NORMAL));
					carpetColors.add(color.getRenderColorByte(Brightness.HIGH)); // Staircased carpet (or noobline)
				}
				if(isPistonClearableColor(color)){
					pistonColors.add(color.getRenderColorByte(Brightness.NORMAL));
					pistonColors.add(color.getRenderColorByte(Brightness.HIGH)); // Noobline
				}
				northLower.add(color.getRenderColorByte(Brightness.HIGH));
				northHigher.add(color.getRenderColorByte(Brightness.LOW));
			}
		}
		catch(ReflectiveOperationException e){
			e.printStackTrace();
		}
	}
	public enum Palette{EMPTY, CARPET, PISTON_CLEAR, FULLBLOCK};
	public final record MapColorData(Palette palette, int height, int uniqueColors, boolean noobline, boolean transparency, int percentCarpet){}
	public static final MapColorData getColorData(final byte[] colors){
		assert colors != null && colors.length == 128*128;
		int maxDiffH = 0;
		Palette palette = Palette.EMPTY;
		boolean transparency = false;
		boolean staircaseBelowTopRow = false;
		HashSet<Byte> uniqueColors = new HashSet<>();
		for(int x=0; x<128; ++x){
			int h = 0;
			for(int y=0; y<128; ++y){
				final byte color = colors[x + y*128];
				uniqueColors.add(color);
				final boolean isTransparent = transparentColors.contains(color);
				switch(palette){
					case EMPTY:
						if(isTransparent){transparency = true; break;}
						palette = Palette.CARPET;
					case CARPET:
						if(carpetColors.contains(color)) break;
						palette = Palette.PISTON_CLEAR;
					case PISTON_CLEAR:
						if(pistonColors.contains(color)) break;
						palette = Palette.FULLBLOCK;
					case FULLBLOCK:
				}
				if(isTransparent){h=0; continue;}
				else if(northLower.contains(color)){h = h<0 ? 1 : ++h;}
				else if(northHigher.contains(color)){h = h>0 ? -1 : --h;}
				else continue;
				maxDiffH = Math.max(maxDiffH, Math.abs(h));
				staircaseBelowTopRow |= y!=0;
			}
		}

		boolean noobline = false;
		if(maxDiffH == 1 && !staircaseBelowTopRow){--maxDiffH; noobline = true;}

//		final boolean topRowAllBrighter = IntStream.range(0, 128).allMatch(i -> northLower.contains(colors[i]));
//		final boolean secondRowAllBrighter = IntStream.range(128, 256).allMatch(i -> northLower.contains(colors[i]));
//		else if(topRowAllBrighter && !secondRowAllBrighter) noobline = true;
		else if(IntStream.range(0, 128).allMatch(i -> northLower.contains(colors[i])) &&
				!IntStream.range(128, 256).allMatch(i -> northLower.contains(colors[i]))) noobline = true;

		int percentCarpet = 0;
		if(Main.mapMdPercentCarpet){
			int numCarpetOrTransparent = (int)IntStream.range(0, colors.length).filter(i -> carpetColors.contains(colors[i])).count();
			percentCarpet = (int)((numCarpetOrTransparent*100d)/colors.length);
		}
		return new MapColorData(palette, maxDiffH, uniqueColors.size(), noobline, transparency, percentCarpet);
	}
}