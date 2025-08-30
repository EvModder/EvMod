package net.evmodder.KeyBound;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;
import javax.imageio.ImageIO;
import net.evmodder.EvLib.FileIO;
import net.minecraft.block.MapColor;
import net.minecraft.block.MapColor.Brightness;

public class MapIdsFromImg{
	/*static int getIntFromARGB(int a, int r, int g, int b){return (a<<24) | (r<<16) | (g<<8) | b;}
	public static void calculateMapColors(){
		// Copy table from https://minecraft.wiki/w/Map_item_format#Color_table
		// Paste into regex website:
		//Find: (\d+) [A-Z_]+\s+(\d+), (\d+), (\d+)\s+[^\n]+
		//Replace: $2,
		int[] r = {0, 127, 247, 199, 255, 160, 167, 0, 255, 164, 151, 112, 64, 143, 255, 216, 178, 102, 229, 127, 242, 76, 153, 76, 127, 51, 102, 102, 153, 25, 250, 92, 74, 0, 129, 112, 209, 159, 149, 112, 186, 103, 160, 57, 135, 87, 122, 76, 76, 76, 142, 37, 189, 148, 92, 22, 58, 86, 20, 100, 216, 127};
		int[] g = {0, 178, 233, 199, 0, 160, 167, 124, 255, 168, 109, 112, 64, 119, 252, 127, 76, 153, 229, 204, 127, 76, 153, 127, 63, 76, 76, 127, 51, 25, 238, 219, 128, 217, 86, 2, 177, 82, 87, 108, 133, 117, 77, 41, 107, 92, 73, 62, 50, 82, 60, 22, 48, 63, 25, 126, 142, 44, 180, 100, 175, 167};
		int[] b = {0, 56, 163, 199, 0, 255, 167, 0, 255, 184, 77, 112, 255, 72, 245, 51, 216, 216, 51, 25, 165, 76, 153, 153, 178, 178, 51, 51, 51, 25, 77, 213, 255, 58, 49, 0, 161, 36, 108, 138, 36, 53, 78, 35, 98, 92, 88, 92, 35, 42, 46, 16, 49, 97, 29, 134, 140, 62, 133, 100, 147, 150};
		int[] shades = {180, 220, 255, 135};

//		System.out.println(""+getIntFromARGB(255, 255, 255, 255));
//		System.out.println(""+getIntFromARGB(255, 0, 0, 0));

		System.out.println("0 0 0 0");
		System.out.println(255<<24);
		for(int i=1; i<62; ++i){
			for(int j=0; j<4; ++j){
				//int colorId = i*4 + j;
				int ri = r[i]*shades[j]/255, gi = g[i]*shades[j]/255, bi = b[i]*shades[j]/255;
				//int rgb = getIntFromARGB(0, r[i]*shades[j]/255, g[i]*shades[j]/255, b[i]*shades[j]/255);
				int rgb = (255<<24) | (ri<<16) | (gi<<8) | bi;
				System.out.print(", "+rgb);
			}
		}
	}*/

