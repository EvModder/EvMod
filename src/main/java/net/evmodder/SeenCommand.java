package net.evmodder;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
//import net.minecraft.client.multiplayer.PlayerInfo;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class SeenCommand{
	//TODO: update map entry every time a player leaves the server
	//TODO: clear map every time i leave the server
	//TODO: NO-OP if not connected to the server
	//TODO: 
	SeenCommand(){
		ClientCommandRegistrationCallback.EVENT.register(
			(dispatcher, registryAccess) -> dispatcher.register(
				ClientCommandManager.literal("seen").then(
					//ClientCommandManager.argument("name", EntityArgumentType.player())
					ClientCommandManager.argument("name", StringArgumentType.word())
					.executes(ctx->{
						//ctx.getArgument("name", EntitySelector.class).getPlayer(ctx.getSource());
						String name = ctx.getArgument("name", String.class);
						for(String onlinePlayerName : ctx.getSource().getPlayerNames()) if(onlinePlayerName.equals(name)){
							ctx.getSource().sendFeedback(Text.literal(onlinePlayerName+" is online").formatted(Formatting.GRAY));
							return Command.SINGLE_SUCCESS;
						}
//						ctx.getSource().getClient()
//						if(si == null) ctx.getSource().sendError(Text.literal("You need to be connected to a server"));
//						else if(!si.address.equals("2b2t.org")) ctx.getSource().sendError(Text.literal("Only 2b2t is supported currently"));
						//else if(ctx.getSource().getClient().getpl);
//						else{
//							ctx.getSource().getPlayerNames().contains(name);
//							ctx.getSource().sendFeedback(Text.literal("You entered: "+name));
//						}
						ctx.getSource().sendFeedback(Text.literal("You entered: "+name));
						return 1;
					})
				)
			)
		);
	}
	/*
	class EntityUUIDArgument implements ArgumentType<EntityUUIDArgument.Result> {
		private static final Collection<String> EXAMPLES = Arrays.asList("Player", "0123", "dd12be42-52a9-4a91-a8a1-11c01849e498", "@e");

		public static EntityUUIDArgument entityUuid(){return new EntityUUIDArgument();}

		public static UUID getEntityUuid(CommandContext<FabricClientCommandSource> context, String name) throws CommandSyntaxException {
			return context.getArgument(name, Result.class).getUUID(context.getSource());
		}

		@Override public Result parse(StringReader reader) throws CommandSyntaxException {
			if(reader.canRead() && reader.peek() == '@'){
				CEntitySelectorParser selectorParser = new CEntitySelectorParser(reader, true);
				CEntitySelector selector = selectorParser.parse();
				return new SelectorBacked(selector);
			}
			int start = reader.getCursor();
			while (reader.canRead() && reader.peek() != ' ') {
				reader.skip();
			}
			String argument = reader.getString().substring(start, reader.getCursor());
			try{
				UUID uuid = UUID.fromString(argument);
				return new UuidBacked(uuid);
			}
			catch (IllegalArgumentException ignore){}
			return new NameBacked(argument);
		}

		@Override public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder){
			if(context.getSource() instanceof SharedSuggestionProvider commandSource){
				StringReader stringReader = new StringReader(builder.getInput());
				stringReader.setCursor(builder.getStart());
				CEntitySelectorParser entitySelectorParser = new CEntitySelectorParser(stringReader, true);
				try{entitySelectorParser.parse();}
				catch(CommandSyntaxException ignored){}

				return entitySelectorParser.fillSuggestions(builder, builderx -> {
					Collection<String> collection = commandSource.getOnlinePlayerNames();
					Iterable<String> iterable = Iterables.concat(collection, commandSource.getSelectedEntities());
					SharedSuggestionProvider.suggest(iterable, builderx);
				});
			}
			return Suggestions.empty();
		}

		@Override public Collection<String> getExamples(){return EXAMPLES;}

		sealed interface Result{UUID getUUID(FabricClientCommandSource source) throws CommandSyntaxException;}

		private record NameBacked(String name) implements Result{
			@Override public UUID getUUID(FabricClientCommandSource source) throws CommandSyntaxException {
				PlayerInfo entry = source.getClient().getCon.getConnection().getPlayerInfo(name);
				if(entry == null) throw CEntityArgument.ENTITY_NOT_FOUND_EXCEPTION.create();
				return entry.getProfile().getId();
			}
		}

		private record UuidBacked(UUID uuid) implements Result {
			@Override public UUID getUUID(FabricClientCommandSource source){return uuid;}
		}

		private record SelectorBacked(CEntitySelector selector) implements Result {
			@Override public UUID getUUID(FabricClientCommandSource source) throws CommandSyntaxException{
				return selector.findSingleEntity(source).getUUID();
			}
		}
	}*/
}
