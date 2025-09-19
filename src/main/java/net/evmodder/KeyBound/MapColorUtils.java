package net.evmodder.KeyBound;

import java.util.Arrays;
import java.util.HashSet;
import java.util.stream.IntStream;
import net.minecraft.block.MapColor;
import net.minecraft.block.MapColor.Brightness;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

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
		return (color.id >= 15 && color.id <= 29)
				|| color.id == 8 // Snow/white wool/carpet
				|| color.id == 0 // Also include transparency hmm
		;
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
			pistonColors = new HashSet<>(), pistonNooblineColors = new HashSet<>(),
			northLower = new HashSet<>(),
			northHigher = new HashSet<>();
	static{
		for(int i=0; i<64; ++i){
			MapColor color = MapColor.get(i);
			if(color.id == /*0*/MapColor.CLEAR.id){
				transparentColors.add(color.getRenderColorByte(Brightness.LOW));
				transparentColors.add(color.getRenderColorByte(Brightness.NORMAL));
				transparentColors.add(color.getRenderColorByte(Brightness.HIGH));
				pistonColors.add(color.getRenderColorByte(Brightness.LOW));
			}
			if(color.id == /*12*/MapColor.WATER_BLUE.id){
//				pistonColors.add(color.getRenderColorByte(Brightness.LOW)); // Platform tech for this isn't developed yet
//				pistonColors.add(color.getRenderColorByte(Brightness.NORMAL)); // Platform tech for this isn't developed yet
				pistonColors.add(color.getRenderColorByte(Brightness.HIGH));
				continue;
			}
			if(isCarpetColor(color)){ // Includes transparent colors
				carpetColors.add(color.getRenderColorByte(Brightness.LOW)); // Staircased carpet
				carpetColors.add(color.getRenderColorByte(Brightness.NORMAL));
				carpetColors.add(color.getRenderColorByte(Brightness.HIGH)); // Staircased carpet (or noobline)
			}
			if(isPistonClearableColor(color)){ // Includes carpet and transparent colors
				pistonColors.add(color.getRenderColorByte(Brightness.NORMAL));
				pistonNooblineColors.add(color.getRenderColorByte(Brightness.HIGH));
			}
			northLower.add(color.getRenderColorByte(Brightness.HIGH));
			northHigher.add(color.getRenderColorByte(Brightness.LOW));
		}
//		Main.LOGGER.info("Num transparent colors: "+transparentColors.size());
//		Main.LOGGER.info("Num carpet colors: "+carpetColors.size());
//		Main.LOGGER.info("Num piston colors: "+pistonColors.size());
//		Main.LOGGER.info("Num brighter colors: "+northLower.size());
//		Main.LOGGER.info("Num darker colors: "+northHigher.size());
	}
	public enum Palette{EMPTY, CARPET, PISTON_CLEAR, FULLBLOCK};
	public final record MapColorData(Palette palette, int height, int uniqueColors, int uniqueColorIds, boolean noobline, boolean transparency,
			int percentCarpet, int percentStaircase){}
	public static final MapColorData getColorData(final byte[] colors){
		assert colors != null && colors.length == 128*128;
		int maxDiffH = 0;
		Palette palette = Palette.EMPTY;
		boolean transparency = false;
		boolean staircaseBelowTopRow = false;
//		int staircasedX=0, staircasedY=0;
		HashSet<Byte> uniqueColors = new HashSet<>(), uniqueColorIds = new HashSet<>();
		for(int x=0; x<128; ++x){
			int h = 0;
			for(int y=0; y<128; ++y){
				final byte color = colors[x + y*128];
				final boolean isTransparent = transparentColors.contains(color);
				uniqueColors.add(isTransparent ? 0 : color);
				uniqueColorIds.add((byte)((color&0xFF)/4));
				switch(palette){
					case EMPTY:
						if(isTransparent){transparency = true; break;}
						palette = Palette.CARPET;
					case CARPET:
						if(carpetColors.contains(color)) break;
						palette = Palette.PISTON_CLEAR;
					case PISTON_CLEAR:
						if(pistonColors.contains(color)
							|| (pistonNooblineColors.contains(color) && (y==0 || transparentColors.contains(colors[x+(y-1)*128])))) break;
						MinecraftClient.getInstance().player.sendMessage(Text.literal("First non-piston-clear color: "+x+","+y+"  (id:"+(color&0xFF)/4+")"), true);
						palette = Palette.FULLBLOCK;
					case FULLBLOCK:
				}
				if(isTransparent){h=0; continue;}
				else if(northLower.contains(color) && !transparentColors.contains(colors[x+(y-1)*128])){h = h<0 ? 1 : ++h;}
				else if(northHigher.contains(color)){h = h>0 ? -1 : --h;}
				else continue;
				maxDiffH = Math.max(maxDiffH, Math.abs(h));
//				if(!staircaseBelowTopRow && y!=0){
//					Main.LOGGER.info("found staircased pixel at "+x+","+y);
//				}
				staircaseBelowTopRow |= (y!=0);
//				if(staircasedX == 0){staircasedX = x; staircasedY = y;}
			}
		}
//		Main.LOGGER.info("maxH: "+maxDiffH);

		boolean noobline = false;
		if(maxDiffH == 1 && !staircaseBelowTopRow){--maxDiffH; noobline = true;}

//		final boolean topRowAllBrighter = IntStream.range(0, 128).allMatch(i -> northLower.contains(colors[i]));
//		final boolean secondRowAllBrighter = IntStream.range(128, 256).allMatch(i -> northLower.contains(colors[i]));
//		else if(topRowAllBrighter && !secondRowAllBrighter) noobline = true;
		else if(IntStream.range(0, 128).allMatch(i -> northLower.contains(colors[i])) &&
				!IntStream.range(128, 256).allMatch(i -> northLower.contains(colors[i]))) noobline = true;

		int numTransparent = (int)IntStream.range(0, colors.length).filter(i -> transparentColors.contains(colors[i])).count();
		int numCarpet = (int)IntStream.range(0, colors.length).filter(i -> carpetColors.contains(colors[i])).count()-numTransparent;
		int percentCarpet = (int)Math.ceil((numCarpet*100d)/(colors.length-numTransparent));

		final int[] staircasedPixels = IntStream.range(0, colors.length)
				.filter(i -> (northLower.contains(colors[i]) || northHigher.contains(colors[i])) && !transparentColors.contains(colors[i])).toArray();
		int numShaded = staircasedPixels.length;
		int percentStaircase = (int)Math.ceil((numShaded*100d)/(colors.length-numTransparent));
		if(percentStaircase <= 2 && percentStaircase > 0){
			Main.LOGGER.info("numShaded: "+numShaded+", numTransparent: "+numTransparent+", percentStaircase: "+percentStaircase);
			int j = staircasedPixels[0];
			int x = j%128, y = j/128;
			MinecraftClient.getInstance().player.sendMessage(Text.literal("First staircased pixel: "+x+","+y+"  (id:"+(colors[j]&0xFF)/4+")"), true);
			double numShadedAtTop = Arrays.stream(staircasedPixels).filter(i -> i<128).count();
			if(numShadedAtTop/numShaded > .8) noobline = true; // If 80%+ of the shading is only the top row, consider it a noobline
		}

		return new MapColorData(palette, maxDiffH, uniqueColors.size(), uniqueColorIds.size(), noobline, transparency, percentCarpet, percentStaircase);
	}
}