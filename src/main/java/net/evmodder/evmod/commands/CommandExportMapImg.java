package net.evmodder.evmod.commands;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import fi.dy.masa.malilib.util.StringUtils;
import net.evmodder.EvLib.util.FileIO;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.Main;
import net.evmodder.evmod.apis.InvUtils;
import net.evmodder.evmod.apis.MapRelationUtils;
import net.evmodder.evmod.apis.MapRelationUtils.RelatedMapsData;
import net.evmodder.evmod.onTick.UpdateItemFrameHighlights;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.block.MapColor;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BundleContentsComponent;
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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.math.Direction.Axis;

public class CommandExportMapImg{
	final int RENDER_DIST = 10*16;
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
		final int BORDER_1 = Configs.Visuals.EXPORT_MAP_IMG_BORDER_COLOR1.getIntegerValue();
		final int BORDER_2 = Configs.Visuals.EXPORT_MAP_IMG_BORDER_COLOR1.getIntegerValue();
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

	private BufferedImage drawImgForMapStates(final FabricClientCommandSource source, final List<MapState> states, final int width){
		final int height = (states.size()-1)/width + 1;
		final int border = Configs.Visuals.EXPORT_MAP_IMG_BORDER.getBooleanValue() ? 8 : 0;
		BufferedImage img = new BufferedImage(128*width + border*2, 128*height + border*2, BufferedImage.TYPE_INT_ARGB);
		if(border > 0) drawBorder(img);

		Iterator<MapState> contents = states.iterator();
		for(int y=0; y<height; ++y) for(int x=0; x<width; ++x){
			final byte[] colors = contents.next().colors;
			final int xo = x*128+border, yo = y*128+border;
			for(int a=0; a<128; ++a) for(int b=0; b<128; ++b) img.setRGB(xo+a, yo+b, MapColor.getRenderColor(colors[a + b*128]));
			if(!contents.hasNext()) return img;
		}
		assert false : "ExportMapImg: Width*Height < states.size()?!";
		return img;
	}

	private String lastRelPath = null;
	private int genImgForMapsInInv(FabricClientCommandSource source, List<ItemStack> inventory, final String name, final int width, final boolean combine){
		final List<MapState> unnestedMaps = inventory.stream().map(s -> FilledMapItem.getMapState(s, source.getWorld())).filter(Objects::nonNull).toList();
		List<MapState> allMaps = InvUtils.getAllNestedItems(inventory.stream())
				.map(s -> FilledMapItem.getMapState(s, source.getWorld()))
				.filter(Objects::nonNull).toList();

		int numExports = 0;
		if(!unnestedMaps.isEmpty()){
			final int w = combine && allMaps.size() > unnestedMaps.size() ? (int)Math.ceil(Math.sqrt(allMaps.size())) : Math.min(width, unnestedMaps.size());
			final BufferedImage img = drawImgForMapStates(source, combine ? allMaps : unnestedMaps, w);

			if(!new File(FileIO.DIR+MAP_EXPORT_DIR).exists()) new File(FileIO.DIR+MAP_EXPORT_DIR).mkdir();
			try{ImageIO.write(img, "png", new File(lastRelPath=(FileIO.DIR+MAP_EXPORT_DIR+name+".png")));}
			catch(IOException e){e.printStackTrace();}

			if(combine || allMaps.size() == unnestedMaps.size()) return 1;
			//else: handle sub-maps
		}
		for(int i=0; i<inventory.size(); ++i){
			final ItemStack stack = inventory.get(i);
			final ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
			final BundleContentsComponent contents = stack.get(DataComponentTypes.BUNDLE_CONTENTS);
			if(container == null && contents == null) continue;
			final Text nameText = stack.getCustomName();
			final String containerName = nameText != null ? nameText.getString() : name+"-slot"+i+":"+stack.getItemName().getString();
			if(container != null){
				List<ItemStack> subItems = com.google.common.collect.Streams.stream(container.iterateNonEmpty()).toList();
				boolean subCombine = subItems.stream().noneMatch(s -> FilledMapItem.getMapState(s, source.getWorld()) != null);
				int w = subCombine ? (int)Math.ceil(Math.sqrt(subItems.size())) : 9;
				numExports += genImgForMapsInInv(source, subItems, containerName, w, subCombine);
			}
			if(contents != null){
				List<ItemStack> subItems = com.google.common.collect.Streams.stream(contents.iterate()).toList();
				int w = (int)Math.ceil(Math.sqrt(subItems.size())); // Should max out at 8
				numExports += genImgForMapsInInv(source, subItems, containerName, w, /*combine=*/true); // Combine nested bundles
			}
		}
		return numExports;
	}

