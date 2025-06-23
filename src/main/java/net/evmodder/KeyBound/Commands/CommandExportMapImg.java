package net.evmodder.KeyBound.Commands;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
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
import net.minecraft.block.MapColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapState;
import net.minecraft.text.Text;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.math.Direction.Axis;

public class CommandExportMapImg{
	final int RENDER_DIST = 10;
	final boolean BLOCK_BORDER;
	final int BORDER_1, BORDER_2, UPSCALE_TO;
	final String MAP_EXPORT_DIR = "mapart_exports/";

	private void drawBorder(BufferedImage img){
		final int border = 8;
		int MAGIC = 128-border;
		int w = (img.getWidth()-border*2)/128;
		int h = (img.getHeight()-border*2)/128;
		int symW = w & 1, symH = h&1;
		for(int x=0; x<img.getWidth(); ++x) for(int i=0; i<border; ++i){
			img.setRGB(x, i, (((x+MAGIC)/128) & 1) == 1 ? BORDER_1 : BORDER_2);
			img.setRGB(x, img.getHeight()-1-i, (((x+MAGIC)/128) & 1) == symH ? BORDER_1 : BORDER_2);
		}
		for(int y=0; y<img.getHeight(); ++y) for(int i=0; i<border; ++i){
			img.setRGB(i, y, (((y+MAGIC)/128) & 1) == 1 ? BORDER_1 : BORDER_2);
			img.setRGB(img.getWidth()-1-i, y, (((y+MAGIC)/128) & 1) == symW ? BORDER_1 : BORDER_2);
		}
	}