	//0xff000000 = 255<<24
	/*private static final int[] MAP_COLORS = new int[] { 0xff000000, 0xff000000, 0xff000000, 0xff000000,
			-10912473, -9594576, -8408520, -12362211, -5331853, -2766452, -530013, -8225962, -7566196, -5526613,
			-3684409, -9868951, -4980736, -2359296, -65536, -7929856, -9408332, -7697700, -6250241, -11250553, -9079435, -7303024, -5789785, -10987432,
			-16754944, -16750080, -16745472, -16760576, -4934476, -2302756, -1, -7895161, -9210239, -7499618, -5986120, -11118495, -9810890, -8233406, -6853299,
			-11585240, -11579569, -10461088, -9408400, -12895429, -13816396, -13158436, -12566273, -14605945, -10202062, -8690114, -7375032, -11845850,
			-4935252, -2303533, -779, -7895679, -6792924, -4559572, -2588877, -9288933, -8571496, -6733382, -5092136, -10606478, -12030824, -10976070,
			-10053160, -13217422, -6184668, -3816148, -1710797, -8816357, -10907631, -9588715, -8401895, -12358643, -5613196, -3117682, -884827, -8371369,
			-13290187, -12500671, -11776948, -14145496, -9671572, -8092540, -6710887, -11447983, -13280916, -12489340, -11763815, -14138543, -10933123,
			-9619815, -8437838, -12377762, -14404227, -13876839, -13415246, -14997410, -12045020, -10993364, -10073037, -13228005, -12035804, -10982100,
			-10059981, -13221093, -9690076, -8115156, -6737101, -11461861, -15658735, -15395563, -15132391, -15921907, -5199818, -2634430, -332211, -8094168,
			-12543338, -11551561, -10691627, -13601936, -13346124, -12620068, -11894529, -14204025, -16738008, -16729294, -16721606, -16748002, -10798046,
			-9483734, -8301007, -12309223, -11599616, -10485504, -9436672, -12910336, -7111567, -4941686, -3034719, -9544363, -9422567, -7780833, -6335964,
			-11261165, -9880244, -8369315, -6989972, -11653575, -11580319, -10461833, -9409398, -12895927, -8168167, -6262241, -4553436, -10336749, -12037595,
			-10984403, -9997003, -13222628, -9423305, -7716285, -6271666, -11261911, -14148584, -13556962, -13031133, -14805742, -10532027, -9151404, -7902366,
			-12109773, -12763072, -11841713, -11051940, -13750224, -11128002, -9879989, -8763048, -12573138, -13292736, -12503729, -11780516, -14147536,
			-13294824, -12506338, -11783645, -14149102, -13289187, -12499420, -11775446, -14144746, -10212832, -8768729, -7455698, -11854056, -15069429,
			-14740979, -14346736, -15529208, -8052446, -6084310, -4378575, -10217191, -9950140, -8440237, -7061663, -11656909, -12578540, -11594471, -10741475,
			-13628145, -15771554, -15569805, -15303034, -16039354, -14130078, -13469064, -12939636, -14791862, -12837077, -11918027, -11129794, -13822176,
			-15827107, -15623310, -15420283, -16097466, -12171706, -11119018, -10197916, -13355980, -6784153, -4548994, -2576493, -9282483, -10914455, -9596799,
			-8411242, -12363697
	};
	private static final HashMap<Integer, Byte> MAP_COLORS_REVERSE = new HashMap<>();
	static{
		for(int b=0; b<MAP_COLORS.length; ++b) MAP_COLORS_REVERSE.put(MAP_COLORS[b], (byte)b);
		MAP_COLORS_REVERSE.put(0, (byte)0);
		MAP_COLORS_REVERSE.put(0xff000000, (byte)0);
	}*/

	private static MapColor[] MAP_COLORS;
	private static final HashMap<Integer, Byte> MAP_COLORS_REVERSE = new HashMap<>();
	static{
		try{
			Field f = MapColor.class.getDeclaredField("COLORS");
			f.setAccessible(true);
			MAP_COLORS = (MapColor[])f.get(null);
			for(MapColor mc : MAP_COLORS){
				if(mc == null) continue;
				for(MapColor.Brightness brightness : MapColor.Brightness.values()){
					MAP_COLORS_REVERSE.put(mc.getRenderColor(brightness), mc.getRenderColorByte(brightness));
				}
			}
			MAP_COLORS_REVERSE.put(0xff000000, (byte)0);
			// Use same byte for all transparent brightnesses
			MAP_COLORS_REVERSE.put(1, (byte)0); MAP_COLORS_REVERSE.put(2, (byte)0); MAP_COLORS_REVERSE.put(3, (byte)0);
//			for(MapColor.Brightness brightness : MapColor.Brightness.values()){
//				MAP_COLORS_REVERSE.put(MAP_COLORS[0].getRenderColor(brightness), MAP_COLORS[0].getRenderColorByte(Brightness.LOW));
//			}
		}
		catch(NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e){
			e.printStackTrace();
		}
	}

	/*private static BufferedImage getMapImg(final byte[] colors){
		assert colors.length == 128*128;
		BufferedImage img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
		for(int x=0; x<128; ++x) for(int y=0; y<128; ++y) img.setRGB(x, y, MAP_COLORS[((int)colors[x + y*128]) & 0xFF]);
		return img;
	}*/
	/*private static void addMapToImg(final BufferedImage img, final byte[] colors, final int xo, final int yo){
		assert colors.length == 128*128;
		for(int x=0; x<128; ++x) for(int y=0; y<128; ++y) img.setRGB(xo+x, yo+y, MAP_COLORS[((int)colors[x + y*128]) & 0xFF]);
	}*/
	private static byte[] colorsFromImg(final BufferedImage img, final int xo, final int yo){
		byte[] bs = new byte[128*128];
		for(int x=0; x<128; ++x) for(int y=0; y<128; ++y){
			int argb = img.getRGB(xo+x, yo+y);
			Byte b = MAP_COLORS_REVERSE.get(argb);
			if(b == null){
				System.err.println("Unsupported color detected (not a valid map color) at "+(xo+x)+","+(yo+y)+": "+argb);
//				System.err.println("Unsigned representation: "+Integer.toUnsignedString(argb));
//				int alpha = (argb >> 24) & 0xFF; // Shift right by 24 bits to get alpha, then mask with 0xFF
//				int red = (argb >> 16) & 0xFF;   // Shift right by 16 bits to get red, then mask with 0xFF
//				int green = (argb >> 8) & 0xFF;  // Shift right by 8 bits to get green, then mask with 0xFF
//				int blue = argb & 0xFF;         // Mask with 0xFF to get blue
//				System.err.println("A: "+alpha+", R: "+red+", G: "+green+", B: "+blue);
				System.exit(1);
			}
			bs[x + y*128] = b;
//			img.setRGB(xo+x, yo+y, MAP_COLORS[((int)colors[x + y*128]) & 0xFF]);
		}
		return bs;
	}