	private final String getCleanedName(String itemName0, RelatedMapsData data){
//		if(data.prefixLen() == -1) return itemName0;
//		else{
//			final String nameWoArtist = MapRelationUtils.removeByArtist(itemName0);
//			return itemName0.substring(0, data.prefixLen())
//					+ "*"
//					+ nameWoArtist.substring(nameWoArtist.length()-data.suffixLen())
//					+ itemName0.substring(nameWoArtist.length());
//		}
		if(data.prefixLen() == -1) return itemName0.trim();
		else{
			String nameWithoutArtist = MapRelationUtils.removeByArtist(itemName0);
			String prefixStr = nameWithoutArtist.substring(0, data.prefixLen());
			String suffixStr = nameWithoutArtist.substring(nameWithoutArtist.length() - data.suffixLen());
			if(itemName0.startsWith(nameWithoutArtist)) suffixStr += itemName0.substring(nameWithoutArtist.length());
			else if(itemName0.endsWith(nameWithoutArtist)) prefixStr = prefixStr + itemName0.substring(0, itemName0.length()-nameWithoutArtist.length());
			else Main.LOGGER.info("CmdExportImg: trouble re-attaching artist name (not at start or end)");
			if(REMOVE_MAX_CNT){
				final String szCntStr = ""+data.slots().size();
				final int idx = suffixStr.indexOf(szCntStr);
				if(idx != -1 && Normalizer.normalize(suffixStr.substring(0, idx), Normalizer.Form.NFKD).toLowerCase().matches("\\s*(of|/)\\s*")){
					suffixStr = suffixStr.substring(idx + szCntStr.length());
				}
			}
			// Trim leading/trailing whitespace
			final String prefixStrTrimmed = prefixStr.stripTrailing();
			final String suffixStrTrimmed =  suffixStr.stripLeading();
			final boolean hadSpace = prefixStrTrimmed.length() < prefixStr.length() || suffixStrTrimmed.length() < suffixStr.length();
			prefixStr = prefixStrTrimmed + (hadSpace ? " " : "");
			suffixStr = suffixStrTrimmed;

			if(REMOVE_BRACKET_SYMBOLS){
				int a=prefixStr.length()-1, b=0;
				while(true){
					while(a >= 0 && Character.isWhitespace(prefixStr.charAt(a))) --a;
					while(b < suffixStr.length() && Character.isWhitespace(suffixStr.charAt(b))) ++b;
					if(a == -1 || b == suffixStr.length() || !isReflectedChar(prefixStr.charAt(a), suffixStr.charAt(b))) break;
					--a; ++b;
				}
				prefixStr = prefixStr.substring(0, a+1) + (hadSpace ? " " : "");
				suffixStr = suffixStr.substring(b);
			}
			return (prefixStr + suffixStr).trim();
		}
	}

