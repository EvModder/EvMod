package net.evmodder.KeyBound.commands;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.evmodder.EvLib.FileIO;
import net.evmodder.KeyBound.Main;
import net.evmodder.KeyBound.MapRelationUtils;
import net.evmodder.KeyBound.MapRelationUtils.RelatedMapsData;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.block.MapColor;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapState;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.BlockPos;
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

	// Matrix math from the internet:
	private final void rotate90(byte[] matrix){
		// Transpose the matrix
		for(int i=0; i<128; ++i) for(int j=i+1; j<128; ++j){
			byte temp = matrix[i*128+j];
			matrix[i*128+j] = matrix[j*128+i];
			matrix[j*128+i] = temp;
		}
		// Reverse each row
		for(int i=0; i<128; ++i) for(int j=0; j<64; ++j){
			byte temp = matrix[i*128+j];
			matrix[i*128+j] = matrix[i*128+127-j];
			matrix[i*128+127-j] = temp;
		}
	}
	private final void rotate180(byte[] matrix){
		// Reverse the rows
		for(int i=0; i<64; ++i) for(int j=0; j<128; ++j){
			byte temp = matrix[i*128+j];
			matrix[i*128+j] = matrix[128*(127-i)+j];
			matrix[128*(127-i)+j] = temp;
		}
		// Reverse each row
		for(int i=0; i<128; ++i) for(int j=0; j<64; ++j){
			byte temp = matrix[i*128+j];
			matrix[i*128+j] = matrix[i*128+127-j];
			matrix[i*128+127-j] = temp;
		}
	}
	private final void rotate270(byte[] matrix){
		rotate90(matrix);
		rotate90(matrix);
		rotate90(matrix);
	}
	// </Matrix math from the internet>

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

	private int genImgForMapsInInv(FabricClientCommandSource source){
		int numShulksSaved = 0;
		String lastRelPath = null;
		/*invloop:*/for(int i=0; i<41; ++i){
			ItemStack stack = source.getPlayer().getInventory().getStack(i);
			ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
			if(container == null) continue;
			if(!container.streamNonEmpty().anyMatch(s -> FilledMapItem.getMapState(s, source.getWorld()) != null)) continue;
			final int size = (int)container.stream().count();
			if(size % 9 != 0){source.sendError(Text.literal("Unsupported container size: "+size)); continue;}
			final int border = BLOCK_BORDER ? 8 : 0;
			BufferedImage img = new BufferedImage(128*9+border*2, 128*(size/9)+border*2, BufferedImage.TYPE_INT_ARGB);
			if(border > 0) drawBorder(img);

			Iterator<ItemStack> contents = container.stream().toList().iterator();
			for(int y=0; y<(size/9); ++y) for(int x=0; x<9; ++x){
				final MapState state = FilledMapItem.getMapState(contents.next(), source.getWorld());
				if(state == null){
					//source.sendError(Text.literal("Slot "+x+","+y+" does not contain a loaded map"));
					//continue invloop;
					continue;
				}
				final int xo = x*128+border, yo = y*128+border;
				for(int a=0; a<128; ++a) for(int b=0; b<128; ++b) img.setRGB(xo+a, yo+b, MapColor.getRenderColor(state.colors[a + b*128]));
			}
			if(contents.hasNext()) source.sendError(Text.literal("HUH?! Leftover items in container iterator.. bug"));

			final String imgName = (stack.getCustomName() != null ? stack.getCustomName() : stack.getItemName()).getString()+" - slot"+i;
			if(!new File(FileIO.DIR+MAP_EXPORT_DIR).exists()) new File(FileIO.DIR+MAP_EXPORT_DIR).mkdir();
			try{ImageIO.write(img, "png", new File(lastRelPath=(FileIO.DIR+MAP_EXPORT_DIR+imgName+".png")));}
			catch(IOException e){e.printStackTrace();}
			++numShulksSaved;
		}
		if(numShulksSaved == 1){
			final String absolutePath = new File(lastRelPath).getAbsolutePath();
			final Text text = Text.literal("Saved map shulk img to ").withColor(16755200).append(
					Text.literal(lastRelPath).withColor(43520).formatted(Formatting.UNDERLINE)
					.styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, absolutePath)))
			);
			source.sendFeedback(text);
		}
		if(numShulksSaved > 1){
			final Text text = Text.literal("Saved "+numShulksSaved+" map shulk imgs to ").withColor(16755200).append(
					Text.literal(FileIO.DIR+MAP_EXPORT_DIR).withColor(43520).formatted(Formatting.UNDERLINE)
					.styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, new File(FileIO.DIR+MAP_EXPORT_DIR).getAbsolutePath())))
			);
			source.sendFeedback(text);
		}
		return numShulksSaved;
	}

	private void buildMapImgFile(final FabricClientCommandSource source, final HashMap<Vec3i, ItemFrameEntity> ifeLookup,
			final ArrayList<Vec3i> mapWall, final int w, final int h){
		final int border = BLOCK_BORDER ? 8 : 0;
		BufferedImage img = new BufferedImage(128*w+border*2, 128*h+border*2, BufferedImage.TYPE_INT_ARGB);
		if(BLOCK_BORDER) drawBorder(img);
		boolean nonRectangularWarningShown = false;
		for(int i=0; i<h; ++i) for(int j=0; j<w; ++j){
			ItemFrameEntity ife = ifeLookup.get(mapWall.get(i*w+j));
			if(ife == null){
				if(!nonRectangularWarningShown){
					source.sendError(Text.literal("Non-rectangular MapArt wall is not fully supported"));
					nonRectangularWarningShown = true;
				}
				//return false;
				continue;
			}
			final byte[] colors = FilledMapItem.getMapState(ife.getHeldItemStack(), source.getWorld()).colors;
			switch(ife.getRotation()%4){
				case 1: rotate90(colors); break;
				case 2: rotate180(colors); break;
				case 3: rotate270(colors); break;
			}
			final int xo = j*128+border, yo = i*128+border;
			for(int x=0; x<128; ++x) for(int y=0; y<128; ++y) img.setRGB(xo+x, yo+y, MapColor.getRenderColor(colors[x + y*128]));
		}
		if(128*w < UPSCALE_TO || 128*h < UPSCALE_TO){
			int s = 2; while(128*w*s < UPSCALE_TO || 128*h*s < UPSCALE_TO) ++s;
			source.sendFeedback(Text.literal("Upscaling img: x"+s));
			BufferedImage upscaledImg = new BufferedImage(128*w*s+(BLOCK_BORDER?s*2:0), 128*h*s+(BLOCK_BORDER?s*2:0), img.getType());
			Graphics2D g2d = upscaledImg.createGraphics();
			g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			g2d.drawImage(img, 0, 0, 128*w*s, 128*h*s, null);
			g2d.dispose();
			img = upscaledImg;
		}

		final ItemStack tlMapItemStack = ifeLookup.get(mapWall.getFirst()).getHeldItemStack();
		final Text nameText = tlMapItemStack.getCustomName();
		final String nameStr = nameText == null ? null : nameText.getLiteralString();
		String imgName;
		if(mapWall.size() == 1 || nameStr == null){
			imgName = nameStr == null ? tlMapItemStack.get(DataComponentTypes.MAP_ID).asString() : nameStr;
		}
		else{
			ItemStack[] sampleStacks = new ItemStack[]{
				tlMapItemStack,
				ifeLookup.get(mapWall.getLast()).getHeldItemStack()
			};
			RelatedMapsData data = MapRelationUtils.getRelatedMapsByName0(sampleStacks, source.getWorld());
			if(data.slots().size() != 2) imgName = nameStr;
			else imgName = nameStr.substring(0, data.prefixLen()) + nameStr.substring(nameStr.length()-data.suffixLen());
		}
		imgName = imgName.replaceAll("[.\\\\/]+", "_");

		//16755200
		if(!new File(FileIO.DIR+MAP_EXPORT_DIR).exists()) new File(FileIO.DIR+MAP_EXPORT_DIR).mkdir();
		final String relFilePath = FileIO.DIR+MAP_EXPORT_DIR+imgName+".png";
		File imgFile = new File(relFilePath);
		try{ImageIO.write(img, "png", imgFile);}
		catch(IOException e){e.printStackTrace();}

		final Text text = Text.literal("Saved mapwall to ").withColor(16755200).append(
				Text.literal(relFilePath).withColor(43520).formatted(Formatting.UNDERLINE)
				.styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, imgFile.getAbsolutePath())))
		);
		source.sendFeedback(text);
	}