	private static UUID getLockedIdForColors(byte[] colors){
		UUID uuid = UUID.nameUUIDFromBytes(colors);
		// set 1st bit = state.locked
		return new UUID(uuid.getMostSignificantBits() | 1l, uuid.getLeastSignificantBits());
	}

	public static HashSet<UUID> loadColorIds(String filename){
		byte[] data = FileIO.loadFileBytes(filename);
		int numIds = data.length/16;
		assert numIds*16 == data.length;
		HashSet<UUID> colorIds = new HashSet<>(numIds);
		ByteBuffer bb = ByteBuffer.wrap(data);
		for(int i=0; i<numIds; ++i) colorIds.add(new UUID(bb.getLong(), bb.getLong()));
		return colorIds;
	}

	public static void main(String... args) throws IOException{
//		calculateMapColors();
		String imgName = "tmp_workstuff/1x1s_generic.png";
		String grpName = "tmp_workstuff/hashcode_1x1s_ev";
		BufferedImage img = ImageIO.read(new File(imgName));
		HashSet<UUID> compareColorIds = loadColorIds(grpName);

		if(img.getWidth()%128 != 0 || img.getHeight()%128 != 0){
			System.err.println("Image W and H must be divisible by 128, but are not: "+img.getWidth()+" x "+img.getHeight());
			return;
		}
		System.out.println("Img type: "+img.getType()+" goal: "+BufferedImage.TYPE_INT_ARGB);
		if(img.getType() != BufferedImage.TYPE_INT_ARGB){
			BufferedImage convertedImg = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
			convertedImg.getGraphics().drawImage(img, 0, 0, null);
			img = convertedImg;
//			ImageIO.write(convertedImg, "png", new File("test.png"));
		}
		Graphics g = img.getGraphics();
		g.setColor(new Color(0, 0, 0, 0));
		((Graphics2D)g).setComposite(AlphaComposite.getInstance(AlphaComposite.CLEAR));
		boolean edited = false;

		HashSet<UUID> newMapIds = new HashSet<>(), hadMapIds = new HashSet<>();
		ArrayDeque<byte[]> newMapColors = new ArrayDeque<>();
		int w = img.getWidth()/128, h = img.getHeight()/128;
		for(int y=0; y<h; ++y) for(int x=0; x<w; ++x){
			byte[] colors = colorsFromImg(img, 128*x, 128*y);
			UUID uuid = getLockedIdForColors(colors);
			boolean newToMe = !compareColorIds.contains(uuid);
			if(newToMe && newMapIds.add(uuid)) newMapColors.add(colors);
			else{
				g.fillRect(128*x, 128*y, 128, 128);
				edited = true;
			}
			(newToMe ? newMapIds : hadMapIds).add(uuid);
//			System.out.print(uuid+", ");
		}
		System.out.println("Unique ids: "+(hadMapIds.size()+newMapIds.size())+", NewToMe: "+newMapIds.size()+", iAlrGot: "+hadMapIds.size());
		if(edited){
			System.out.print("Saving edited images");
			g.dispose();
			int idx = imgName.indexOf('.');
			ImageIO.write(img, "png", new File(imgName.substring(0, idx)+"_edited"+imgName.substring(idx)));

			// Get new W & H
			int editW = (int)Math.ceil(Math.sqrt(newMapColors.size()));
			int editH = editW*(editW-1) >= newMapColors.size() ? editW-1 : editW;

			// Make new (smaller) img
			img = new BufferedImage(128*editW, 128*editH, BufferedImage.TYPE_INT_ARGB);
			for(int i=0; i<editW; ++i) for(int j=0; j<editH; ++j){
				if(newMapColors.isEmpty()) break;
				byte[] colors = newMapColors.poll();
				final int xo = i*128, yo = j*128;
				for(int x=0; x<128; ++x) for(int y=0; y<128; ++y) img.setRGB(xo+x, yo+y, MapColor.getRenderColor(colors[x + y*128]));
			}
			ImageIO.write(img, "png", new File(imgName.substring(0, idx)+"_edited_small"+imgName.substring(idx)));
		}
//		final ByteBuffer bb = ByteBuffer.allocate(colorIds.size()*16);
//		for(UUID uuid : colorIds) bb.putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits());
//		FileIO.saveFileBytes("hashcode_"+imgName.substring(0, imgName.indexOf('.')), bb.array());
	}
}