	private int overwritten;
	private final void buildMapImgFile(final FabricClientCommandSource source, final Map<Vec3i, ItemFrameEntity> ifeLookup,
			final ArrayList<Vec3i> mapWall, final int w, final int h, final String namePrefix){
		final boolean BLOCK_BORDER = Configs.Visuals.EXPORT_MAP_IMG_BORDER.getBooleanValue();
		final int border = BLOCK_BORDER ? 8 : 0;
		BufferedImage img = new BufferedImage(128*w+border*2, 128*h+border*2, BufferedImage.TYPE_INT_ARGB);
		if(BLOCK_BORDER) drawBorder(img);
//		boolean nonRectangularWarningShown = false;
		for(int i=0; i<h; ++i) for(int j=0; j<w; ++j){
			ItemFrameEntity ife = ifeLookup.get(mapWall.get(i*w+j));
			if(ife == null){
//				if(!nonRectangularWarningShown){
					source.sendError(Text.literal("Non-rectangular MapArt wall is not fully supported"));
//					nonRectangularWarningShown = true;
//				}
//				return;
				continue;
			}
			final MapState state = FilledMapItem.getMapState(ife.getHeldItemStack(), source.getWorld());
			if(state == null){
				source.sendError(Text.literal("state == null in buildMapImgFile()!"));
				Main.LOGGER.error("ExportMapImg: state == null in buildMapImgFile()!");
				continue;
			}
			final byte[] colors = state.colors;
			switch(ife.getRotation()%4){
				case 1: rotate90(colors); break;
				case 2: rotate180(colors); break;
				case 3: rotate270(colors); break;
			}
			final int xo = j*128+border, yo = i*128+border;
			for(int x=0; x<128; ++x) for(int y=0; y<128; ++y) img.setRGB(xo+x, yo+y, MapColor.getRenderColor(colors[x + y*128]));
		}
		final int UPSCALE_TO = Configs.Visuals.EXPORT_MAP_IMG_UPSCALE.getIntegerValue();
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

		final ItemStack tlMapItemStack = ifeLookup.get(mapWall.stream().filter(ifeLookup::containsKey).findFirst().get()).getHeldItemStack();
		final Text nameText = tlMapItemStack.getCustomName();
		final String nameStr = nameText == null ? null : nameText.getString();
		String imgName;
		if(mapWall.size() == 1 || nameStr == null){
			imgName = nameStr == null ? tlMapItemStack.get(DataComponentTypes.MAP_ID).asString() : nameStr;
		}
		else{
			List<ItemStack> sampleStacks = List.of(
				tlMapItemStack,
				ifeLookup.get(mapWall.reversed().stream().filter(ifeLookup::containsKey).findFirst().get()).getHeldItemStack()
			);
			RelatedMapsData data = MapRelationUtils.getRelatedMapsByName0(sampleStacks, source.getWorld());
			imgName = getCleanedName(nameStr, data);
		}
		imgName = namePrefix + imgName.trim().replaceAll("[.\\\\/]+", "_");

		//16755200
		if(!new File(FileIO.DIR+MAP_EXPORT_DIR).exists()) new File(FileIO.DIR+MAP_EXPORT_DIR).mkdir();
		final String relFilePath = FileIO.DIR+MAP_EXPORT_DIR+imgName+".png";
		final File imgFile = new File(relFilePath);
		if(imgFile.exists()) ++overwritten;
		try{ImageIO.write(img, "png", imgFile);}
		catch(IOException e){e.printStackTrace();}

		final Text text = Text.literal("Saved mapwall to ").withColor(16755200).append(
				Text.literal(relFilePath).withColor(43520).formatted(Formatting.UNDERLINE)
				.styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, imgFile.getAbsolutePath())))
		);
		source.sendFeedback(text);
	}

