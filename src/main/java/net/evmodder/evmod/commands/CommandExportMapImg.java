package net.evmodder.evmod.commands;

import static net.evmodder.evmod.compat.MinecraftCompat.nonEmptyItemViews;
import static net.evmodder.evmod.compat.MinecraftCompat.bundleItems;
import static net.evmodder.evmod.compat.MinecraftCompat.itemStack;
import static net.evmodder.evmod.compat.MinecraftCompat.openFile;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.imageio.ImageIO;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import fi.dy.masa.malilib.util.StringUtils;
import net.evmodder.EvLib.util.FileIO;
import net.evmodder.EvLib.util.Pair;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.Main;
import net.evmodder.evmod.apis.InvUtils;
import net.evmodder.evmod.apis.MapRelationUtils;
import net.evmodder.evmod.apis.MapRelationUtils.RelatedMapsData;
import net.evmodder.evmod.onTick.UpdateItemFrameContents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
//? >=26.1 {
import net.minecraft.world.item.ItemInstance;
//?}
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class CommandExportMapImg{
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

	private final void drawBorder(final BufferedImage img){
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

	private final BufferedImage drawImgForMapStates(final FabricClientCommandSource source, final List<MapItemSavedData> states, final int width){
		final int height = (states.size()-1)/width + 1;
		final int border = Configs.Visuals.EXPORT_MAP_IMG_BORDER.getBooleanValue() ? 8 : 0;
		BufferedImage img = new BufferedImage(128*width + border*2, 128*height + border*2, BufferedImage.TYPE_INT_ARGB);
		if(border > 0) drawBorder(img);

		Iterator<MapItemSavedData> contents = states.iterator();
		for(int y=0; y<height; ++y) for(int x=0; x<width; ++x){
			final byte[] colors = contents.next().colors;
			final int xo = x*128+border, yo = y*128+border;
			for(int a=0; a<128; ++a) for(int b=0; b<128; ++b) img.setRGB(xo+a, yo+b, MapColor.getColorFromPackedId(colors[a + b*128]));
			if(!contents.hasNext()) return img;
		}
		assert false : "ExportMapImg: Width*Height < states.size()?!";
		return img;
	}

	private String lastRelPath = null;
	//? >=26.1 {
	private final int genImgForMapsInInv(final FabricClientCommandSource source, final List<? extends ItemInstance> inventory, final String name, final int width,
	//?} else {
	/*private final int genImgForMapsInInv(final FabricClientCommandSource source, final List<? extends ItemStack> inventory, final String name, final int width,*/
	//?}
			final boolean combine){
		final List<MapItemSavedData> unnestedMaps = inventory.stream().map(s -> MapItem.getSavedData(s.get(DataComponents.MAP_ID), source.getLevel())).filter(Objects::nonNull).toList();
		List<MapItemSavedData> allMaps = InvUtils.getAllNestedItemViews(inventory.stream())
				.map(s -> MapItem.getSavedData(s.get(DataComponents.MAP_ID), source.getLevel()))
				.filter(Objects::nonNull).toList();

		int numExports = 0;
		if(!unnestedMaps.isEmpty()){
			final int w = combine && allMaps.size() > unnestedMaps.size() ? (int)Math.ceil(Math.sqrt(allMaps.size())) : Math.min(width, unnestedMaps.size());
			final BufferedImage img = drawImgForMapStates(source, combine ? allMaps : unnestedMaps, w);

			if(!new File(FileIO.DIR+MAP_EXPORT_DIR).exists()) new File(FileIO.DIR+MAP_EXPORT_DIR).mkdirs();
			try{ImageIO.write(img, "png", new File(lastRelPath=(FileIO.DIR+MAP_EXPORT_DIR+name+".png")));}
			catch(IOException e){e.printStackTrace();}

			if(combine || allMaps.size() == unnestedMaps.size()) return 1;
			//else: handle sub-maps (TODO)
		}
		for(int i=0; i<inventory.size(); ++i){
			final var stack = inventory.get(i);
			final ItemContainerContents container = stack.get(DataComponents.CONTAINER);
			final BundleContents contents = stack.get(DataComponents.BUNDLE_CONTENTS);
			if(container == null && contents == null) continue;
			final Component nameText = stack.get(DataComponents.CUSTOM_NAME);
			final String containerName = nameText != null ? nameText.getString() : name+"-slot"+i+":"+itemStack(stack).getItemName().getString();
			if(container != null){
				var subItems = nonEmptyItemViews(container).toList();
				boolean subCombine = subItems.stream().noneMatch(s -> MapItem.getSavedData(s.get(DataComponents.MAP_ID), source.getLevel()) != null); // TODO: ?
				int w = subCombine ? (int)Math.ceil(Math.sqrt(subItems.size())) : 9;
				numExports += genImgForMapsInInv(source, subItems, containerName, w, subCombine);
			}
			else/*if(contents != null) already implied*/{
				var subItems = bundleItems(contents).toList();
				int w = (int)Math.ceil(Math.sqrt(subItems.size())); // Should max out at 8
				numExports += genImgForMapsInInv(source, subItems, containerName, w, /*combine=*/true); // Combine nested bundles
			}
		}
		return numExports;
	}

	private int atomic = 0;
	private int overwritten;
	private final void buildMapImgFile(final FabricClientCommandSource source, final Map<Vec3i, ItemFrame> ifeLookup,
			final ArrayList<Vec3i> mapWall, final int w, final int h, final String namePrefix){
		final boolean BLOCK_BORDER = Configs.Visuals.EXPORT_MAP_IMG_BORDER.getBooleanValue();
		final int border = BLOCK_BORDER ? 8 : 0;
		BufferedImage img = new BufferedImage(128*w+border*2, 128*h+border*2, BufferedImage.TYPE_INT_ARGB);
		if(BLOCK_BORDER) drawBorder(img);
//		boolean nonRectangularWarningShown = false;
		for(int i=0; i<h; ++i) for(int j=0; j<w; ++j){
			ItemFrame ife = ifeLookup.get(mapWall.get(i*w+j));
			if(ife == null){
//				if(!nonRectangularWarningShown){
					source.sendError(Component.literal("Non-rectangular MapArt wall is not fully supported"));
//					nonRectangularWarningShown = true;
//				}
//				return;
				continue;
			}
			final MapItemSavedData state = MapItem.getSavedData(ife.getItem(), source.getLevel());
			if(state == null){
				source.sendError(Component.literal("state == null in buildMapImgFile()!"));
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
			for(int x=0; x<128; ++x) for(int y=0; y<128; ++y) img.setRGB(xo+x, yo+y, MapColor.getColorFromPackedId(colors[x + y*128]));
		}
		final int UPSCALE_TO = Configs.Visuals.EXPORT_MAP_IMG_UPSCALE.getIntegerValue();
		if(128*w < UPSCALE_TO || 128*h < UPSCALE_TO){
			int s = 2; while(128*w*s < UPSCALE_TO || 128*h*s < UPSCALE_TO) ++s;
			source.sendFeedback(Component.literal("Upscaling img: x"+s));
			BufferedImage upscaledImg = new BufferedImage(128*w*s+(BLOCK_BORDER?s*2:0), 128*h*s+(BLOCK_BORDER?s*2:0), img.getType());
			Graphics2D g2d = upscaledImg.createGraphics();
			g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			g2d.drawImage(img, 0, 0, 128*w*s, 128*h*s, null);
			g2d.dispose();
			img = upscaledImg;
		}

		final String imgName;
		if(Configs.Visuals.EXPORT_MAP_IMG_ATOMIC_NAMING.getBooleanValue()){
			imgName = namePrefix + ++atomic;
		}
		else{
			final ItemStack tlMapItemStack = ifeLookup.get(mapWall.stream().filter(ifeLookup::containsKey).findFirst().get()).getItem();
			final Component nameText = tlMapItemStack.getCustomName();
			final String nameStr = nameText == null ? null : nameText.getString();
			String tempName;
			if(mapWall.size() == 1 || nameStr == null){
				tempName = nameStr == null ? tlMapItemStack.get(DataComponents.MAP_ID).key() : nameStr;
			}
			else{
				List<ItemStack> sampleStacks = List.of(
					tlMapItemStack,
					ifeLookup.get(mapWall.reversed().stream().filter(ifeLookup::containsKey).findFirst().get()).getItem()
				);
				RelatedMapsData data = MapRelationUtils.getRelatedMapsByName0(sampleStacks, source.getLevel());
				tempName = CommandExportMapNames.getCleanedName(nameStr, data);
			}
			imgName = namePrefix + tempName.trim().replaceAll("[.\\\\/<>:\"|?*$]", "_");
		}

		//16755200
		if(!new File(FileIO.DIR+MAP_EXPORT_DIR).exists()) new File(FileIO.DIR+MAP_EXPORT_DIR).mkdirs();
		final String relFilePath = FileIO.DIR+MAP_EXPORT_DIR+imgName+".png";
		final File imgFile = new File(relFilePath);
		if(imgFile.exists()) ++overwritten;
		try{ImageIO.write(img, "png", imgFile);}
		catch(IOException e){e.printStackTrace();}

		final Component text = Component.literal("Saved mapwall to ").withColor(16755200).append(
				Component.literal(relFilePath).withColor(43520).withStyle(ChatFormatting.UNDERLINE)
				.withStyle(style -> style.withClickEvent(openFile(imgFile.getAbsolutePath())))
		);
		source.sendFeedback(text);
	}

//	private boolean ongoingExport;
	private final int genImgForMapsInItemFrames(final FabricClientCommandSource source, final List<ItemFrame> ifes, final Pair<Integer, Integer> shape,
			final String namePrefix){
		Direction facing = ifes.getFirst().getNearestViewDirection();
		int minX = facing.getAxis() == Axis.X ? ifes.getFirst().getBlockX() : ifes.stream().mapToInt(ItemFrame::getBlockX).min().getAsInt();
		int maxX = facing.getAxis() == Axis.X ? ifes.getFirst().getBlockX() : ifes.stream().mapToInt(ItemFrame::getBlockX).max().getAsInt();
		int minY = facing.getAxis() == Axis.Y ? ifes.getFirst().getBlockY() : ifes.stream().mapToInt(ItemFrame::getBlockY).min().getAsInt();
		int maxY = facing.getAxis() == Axis.Y ? ifes.getFirst().getBlockY() : ifes.stream().mapToInt(ItemFrame::getBlockY).max().getAsInt();
		int minZ = facing.getAxis() == Axis.Z ? ifes.getFirst().getBlockZ() : ifes.stream().mapToInt(ItemFrame::getBlockZ).min().getAsInt();
		int maxZ = facing.getAxis() == Axis.Z ? ifes.getFirst().getBlockZ() : ifes.stream().mapToInt(ItemFrame::getBlockZ).max().getAsInt();

		Map<Vec3i, ItemFrame> ifeLookup = ifes.stream().collect(Collectors.toMap(ItemFrame::blockPosition, Function.identity()));
		ArrayList<Vec3i> mapWall = new ArrayList<>();
		final int WALL_SIZE = (1+maxX-minX)*(1+maxY-minY)*(1+maxZ-minZ);
		mapWall.ensureCapacity(WALL_SIZE);
		final int w;
		switch(facing){
			case UP: w=1+maxX-minX; for(int z=minZ; z<=maxZ; ++z) for(int x=minX; x<=maxX; ++x) mapWall.add(new Vec3i(x, minY, z)); break;
			case DOWN: w=1+maxX-minX; for(int z=maxZ; z>=minZ; --z) for(int x=minX; x<=maxX; ++x) mapWall.add(new Vec3i(x, minY, z)); break;
			case NORTH: w=1+maxX-minX; for(int y=maxY; y>=minY; --y) for(int x=maxX; x>=minX; --x) mapWall.add(new Vec3i(x, y, minZ)); break;
			case SOUTH: w=1+maxX-minX; for(int y=maxY; y>=minY; --y) for(int x=minX; x<=maxX; ++x) mapWall.add(new Vec3i(x, y, minZ)); break;
			case EAST: w=1+maxZ-minZ; for(int y=maxY; y>=minY; --y) for(int z=maxZ; z>=minZ; --z) mapWall.add(new Vec3i(minX, y, z)); break;
			case WEST: w=1+maxZ-minZ; for(int y=maxY; y>=minY; --y) for(int z=minZ; z<=maxZ; ++z) mapWall.add(new Vec3i(minX, y, z)); break;
			default: throw new RuntimeException("unreachable (invalid ife facing dir)");
		}
		assert mapWall.size() == WALL_SIZE && WALL_SIZE % w == 0;
		final int h = mapWall.size()/w;
		Main.LOGGER.info("ExportMapImg: Map wall size: "+w+"x"+h+" ("+mapWall.size()+")");
//		source.sendFeedback(Text.literal("Map wall size: "+w+"x"+h+" ("+mapWall.size()+")"));

		if(shape != null){
			if(w % shape.a != 0 || h % shape.b != 0){
				source.sendFeedback(Component.literal("Map wall ("+w+"x"+h+") is not divisible by "+shape.a+"x"+shape.b));
				return 0;
			}
			final int SUB_WALL_SIZE = shape.a*shape.b;
			assert WALL_SIZE % SUB_WALL_SIZE == 0;
			final ArrayList<Vec3i> subMapWall = new ArrayList<>(Collections.nCopies(SUB_WALL_SIZE, null));
			int numMapsSaved = 0;
			for(int a0=0; a0<w; a0+=shape.a) for(int b0=0; b0<h; b0+=shape.b){
				boolean anyIfe = false;
				for(int a=0; a<shape.a; ++a) for(int b=0; b<shape.b; ++b){
					final Vec3i ifePos = mapWall.get((a0+a) + (b0+b)*w);
					subMapWall.set(a + b*shape.a, ifePos);
					anyIfe |= ifeLookup.containsKey(ifePos);
				}
				if(anyIfe){
					++numMapsSaved;
					buildMapImgFile(source, ifeLookup, subMapWall, shape.a, shape.b, toString(shape));
				}
			}
			return numMapsSaved;
		}
		if(w*h > 400){
			source.sendFeedback(Component.literal("Large image detected, may take a moment..."));
//			if(ongoingExport) return false;
//			ongoingExport = true;
			new Thread(){@Override public void run(){buildMapImgFile(source, ifeLookup, mapWall, w, h, namePrefix);/* ongoingExport = false;*/}}.run();
		}
		else buildMapImgFile(source, ifeLookup, mapWall, w, h, namePrefix);
		return 1;
	}

	//private void getConnectedFramesRecur(final Map<XYZD, ?> ifeLookup, final XYZD xyzd, final HashSet<XYZD> connected){
	private final void getConnectedFramesRecur(final Map<Vec3i, ?> ifeLookup, final Axis axis, final Vec3i pos, final HashSet<Vec3i> connected){
		connected.add(pos);
		for(Direction dir : Direction.values()){
			if(dir.getAxis() == axis) continue;
			Vec3i u = pos.relative(dir);
			//XYZD u = new XYZD(xyzd.xyz.offset(dir), xyzd.d);
			if(!connected.contains(u) && ifeLookup.containsKey(u)) getConnectedFramesRecur(ifeLookup, axis, u, connected);
		}
	}
	//private List<ItemFrameEntity> getConnectedFrames(Map<XYZD, ItemFrameEntity> ifeLookup, ItemFrameEntity ife){
	private final List<ItemFrame> getConnectedFrames(final Map<Vec3i, ItemFrame> ifeLookup, final ItemFrame ife){
		final HashSet<Vec3i> connected = new HashSet<>();
		getConnectedFramesRecur(ifeLookup, ife.getNearestViewDirection().getAxis(), ife.blockPosition(), connected);

		return connected.stream().map(ifeLookup::get).toList();
	}

	static final List<ItemFrame> getItemFramesWithMaps(final LocalPlayer player){
		return StreamSupport.stream(((ClientLevel)player.level()).entitiesForRendering().spliterator(), false)
				.filter(ItemFrame.class::isInstance).map(ItemFrame.class::cast)
				.filter(ife -> ife.getItem().getItem() == Items.FILLED_MAP).toList();
	}

	private final Pattern pNxM = Pattern.compile("(?:as_)?([1-9][0-9]*)[x*]([1-9][0-9]*)");
	private final Pair<Integer, Integer> getShapeArgOrNull(final CommandContext<FabricClientCommandSource> ctx){
		try{
			final Matcher m = pNxM.matcher(ctx.getArgument("as_NxM", String.class));
			if(m.find()) return new Pair<>(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
		}
		catch(IllegalArgumentException ex){}
		return null;
	}
	private final String toString(final Pair<Integer, Integer> shape){return "shape_"+shape.a+"x"+shape.b+"_";}

	private final int runCommandInInventory(final CommandContext<FabricClientCommandSource> ctx){
		// TODO: use getShapeArgOrNull()
		final int numSaved = genImgForMapsInInv(ctx.getSource(),
				ctx.getSource().getPlayer().getInventory().getNonEquipmentItems(),
				/*name=*/StringUtils.translate("container.inventory"), /*width=*/9, /*combine=*/false);
		if(numSaved == 1){
			final String absolutePath = new File(lastRelPath).getAbsolutePath();
			ctx.getSource().sendFeedback(Component.literal("Saved map shulk img to ").withColor(16755200).append(
					Component.literal(lastRelPath).withColor(43520).withStyle(ChatFormatting.UNDERLINE)
					.withStyle(style -> style.withClickEvent(openFile(absolutePath)))
			));
		}
		if(numSaved > 1){
			ctx.getSource().sendFeedback(Component.literal("Saved "+numSaved+" map shulk imgs to ").withColor(16755200).append(
					Component.literal(FileIO.DIR+MAP_EXPORT_DIR).withColor(43520).withStyle(ChatFormatting.UNDERLINE)
					.withStyle(style -> style.withClickEvent(openFile(new File(FileIO.DIR+MAP_EXPORT_DIR).getAbsolutePath())))
			));
		}
		return numSaved == 0 ? 1 : 0;
	}

	private final int runCommandNoArg(final CommandContext<FabricClientCommandSource> ctx){
		ItemFrame targetIFrame = null;
		double bestUh = 0;
		final LocalPlayer player = ctx.getSource().getPlayer();
		final Vec3 vec3d = player.getViewVector(1.0F).normalize();
		final List<ItemFrame> iFrames = getItemFramesWithMaps(ctx.getSource().getPlayer());
		for(ItemFrame ife : iFrames){
			if(!player.hasLineOfSight(ife)) continue;
			Vec3 vec3d2 = new Vec3(ife.getX()-player.getX(), ife.getEyeY()-player.getEyeY(), ife.getZ()-player.getZ());
			final double d = vec3d2.length();
			vec3d2 = new Vec3(vec3d2.x / d, vec3d2.y / d, vec3d2.z / d); // normalize
			final double e = vec3d.dot(vec3d2);
			//e > 1.0d - 0.025d / d
			final double uh = (1.0d - 0.1d/d) - e;
			if(uh < bestUh){bestUh = uh; targetIFrame = ife;}
		}
		if(targetIFrame == null){
			// Try checking in inventory
			final int cmdFeedbackStatus = runCommandInInventory(ctx);
			if(cmdFeedbackStatus == 1) ctx.getSource().sendError(Component.literal("No mapwall (in front of cursor) detected"));
			else ctx.getSource().sendError(Component.literal("No mapwall (in front of cursor) detected, so exported maps from inventory"));
			return cmdFeedbackStatus;
		}
		// Fetch from iframe wall
//		HashMap<Vec3i, ItemFrameEntity> ifeLookup = new HashMap<>();
		final Direction facing = targetIFrame.getNearestViewDirection();
		final Axis axis = facing.getAxis();
		final int axisComponent = targetIFrame.blockPosition().get(axis);
		Map<Vec3i, ItemFrame> ifeLookup = iFrames.stream()
			.filter(ife -> ife.getNearestViewDirection() == facing && ife.blockPosition().get(axis) == axisComponent)
			.collect(Collectors.toMap(ItemFrame::blockPosition, Function.identity()));
//		Main.LOGGER.info("ExportMapImg: Same-direction iFrames: "+iFrames.size());
		List<ItemFrame> ifes = getConnectedFrames(ifeLookup, targetIFrame);
		Main.LOGGER.info("ExportMapImg: Connected iFrames: "+ifes.size());
		final int numSaved = genImgForMapsInItemFrames(ctx.getSource(), ifes, getShapeArgOrNull(ctx), /*"wall_"*/"");
		return numSaved > 0 ? 0 : 1;
	}

	private final record MapWall(Direction dir, int axis){}
	private BinaryOperator<ItemFrame> pickFirst = (o, _) -> o;
	private final int runCommandForAllWalls(final CommandContext<FabricClientCommandSource> ctx){
		final Pair<Integer, Integer> shape = getShapeArgOrNull(ctx);
		final Map<MapWall, List<ItemFrame>> mapWalls = getItemFramesWithMaps(ctx.getSource().getPlayer()).stream().collect(Collectors.groupingBy(
				ife -> new MapWall(ife.getNearestViewDirection(), ife.blockPosition().get(ife.getNearestViewDirection().getAxis())) // Group by MapWall
		));
		Main.LOGGER.info("CmdImgExport: runCommandWithAllMapWalls() num mapwalls: "+mapWalls.size());
		int numMapsSaved = 0;
		overwritten = 0;
		for(List<ItemFrame> mapWall : mapWalls.values()){
//			Main.LOGGER.info("CmdImgExport: mapWall size A: "+mapWall.size());
			final Map<Vec3i, ItemFrame> ifeLookup = mapWall.stream().collect(Collectors.toMap(
					ItemFrame::blockPosition, // Key
					Function.identity(), // Value
					pickFirst, // Merge function (for key collisions)
					HashMap::new // Map supplier
				));
//			Main.LOGGER.info("CmdImgExport: mapWall size B: "+ifeLookup.size());
			while(!ifeLookup.isEmpty()){
				List<ItemFrame> ifes = getConnectedFrames(ifeLookup, ifeLookup.values().iterator().next());
//				Main.LOGGER.info("CmdImgExport: size of connected mapWall section: "+ifes.size());
				final int subNumSaved = genImgForMapsInItemFrames(ctx.getSource(), ifes, shape, "wall_");
				if(subNumSaved < 1){
					Main.LOGGER.error("CmdImgExport: Encountered an error while exporting a "+ifes.size()+"-id mapwall");
					ctx.getSource().sendError(Component.literal("Encountered an error while exporting a "+ifes.size()+"-id mapwall"));
					return -1;
				}
				numMapsSaved += subNumSaved;
				ifes.stream().map(ItemFrame::blockPosition).forEach(ifeLookup::remove);
			}
		}
		numMapsSaved -= overwritten;
		if(numMapsSaved > 5) ctx.getSource().sendFeedback(Component.literal(numMapsSaved+(overwritten>0?" new":"")+" images saved"));
		if(overwritten > 0) ctx.getSource().sendFeedback(Component.literal(overwritten+" images overwritten"));
		return 1;
	}
	private final int runCommandForAllNames(final CommandContext<FabricClientCommandSource> ctx){
		final HashSet<String> seen = new HashSet<>();
		final Map<MapWall, List<ItemFrame>> mapWalls = getItemFramesWithMaps(ctx.getSource().getPlayer()).stream()
				.filter(ife -> ife.getItem().getCustomName() != null) // Only consider named maps
				.filter(ife -> seen.add(ife.getItem().getCustomName().getString())) // Remove duplicate names
				.collect(Collectors.groupingBy(
						ife -> new MapWall(ife.getNearestViewDirection(), ife.blockPosition().get(ife.getNearestViewDirection().getAxis())) // Group by MapWall
		));
		int numMapsSaved = 0;
		for(List<ItemFrame> mapWall : mapWalls.values()){
			final Map<Vec3i, ItemFrame> ifeLookup = mapWall.stream().collect(Collectors.toMap(
					ItemFrame::blockPosition, // Key
					Function.identity(), // Value
					pickFirst, // Merge function (for key collisions)
					HashMap::new // Map supplier
				));
			while(!ifeLookup.isEmpty()){
				List<ItemFrame> ifes = getConnectedFrames(ifeLookup, ifeLookup.values().iterator().next());
				IdentityHashMap<ItemStack, ItemFrame> stackToIfe = ifes.stream().collect(Collectors.toMap(
						ItemFrame::getItem, // Key: ItemStack (address)
						Function.identity(), // Equivalent to `ife -> ife`
						pickFirst, // Merge function for duplicates (will never be called for this case)
						IdentityHashMap::new // Map supplier
				));
				List<ItemStack> mapItems = new LinkedList<>(stackToIfe.keySet());
				while(!mapItems.isEmpty()){
					final String name = mapItems.getFirst().getHoverName().getString();
					final MapItemSavedData state = MapItem.getSavedData(mapItems.getFirst(), ctx.getSource().getLevel());
					if(state == null) Main.LOGGER.error("ExportMapImg: State is null! in runCommandForAllMaps()");
					final Boolean locked = state == null ? null : state.locked;
					RelatedMapsData data = MapRelationUtils.getRelatedMapsByName(mapItems, name, 1, locked, ctx.getSource().getLevel());
					assert !data.slots().isEmpty();
					final boolean success;
					if(data.slots().size() <= 1){
						success = genImgForMapsInItemFrames(ctx.getSource(), List.of(stackToIfe.get(mapItems.getFirst())), null, "named_") == 1;
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
						success = genImgForMapsInItemFrames(ctx.getSource(), relatedStacks.stream().map(stackToIfe::get).toList(), null, "named_") == 1;
					}
					if(!success){
						ctx.getSource().sendError(Component.literal("Encountered an error while exporting map img: "+name));
						Main.LOGGER.error("CmdImgExport: Encountered error while exporting map img for name: "+name);
						return -1;
					}
					++numMapsSaved;
				}
				ifes.stream().map(ItemFrame::blockPosition).forEach(ifeLookup::remove);
			}
		}
		if(numMapsSaved > 5) ctx.getSource().sendFeedback(Component.literal(numMapsSaved+" images saved"));
		return 1;
	}
	private final int runCommandForMapName(final CommandContext<FabricClientCommandSource> ctx){
		final String mapName = ctx.getArgument("map_name", String.class);
		final String mapName0 = cmdMapNames.getOrDefault(mapName, mapName);
		Main.LOGGER.info("Using lookup name: "+mapName0);
//		assert !mapName.isBlank();
		IdentityHashMap<ItemStack, ItemFrame> stackToIfe = getItemFramesWithMaps(ctx.getSource().getPlayer()).stream().collect(Collectors.toMap(
				ItemFrame::getItem, // Key: ItemStack (address)
				Function.identity(), // Equivalent to `ife -> ife`
				pickFirst, // Merge function for duplicates (will never be called for this case)
				IdentityHashMap::new // Map supplier
		));
				//.collect(Collectors.toMap(ItemFrameEntity::getHeldItemStack, ife -> ife));
//		assert stackToIfe.size() == iFrames.size();
		ArrayList<ItemStack> slots = new ArrayList<>(stackToIfe.keySet());
		RelatedMapsData data = MapRelationUtils.getRelatedMapsByName(slots, mapName0, 1, /*locked=*/null, ctx.getSource().getLevel());
		Main.LOGGER.info("related maps found: "+data.slots().size());
		if(data.slots().isEmpty()){
			ctx.getSource().sendError(Component.literal("Unable to find map: "+mapName));
			return -1;
		}
//		if(data.prefixLen() != -1) Main.LOGGER.info("CmdImgExport: prefix/suffix len: "+data.prefixLen()+", "+data.suffixLen());

		final int numSaved = genImgForMapsInItemFrames(ctx.getSource(), data.slots().stream().map(i -> stackToIfe.get(slots.get(i))).toList(), null, "named_");
		if(numSaved != 1){
			ctx.getSource().sendError(Component.literal("Encountered an error while exporting map img"));
			Main.LOGGER.error("CmdImgExport: Encountered error while exporting map img for name: "+mapName);
			return -1;
		}

//		ctx.getSource().sendError(Text.literal("This version of the command is not yet implemented (try without a param)"));
		return 1;
	}
	private final int runCommandForPos1AndPos2(final CommandContext<FabricClientCommandSource> ctx){
		final Vec3i pos1 = ClientBlockPosArgumentType.getBlockPos(ctx, "pos1");
		final Vec3i pos2 = ctx.getArgument("pos2", BlockPos.class); // Equivalent to above
		if(pos1.getX() != pos2.getX() && pos1.getY() != pos2.getY() && pos1.getZ() != pos2.getZ()){
			ctx.getSource().sendError(Component.literal("iFrame selection area must be 2D (flat surface)"));
			return -1;
		}
		final AABB box = new AABB(new Vec3(pos1), new Vec3(pos2));
		final List<ItemFrame> iFrames = getItemFramesWithMaps(ctx.getSource().getPlayer());
		iFrames.removeIf(ife -> !box.contains(ife.position()));
		if(iFrames.isEmpty()){
			ctx.getSource().sendError(Component.literal("No iFrames found within the given selection"));
			return -1;
		}
		// Get mode (most common occuring facing direction)
		final Direction facing = iFrames.stream().map(ife -> ife.getNearestViewDirection()).collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
				.entrySet().stream().max(Map.Entry.comparingByValue()).get().getKey();
		iFrames.removeIf(ife -> ife.getNearestViewDirection() != facing);

		final int numSaved = genImgForMapsInItemFrames(ctx.getSource(), iFrames, getShapeArgOrNull(ctx), "area_");
		return numSaved > 0 ? 0 : 1;
	}

	private final boolean SHOW_ONLY_IF_HAS_AZ = true;
	private final HashMap<String, String> cmdMapNames = new HashMap<>();
	private long lastNameComputeTs;
	private final Set<String> getNearbyMapNames(final LocalPlayer player){
		if(!cmdMapNames.isEmpty() && lastNameComputeTs >= UpdateItemFrameContents.lastIFrameMapGroupUpdateTs) return cmdMapNames.keySet();
		lastNameComputeTs = System.currentTimeMillis();
		cmdMapNames.clear();

		final HashSet<String> seen = new HashSet<>();
		Stream<ItemFrame> ifeStream = getItemFramesWithMaps(player).stream()
				.filter(ife -> ife.getItem().getCustomName() != null); // Only consider named maps
		if(SHOW_ONLY_IF_HAS_AZ) ifeStream = ifeStream.filter(ife -> ife.getItem().getCustomName().getString().matches(".*[a-zA-Z].*"));
		ifeStream = ifeStream.filter(ife -> seen.add(ife.getItem().getCustomName().getString())); // Remove duplicate names
		final Map<MapWall, List<ItemFrame>> mapWalls = ifeStream.collect(Collectors.groupingBy(
				ife -> new MapWall(ife.getNearestViewDirection(), ife.blockPosition().get(ife.getNearestViewDirection().getAxis())) // Group by MapWall
		));
		for(List<ItemFrame> mapWall : mapWalls.values()){
			List<ItemStack> mapItems = mapWall.stream().map(ife -> ife.getItem()).collect(Collectors.toCollection(LinkedList::new));
			while(!mapItems.isEmpty()){
				final String name = mapItems.getFirst().getCustomName().getString();
				final MapItemSavedData state = MapItem.getSavedData(mapItems.getFirst(), player.level());
				final Boolean locked = state == null ? null : state.locked;
				RelatedMapsData data = MapRelationUtils.getRelatedMapsByName(mapItems, name, 1, locked, player.level());
				final String nameKey = CommandExportMapNames.getCleanedName(name, data);
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
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, _) -> {
			dispatcher.register(
				ClientCommands.literal(getClass().getSimpleName().substring(7).toLowerCase()/*"mapwallimg"*/)
				.executes(this::runCommandNoArg)
				.then(ClientCommands.argument("as_NxM", StringArgumentType.word()).executes(this::runCommandNoArg))
				.then(
					ClientCommands.literal("all_walls")
					.executes(this::runCommandForAllWalls)
					.then(ClientCommands.argument("as_NxM", StringArgumentType.word()).executes(this::runCommandForAllWalls))
				)
				.then(
					ClientCommands.literal("all_names")
					.executes(this::runCommandForAllNames)
				)
				.then(
					ClientCommands.literal("in_inv")
					.executes(this::runCommandInInventory)
					.then(ClientCommands.argument("as_NxM", StringArgumentType.word()).executes(this::runCommandInInventory))
				)
				.then(
					ClientCommands.literal("by_name")
					.then(
						ClientCommands.argument("map_name", StringArgumentType.greedyString())
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
					ClientCommands.argument("pos1", ClientBlockPosArgumentType.blockPos())
					.then(
						ClientCommands.argument("pos2", ClientBlockPosArgumentType.blockPos())
						.executes(this::runCommandForPos1AndPos2)
						.then(ClientCommands.argument("as_NxM", StringArgumentType.word()).executes(this::runCommandForPos1AndPos2))
					)
				)
			);
		});
	}
}