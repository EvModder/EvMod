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
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.evmodder.EvLib.FileIO;
import net.evmodder.KeyBound.MapGroupUtils;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.item.map.MapState;
import net.minecraft.text.Text;

public class CommandSetMapArtGroup{
	private final int MAX_MAPS_IN_INV_AND_ECHEST = 64*27*(36+27); // 108864

//	private int countMapIds(ItemStack stack, ClientWorld world){
//		if(stack.isEmpty()) return 0;
//		ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
//		if(container != null) return container.stream().map(s -> countMapIds(s, world)).mapToInt(Integer::intValue).sum();
//		return stack.getComponents().contains(DataComponentTypes.MAP_ID) ? 1 : 0;
//	}
//	private int getNumMapsInInvAndEchest(){
//		int minIdsToCheck = 0;
//		for(ItemStack stack : client.player.getEnderChestInventory().getHeldStacks()){
//			minIdsToCheck += countMapIds(stack, client.world);
//		}
//		for(int i=0; i<client.player.getInventory().size(); ++i){
//			minIdsToCheck += countMapIds(client.player.getInventory().getStack(i), client.world);
//		}
//		ctx.getSource().sendFeedback(Text.literal("Min Ids to check: "+minIdsToCheck));
//		return minIdsToCheck;
//	}

	private int setActiveGroupFromLoadedMaps(CommandContext<FabricClientCommandSource> ctx){
		if(MapGroupUtils.mapsInGroup != null) MapGroupUtils.mapsInGroup.clear();
		else MapGroupUtils.mapsInGroup = new HashSet<>();
		MinecraftClient client = ctx.getSource().getClient();
		int i=0;
		MapState mapState;
		while((mapState=client.world.getMapState(new MapIdComponent(i))) != null || i < MAX_MAPS_IN_INV_AND_ECHEST/*getNumMapsInInvAndEchest()*/){
			if(mapState != null) MapGroupUtils.mapsInGroup.add(MapGroupUtils.getIdForMapState(mapState));
			++i;
		}
		if(MapGroupUtils.mapsInGroup.isEmpty()){
			MapGroupUtils.mapsInGroup = null;
			return 0;
		}
		return MapGroupUtils.mapsInGroup.size();
	}
	private int runCommandNoArg(CommandContext<FabricClientCommandSource> ctx){
		final int numLoaded = setActiveGroupFromLoadedMaps(ctx);
		if(numLoaded == 0) ctx.getSource().sendFeedback(Text.literal("No maps found").copy().withColor(/*&c=*/16733525));
		else ctx.getSource().sendFeedback(Text.literal("Set the current active group (ids: "+MapGroupUtils.mapsInGroup.size()+").").copy().withColor(/*&a=*/5635925));
		return 1;
	}
	private int runCommandWithGroupName(CommandContext<FabricClientCommandSource> ctx){
		final String groupName = ctx.getArgument("group_name", String.class);
		final byte[] data = FileIO.loadFileBytes("mapart_group_"+groupName);
		if(data != null){
			final int numInGroup = data.length / 16;
			if(numInGroup*16 != data.length){
				ctx.getSource().sendFeedback(Text.literal("Corrupted/unrecognized map group file").copy().withColor(/*&c=*/16733525));
				return 1;
			}
			if(MapGroupUtils.mapsInGroup != null) MapGroupUtils.mapsInGroup.clear();
			else MapGroupUtils.mapsInGroup = new HashSet<>();
			final ByteBuffer bb = ByteBuffer.wrap(data);
			for(int i=0; i<numInGroup; ++i) MapGroupUtils.mapsInGroup.add(new UUID(bb.getLong(), bb.getLong()));
			ctx.getSource().sendFeedback(Text.literal("Loaded group '"+groupName+"' (ids: "+ MapGroupUtils.mapsInGroup.size()+").").copy().withColor(/*&6=*/16755200));
			return 1;
		}
		final int numLoaded = setActiveGroupFromLoadedMaps(ctx);
		if(numLoaded == 0){
			ctx.getSource().sendFeedback(Text.literal("No maps found").copy().withColor(/*&c=*/16733525));
			return 1;
		}

		final ByteBuffer bb = ByteBuffer.allocate(MapGroupUtils.mapsInGroup.size()*16);
		for(UUID uuid : MapGroupUtils.mapsInGroup){
			bb.putLong(uuid.getMostSignificantBits());
			bb.putLong(uuid.getLeastSignificantBits());
		}
		FileIO.saveFileBytes("mapart_group_"+groupName, bb.array());

		ctx.getSource().sendFeedback(Text.literal("Created new group '"+groupName+"' and set as active (ids: "
					+ MapGroupUtils.mapsInGroup.size()+").").copy().withColor(/*&a=*/5635925));
		return 1;
	}

	public CommandSetMapArtGroup(){
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, _) -> {
			dispatcher.register(ClientCommandManager.literal("setmapartgroup")
					.executes(this::runCommandNoArg)
					.then(ClientCommandManager.argument("group_name", StringArgumentType.word())
						.suggests(
							new SuggestionProvider<FabricClientCommandSource>(){
							@Override public CompletableFuture<Suggestions> getSuggestions(
							CommandContext<FabricClientCommandSource> ctx, SuggestionsBuilder builder) throws CommandSyntaxException
							{
								int i = ctx.getInput().lastIndexOf(' ');
								String lastArg = i == -1 ? "" : ctx.getInput().substring(i+1);
								try{
									Files.list(Paths.get(FileIO.DIR)).map(path -> path.getFileName().toString())
									.filter(name -> name.startsWith("mapart_group_") && name.startsWith(lastArg, 13))
									.forEach(name -> builder.suggest(name.substring(13)));
								}
								catch(IOException e){
									e.printStackTrace();
									return null;
								}

								// Lock the suggestions after we've modified them.
								return builder.buildFuture();//TODO: why not .build() ?
							}})
							.executes(this::runCommandWithGroupName)
//					.then(ClientCommandManager.argument("value_two", StringArgumentType.word()).executes(this::runCommandWithTwoArgs)
			));
		});
	}
}
