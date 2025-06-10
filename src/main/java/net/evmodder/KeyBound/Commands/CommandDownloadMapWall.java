package net.evmodder.KeyBound.Commands;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.evmodder.EvLib.FileIO;
import net.evmodder.KeyBound.Main;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.math.Direction.Axis;

public class CommandDownloadMapWall{
	final int RENDER_DIST = 10;
	final boolean SCALE_TO_640, BLOCK_BORDER;
	final int BORDER_1 = -14236, BORDER_2 = -13495266; // Orange, Near-black purple
	//final Box everythingBox = Box.of(client.player.getPos(), RENDER_DIST*16, RENDER_DIST*16, RENDER_DIST*16);

	/*static int getIntFromARGB(int a, int r, int g, int b){return (a<<24) | (r<<16) | (g<<8) | b;}
	public static void main(String... args){
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

		System.out.print("0 0 0 0");
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

	private int[] MAP_COLORS = new int[] { 0, 0, 0, 0,
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
			-8411242, -12363697 };

	/*private BufferedImage getMapImg(final byte[] colors){
		assert colors.length == 128*128;
		BufferedImage img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
		for(int x=0; x<128; ++x) for(int y=0; y<128; ++y) img.setRGB(x, y, MAP_COLORS[((int)colors[x + y*128]) & 0xFF]);
		return img;
	}*/
	private void addMapToImg(final BufferedImage img, final byte[] colors, final int xo, final int yo){
		assert colors.length == 128*128;
		for(int x=0; x<128; ++x) for(int y=0; y<128; ++y) img.setRGB(xo+x, yo+y, MAP_COLORS[((int)colors[x + y*128]) & 0xFF]);
	}

