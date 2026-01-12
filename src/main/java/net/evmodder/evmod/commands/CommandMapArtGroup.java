package net.evmodder.evmod.commands;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.evmodder.EvLib.util.FileIO;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.Main;
import net.evmodder.evmod.apis.MapGroupUtils;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class CommandMapArtGroup{
	public static final String DIR = "mapart_groups/";
	private final String CONFIRM;
	private HashSet<UUID> activeGroup;
	private String activeGroupName;
	private final int ERROR_COLOR = 16733525/*&c*/, CREATE_COLOR = 5635925/*&a*/, DONE_COLOR = 16755200/*&6*/; // &2=43520
	private static final String PREFIX = Main.MOD_ID+".command.mapartgroup.";
	private enum Command{
		SET, CREATE, APPEND, COMPARE, RESET;

		final String translation;
		Command(){
			final String name = name().toLowerCase();
			translation = Text.translatableWithFallback(PREFIX+"subcommand."+name, name).getString();
		}
	};

	interface TextListener{
		public void sendFeedback(Text message);
		public void sendError(Text message);
	}
	TextListener asTextListener(FabricClientCommandSource fccs){
		return new TextListener(){
			@Override public void sendFeedback(Text message){fccs.sendFeedback(message);}
			@Override public void sendError(Text message){fccs.sendError(message);}
		};
	}

	public final HashSet<UUID> getGroupIdsOrSendError(final TextListener source, final String... groups){
		final byte[][] data = new byte[groups.length][];
		for(int i=0; i<groups.length; ++i) data[i] = FileIO.loadFileBytes(DIR+groups[i]);

		final String notFoundGroups = IntStream.range(0, groups.length).filter(i -> data[i] == null).mapToObj(i -> groups[i]).collect(Collectors.joining(","));
		if(!notFoundGroups.isEmpty()){
//			source.sendError(Text.literal("MapArtGroup file not found: "+FileIO.DIR+FILE_PATH+notFoundGroups).withColor(ERROR_COLOR));
			String plural = notFoundGroups.indexOf(',') !=- 1 ? "s" : "";
			source.sendError(Text.translatable(PREFIX+"notFound", plural, notFoundGroups).withColor(ERROR_COLOR));
//			source.sendError(Text.literal("MapArtGroup"+plural+" not found: "+notFoundGroups).withColor(ERROR_COLOR));
			return null;
		}
		final int[] numIds = new int[groups.length];
		int totalIds = 0;
		for(int i=0; i<groups.length; ++i) totalIds += (numIds[i] = data[i].length/16);
		final String corruptedGroups = IntStream.range(0, groups.length).filter(i -> numIds[i]==0 || numIds[i]*16 != data[i].length)
				.mapToObj(i -> groups[i]).collect(Collectors.joining(","));
		if(!corruptedGroups.isEmpty()){
			source.sendError(Text.translatable(PREFIX+"corrupted", corruptedGroups).withColor(ERROR_COLOR));
//			source.sendError(Text.literal("MapArtGroup file corrupted/unrecognized: "+corruptedGroups).withColor(ERROR_COLOR));
			return null;
		}
		HashSet<UUID> colorIds = new HashSet<>(totalIds);
		for(int i=0; i<groups.length; ++i){
			final ByteBuffer bb = ByteBuffer.wrap(data[i]);
			for(int j=0; j<numIds[i]; ++j) colorIds.add(new UUID(bb.getLong(), bb.getLong()));
		}
		return colorIds;
	}

	private int runCompareCommand(final TextListener source, final String[] group1, final String[] group2){
		if(group2 == null || group2.length == 0){
			source.sendError(Text.translatable(PREFIX+".compare.needsSecondGroup").withColor(ERROR_COLOR));
//			source.sendError(Text.literal("Specify a 2nd group to compare against").withColor(ERROR_COLOR));
			return 1;
		}
		HashSet<UUID> colorIds1 = getGroupIdsOrSendError(source, group1);
		if(colorIds1 == null) return 1;
		HashSet<UUID> colorIds2 = getGroupIdsOrSendError(source, group2);
		if(colorIds2 == null) return 1;

		List<UUID> in1Not2 = colorIds1.stream().filter(Predicate.not(colorIds2::contains)).toList();
		List<UUID> in2Not1 = colorIds2.stream().filter(Predicate.not(colorIds1::contains)).toList();

		String groupName1 = Arrays.stream(group1).collect(Collectors.joining(","));
		String groupName2 = Arrays.stream(group2).collect(Collectors.joining(","));

		if(in1Not2.isEmpty() && in2Not1.isEmpty()){
			source.sendError(Text.translatable(PREFIX+".compare.identical", groupName1, groupName2).withColor(DONE_COLOR));
//			source.sendFeedback(Text.literal("MapArtGroups "+groupName1+" and "+groupName2+" are identical").withColor(DONE_COLOR));
			return 1;
		}
		if(!in1Not2.isEmpty()){
			final ByteBuffer bb1 = ByteBuffer.allocate(in1Not2.size()*16);
			for(UUID uuid : in1Not2) bb1.putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits());
			final String in1Not2Name = "in_"+groupName1+"_NOT_IN_"+groupName2;//TODO: comment out this line unless arg for write-to-file
			FileIO.saveFileBytes(DIR+in1Not2Name, bb1.array());
			if(in2Not1.isEmpty()){
				colorIds1.removeIf(in1Not2::contains);
				MapGroupUtils.setCurrentGroup(activeGroup = colorIds1);
				activeGroupName = "in_"+groupName1+"_AND_IN_"+groupName2;
				source.sendError(Text.translatable(PREFIX+".compare.create",
						activeGroupName, colorIds1.size()).withColor(CREATE_COLOR));
//				source.sendFeedback(Text.literal("Created group '"+activeGroupName
//						+"' and set as active (ids: "+colorIds1.size()+")").withColor(CREATE_COLOR));
				return 1;
			}
		}
		if(!in2Not1.isEmpty()){
			final ByteBuffer bb2 = ByteBuffer.allocate(in2Not1.size()*16);
			for(UUID uuid : in2Not1) bb2.putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits());
			final String in2Not1Name = "in_"+groupName2+"_NOT_IN_"+groupName1;//TODO: comment out this line unless arg for write-to-file
			FileIO.saveFileBytes(DIR+in2Not1Name, bb2.array());
//			if(in1Not2.isEmpty()){
			int colorIds2OriginalSize = colorIds2.size();
				colorIds2.removeIf(in2Not1::contains);
//				MapGroupUtils.setCurrentGroup(activeGroup = new HashSet<>(in2Not1));
//				activeGroupName = in2Not1Name;
//				source.sendFeedback(Text.literal("Created group '"+in2Not1Name+"' and set as active (ids: "+in2Not1.size()+")").withColor(CREATE_COLOR));
				MapGroupUtils.setCurrentGroup(activeGroup = colorIds2);
				if(in1Not2.isEmpty()){
					activeGroupName = "in_"+groupName2+"_AND_IN_"+groupName1;
					source.sendError(Text.translatable(PREFIX+".compare.create",
							activeGroupName, colorIds2.size()).withColor(CREATE_COLOR));
//					source.sendFeedback(Text.literal("Created group '"+activeGroupName
//							+"' and set as active (ids: "+colorIds2.size()+")").withColor(CREATE_COLOR));
				}
				else{
					activeGroupName = "intersection_"+groupName1+"_and_"+groupName2; // Set intersection
					source.sendError(Text.translatable(PREFIX+".compare.intersection",
							colorIds1.size(), in1Not2.size(), colorIds2OriginalSize, in2Not1.size(), colorIds2.size())
							.withColor(CREATE_COLOR));
//					source.sendFeedback(Text.literal("Using set-intersection as active group "
//							+"(ids: ("+colorIds1.size()+"-"+in1Not2.size()+")+("+(colorIds2.size()+in2Not1.size())+"-"+in2Not1.size()+")="
//							+colorIds2.size()+")").withColor(CREATE_COLOR));
				}
				return 1;
//			}
		}