	private boolean genImgForMapsInInv(FabricClientCommandSource source){
		MinecraftClient client = MinecraftClient.getInstance();
		int numShulksSaved = 0;
		/*invloop:*/for(int i=0; i<41; ++i){
			ItemStack stack = client.player.getInventory().getStack(i);
			ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
			if(container == null) continue;
			if(!container.streamNonEmpty().anyMatch(s -> FilledMapItem.getMapState(s, client.world) != null)) continue;
			final int size = (int)container.stream().count();
			if(size % 9 != 0){source.sendError(Text.literal("Unsupported container size: "+size)); continue;}
			final int border = BLOCK_BORDER ? 8 : 0;
			BufferedImage img = new BufferedImage(128*9+border*2, 128*(size/9)+border*2, BufferedImage.TYPE_INT_ARGB);
			if(border > 0) drawBorder(img);

			Iterator<ItemStack> contents = container.stream().toList().iterator();
			for(int y=0; y<(size/9); ++y) for(int x=0; x<9; ++x){
				final MapState state = FilledMapItem.getMapState(contents.next(), client.world);
				if(state == null){
					//source.sendError(Text.literal("Slot "+x+","+y+" does not contain a loaded map"));
					//continue invloop;
					continue;
				}
				final int xo = x*128+border, yo = y*128+border;
				for(int a=0; a<128; ++a) for(int b=0; b<128; ++b) img.setRGB(xo+a, yo+b, MapColor.getRenderColor(state.colors[a + b*128]));
			}
			if(contents.hasNext()) source.sendError(Text.literal("HUH?! Leftover items in container iterator.. bug"));

			final Text nameText = stack.getCustomName();
			final String nameStr = nameText == null ? null : nameText.getLiteralString();
			final String imgName = nameStr != null ? nameStr : stack.get(DataComponentTypes.MAP_ID).asString();

			if(!new File(FileIO.DIR+MAP_EXPORT_DIR).exists()) new File(FileIO.DIR+MAP_EXPORT_DIR).mkdir();
			try{ImageIO.write(img, "png", new File(FileIO.DIR+MAP_EXPORT_DIR+imgName+".png"));}
			catch(IOException e){e.printStackTrace();}
			++numShulksSaved;
		}
		if(numShulksSaved > 0) source.sendFeedback(Text.literal("Exported "+numShulksSaved+" map shulk imgs to ./config/"+Main.MOD_ID+"/"+MAP_EXPORT_DIR));
		return numShulksSaved > 0;
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
			if(!genImgForMapsInInv(ctx.getSource())) ctx.getSource().sendError(Text.literal("No mapwall (in front of cursor) detected"));
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

		ArrayList<Vec3i> mapWall = new ArrayList<>();//((1+maxX-minX)*(1+maxY-minY)*(1+maxZ-minZ));
		final int w;
		switch(facing){
			case UP: w=1+maxX-minX; for(int z=minZ; z<=maxZ; ++z) for(int x=minX; x<=maxX; ++x) mapWall.add(new Vec3i(x, tY, z)); break;
			case DOWN: w=1+maxX-minX; for(int z=maxZ; z>=minZ; --z) for(int x=minX; x<=maxX; ++x) mapWall.add(new Vec3i(x, tY, z)); break;
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
		final int border = BLOCK_BORDER ? 8 : 0;
		BufferedImage img = new BufferedImage(128*w+border*2, 128*h+border*2, BufferedImage.TYPE_INT_ARGB);
		if(BLOCK_BORDER) drawBorder(img);
//			int MAGIC = 128-border;
//			int symW = w & 1, symH = h&1;
//			for(int x=0; x<img.getWidth(); ++x) for(int i=0; i<border; ++i){
//				img.setRGB(x, i, (((x+MAGIC)/128) & 1) == 1 ? BORDER_1 : BORDER_2);
//				img.setRGB(x, img.getHeight()-1-i, (((x+MAGIC)/128) & 1) == symH ? BORDER_1 : BORDER_2);
//			}
//			for(int y=0; y<img.getHeight(); ++y) for(int i=0; i<border; ++i){
//				img.setRGB(i, y, (((y+MAGIC)/128) & 1) == 1 ? BORDER_1 : BORDER_2);
//				img.setRGB(img.getWidth()-1-i, y, (((y+MAGIC)/128) & 1) == symW ? BORDER_1 : BORDER_2);
//			}
//		}
		for(int i=0; i<h; ++i) for(int j=0; j<w; ++j){
			ItemFrameEntity ife = ifeLookup.get(mapWall.get(i*w+j));
			if(ife == null){
				ctx.getSource().sendError(Text.literal("Non-rectangular MapArt wall not yet supported"));
				return 1;
			}
			if(ife.getRotation() != 0 && ife.getRotation() != 4){ // 8 possible rotations, but only 4 for mapart
				ctx.getSource().sendError(Text.literal("Rotated itemframes not yet supported ("
						+ife.getBlockX()%1000+" "+ife.getBlockY()+" "+ife.getBlockZ()%1000+")"));
				return 1;
			}
			final byte[] colors = FilledMapItem.getMapState(ife.getHeldItemStack(), client.world).colors;
			final int xo = j*128+border, yo = i*128+border;
			for(int x=0; x<128; ++x) for(int y=0; y<128; ++y) img.setRGB(xo+x, yo+y, MapColor.getRenderColor(colors[x + y*128]));
		}
		if(128*w < UPSCALE_TO || 128*h < UPSCALE_TO){
			int s = 2; while(128*w*s < UPSCALE_TO || 128*h*s < UPSCALE_TO) ++s;
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

		if(!new File(FileIO.DIR+MAP_EXPORT_DIR).exists()) new File(FileIO.DIR+MAP_EXPORT_DIR).mkdir();
		try{ImageIO.write(img, "png", new File(FileIO.DIR+MAP_EXPORT_DIR+imgName+".png"));}
		catch(IOException e){e.printStackTrace();}

		ctx.getSource().sendFeedback(Text.literal("Saved mapwall to ./config/"+Main.MOD_ID+"/"+MAP_EXPORT_DIR+imgName+".png"));
		return 1;
	}
	private int runCommandWithMapName(CommandContext<FabricClientCommandSource> ctx){
		//final String mapName = ctx.getArgument("map_name", String.class);
		//TODO: get map by name (or * to download all)
		ctx.getSource().sendError(Text.literal("This version of the command is not yet implemented"));
		return 1;
	}

	public CommandExportMapImg(final int upscaleTo, final boolean border, final int border1, final int border2){
		UPSCALE_TO = upscaleTo;
		BLOCK_BORDER = border;
		BORDER_1 = border1;
		BORDER_2 = border2;
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, _0) -> {
			dispatcher.register(
				ClientCommandManager.literal(getClass().getSimpleName().substring(7).toLowerCase()/*"mapwallimg"*/)
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