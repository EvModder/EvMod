package net.evmodder.evmod.commands;

import static net.evmodder.evmod.compat.MinecraftCompat.openFile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import com.mojang.brigadier.context.CommandContext;
import net.evmodder.EvLib.util.FileIO;
import net.evmodder.evmod.Main;
import net.evmodder.evmod.apis.MapRelationUtils;
import net.evmodder.evmod.apis.MapRelationUtils.RelatedMapsData;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class CommandExportMapNames{
	private static final HashSet<String> MAP_NAMES = new HashSet<>();
	private static final String MAP_EXPORT_DIR = "mapart_exports/";
	private static final boolean REMOVE_MAX_CNT = true, REMOVE_BRACKET_SYMBOLS = true;
	private static volatile boolean exportInProgress;

	private record MapNameData(ItemStack item, String rawName, String relationName, int posDimensions){
		private final boolean canBeRelatedTo(final MapNameData other){
			if(this == other) return true;
			// Without a shared first/last character, names can only be related if both are entirely position data.
			if(!relationName.isEmpty() && !other.relationName.isEmpty() && (relationName.charAt(0) == other.relationName.charAt(0)
					|| relationName.charAt(relationName.length()-1) == other.relationName.charAt(other.relationName.length()-1))) return true;
			return posDimensions != 0 && posDimensions == other.posDimensions;
		}
	}

	public static final void addMapName(final String name){MAP_NAMES.add(name);}
	public static final void clearMapNames(){MAP_NAMES.clear();}

	private static final boolean isReflectedChar(final char l, final char r){
		switch(l){
			case '[': return r == ']';
			case '(': return r == ')';
			case '{': return r == '}';
			case '<': return r == '>';
			case '-': return r == '-';
			default: return Character.isWhitespace(l) && Character.isWhitespace(r);
		}
	}
	static final String getCleanedName(final String itemName0, final RelatedMapsData data){
		return getCleanedName(itemName0, data, /*keepArtist=*/true);
	}
	private static final String getCleanedName(final String itemName0, final RelatedMapsData data, final boolean keepArtist){
		if(data.prefixLen() == -1) return (keepArtist ? itemName0 : MapRelationUtils.removeByArtist(itemName0)).trim();
		String nameWithoutArtist = MapRelationUtils.removeByArtist(itemName0);
		String prefixStr = nameWithoutArtist.substring(0, data.prefixLen());
		String suffixStr = nameWithoutArtist.substring(nameWithoutArtist.length() - data.suffixLen());
		if(keepArtist){
			if(itemName0.startsWith(nameWithoutArtist)) suffixStr += itemName0.substring(nameWithoutArtist.length());
			else if(itemName0.endsWith(nameWithoutArtist)) prefixStr = prefixStr + itemName0.substring(0, itemName0.length()-nameWithoutArtist.length());
			else Main.LOGGER.info("CmdExportImg: trouble re-attaching artist name (not at start or end)");
		}
		if(REMOVE_MAX_CNT){
			final String szCntStr = ""+data.slots().size();
			final int idx = suffixStr.indexOf(szCntStr);
			if(idx != -1 && Normalizer.normalize(suffixStr.substring(0, idx), Normalizer.Form.NFKD).toLowerCase().matches("\\s*(of|/)\\s*")){
				suffixStr = suffixStr.substring(idx + szCntStr.length());
			}
		}
		final String prefixStrTrimmed = prefixStr.stripTrailing();
		final String suffixStrTrimmed = suffixStr.stripLeading();
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

	private static final MapNameData createMapNameData(final String name){
		final ItemStack map = new ItemStack(Items.FILLED_MAP);
		map.set(DataComponents.CUSTOM_NAME, Component.literal(name));
		final String relationName = MapRelationUtils.removeByArtist(name);
		final String posStr = MapRelationUtils.simplifyPosStr(relationName);
		final int posDimensions = MapRelationUtils.isValidPosStr(posStr) ? (posStr.indexOf(' ') == -1 ? 1 : 2) : 0;
		return new MapNameData(map, name, relationName, posDimensions);
	}

	private static final void finishExport(final FabricClientCommandSource source, final Component message, final boolean error){
		Minecraft.getInstance().execute(()->{
			exportInProgress = false;
			if(error) source.sendError(message);
			else source.sendFeedback(message);
		});
	}
	private static final Component fileLink(final File file){
		return Component.literal(file.getName()).withColor(43520).withStyle(ChatFormatting.UNDERLINE)
				.withStyle(style -> style.withClickEvent(openFile(file.getAbsolutePath())));
	}

	private static final HashMap<String, String> getStrippedNames(final List<String> names){
		final List<MapNameData> mapItems = names.stream().map(CommandExportMapNames::createMapNameData)
				.collect(Collectors.toCollection(LinkedList::new));
		final HashMap<String, String> strippedNames = new HashMap<>(names.size());
		while(!mapItems.isEmpty()){
			final MapNameData sourceMap = mapItems.getFirst();
			final List<ItemStack> candidates = mapItems.stream().filter(sourceMap::canBeRelatedTo)
					.map(MapNameData::item).toList();
			final RelatedMapsData data = MapRelationUtils.getRelatedMapsByName(
					candidates, sourceMap.rawName(), 1, /*locked=*/null, /*world=*/null);
			if(data.slots().size() <= 1){
				strippedNames.put(sourceMap.rawName(), getCleanedName(sourceMap.rawName(), data, /*keepArtist=*/false));
				mapItems.removeFirst();
			}
			else{
				final HashSet<String> relatedNames = new HashSet<>(data.slots().size()*4/3+1);
				for(final int slot : data.slots()){
					final String relatedName = candidates.get(slot).getCustomName().getString();
					strippedNames.put(relatedName, getCleanedName(relatedName, data, /*keepArtist=*/false));
					relatedNames.add(relatedName);
				}
				mapItems.removeIf(map -> relatedNames.contains(map.rawName()));
			}
		}
		return strippedNames;
	}

	private static final void exportNames(final FabricClientCommandSource source, final List<String> names){
		names.sort(null);
		HashMap<String, String> strippedNames = null;
		try{strippedNames = getStrippedNames(names);}
		catch(RuntimeException ex){Main.LOGGER.error("CmdExportMapNames: Unable to strip map names", ex);}
		final StringBuilder tsv = new StringBuilder("raw_name\tstripped_name\n");
		for(final String name : names){
			tsv.append(name).append('\t');
			if(strippedNames != null) tsv.append(strippedNames.get(name));
			tsv.append('\n');
		}
		final File exportDir = new File(FileIO.DIR+MAP_EXPORT_DIR);
		exportDir.mkdirs();
		final File file = new File(exportDir, "map_names_"+LocalDateTime.now().format(
				DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))+".tsv");
		try{Files.writeString(file.toPath(), tsv, StandardCharsets.UTF_8);}
		catch(IOException ex){
			Main.LOGGER.error("CmdExportMapNames: Unable to export map names", ex);
			finishExport(source, Component.literal("Unable to export map names"), /*error=*/true);
			return;
		}
		final String suffix = strippedNames == null ? " (stripped names unavailable)" : "";
		finishExport(source, Component.literal("Exported "+names.size()+" raw map names to ").append(fileLink(file)).append(suffix),
				/*error=*/false);
	}

	private final int runCommand(final CommandContext<FabricClientCommandSource> ctx){
		if(exportInProgress){
			ctx.getSource().sendError(Component.literal("A map-name export is already in progress"));
			return -1;
		}
		final List<String> names = new ArrayList<>(MAP_NAMES);
		if(names.isEmpty()){
			ctx.getSource().sendError(Component.literal("No named maps have been seen this session"));
			return -1;
		}
		exportInProgress = true;
		ctx.getSource().sendFeedback(Component.literal("Exporting "+names.size()+" map names..."));
		final Thread thread = new Thread(()->{
			try{exportNames(ctx.getSource(), names);}
			catch(RuntimeException ex){
				Main.LOGGER.error("CmdExportMapNames: Unable to export map names", ex);
				finishExport(ctx.getSource(), Component.literal("Unable to export map names"), /*error=*/true);
			}
		}, "EvMod-export-map-names");
		thread.setDaemon(true);
		thread.start();
		return 1;
	}

	public CommandExportMapNames(){
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, _) ->
				dispatcher.register(ClientCommands.literal("exportmapnames").executes(this::runCommand)));
	}
}