	private int runCommandNoArg(final CommandContext<FabricClientCommandSource> ctx){
		MinecraftClient client = MinecraftClient.getInstance();
		Box everythingBox = Box.of(client.player.getPos(), RENDER_DIST*16, RENDER_DIST*16, RENDER_DIST*16);

		final List<ItemFrameEntity> iFrames = client.world.getEntitiesByType(TypeFilter.instanceOf(ItemFrameEntity.class), everythingBox,
				e -> e.getHeldItemStack().getItem() == Items.FILLED_MAP);

		ItemFrameEntity targetIFrame = null;
		double bestUh = 0;
		final Vec3d vec3d = client.player.getRotationVec(1.0F).normalize();
		for(ItemFrameEntity ife : iFrames){
			if(!client.player.canSee(ife)) continue;
			Vec3d vec3d2 = new Vec3d(ife.getX()-client.player.getX(), ife.getEyeY()-client.player.getEyeY(), ife.getZ()-client.player.getZ());
			final double d = vec3d2.length();
			vec3d2 = new Vec3d(vec3d2.x / d, vec3d2.y / d, vec3d2.z / d); // normalize
			final double e = vec3d.dotProduct(vec3d2);
			//e > 1.0d - 0.025d / d
			final double uh = (1.0d - 0.1d/d) - e;
			if(uh < bestUh){bestUh = uh; targetIFrame = ife;}
		}
		if(targetIFrame == null){
			ctx.getSource().sendError(Text.literal("No mapwall (in front of cursor) detected"));
			return 1;
		}

		HashMap<Vec3i, ItemFrameEntity> ifeLookup = new HashMap<>(iFrames.size());
		final Direction facing = targetIFrame.getFacing();
		iFrames.stream().filter(ife -> ife.getFacing() == facing).forEach(ife -> ifeLookup.put(ife.getBlockPos(), ife));

		Vec3i targetPos = targetIFrame.getBlockPos();
//		ctx.getSource().sendFeedback(Text.literal("Facing: "+facing));

		final int tX = targetPos.getX(), tY = targetPos.getY(), tZ = targetPos.getZ();
		int minX, minY, minZ, maxX, maxY, maxZ;
		minX = maxX = tX; minY = maxY = tY; minZ = maxZ = tZ;

//		ctx.getSource().sendFeedback(Text.literal("Loaded ifes: "+ifeLookup.size()));
		if(facing.getOffsetX() == 0){
//			ctx.getSource().sendFeedback(Text.literal("Varying X"));
			while(ifeLookup.containsKey(targetPos.offset(Axis.X, (minX-tX)-1))) --minX;
			while(ifeLookup.containsKey(targetPos.offset(Axis.X, (maxX-tX)+1))) ++maxX;
		}
		if(facing.getOffsetY() == 0){
//			ctx.getSource().sendFeedback(Text.literal("Varying Y"));
			while(ifeLookup.containsKey(targetPos.offset(Axis.Y, (minY-tY)-1))) --minY;
			while(ifeLookup.containsKey(targetPos.offset(Axis.Y, (maxY-tY)+1))) ++maxY;
		}
		if(facing.getOffsetZ() == 0){
//			ctx.getSource().sendFeedback(Text.literal("Varying Z"));
			while(ifeLookup.containsKey(targetPos.offset(Axis.Z, (minZ-tZ)-1))) --minZ;
			while(ifeLookup.containsKey(targetPos.offset(Axis.Z, (maxZ-tZ)+1))) ++maxZ;
		}
//		ctx.getSource().sendFeedback(Text.literal("Min: "+minX+" "+minY+" "+minZ+" | Max: "+maxX+" "+maxY+" "+maxZ));

//		final int tlX, tlY, tlZ, brX, brY, brZ;
//		if	   (tX == attachedPos.getX() + 1){tlX=brX=tX; tlY = maxY; brY = minY; tlZ = maxZ; brZ = minZ;}
//		else if(tX == attachedPos.getX() - 1){tlX=brX=tX; tlY = maxY; brY = minY; tlZ = minZ; brZ = maxZ;}
//		else if(tZ == attachedPos.getZ() + 1){tlZ=brZ=tZ; tlY = maxY; brY = minY; tlX = minX; brX = maxX;}
//		else if(tZ == attachedPos.getZ() - 1){tlZ=brZ=tZ; tlY = maxY; brY = minY; tlX = maxX; brX = minX;}
//		else if(tY == attachedPos.getY() + 1){tlY=brY=tY; tlX = minX; brX = maxX; tlZ = minZ; brZ = maxZ;}
//		else if(tY == attachedPos.getY() - 1){tlY=brY=tY; tlX = maxX; brX = minX; tlZ = maxZ; brZ = minZ;}
		ArrayList<Vec3i> mapWall = new ArrayList<>();//((1+maxX-minX)*(1+maxY-minY)*(1+maxZ-minZ));
		final int w;
		switch(facing){
			case UP: w=1+maxX-minX; for(int z=minZ; z<=maxZ; ++z) for(int x=minX; x<=maxX; ++x) mapWall.add(new Vec3i(x, tY, z)); break;
			case DOWN: w=1+maxX-minX; for(int z=maxZ; z>=minZ; --z) for(int x=maxX; x>=minX; --x) mapWall.add(new Vec3i(x, tY, z)); break;
			case NORTH: w=1+maxX-minX; for(int y=maxY; y>=minY; --y) for(int x=maxX; x>=minX; --x) mapWall.add(new Vec3i(x, y, tZ)); break;
			case SOUTH: w=1+maxX-minX; for(int y=maxY; y>=minY; --y) for(int x=minX; x<=maxX; ++x) mapWall.add(new Vec3i(x, y, tZ)); break;
			case EAST: w=1+maxZ-minZ; for(int y=maxY; y>=minY; --y) for(int z=maxZ; z>=minZ; --z) mapWall.add(new Vec3i(tX, y, z)); break;
			case WEST: w=1+maxZ-minZ; for(int y=maxY; y>=minY; --y) for(int z=minZ; z<=maxZ; ++z) mapWall.add(new Vec3i(tX, y, z)); break;
			default:
				// UNREACHABLE
				ctx.getSource().sendError(Text.literal("Invalid attached block distance"));
				return 1;
		}
		final int h = mapWall.size()/w;

		ctx.getSource().sendFeedback(Text.literal("Map wall size: "+w+"x"+h+" ("+mapWall.size()+")"));
		final int border = BLOCK_BORDER ? 1 : 0;
		BufferedImage img = new BufferedImage(128*w+border*2, 128*h+border*2, BufferedImage.TYPE_INT_ARGB);
		if(BLOCK_BORDER){
			int symW = w & 1, symH = h&1;
			for(int x=0; x<img.getWidth(); ++x){
				img.setRGB(x, 0, (((x+127)/128) & 1) == 1 ? BORDER_1 : BORDER_2);
				img.setRGB(x, img.getHeight()-1, (((x+127)/128) & 1) == symH ? BORDER_1 : BORDER_2);
			}
			for(int y=0; y<img.getHeight(); ++y){
				img.setRGB(0, y, (((y+127)/128) & 1) == 1 ? BORDER_1 : BORDER_2);
				img.setRGB(img.getWidth()-1, y, (((y+127)/128) & 1) == symW ? BORDER_1 : BORDER_2);
			}
		}
		for(int i=0; i<h; ++i) for(int j=0; j<w; ++j){
			ItemFrameEntity ife = ifeLookup.get(mapWall.get(i*w+j));
			if(ife == null){
				ctx.getSource().sendError(Text.literal("Non-rectangular MapArt wall not yet supported"));
				return 1;
			}
			if(ife.getRotation() != 0 && ife.getRotation() != 4){ // 8 possible rotations, but only 4 for mapart
				ctx.getSource().sendError(Text.literal("Rotated itemframes not yet supported"));
				return 1;
			}
			byte[] colors = FilledMapItem.getMapState(ife.getHeldItemStack(), client.world).colors;
			addMapToImg(img, colors, j*128+border, i*128+border);
		}
		if(SCALE_TO_640 && (w < 5 || h < 5)){
			int s = 2; while(128*w*s < 640 || 128*h*s < 640) ++s;
			ctx.getSource().sendFeedback(Text.literal("Upscaling img: x"+s));
			BufferedImage upscaledImg = new BufferedImage(128*w*s+(BLOCK_BORDER?s*2:0), 128*h*s+(BLOCK_BORDER?s*2:0), img.getType());
			Graphics2D g2d = upscaledImg.createGraphics();
			g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			g2d.drawImage(img, 0, 0, 128*w*s, 128*h*s, null);
			g2d.dispose();
			img = upscaledImg;
		}

		final Text nameText = targetIFrame.getHeldItemStack().getCustomName();
		final String nameStr = nameText == null ? null : nameText.getLiteralString();
		final String imgName = nameStr != null ? nameStr : targetIFrame.getHeldItemStack().get(DataComponentTypes.MAP_ID).asString();

		if(!new File(FileIO.DIR+"mapwalls/").exists()) new File(FileIO.DIR+"mapwalls/").mkdir();
		try{ImageIO.write(img, "png", new File(FileIO.DIR+"mapwalls/"+imgName+".png"));}
		catch(IOException e){e.printStackTrace();}

		ctx.getSource().sendFeedback(Text.literal("Saved mapwall to ./config/"+Main.MOD_ID+"/mapwalls/"+imgName+".png"));
		return 1;
	}
	private int runCommandWithMapName(CommandContext<FabricClientCommandSource> ctx){
		//final String mapName = ctx.getArgument("map_name", String.class);
		//TODO: get map by name (or * to download all)
		ctx.getSource().sendError(Text.literal("This version of the command is not yet implemented"));
		return 1;
	}

	public CommandDownloadMapWall(final boolean upscale, final boolean border){
		SCALE_TO_640 = upscale;
		BLOCK_BORDER = border;
		ClientCommandRegistrationCallback.EVENT.register(
//				new ClientCommandRegistrationCallback(){
//				@Override public void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess registryAccess){
				(dispatcher, _0) -> {
			dispatcher.register(
				ClientCommandManager.literal("downloadmapwall")
				.executes(this::runCommandNoArg)
				.then(
					ClientCommandManager.argument("map_name", StringArgumentType.word())
					.suggests((ctx, builder) -> {
						final int i = ctx.getInput().lastIndexOf(' ');
						final String lastArg = i == -1 ? "" : ctx.getInput().substring(i+1);
						Stream.of("*", "MapName 1", "MapName 2")
								.filter(name -> name.startsWith(lastArg))
								.forEach(name -> builder.suggest(name));
						return builder.buildFuture();
					})
					.executes(this::runCommandWithMapName)
				)
			);
		});
	}
}