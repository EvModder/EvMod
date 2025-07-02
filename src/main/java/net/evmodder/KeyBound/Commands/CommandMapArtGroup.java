package net.evmodder.KeyBound.Commands;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.evmodder.EvLib.FileIO;
import net.evmodder.KeyBound.MapGroupUtils;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

public class CommandMapArtGroup{
	//TODO: /mapartgroup <set/create/compare> <g1> [g2]
	private enum Command{SET, CREATE, APPEND, COMPARE, RESET};
	private final String FILE_PATH = "mapart_groups/";
	private final String CONFIRM = "confirm";
	private HashSet<UUID> activeGroup;
	private String activeGroupName;
	private int ERROR_COLOR = 16733525, CREATE_COLOR = 5635925, DONE_COLOR = 16755200;

	private int runCompareCommand(final FabricClientCommandSource source, final String[] groups, final String[] groups2){
		if(groups2 == null || groups2.length == 0){
			source.sendError(Text.literal("Specify a 2nd group to compare against").copy().withColor(ERROR_COLOR));
			return 1;
		}
		final byte[] data1 = FileIO.loadFileBytes(FILE_PATH+groups[0]);
		if(data1 == null){
			source.sendError(Text.literal("MapArtGroup not found: "+FileIO.DIR+FILE_PATH+groups[0]).copy().withColor(ERROR_COLOR));
			return 1;
		}
		final byte[] data2 = FileIO.loadFileBytes(FILE_PATH+groups2[0]);
		if(data2 == null){
			source.sendError(Text.literal("MapArtGroup not found: "+FileIO.DIR+FILE_PATH+groups2[0]).copy().withColor(ERROR_COLOR));
			return 1;
		}
		//TODO: implement compare functionality
		return 1;
	}
	private int runCommand(final FabricClientCommandSource source, final Command cmd, final String[] groups, final String[] groups2){
		assert groups.length > 0;
		if(cmd == Command.COMPARE) return runCompareCommand(source, groups, groups2);

		final byte[][] data = new byte[groups.length][];
		for(int i=0; i<groups.length; ++i) data[i] = FileIO.loadFileBytes(FILE_PATH+groups[i]);

		final String notFoundGroups = IntStream.range(0, groups.length).filter(i -> data[i] == null).mapToObj(i -> groups[i])
				.collect(Collectors.joining(","));
		if(!notFoundGroups.isEmpty() && cmd != Command.CREATE){
			source.sendError(Text.literal("MapArtGroup not found: "+FileIO.DIR+FILE_PATH+notFoundGroups).copy().withColor(ERROR_COLOR));
			return 1;
		}
		if(groups.length != 1 && (cmd == Command.CREATE || cmd == Command.APPEND)){
			source.sendError(Text.literal("Command requires a single MapArtGroup name (no commas)").copy().withColor(ERROR_COLOR));
			return 1;
		}
		if(data[0] != null && cmd == Command.CREATE && !CONFIRM.equalsIgnoreCase(groups[0])){
			source.sendError(Text.literal("MapArtGroup '"+groups[0]+"' already exists!").copy().withColor(ERROR_COLOR));
			source.sendFeedback(Text.literal("To overwrite it, add 'confirm' to the end of the command"));
			return 1;
		}
		if(groups2 != null && (cmd != Command.CREATE || !CONFIRM.equalsIgnoreCase(groups2[0]))){
			source.sendError(Text.literal("Too many arguments provided").copy().withColor(ERROR_COLOR));
			return 1;
		}
		HashSet<UUID> mapsInGroup = new HashSet<>();
		if(cmd != Command.CREATE){
			for(int i=0; i<data.length; ++i){
				final int numIdsInFile = data[i].length / 16;
				if(numIdsInFile*16 != data[i].length || numIdsInFile == 0){
					source.sendError(Text.literal("Corrupted/unrecognized map group file").copy().withColor(ERROR_COLOR));
					return 1;
				}
				final ByteBuffer bb = ByteBuffer.wrap(data[i]);
				for(int _0=0; _0<numIdsInFile; ++_0) mapsInGroup.add(new UUID(bb.getLong(), bb.getLong()));
			}
		}
		final String newActiveGroup = String.join(",", groups);
		if(cmd == Command.CREATE || cmd == Command.APPEND){
			final int oldSize = mapsInGroup.size();
			final HashSet<UUID> loadedMaps = MapGroupUtils.getLoadedMaps(source.getWorld());
			if(loadedMaps.isEmpty()){
				source.sendError(Text.literal("No maps found").copy().withColor(ERROR_COLOR));
				return 1;
			}
			for(UUID uuid : loadedMaps) mapsInGroup.add(uuid);
			if(mapsInGroup.size() == oldSize){
				source.sendError(Text.literal("No new maps found for group '"+newActiveGroup+"'").copy().withColor(DONE_COLOR));
				return 1;
			}
			for(UUID uuid : MapGroupUtils.getLoadedMaps(source.getWorld())) mapsInGroup.add(uuid);
			final ByteBuffer bb = ByteBuffer.allocate(mapsInGroup.size()*16);
			for(UUID uuid : mapsInGroup) bb.putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits());
			if(data[0] == null && FILE_PATH.endsWith("/") && !new File(FileIO.DIR+FILE_PATH).exists()) new File(FileIO.DIR+FILE_PATH).mkdir();
			FileIO.saveFileBytes(FILE_PATH+groups[0], bb.array());
			source.sendFeedback(Text.literal((data[0] == null ? "Created new" : "Expanded") + " group '"+groups[0]
					+"' and set as active (ids: "+ (data[0] == null ? "" : (data[0].length/16)+" -> ") + mapsInGroup.size()+").")
					.copy().withColor(CREATE_COLOR));
		}
		else if(newActiveGroup.equals(activeGroupName)){
			if(activeGroup.equals(mapsInGroup)){
				source.sendError(Text.literal("Group '"+activeGroupName+"' is already active (ids: "+activeGroup.size()+")").copy().withColor(DONE_COLOR));
				return 1;
			}
			else{
				source.sendFeedback(Text.literal("Updated group from file: '"+activeGroupName
						+"' (ids: "+activeGroup.size()+" -> "+mapsInGroup.size()+").").copy().withColor(DONE_COLOR));
			}
		}
		else{
			source.sendFeedback(Text.literal("Set active group: '"+newActiveGroup+"' (ids: "+mapsInGroup.size()+").").copy().withColor(DONE_COLOR));
		}
		MapGroupUtils.setCurrentGroup(activeGroup = mapsInGroup);
		activeGroupName = newActiveGroup;
		return 1;
	}

	private CompletableFuture<Suggestions> getGroupNameSuggestions(CommandContext<?> ctx, SuggestionsBuilder builder) {
		int i = ctx.getInput().lastIndexOf(' ');
		String lastArg = i == -1 ? "" : ctx.getInput().substring(i+1);
		i = ctx.getInput().lastIndexOf(',');
//		final String lastArgLastPart, lastArgFirstPart;
//		if(i == -1){lastArgLastPart = lastArg; lastArgFirstPart = "";}
//		else{lastArgLastPart = lastArg.substring(i+1); lastArgFirstPart = lastArg.substring(0, i+1);}
		final String lastArgLastPart = i == -1 ? lastArg : lastArg.substring(i+1);
		try{
//			Files.list(Paths.get(FileIO.DIR)).map(path -> path.getFileName().toString())
//			.filter(name -> name.startsWith(FILE_PATH) && name.startsWith(lastArgLastPart, FILE_PATH.length()))
//			.forEach(name -> builder.suggest(lastArgFirstPart+name.substring(FILE_PATH.length())));
			Files.list(Paths.get(FileIO.DIR+FILE_PATH)).map(path -> path.getFileName().toString())
			.filter(name -> name.startsWith(lastArgLastPart))
			.forEach(name -> builder.suggest(name));
		}
		catch(IOException e){e.printStackTrace(); return null;}
		// Lock the suggestions after we've modified them.
		return builder.buildFuture();//TODO: why not .build() ?
	}

	public CommandMapArtGroup(){
		ClientCommandRegistrationCallback.EVENT.register(
//				new ClientCommandRegistrationCallback(){
//				@Override public void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess registryAccess){
				(dispatcher, _0) -> {
			dispatcher.register(
				ClientCommandManager.literal(getClass().getSimpleName().substring(7).toLowerCase())
				.executes(ctx->{
					ctx.getSource().sendError(Text.literal("Missing subcommand: set/create/append <g>, or compare <g1> <g2>"));
					return 1;
				})
				.then(
					ClientCommandManager.argument("command", StringArgumentType.word())
					.suggests((ctx, builder) -> {
						for(Command cmd : Command.values()) builder.suggest(cmd.name().toLowerCase());
						return builder.buildFuture();
					})
					.executes(ctx->{
						final String cmd = ctx.getArgument("command", String.class);
						if(cmd.equalsIgnoreCase("reset")){MapGroupUtils.setCurrentGroup(activeGroup = null); activeGroupName = null;}
						else ctx.getSource().sendError(Text.literal("Command needs a group name").copy().withColor(ERROR_COLOR));
						return 1;
					})
					.then(
						ClientCommandManager.argument("group", StringArgumentType.word())
						.suggests(this::getGroupNameSuggestions)
						.executes(ctx->{
							final String cmdStr = ctx.getArgument("command", String.class);
							final String[] groups = ctx.getArgument("group", String.class).split(",");
							try{
								return runCommand(ctx.getSource(), Command.valueOf(cmdStr.toUpperCase()), groups, null);
							}
							catch(IllegalArgumentException ex){
								ctx.getSource().sendError(Text.literal("Invalid subcommand: "+cmdStr).copy().withColor(ERROR_COLOR));
								return 1;
							}
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
//									ctx.getSource().sendFeedback(Text.literal("Invalid subcommand: "+cmdStr).copy().withColor(ERROR_COLOR));
//									return 1;
//								}
//							})
//						)
						.then(
							ClientCommandManager.argument("group2", StringArgumentType.word())
							.suggests((ctx, builder) -> {
								final int i = ctx.getInput().indexOf(' '), j = ctx.getInput().lastIndexOf(' ');
								if(i != j && ctx.getInput().substring(i+1, j).equalsIgnoreCase(Command.COMPARE.name())){
									return getGroupNameSuggestions(ctx, builder);
								}
								else{
									builder.suggest(CONFIRM);
									return builder.buildFuture();
								}
							})
							.executes(ctx->{
								final String cmdStr = ctx.getArgument("command", String.class);
								final String[] groups = ctx.getArgument("group", String.class).split(",");
								final String[] groups2 = ctx.getArgument("group2", String.class).split(",");
								try{
									return runCommand(ctx.getSource(), Command.valueOf(cmdStr.toUpperCase()), groups, groups2);
								}
								catch(IllegalArgumentException ex){
									ctx.getSource().sendError(Text.literal("Invalid subcommand: "+cmdStr).copy().withColor(ERROR_COLOR));
									return 1;
								}
							})
						)
					)
				)
			);
		});
	}
}