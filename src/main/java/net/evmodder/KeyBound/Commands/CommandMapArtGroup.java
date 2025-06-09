package net.evmodder.KeyBound.Commands;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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
	enum Command{SET, CREATE, APPEND, COMPARE, RESET};
	final String FILE_PATH = "mapart_group_";
	final String CONFIRM = "confirm";
	int ERROR_COLOR = 16733525, CREATE_COLOR = 5635925, DONE_COLOR = 16755200;

	private int runCompareCommand(final FabricClientCommandSource source, final String group, final String group2){
		if(group2 == null){
			source.sendFeedback(Text.literal("Specify a 2nd group to compare against").copy().withColor(ERROR_COLOR));
			return 1;
		}
		final byte[] data1 = FileIO.loadFileBytes(FILE_PATH+group);
		if(data1 == null){
			source.sendFeedback(Text.literal("MapArtGroup not found: "+FileIO.DIR+FILE_PATH+group).copy().withColor(ERROR_COLOR));
			return 1;
		}
		final byte[] data2 = FileIO.loadFileBytes(FILE_PATH+group2);
		if(data2 == null){
			source.sendFeedback(Text.literal("MapArtGroup not found: "+FileIO.DIR+FILE_PATH+group2).copy().withColor(ERROR_COLOR));
			return 1;
		}
		return 1;
	}
	private int runCommand(final FabricClientCommandSource source, final Command cmd, final String group, final String group2){
		if(cmd == Command.COMPARE) return runCompareCommand(source, group, group2);
		final byte[] data = FileIO.loadFileBytes(FILE_PATH+group);
		if(data == null && cmd != Command.CREATE){
			source.sendFeedback(Text.literal("MapArtGroup not found: "+FileIO.DIR+FILE_PATH+group).copy().withColor(ERROR_COLOR));
			return 1;
		}
		if(data != null && cmd == Command.CREATE && !CONFIRM.equalsIgnoreCase(group2)){
			source.sendFeedback(Text.literal("MapArtGroup '"+group+"' already exists!").copy().withColor(ERROR_COLOR));
			source.sendFeedback(Text.literal("To overwrite it, add 'confirm' to the end of the command"));
			return 1;
		}
		if(group2 != null && (cmd != Command.CREATE || !CONFIRM.equalsIgnoreCase(group2))){
			source.sendFeedback(Text.literal("Too many arguments provided").copy().withColor(ERROR_COLOR));
			return 1;
		}
		HashSet<UUID> mapsInGroup = new HashSet<>();
		if(data != null && cmd != Command.CREATE){
			final int numIdsInFile = data.length / 16;
			if(numIdsInFile*16 != data.length || numIdsInFile == 0){
				source.sendFeedback(Text.literal("Corrupted/unrecognized map group file").copy().withColor(ERROR_COLOR));
				return 1;
			}
			final ByteBuffer bb = ByteBuffer.wrap(data);
			for(int i=0; i<numIdsInFile; ++i) mapsInGroup.add(new UUID(bb.getLong(), bb.getLong()));
		}
		if(cmd == Command.CREATE || cmd == Command.APPEND){
			final int oldSize = mapsInGroup.size();
			final HashSet<UUID> loadedMaps = MapGroupUtils.getLoadedMaps(source.getWorld());
			if(loadedMaps.isEmpty()){
				source.sendFeedback(Text.literal("No maps found").copy().withColor(ERROR_COLOR));
				return 1;
			}
			for(UUID uuid : loadedMaps) mapsInGroup.add(uuid);
			if(mapsInGroup.size() == oldSize){
				source.sendFeedback(Text.literal("No new maps (that weren't already in the group)").copy().withColor(DONE_COLOR));
				return 1;
			}
			for(UUID uuid : MapGroupUtils.getLoadedMaps(source.getWorld())) mapsInGroup.add(uuid);
			final ByteBuffer bb = ByteBuffer.allocate(mapsInGroup.size()*16);
			for(UUID uuid : mapsInGroup) bb.putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits());
			FileIO.saveFileBytes(FILE_PATH+group, bb.array());
			source.sendFeedback(Text.literal((data == null ? "Created new" : "Expanded") + " group '"+group
					+"' and set as active (ids: "+ (data == null ? "" : (data.length/16)+" -> ") + mapsInGroup.size()+").")
					.copy().withColor(CREATE_COLOR));
		}
		else{
			source.sendFeedback(Text.literal("Set active group: '"+group+"' (ids: "+mapsInGroup.size()+").").copy().withColor(DONE_COLOR));
		}
		MapGroupUtils.setCurrentGroup(mapsInGroup);
		return 1;
	}

	private CompletableFuture<Suggestions> getGroupNameSuggestions(CommandContext<?> ctx, SuggestionsBuilder builder) {
		int i = ctx.getInput().lastIndexOf(' ');
		String lastArg = i == -1 ? "" : ctx.getInput().substring(i+1);
		try{
			Files.list(Paths.get(FileIO.DIR)).map(path -> path.getFileName().toString())
			.filter(name -> name.startsWith(FILE_PATH) && name.startsWith(lastArg, FILE_PATH.length()))
			.forEach(name -> builder.suggest(name.substring(FILE_PATH.length())));
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
				ClientCommandManager.literal("mapartgroup")
				.executes(ctx->{
					ctx.getSource().sendFeedback(Text.literal("Missing subcommand: set/create/append <g>, or compare <g1> <g2>"));
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
						if(cmd.equalsIgnoreCase("reset")) MapGroupUtils.setCurrentGroup(null);
						else ctx.getSource().sendFeedback(Text.literal("Command needs a group name").copy().withColor(ERROR_COLOR));
						return 1;
					})
					.then(
						ClientCommandManager.argument("group", StringArgumentType.word())
						.suggests(this::getGroupNameSuggestions)
						.executes(ctx->{
							final String cmdStr = ctx.getArgument("command", String.class);
							final String group = ctx.getArgument("group", String.class);
							try{
								return runCommand(ctx.getSource(), Command.valueOf(cmdStr.toUpperCase()), group, null);
							}
							catch(IllegalArgumentException ex){
								ctx.getSource().sendFeedback(Text.literal("Invalid subcommand: "+cmdStr).copy().withColor(ERROR_COLOR));
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
								final String group = ctx.getArgument("group", String.class);
								final String group2 = ctx.getArgument("group2", String.class);
								try{
									return runCommand(ctx.getSource(), Command.valueOf(cmdStr.toUpperCase()), group, group2);
								}
								catch(IllegalArgumentException ex){
									ctx.getSource().sendFeedback(Text.literal("Invalid subcommand: "+cmdStr).copy().withColor(ERROR_COLOR));
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