//	private boolean ongoingExport;
	private boolean genImgForMapsInItemFrames(FabricClientCommandSource source, final List<ItemFrameEntity> ifes, final String namePrefix){
		Direction facing = ifes.getFirst().getFacing();
		int minX = facing.getAxis() == Axis.X ? ifes.getFirst().getBlockX() : ifes.stream().mapToInt(ItemFrameEntity::getBlockX).min().getAsInt();
		int maxX = facing.getAxis() == Axis.X ? ifes.getFirst().getBlockX() : ifes.stream().mapToInt(ItemFrameEntity::getBlockX).max().getAsInt();
		int minY = facing.getAxis() == Axis.Y ? ifes.getFirst().getBlockY() : ifes.stream().mapToInt(ItemFrameEntity::getBlockY).min().getAsInt();
		int maxY = facing.getAxis() == Axis.Y ? ifes.getFirst().getBlockY() : ifes.stream().mapToInt(ItemFrameEntity::getBlockY).max().getAsInt();
		int minZ = facing.getAxis() == Axis.Z ? ifes.getFirst().getBlockZ() : ifes.stream().mapToInt(ItemFrameEntity::getBlockZ).min().getAsInt();
		int maxZ = facing.getAxis() == Axis.Z ? ifes.getFirst().getBlockZ() : ifes.stream().mapToInt(ItemFrameEntity::getBlockZ).max().getAsInt();

		Map<Vec3i, ItemFrameEntity> ifeLookup = ifes.stream().collect(Collectors.toMap(ItemFrameEntity::getBlockPos, Function.identity()));
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
//		source.sendFeedback(Text.literal("Map wall size: "+w+"x"+h+" ("+mapWall.size()+")"));

		if(w*h > 400){
			source.sendFeedback(Text.literal("Large image detected, may take a moment..."));
//			if(ongoingExport) return false;
//			ongoingExport = true;
			new Thread(){@Override public void run(){buildMapImgFile(source, ifeLookup, mapWall, w, h, namePrefix);/* ongoingExport = false;*/}}.run();
		}
		else buildMapImgFile(source, ifeLookup, mapWall, w, h, namePrefix);
		return true;
	}

	//private void getConnectedFramesRecur(final Map<XYZD, ?> ifeLookup, final XYZD xyzd, final HashSet<XYZD> connected){
	private void getConnectedFramesRecur(final Map<Vec3i, ?> ifeLookup, final Axis axis, final Vec3i pos, final HashSet<Vec3i> connected){
		connected.add(pos);
		for(Direction dir : Direction.values()){
			if(dir.getAxis() == axis) continue;
			Vec3i u = pos.offset(dir);
			//XYZD u = new XYZD(xyzd.xyz.offset(dir), xyzd.d);
			if(!connected.contains(u) && ifeLookup.containsKey(u)) getConnectedFramesRecur(ifeLookup, axis, u, connected);
		}
	}
	//private List<ItemFrameEntity> getConnectedFrames(Map<XYZD, ItemFrameEntity> ifeLookup, ItemFrameEntity ife){
	private List<ItemFrameEntity> getConnectedFrames(Map<Vec3i, ItemFrameEntity> ifeLookup, ItemFrameEntity ife){
		final HashSet<Vec3i> connected = new HashSet<>();
		getConnectedFramesRecur(ifeLookup, ife.getFacing().getAxis(), ife.getBlockPos(), connected);

		return connected.stream().map(ifeLookup::get).toList();
	}

	private List<ItemFrameEntity> getItemFramesWithMaps(ClientPlayerEntity player){
		Box everythingBox = Box.of(player.getPos(), RENDER_DIST, RENDER_DIST, RENDER_DIST);

		return player.getWorld().getEntitiesByType(TypeFilter.instanceOf(ItemFrameEntity.class), everythingBox,
				e -> e.getHeldItemStack().getItem() == Items.FILLED_MAP);
	}

	private int runCommandInInventory(final CommandContext<FabricClientCommandSource> ctx){
		final int numSaved = genImgForMapsInInv(ctx.getSource(),
				ctx.getSource().getPlayer().getInventory().main,
				/*name=*/StringUtils.translate("container.inventory"), /*width=*/9, /*combine=*/false);
		if(numSaved == 1){
			final String absolutePath = new File(lastRelPath).getAbsolutePath();
			ctx.getSource().sendFeedback(Text.literal("Saved map shulk img to ").withColor(16755200).append(
					Text.literal(lastRelPath).withColor(43520).formatted(Formatting.UNDERLINE)
					.styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, absolutePath)))
			));
		}
		if(numSaved > 1){
			ctx.getSource().sendFeedback(Text.literal("Saved "+numSaved+" map shulk imgs to ").withColor(16755200).append(
					Text.literal(FileIO.DIR+MAP_EXPORT_DIR).withColor(43520).formatted(Formatting.UNDERLINE)
					.styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, new File(FileIO.DIR+MAP_EXPORT_DIR).getAbsolutePath())))
			));
		}
		return numSaved == 0 ? 1 : 0;
	}

	private int runCommandNoArg(final CommandContext<FabricClientCommandSource> ctx){
		ItemFrameEntity targetIFrame = null;
		double bestUh = 0;
		ClientPlayerEntity player = ctx.getSource().getPlayer();
		final Vec3d vec3d = player.getRotationVec(1.0F).normalize();
		final List<ItemFrameEntity> iFrames = getItemFramesWithMaps(ctx.getSource().getPlayer());
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
			// Try checking in inventory
			final int cmdFeedbackStatus = runCommandInInventory(ctx);
			if(cmdFeedbackStatus == 1) ctx.getSource().sendError(Text.literal("No mapwall (in front of cursor) detected"));
			return cmdFeedbackStatus;
		}
		// Fetch from iframe wall