//		HashSet<UUID> merged = new HashSet<UUID>(in1Not2.size()+in2Not1.size());
//		merged.addAll(in1Not2);
//		merged.addAll(in2Not1);
//		assert merged.size() == in1Not2.size() + in2Not1.size();
//		MapGroupUtils.setCurrentGroup(activeGroup = merged);
//		activeGroupName = "sym_diff_"+groupName1+"_and_"+groupName2; // Symmetric Difference
//		source.sendFeedback(Text.literal("Using Symmetric-Difference as active group "
//				+ "(ids: "+in1Not2.size()+"+"+in2Not1.size()+"="+merged.size()+")").withColor(CREATE_COLOR));
		return 1;
	}
	/*private int runCompareCommand(final TextListener source, final String[] group1, final String[] group2){
		if(group2 == null || group2.length == 0){
			source.sendError(Text.literal("Specify a 2nd group to compare against").withColor(ERROR_COLOR));
			return 1;
		}
		HashSet<UUID> colorIds1 = getGroupIdsOrSendError(source, group1);
		if(colorIds1 == null) return 1;
		HashSet<UUID> colorIds2 = getGroupIdsOrSendError(source, group2);
		if(colorIds2 == null) return 1;

		List<UUID> in1Not2 = colorIds1.stream().filter(Predicate.not(colorIds2::contains)).toList();
		List<UUID> in2Not1 = colorIds2.stream().filter(Predicate.not(colorIds1::contains)).toList();

		String groupName1 = Arrays.stream(group1).collect(Collectors.joining(","));
		String groupName2 = Arrays.stream(group2).collect(Collectors.joining(","));

		if(in1Not2.isEmpty() && in2Not1.isEmpty()){
			source.sendFeedback(Text.literal("MapArtGroups "+groupName1+" and "+groupName2+" are identical").withColor(DONE_COLOR));
			return 1;
		}
		if(!in1Not2.isEmpty()){
			final ByteBuffer bb1 = ByteBuffer.allocate(in1Not2.size()*16);
			for(UUID uuid : in1Not2) bb1.putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits());
			final String in1Not2Name = "in_"+groupName1+"_NOT_IN_"+groupName2;
			FileIO.saveFileBytes(FILE_PATH+in1Not2Name, bb1.array());
			if(in2Not1.isEmpty()){
				MapGroupUtils.setCurrentGroup(activeGroup = new HashSet<>(in1Not2));
				activeGroupName = in1Not2Name;
				source.sendFeedback(Text.literal("Created group '"+in1Not2Name+"' and set as active (ids: "+in1Not2.size()+")").withColor(CREATE_COLOR));
				return 1;
			}
		}
		if(!in2Not1.isEmpty()){
			final ByteBuffer bb2 = ByteBuffer.allocate(in2Not1.size()*16);
			for(UUID uuid : in2Not1) bb2.putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits());
			final String in2Not1Name = "in_"+groupName2+"_NOT_IN_"+groupName1;
			FileIO.saveFileBytes(FILE_PATH+in2Not1Name, bb2.array());
			if(in1Not2.isEmpty()){
				MapGroupUtils.setCurrentGroup(activeGroup = new HashSet<>(in2Not1));
				activeGroupName = in2Not1Name;
				source.sendFeedback(Text.literal("Created group '"+in2Not1Name+"' and set as active (ids: "+in2Not1.size()+")").withColor(CREATE_COLOR));
				return 1;
			}
		}
		HashSet<UUID> merged = new HashSet<UUID>(in1Not2.size()+in2Not1.size());
		merged.addAll(in1Not2);
		merged.addAll(in2Not1);
		assert merged.size() == in1Not2.size() + in2Not1.size();
		MapGroupUtils.setCurrentGroup(activeGroup = merged);
		activeGroupName = "sym_diff_"+groupName1+"_and_"+groupName2; // Symmetric Difference
		source.sendFeedback(Text.literal("Using Symmetric-Difference as active group "
				+ "(ids: "+in1Not2.size()+"+"+in2Not1.size()+"="+merged.size()+")").withColor(CREATE_COLOR));
		return 1;
	}*/
	private int runCommand(final TextListener source, final Command cmd, final String[] groups, final String[] groups2){
		assert groups.length > 0;
		if(cmd == Command.COMPARE) return runCompareCommand(source, groups, groups2);

//		final byte[][] data = new byte[groups.length][];
//		for(int i=0; i<groups.length; ++i) data[i] = FileIO.loadFileBytes(FILE_PATH+groups[i]);

		HashSet<UUID> mapsInGroup = cmd == Command.CREATE ? new HashSet<>() : getGroupIdsOrSendError(source, groups);
		if(mapsInGroup == null) return 1;
		if(groups.length != 1 && (cmd == Command.CREATE || cmd == Command.APPEND)){
			source.sendError(Text.translatable(PREFIX+"create.needsName").withColor(CREATE_COLOR));
//			source.sendError(Text.literal("Command requires a single MapArtGroup name (no commas)").withColor(ERROR_COLOR));
			return 1;
		}
		if(cmd == Command.CREATE && new File(FileIO.DIR+DIR+groups[0]).exists() && (groups2 == null || !CONFIRM.equalsIgnoreCase(groups2[0]))){
			source.sendError(Text.translatable(PREFIX+"create.alreadyExists", groups[0]).withColor(ERROR_COLOR));
//			source.sendError(Text.literal("MapArtGroup '"+groups[0]+"' already exists!").withColor(ERROR_COLOR));
//			source.sendFeedback(Text.literal("To overwrite it, add 'confirm' to the end of the command"));
			return 1;
		}
		if(groups2 != null && (cmd != Command.CREATE || !CONFIRM.equalsIgnoreCase(groups2[0]))){
			source.sendError(Text.translatable(PREFIX+"create.tooManyArgs").withColor(ERROR_COLOR));
//			source.sendError(Text.literal("Too many arguments provided").withColor(ERROR_COLOR));
			return 1;
		}
		final String newActiveGroup = String.join(",", groups);
		if(cmd == Command.CREATE || cmd == Command.APPEND){
			final int oldSize = mapsInGroup.size();
			final HashSet<UUID> loadedMaps = MapGroupUtils.getLegitLoadedMaps(MinecraftClient.getInstance().player.clientWorld);
			if(loadedMaps.isEmpty()){
				source.sendError(Text.translatable(PREFIX+"create.noMapsFound").withColor(ERROR_COLOR));
//				source.sendError(Text.literal("No maps found").withColor(ERROR_COLOR));
				if(cmd == Command.CREATE) return 1;
			}
			else if(!mapsInGroup.addAll(loadedMaps)/*mapsInGroup.size() == oldSize*/){
				assert mapsInGroup.size() == oldSize;
				source.sendError(Text.translatable(PREFIX+"create.noNewMapsFound", newActiveGroup).withColor(ERROR_COLOR));
//				source.sendError(Text.literal("No new maps found for group '"+newActiveGroup+"'").withColor(DONE_COLOR));
				if(cmd == Command.CREATE) return 1;
			}
			else{
				assert mapsInGroup.size() > oldSize;
				final ByteBuffer bb = ByteBuffer.allocate(mapsInGroup.size()*16);
				for(UUID uuid : mapsInGroup) bb.putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits());
				File dir = new File(FileIO.DIR+DIR);
				if(!dir.exists()) dir.mkdir();
				FileIO.saveFileBytes(DIR+groups[0], bb.array());
				if(cmd == Command.CREATE) source.sendFeedback(Text.translatable(
						PREFIX+"create.newGroup",
						groups[0], mapsInGroup.size()).withColor(CREATE_COLOR));
				else source.sendFeedback(Text.translatable(
						PREFIX+"create.expanded" + (newActiveGroup.equals(activeGroupName) ? "Group" : "OtherGroup"),
						groups[0], oldSize, mapsInGroup.size()).withColor(CREATE_COLOR));
//				source.sendFeedback(Text.literal((cmd == Command.CREATE ? "Created group" : "Expanded") + " '"+groups[0]
//						+"' (ids: "+ (oldSize==0 ? "" : oldSize+"\u2192") + mapsInGroup.size()+")"
//						+(newActiveGroup.equals(activeGroupName) ? "" : " and set as active.")
//						).withColor(CREATE_COLOR));
			}
		}
		else if(newActiveGroup.equals(activeGroupName)){
			if(activeGroup.equals(mapsInGroup)){
				source.sendError(Text.translatable(PREFIX+"noUpdate", activeGroupName, activeGroup.size()).withColor(DONE_COLOR));
//				source.sendError(Text.literal("Active group: '"+activeGroupName+"' (ids: "+activeGroup.size()+")").withColor(DONE_COLOR));
				return 1;
			}
			else{
				source.sendError(Text.translatable(PREFIX+"fileUpdate", activeGroupName, activeGroup.size(), mapsInGroup.size()).withColor(DONE_COLOR));
//				source.sendFeedback(Text.literal("Updated group from file: '"+activeGroupName
//						+"' (ids: "+activeGroup.size()+"\u2192"+mapsInGroup.size()+").").withColor(DONE_COLOR));
			}
		}
		else{
			source.sendError(Text.translatable(PREFIX+"groupUpdate", newActiveGroup, mapsInGroup.size()).withColor(DONE_COLOR));
//			source.sendFeedback(Text.literal("Set active group: '"+newActiveGroup+"' (ids: "+mapsInGroup.size()+").").withColor(DONE_COLOR));
		}
		MapGroupUtils.setCurrentGroup(activeGroup = mapsInGroup);
		activeGroupName = newActiveGroup;
		return 1;
	}

	private CompletableFuture<Suggestions> getGroupNameSuggestions(CommandContext<?> ctx, SuggestionsBuilder builder) {
		String wipGroupArg = "";
		try{wipGroupArg = ctx.getArgument("group2", String.class);} catch(IllegalArgumentException e){}
		if(wipGroupArg.isEmpty()){
			try{wipGroupArg = ctx.getArgument("group", String.class);} catch(IllegalArgumentException e){}
		}
		if(ctx.getArgument("command", String.class).equalsIgnoreCase(Command.CREATE.translation) || !new File(FileIO.DIR+DIR).exists()){
			builder.suggest(wipGroupArg.isEmpty() ? "test" : wipGroupArg);
			return builder.buildFuture();
		}
		int i = wipGroupArg.lastIndexOf(',');
		final String lastArgLastPart = i == -1 ? wipGroupArg : wipGroupArg.substring(i+1);
		final String lastArgFirstPart = i == -1 ? "" : wipGroupArg.substring(0, i+1);
		final String lastArgWithCommasAround = ","+wipGroupArg+",";
		try{
			Files.list(Paths.get(FileIO.DIR+DIR))
			.filter(p -> {File f = p.toFile(); return f.isFile() && !f.isHidden();})
			.map(path -> path.getFileName().toString())
			.filter(name -> name.startsWith(lastArgLastPart))
			.filter(name -> !lastArgWithCommasAround.contains(","+name+","))
			.forEach(name -> builder.suggest(lastArgFirstPart+name));
		}
		catch(IOException e){e.printStackTrace(); return null;}
		return builder.buildFuture();
	}

	public CommandMapArtGroup(){
		CONFIRM = Text.translatableWithFallback(PREFIX+"create.confirm", "confirm").getString();
		final String defaultGroupName = Configs.Generic.MAPART_GROUP_DEFAULT.getStringValue();
		if(!defaultGroupName.isBlank()){
			final File defaultGroupFile = new File(FileIO.DIR+defaultGroupName);
			if(defaultGroupFile.exists()){
				runCommand(new TextListener(){
					@Override public void sendFeedback(Text message){Main.LOGGER.info(message.getString());}
					@Override public void sendError(Text message){Main.LOGGER.warn(message.getString());}
				}, Command.SET, new String[]{defaultGroupName}, null);
			}
		}
		ClientCommandRegistrationCallback.EVENT.register(
//				new ClientCommandRegistrationCallback(){
//				@Override public void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess registryAccess){
				(dispatcher, _0) -> {
			dispatcher.register(
				ClientCommandManager.literal(getClass().getSimpleName().substring(7).toLowerCase())
				.executes(ctx->{
					ctx.getSource().sendError(Text.translatable(PREFIX+".missingSubcommand"));
//					ctx.getSource().sendError(Text.literal("Missing subcommand: set/create/append <g>, or compare <g1> <g2>"));
					return 1;
				})
				.then(
					ClientCommandManager.argument("command", StringArgumentType.word())
					.suggests((ctx, builder) -> {
						for(Command cmd : Command.values()) builder.suggest(cmd.translation);
						return builder.buildFuture();
					})
					.executes(ctx->{
						final String cmd = ctx.getArgument("command", String.class);
						if(cmd.equalsIgnoreCase(Command.RESET.translation)){MapGroupUtils.setCurrentGroup(activeGroup = null); activeGroupName = null;}
						else ctx.getSource().sendError(Text.translatable(PREFIX+".needsGroup").withColor(ERROR_COLOR));
//						else ctx.getSource().sendError(Text.literal("Command needs a group name").withColor(ERROR_COLOR));
						return 1;
					})
					.then(
						ClientCommandManager.argument("group", SepStringArgumentType.word(' '))
						.suggests(this::getGroupNameSuggestions)
						.executes(ctx->{
							final String cmdStr = ctx.getArgument("command", String.class);
							final String[] groups = ctx.getArgument("group", String.class).split("[,+]");
							for(Command cmd : Command.values()) if(cmd.translation.equalsIgnoreCase(cmdStr)){
								return runCommand(asTextListener(ctx.getSource()), cmd, groups, /*groups2=*/null);
							}
							ctx.getSource().sendError(Text.translatable(PREFIX+".invalidSubcommand", cmdStr).withColor(ERROR_COLOR));
//							ctx.getSource().sendError(Text.literal("Invalid subcommand: "+cmdStr).withColor(ERROR_COLOR));
							return 1;
						})
//						.then(
//							ClientCommandManager.argument("confirm", BoolArgumentType.bool())
//							.suggests((ctx, builder) -> BoolArgumentType.bool().listSuggestions(ctx, builder))
//							.executes(ctx->{
//								final String cmdStr = ctx.getArgument("command", String.class);
//								final String group = ctx.getArgument("group", String.class);
//								final boolean confirm = ctx.getArgument("confirm", Boolean.class);
//								try{
//									return runCommand(ctx.getSource(), group, Command.valueOf(cmdStr.toUpperCase()), confirm);
//								}
//								catch(IllegalArgumentException ex){
//									ctx.getSource().sendFeedback(Text.literal("Invalid subcommand: "+cmdStr).withColor(ERROR_COLOR));
//									return 1;
//								}
//							})
//						)
						.then(
							ClientCommandManager.argument("group2", SepStringArgumentType.word(' '))
							.suggests((ctx, builder) -> {
								final String cmdStr = ctx.getArgument("command", String.class);
								if(cmdStr.equalsIgnoreCase(Command.COMPARE.translation)) return getGroupNameSuggestions(ctx, builder);
								else if(cmdStr.equalsIgnoreCase(Command.CREATE.translation)
										&& new File(FileIO.DIR+DIR+ctx.getArgument("group", String.class)).exists()) builder.suggest(CONFIRM);
								return builder.buildFuture();
							})
							.executes(ctx->{
								final String cmdStr = ctx.getArgument("command", String.class);
								final String[] groups = ctx.getArgument("group", String.class).split("[,+]");
								final String[] groups2 = ctx.getArgument("group2", String.class).split("[,+]");
								for(Command cmd : Command.values()) if(cmd.translation.equalsIgnoreCase(cmdStr)){
									return runCommand(asTextListener(ctx.getSource()), cmd, groups, groups2);
								}
								ctx.getSource().sendError(Text.translatable(PREFIX+".invalidSubcommand", cmdStr).withColor(ERROR_COLOR));
//								ctx.getSource().sendError(Text.literal("Invalid subcommand: "+cmdStr).withColor(ERROR_COLOR));
								return 1;
							})
						)
					)
				)
			);
		});
	}
}