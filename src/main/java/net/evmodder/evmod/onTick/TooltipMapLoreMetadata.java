package net.evmodder.evmod.onTick;

import java.util.HashMap;
import java.util.List;
import net.minecraft.item.Item.TooltipContext;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.apis.MapColorUtils;
import net.evmodder.evmod.apis.Tooltip;
import net.evmodder.evmod.apis.MapColorUtils.MapColorData;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapState;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class TooltipMapLoreMetadata implements Tooltip{
	private final String paletteSymbol(MapColorUtils.Palette palette){
		return //StringUtils.capitalize(
				palette.name().toLowerCase().replace('_', '-');
				//);
//		switch(palette){
//			case CARPET:
//				return "carpet";
//			case EMPTY:
//				return "void";
//			case FULLBLOCK:
//				return "full";
//			case PISTON_CLEAR:
//				return "piston";
//			default:
//				break;
//		}
	}

	private static final HashMap<ItemStack, List<Text>> tooltipCache = new HashMap<>();
	private static int lastHash;

	@Override public final void get(ItemStack item, TooltipContext context, TooltipType type, List<Text> lines){
		int currHash = UpdateInventoryHighlights.mapsInInvHash + UpdateContainerHighlights.mapsInContainerHash;
		if(lastHash != currHash){lastHash = currHash; tooltipCache.clear();}
		List<Text> cachedLines = tooltipCache.get(item);
		if(cachedLines != null){lines.clear(); lines.addAll(cachedLines); return;}

//		final ContainerComponent container = item.get(DataComponentTypes.CONTAINER);
//		if(container != null){} // TODO: aggregate map data for nested shulker/bundle

		if(item.getItem() != Items.FILLED_MAP) return;
		final MapIdComponent id = item.get(DataComponentTypes.MAP_ID);
		if(id == null){tooltipCache.put(item, lines); return;}
		final MapState state = context.getMapState(id);
		if(state == null){tooltipCache.put(item, lines); return;}

		final MapColorData data = MapColorUtils.getColorData(state.colors);
		final Text staircased = Text.literal(
					data.height() == 0 ? "_" : data.height() == 1 ? "=" : data.height() == 2 ? "☰" : data.height()+"\uD83D\uDCF6"
				).formatted(Formatting.GREEN);

		final boolean showStaircased = Configs.Visuals.MAP_METADATA_TOOLTIP_STAIRCASE.getBooleanValue();
		final boolean showMaterial = Configs.Visuals.MAP_METADATA_TOOLTIP_MATERIAL.getBooleanValue();
		final boolean showPercentStaircased = Configs.Visuals.MAP_METADATA_TOOLTIP_PERCENT_STAIRCASE.getBooleanValue();
		final boolean showPercentCarpet = Configs.Visuals.MAP_METADATA_TOOLTIP_PERCENT_CARPET.getBooleanValue();
		final boolean showNumColors = Configs.Visuals.MAP_METADATA_TOOLTIP_NUM_COLORS.getBooleanValue();
		final boolean showNumColorIds = Configs.Visuals.MAP_METADATA_TOOLTIP_NUM_COLOR_IDS.getBooleanValue();
		final boolean showTransparency = Configs.Visuals.MAP_METADATA_TOOLTIP_TRANSPARENCY.getBooleanValue();
		final boolean showNoobline = Configs.Visuals.MAP_METADATA_TOOLTIP_NOOBLINE.getBooleanValue();

		if(showStaircased){
			lines.add(Text.translatable("advMode.type").formatted(Formatting.GRAY).append(": ").append(staircased));
		}
		if(showStaircased && showPercentStaircased && data.height()>0) lines.add(
				lines.removeLast().copy().append(" ("+data.percentStaircase()+"%)"+(showMaterial?",":"")));
		if(showMaterial){
			if(showStaircased) lines.add(lines.removeLast().copy().append(" "+paletteSymbol(data.palette())));
			else lines.add(Text.translatable("advMode.type").formatted(Formatting.GRAY).append(": "+paletteSymbol(data.palette())));
		}
		if(showMaterial && showPercentCarpet && data.percentCarpet() < 100) lines.add(lines.removeLast().copy().append(" ("+data.percentCarpet()+"% carpet)"));
//		if(showStaircased){// If material 1st then staircased, on same line
//			if(showMaterial) lines.add(lines.removeLast().copy().append(" ").append(staircased));
//			else lines.add(Text.translatable("advMode.type").formatted(Formatting.GRAY).append(": ").append(staircased));
//		}
//		if(showStaircased && showPercentStaircased && data.height()>0) lines.add(lines.removeLast().copy().append(" ("+data.percentStaircase()+"%)"));
		if(showNumColors){
			lines.add(Text.translatable("options.chat.color").formatted(Formatting.GRAY).append(": ")
					.append(Text.literal(""+data.uniqueColors()).formatted(Formatting.GREEN)));
			if(showNumColorIds && data.uniqueColors() > data.uniqueColorIds()){
				lines.add(lines.removeLast().copy().append(" (").append(Text.translatable("soundCategory.block")).append(": "+data.uniqueColorIds()+")"));
			}
		}
		if(showTransparency && data.transparency()) lines.add(Text.literal("Transparency").formatted(Formatting.AQUA));
		if(showNoobline && data.noobline()) lines.add(Text.literal("Noobline").formatted(Formatting.RED));
		tooltipCache.put(item, lines);
	}
}