//		HashMap<Vec3i, ItemFrameEntity> ifeLookup = new HashMap<>();
		final Direction facing = targetIFrame.getFacing();
		final Axis axis = facing.getAxis();
		final int axisComponent = targetIFrame.getBlockPos().getComponentAlongAxis(axis);
		Map<Vec3i, ItemFrameEntity> ifeLookup = iFrames.stream()
			.filter(ife -> ife.getFacing() == facing && ife.getBlockPos().getComponentAlongAxis(axis) == axisComponent)
			.collect(Collectors.toMap(ItemFrameEntity::getBlockPos, Function.identity()));
//		Main.LOGGER.info("ExportMapImg: Same-direction iFrames: "+iFrames.size());
		List<ItemFrameEntity> ifes = getConnectedFrames(ifeLookup, targetIFrame);
		Main.LOGGER.info("ExportMapImg: Connected iFrames: "+ifes.size());
		return genImgForMapsInItemFrames(ctx.getSource(), ifes, "") ? 0 : 1;
	}

	private record MapWall(Direction dir, int axis){}
	private int runCommandForAllWalls(CommandContext<FabricClientCommandSource> ctx){
		final Map<MapWall, List<ItemFrameEntity>> mapWalls = getItemFramesWithMaps(ctx.getSource().getPlayer()).stream().collect(Collectors.groupingBy(
				ife -> new MapWall(ife.getFacing(), ife.getBlockPos().getComponentAlongAxis(ife.getFacing().getAxis())) // Group by MapWall
		));
		Main.LOGGER.info("CmdImgExport: runCommandWithAllMapWalls() num mapwalls: "+mapWalls.size());
		int numMapsSaved = 0;
		overwritten = 0;
		for(List<ItemFrameEntity> mapWall : mapWalls.values()){
//			Main.LOGGER.info("CmdImgExport: mapWall size A: "+mapWall.size());
			final Map<Vec3i, ItemFrameEntity> ifeLookup = mapWall.stream().collect(Collectors.toMap(
					ItemFrameEntity::getBlockPos, // Key
					Function.identity(), // Value
					(o, n) -> o, // Merge function (for key collisions)
					HashMap::new // Map supplier
				));
//			Main.LOGGER.info("CmdImgExport: mapWall size B: "+ifeLookup.size());
			while(!ifeLookup.isEmpty()){
				List<ItemFrameEntity> ifes = getConnectedFrames(ifeLookup, ifeLookup.values().iterator().next());
//				Main.LOGGER.info("CmdImgExport: size of connected mapWall section: "+ifes.size());
				if(!genImgForMapsInItemFrames(ctx.getSource(), ifes, "walls_")){
					Main.LOGGER.error("CmdImgExport: Encountered an error while exporting a "+ifes.size()+"-id mapwall");
					ctx.getSource().sendError(Text.literal("Encountered an error while exporting a "+ifes.size()+"-id mapwall"));
					return -1;
				}
				++numMapsSaved;
				ifes.stream().map(ItemFrameEntity::getBlockPos).forEach(ifeLookup::remove);
			}
		}
		numMapsSaved -= overwritten;
		if(numMapsSaved > 5) ctx.getSource().sendFeedback(Text.literal(numMapsSaved+(overwritten>0?" new":"")+" images saved"));
		if(overwritten > 0) ctx.getSource().sendFeedback(Text.literal(overwritten+" images overwritten"));
		return 1;
	}
	private int runCommandForAllMaps(CommandContext<FabricClientCommandSource> ctx){
		final HashSet<String> seen = new HashSet<>();
		final Map<MapWall, List<ItemFrameEntity>> mapWalls = getItemFramesWithMaps(ctx.getSource().getPlayer()).stream()
				.filter(ife -> ife.getHeldItemStack().getCustomName() != null) // Only consider named maps
				.filter(ife -> !seen.add(ife.getHeldItemStack().getCustomName().getString())) // Remove duplicate names
				.collect(Collectors.groupingBy(
				ife -> new MapWall(ife.getFacing(), ife.getBlockPos().getComponentAlongAxis(ife.getFacing().getAxis())) // Group by MapWall
		));
		int numMapsSaved = 0;
		for(List<ItemFrameEntity> mapWall : mapWalls.values()){
			final Map<Vec3i, ItemFrameEntity> ifeLookup = mapWall.stream().collect(Collectors.toMap(
					ItemFrameEntity::getBlockPos, // Key
					Function.identity(), // Value
					(o, n) -> o, // Merge function (for key collisions)
					HashMap::new // Map supplier
				));
			while(!ifeLookup.isEmpty()){
				List<ItemFrameEntity> ifes = getConnectedFrames(ifeLookup, ifeLookup.values().iterator().next());
				IdentityHashMap<ItemStack, ItemFrameEntity> stackToIfe = ifes.stream().collect(Collectors.toMap(
						ItemFrameEntity::getHeldItemStack, // Key: ItemStack (address)
						Function.identity(), // Equivalent to `ife -> ife`
						(o, n) -> o, // Merge function for duplicates (will never be called for this case)
						IdentityHashMap::new // Map supplier
				));
				List<ItemStack> mapItems = new LinkedList<>(stackToIfe.keySet());
				while(!mapItems.isEmpty()){
					final String name = mapItems.getFirst().getName().getString();
					final MapState state = FilledMapItem.getMapState(mapItems.getFirst(), ctx.getSource().getWorld());
					if(state == null) Main.LOGGER.error("ExportMapImg: State is null! in runCommandForAllMaps()");
					final Boolean locked = state == null ? null : state.locked;
					RelatedMapsData data = MapRelationUtils.getRelatedMapsByName(mapItems, name, 1, locked, ctx.getSource().getWorld());
					assert !data.slots().isEmpty();
					final boolean success;
					if(data.slots().size() <= 1){
						success = genImgForMapsInItemFrames(ctx.getSource(), List.of(stackToIfe.get(mapItems.getFirst())), "named_");
						mapItems.removeFirst();
					}
					else{
						List<ItemStack> relatedStacks = new ArrayList<>(data.slots().size());
						Iterator<ItemStack> it = mapItems.iterator();
						for(int i=0, j=0; i<data.slots().size(); ++i, ++j){
							while(j < data.slots().get(i)){it.next(); ++j;}
							relatedStacks.add(it.next());
							it.remove();
						}
						success = genImgForMapsInItemFrames(ctx.getSource(), relatedStacks.stream().map(stackToIfe::get).toList(), "named_");
					}
					if(!success){
						ctx.getSource().sendError(Text.literal("Encountered an error while exporting map img: "+name));
						Main.LOGGER.error("CmdImgExport: Encountered error while exporting map img for name: "+name);
						return -1;
					}
					++numMapsSaved;
				}
				ifes.stream().map(ItemFrameEntity::getBlockPos).forEach(ifeLookup::remove);
			}
		}
		if(numMapsSaved > 5) ctx.getSource().sendFeedback(Text.literal(numMapsSaved+" images saved"));
		return 1;
	}
	private int runCommandForMapName(CommandContext<FabricClientCommandSource> ctx){
		final String mapName = ctx.getArgument("map_name", String.class);
		final String mapName0 = cmdMapNames.getOrDefault(mapName, mapName);
		Main.LOGGER.info("Using lookup name: "+mapName0);
//		assert !mapName.isBlank();
		IdentityHashMap<ItemStack, ItemFrameEntity> stackToIfe = getItemFramesWithMaps(ctx.getSource().getPlayer()).stream().collect(Collectors.toMap(
				ItemFrameEntity::getHeldItemStack, // Key: ItemStack (address)
				Function.identity(), // Equivalent to `ife -> ife`
				(o, n) -> o, // Merge function for duplicates (will never be called for this case)
				IdentityHashMap::new // Map supplier
		));
				//.collect(Collectors.toMap(ItemFrameEntity::getHeldItemStack, ife -> ife));
//		assert stackToIfe.size() == iFrames.size();
		ArrayList<ItemStack> slots = new ArrayList<>(stackToIfe.keySet());
		RelatedMapsData data = MapRelationUtils.getRelatedMapsByName(slots, mapName0, 1, /*locked=*/null, ctx.getSource().getWorld());
		Main.LOGGER.info("related maps found: "+data.slots().size());
		if(data.slots().isEmpty()){
			ctx.getSource().sendError(Text.literal("Unable to find map: "+mapName));
			return -1;
		}
//		if(data.prefixLen() != -1) Main.LOGGER.info("CmdImgExport: prefix/suffix len: "+data.prefixLen()+", "+data.suffixLen());

		if(!genImgForMapsInItemFrames(ctx.getSource(), data.slots().stream().map(i -> stackToIfe.get(slots.get(i))).toList(), "named_")){
			ctx.getSource().sendError(Text.literal("Encountered an error while exporting map img"));
			Main.LOGGER.error("CmdImgExport: Encountered error while exporting map img for name: "+mapName);
			return -1;
		}

//		ctx.getSource().sendError(Text.literal("This version of the command is not yet implemented (try without a param)"));
		return 1;
	}
	private int runCommandForPos1AndPos2(CommandContext<FabricClientCommandSource> ctx){
		final Vec3i pos1 = ClientBlockPosArgumentType.getBlockPos(ctx, "pos1");
		final Vec3i pos2 = ClientBlockPosArgumentType.getBlockPos(ctx, "pos2");
		if(pos1.getX() != pos2.getX() && pos1.getY() != pos2.getY() && pos1.getZ() != pos2.getZ()){
			ctx.getSource().sendError(Text.literal("iFrame selection area must be 2D (flat surface)"));
			return -1;
		}
		final Box box = new Box(new Vec3d(pos1), new Vec3d(pos2));
		final List<ItemFrameEntity> iFrames = getItemFramesWithMaps(ctx.getSource().getPlayer());
		iFrames.removeIf(ife -> !box.contains(ife.getPos()));
		if(iFrames.isEmpty()){
			ctx.getSource().sendError(Text.literal("No iFrames found within the given selection"));
			return -1;
		}
		// Get mode (most common occuring facing direction)
		final Direction facing = iFrames.stream().map(ife -> ife.getFacing()).collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
				.entrySet().stream().max(Map.Entry.comparingByValue()).get().getKey();
		iFrames.removeIf(ife -> ife.getFacing() != facing);

		return genImgForMapsInItemFrames(ctx.getSource(), iFrames, "area_") ? 0 : 1;
	}

	private final boolean isReflectedChar(char l, char r){
		switch(l){
			case '[': return r == ']';
			case '(': return r == ')';
			case '{': return r == '}';
			case '<': return r == '>';
			case '-': return r == '-';
			default:
				return Character.isWhitespace(l) && Character.isWhitespace(r);
		}
	}
	private final boolean SHOW_ONLY_IF_HAS_AZ = true, REMOVE_MAX_CNT = true, REMOVE_BRACKET_SYMBOLS = true;
	private final HashMap<String, String> cmdMapNames = new HashMap<>();
	private long lastNameComputeTs;
	private Set<String> getNearbyMapNames(ClientPlayerEntity player){
		if(!cmdMapNames.isEmpty() && lastNameComputeTs >= UpdateItemFrameHighlights.lastIFrameMapGroupUpdateTs) return cmdMapNames.keySet();
		lastNameComputeTs = System.currentTimeMillis();
		cmdMapNames.clear();

		final HashSet<String> seen = new HashSet<>();
		Stream<ItemFrameEntity> ifeStream = getItemFramesWithMaps(player).stream()
				.filter(ife -> ife.getHeldItemStack().getCustomName() != null); // Only consider named maps
		if(SHOW_ONLY_IF_HAS_AZ) ifeStream = ifeStream.filter(ife -> ife.getHeldItemStack().getCustomName().getString().matches(".*[a-zA-Z].*"));
		ifeStream = ifeStream.filter(ife -> seen.add(ife.getHeldItemStack().getCustomName().getString())); // Remove duplicate names
		final Map<MapWall, List<ItemFrameEntity>> mapWalls = ifeStream.collect(Collectors.groupingBy(
				ife -> new MapWall(ife.getFacing(), ife.getBlockPos().getComponentAlongAxis(ife.getFacing().getAxis())) // Group by MapWall
		));
		for(List<ItemFrameEntity> mapWall : mapWalls.values()){
			List<ItemStack> mapItems = mapWall.stream().map(ife -> ife.getHeldItemStack()).collect(Collectors.toCollection(LinkedList::new));
			while(!mapItems.isEmpty()){
				final String name = mapItems.getFirst().getCustomName().getString();
				final MapState state = FilledMapItem.getMapState(mapItems.getFirst(), player.getWorld());
				final Boolean locked = state == null ? null : state.locked;
				RelatedMapsData data = MapRelationUtils.getRelatedMapsByName(mapItems, name, 1, locked, player.getWorld());
				final String nameKey = getCleanedName(name, data);
				if(!SHOW_ONLY_IF_HAS_AZ || nameKey.matches(".*[a-zA-Z].*")) cmdMapNames.put(nameKey, name);
//				assert data.slots().size() > 0; // Can be size=0 for mismatched pos data
				if(data.slots().size() <= 1) mapItems.removeFirst();
				else{
					Iterator<ItemStack> it = mapItems.iterator();
//					Main.LOGGER.info("cleaning up, need to remove "+data.slots().size()+" related mapItems for name: "+name);
					for(int i=0, j=0; i<data.slots().size(); ++i){
						while(j <= data.slots().get(i)){it.next(); ++j;}
//						Main.LOGGER.info("removing mapItem @ index "+data.slots().get(i)+"/"+mapItems.size());
						it.remove();
					}
				}
			}
		}
		return cmdMapNames.keySet();
	}

	public CommandExportMapImg(){
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, _0) -> {
			dispatcher.register(
				ClientCommandManager.literal(getClass().getSimpleName().substring(7).toLowerCase()/*"mapwallimg"*/)
				.executes(this::runCommandNoArg)
				.then(
					ClientCommandManager.literal("all_walls")
					.executes(this::runCommandForAllWalls)
				)
				.then(
					ClientCommandManager.literal("all_maps")
					.executes(this::runCommandForAllMaps)
				)
				.then(
					ClientCommandManager.literal("in_inv")
					.executes(this::runCommandInInventory)
				)
				.then(
					ClientCommandManager.literal("by_name")
					.then(
						ClientCommandManager.argument("map_name", StringArgumentType.greedyString())
						.suggests((ctx, builder) -> {
							final int i = ctx.getInput().lastIndexOf(' ');
							final String lastArg = i == -1 ? "" : ctx.getInput().substring(i+1);
							getNearbyMapNames(ctx.getSource().getPlayer()).stream().filter(name -> name.startsWith(lastArg)).forEach(builder::suggest);
							return builder.buildFuture();
						})
						.executes(this::runCommandForMapName)
					)
				)
				.then(
					ClientCommandManager.argument("pos1", ClientBlockPosArgumentType.blockPos())
					.then(
						ClientCommandManager.argument("pos2", ClientBlockPosArgumentType.blockPos())
						.executes(this::runCommandForPos1AndPos2)
					)
				)
			);
		});
	}
}