//	private boolean ongoingExport;
	private boolean genImgForMapsInItemFrames(FabricClientCommandSource source,
			final HashMap<Vec3i, ItemFrameEntity> ifeLookup, final List<ItemFrameEntity> ifes){
		Direction facing = ifes.getFirst().getFacing();
		int minX = facing.getAxis() == Axis.X ? ifes.getFirst().getBlockX() : ifes.stream().mapToInt(ItemFrameEntity::getBlockX).min().getAsInt();
		int maxX = facing.getAxis() == Axis.X ? ifes.getFirst().getBlockX() : ifes.stream().mapToInt(ItemFrameEntity::getBlockX).max().getAsInt();
		int minY = facing.getAxis() == Axis.Y ? ifes.getFirst().getBlockY() : ifes.stream().mapToInt(ItemFrameEntity::getBlockY).min().getAsInt();
		int maxY = facing.getAxis() == Axis.Y ? ifes.getFirst().getBlockY() : ifes.stream().mapToInt(ItemFrameEntity::getBlockY).max().getAsInt();
		int minZ = facing.getAxis() == Axis.Z ? ifes.getFirst().getBlockZ() : ifes.stream().mapToInt(ItemFrameEntity::getBlockZ).min().getAsInt();
		int maxZ = facing.getAxis() == Axis.Z ? ifes.getFirst().getBlockZ() : ifes.stream().mapToInt(ItemFrameEntity::getBlockZ).max().getAsInt();

		ArrayList<Vec3i> mapWall = new ArrayList<>();//((1+maxX-minX)*(1+maxY-minY)*(1+maxZ-minZ));
		final int w;
		switch(facing){
			case UP: w=1+maxX-minX; for(int z=minZ; z<=maxZ; ++z) for(int x=minX; x<=maxX; ++x) mapWall.add(new Vec3i(x, minY, z)); break;
			case DOWN: w=1+maxX-minX; for(int z=maxZ; z>=minZ; --z) for(int x=minX; x<=maxX; ++x) mapWall.add(new Vec3i(x, minY, z)); break;
			case NORTH: w=1+maxX-minX; for(int y=maxY; y>=minY; --y) for(int x=maxX; x>=minX; --x) mapWall.add(new Vec3i(x, y, minZ)); break;
			case SOUTH: w=1+maxX-minX; for(int y=maxY; y>=minY; --y) for(int x=minX; x<=maxX; ++x) mapWall.add(new Vec3i(x, y, minZ)); break;
			case EAST: w=1+maxZ-minZ; for(int y=maxY; y>=minY; --y) for(int z=maxZ; z>=minZ; --z) mapWall.add(new Vec3i(minX, y, z)); break;
			case WEST: w=1+maxZ-minZ; for(int y=maxY; y>=minY; --y) for(int z=minZ; z<=maxZ; ++z) mapWall.add(new Vec3i(minX, y, z)); break;
			default:
				// UNREACHABLE
				source.sendError(Text.literal("Invalid attached block distance"));
				return false;
		}
		final int h = mapWall.size()/w;
		Main.LOGGER.info("ExportMapImg: Map wall size: "+w+"x"+h+" ("+mapWall.size()+")");
		source.sendFeedback(Text.literal("Map wall size: "+w+"x"+h+" ("+mapWall.size()+")"));

		if(w*h > 400){
			source.sendFeedback(Text.literal("Large image detected, may take a moment..."));
//			if(ongoingExport) return false;
//			ongoingExport = true;
			new Thread(){@Override public void run(){buildMapImgFile(source, ifeLookup, mapWall, w, h);/* ongoingExport = false;*/}}.run();
		}
		else buildMapImgFile(source, ifeLookup, mapWall, w, h);
		return true;
	}

	private void getConnectedFramesRecur(final HashMap<Vec3i, ?> ifeLookup, final Axis axis, final Vec3i pos, final HashSet<Vec3i> connected){
		connected.add(pos);
		for(Direction dir : Direction.values()){
			if(dir.getAxis() == axis) continue;
			Vec3i u = pos.offset(dir);
			if(!connected.contains(u) && ifeLookup.containsKey(u)) getConnectedFramesRecur(ifeLookup, axis, u, connected);
		}
	}
	private List<ItemFrameEntity> getConnectedFrames(HashMap<Vec3i, ItemFrameEntity> ifeLookup, ItemFrameEntity ife){
		final HashSet<Vec3i> connected = new HashSet<>();
		getConnectedFramesRecur(ifeLookup, ife.getFacing().getAxis(), ife.getBlockPos(), connected);

		/*//HashMap<Vec3i, ItemFrameEntity> ifeLookup = new HashMap<>();
		final Axis axis = ife.getFacing().getAxis();
		//iFrames.stream().filter(ife -> ife.getFacing() == facing).forEach(ife -> ifeLookup.put(ife.getBlockPos(), ife));

		HashSet<Vec3i> connected = new HashSet<>();
		ArrayDeque<Vec3i> adjSides = new ArrayDeque<>();
		adjSides.add(ife.getBlockPos());
		while(!adjSides.isEmpty()){
			Vec3i pos = adjSides.pop();
			connected.add(pos);
			for(Direction dir : Direction.values()){
				if(dir.getAxis() == axis) continue; // constexpr or smth
				Vec3i a = pos.offset(dir);
//				//Vec3i a = pos.offset(axis, 1), b = pos.offset(axis, -1);
				if(!connected.contains(a) && ifeLookup.containsKey(a)) adjSides.add(a);
//				//if(!connected.contains(b) && ifeLookup.containsKey(b)) adjSides.add(b);
			}
		}*/
		return connected.stream().map(ifeLookup::get).toList();
	}

	private List<ItemFrameEntity> getItemFramesWithMaps(FabricClientCommandSource source){
		Box everythingBox = Box.of(source.getPlayer().getPos(), RENDER_DIST*16, RENDER_DIST*16, RENDER_DIST*16);

		return source.getWorld().getEntitiesByType(TypeFilter.instanceOf(ItemFrameEntity.class), everythingBox,
				e -> e.getHeldItemStack().getItem() == Items.FILLED_MAP);
	}

	private int runCommandNoArg(final CommandContext<FabricClientCommandSource> ctx){
		ItemFrameEntity targetIFrame = null;
		double bestUh = 0;
		ClientPlayerEntity player = ctx.getSource().getPlayer();
		final Vec3d vec3d = player.getRotationVec(1.0F).normalize();
		final List<ItemFrameEntity> iFrames = getItemFramesWithMaps(ctx.getSource());
		for(ItemFrameEntity ife : iFrames){
			if(!player.canSee(ife)) continue;
			Vec3d vec3d2 = new Vec3d(ife.getX()-player.getX(), ife.getEyeY()-player.getEyeY(), ife.getZ()-player.getZ());
			final double d = vec3d2.length();
			vec3d2 = new Vec3d(vec3d2.x / d, vec3d2.y / d, vec3d2.z / d); // normalize
			final double e = vec3d.dotProduct(vec3d2);
			//e > 1.0d - 0.025d / d
			final double uh = (1.0d - 0.1d/d) - e;
			if(uh < bestUh){bestUh = uh; targetIFrame = ife;}
		}
		if(targetIFrame == null){
			// Fetch from inventory
			int numSaved = genImgForMapsInInv(ctx.getSource());
			if(numSaved == 0) ctx.getSource().sendError(Text.literal("No mapwall (in front of cursor) detected"));
			return numSaved == 0 ? 1 : 0;
		}
		// Fetch from iframe wall
		HashMap<Vec3i, ItemFrameEntity> ifeLookup = new HashMap<>();
		final Direction facing = targetIFrame.getFacing();
		iFrames.stream().filter(ife -> ife.getFacing() == facing).forEach(ife -> ifeLookup.put(ife.getBlockPos(), ife));
		Main.LOGGER.info("ExportMapImg: Same-direction iFrames: "+iFrames.size());
		List<ItemFrameEntity> ifes = getConnectedFrames(ifeLookup, targetIFrame);
		Main.LOGGER.info("ExportMapImg: Connected iFrames: "+ifes.size());
		return genImgForMapsInItemFrames(ctx.getSource(), ifeLookup, ifes) ? 0 : 1;
	}
	private int runCommandWithMapName(CommandContext<FabricClientCommandSource> ctx){
		final String mapName = ctx.getArgument("map_name", String.class);
		if(mapName.equals("*") || mapName.equalsIgnoreCase("all")){
			final List<ItemFrameEntity> iFrames = getItemFramesWithMaps(ctx.getSource());
			for(Direction dir : Direction.values()){
				HashMap<Vec3i, ItemFrameEntity> ifeLookup = new HashMap<>();
				iFrames.stream().filter(ife -> ife.getFacing() == dir).forEach(ife -> ifeLookup.put(ife.getBlockPos(), ife));
				while(!ifeLookup.isEmpty()){
					List<ItemFrameEntity> ifes = getConnectedFrames(ifeLookup, ifeLookup.values().iterator().next());
					if(!genImgForMapsInItemFrames(ctx.getSource(), ifeLookup, ifes)){
						Main.LOGGER.error("CmdImgExport: Encountered error while doing exporting * maps");
						return -1;
					}
					ifes.stream().map(ItemFrameEntity::getBlockPos).forEach(ifeLookup::remove);
				}
			}
			return 1;
		}
		ctx.getSource().sendError(Text.literal("This version of the command is not yet implemented (try without a param)"));
		return 1;
	}
	private final boolean isWithinBox(Vec3i pos, Vec3i minPos, Vec3i maxPos){//Todo: MiscUtils
		return pos.getX() >= minPos.getX() && pos.getY() >= minPos.getY() && pos.getZ() >= minPos.getZ()
			&& pos.getX() <= maxPos.getX() && pos.getY() <= maxPos.getY() && pos.getZ() <= maxPos.getZ();
	}
	private int runCommandWithPos1AndPos2(CommandContext<FabricClientCommandSource> ctx){
		final Vec3i pos1 = ctx.getArgument("pos1", BlockPos.class);
		final Vec3i pos2 = ctx.getArgument("pos2", BlockPos.class);
		if(pos1.getX() != pos2.getX() && pos1.getY() != pos2.getY() && pos1.getZ() != pos2.getZ()){
			ctx.getSource().sendError(Text.literal("iFrame selection area must be 2D (flat surface)"));
			return -1;
		}
		final Vec3i minPos = new Vec3i(Math.min(pos1.getX(), pos2.getX()), Math.min(pos1.getY(), pos2.getY()), Math.min(pos1.getZ(), pos2.getZ()));
		final Vec3i maxPos = new Vec3i(Math.max(pos1.getX(), pos2.getX()), Math.max(pos1.getY(), pos2.getY()), Math.max(pos1.getZ(), pos2.getZ()));
		final List<ItemFrameEntity> iFrames = getItemFramesWithMaps(ctx.getSource());
		iFrames.removeIf(ife -> !isWithinBox(ife.getBlockPos(), minPos, maxPos));
		if(iFrames.isEmpty()){
			ctx.getSource().sendError(Text.literal("No iFrames found within the given selection"));
			return -1;
		}
		// Get mode (most common occuring facing direction)
		final Direction facing = iFrames.stream().map(ife -> ife.getFacing())
				.collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
				.entrySet().stream().max(Map.Entry.comparingByValue()).get().getKey();
		iFrames.removeIf(ife -> ife.getFacing() != facing);

		HashMap<Vec3i, ItemFrameEntity> ifeLookup = new HashMap<>();
		iFrames.stream().forEach(ife -> ifeLookup.put(ife.getBlockPos(), ife));
		return genImgForMapsInItemFrames(ctx.getSource(), ifeLookup, iFrames) ? 0 : 1;
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
						Stream.of("all", "MapName1", "MapName2")
								.filter(name -> name.startsWith(lastArg))
								.forEach(name -> builder.suggest(name));
						return builder.buildFuture();
					})
					.executes(this::runCommandWithMapName)
				)
				.then(
					ClientCommandManager.argument("pos1", BlockPosArgumentType.blockPos())
					.then(
						ClientCommandManager.argument("pos2", BlockPosArgumentType.blockPos())
						.executes(this::runCommandWithPos1AndPos2)
					)
				)
			);
		});
	}
}