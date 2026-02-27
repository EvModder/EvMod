package net.evmodder.evmod.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

// A more generalized/simpler alternative to StringArgumentType.word()
// StringArgumentType.word() only allows [a-zA-Z0-9_-.+], whereas this allows ANY char which isn't the 'sep'

public class SepStringArgumentType implements ArgumentType<String>{
	private final char sep;

	private SepStringArgumentType(final char sep){this.sep = sep;}
	public static SepStringArgumentType word(final char sep){return new SepStringArgumentType(sep);}

	@Override public String parse(final StringReader reader) throws CommandSyntaxException{
		final int start = reader.getCursor();
		while(reader.canRead() && reader.peek() != sep) reader.skip();
		return reader.getString().substring(start, reader.getCursor());
	}
}