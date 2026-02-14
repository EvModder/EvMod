package net.evmodder.evmod.onTick;

import java.util.HashMap;
import java.util.List;
import net.minecraft.item.Item.TooltipContext;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.apis.MapColorUtils;
import net.evmodder.evmod.apis.Tooltip;
import net.evmodder.evmod.apis.MapColorUtils.MapColorData;
import net.evmodder.evmod.apis.MapColorUtils.Palette;
import net.evmodder.evmod.apis.MapGroupUtils;
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
		int currHash = UpdateInventoryHighlights.getMapInInvHash() + UpdateContainerHighlights.mapsInContainerHash;
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

		final boolean showColorsId = Configs.Visuals.MAP_METADATA_TOOLTIP_UUID.getBooleanValue();
		final boolean showStaircased = Configs.Visuals.MAP_METADATA_TOOLTIP_STAIRCASE.getBooleanValue() && data.palette() != Palette.EMPTY;
		final boolean showStaircasedPercent = Configs.Visuals.MAP_METADATA_TOOLTIP_STAIRCASE_PERCENT.getBooleanValue();
		final boolean showMaterial = Configs.Visuals.MAP_METADATA_TOOLTIP_MATERIAL.getBooleanValue() && data.palette() != Palette.EMPTY;
		final boolean showCarpetPercent = Configs.Visuals.MAP_METADATA_TOOLTIP_CARPET_PERCENT.getBooleanValue();
		final boolean showNumColors = Configs.Visuals.MAP_METADATA_TOOLTIP_NUM_COLORS.getBooleanValue() && data.palette() != Palette.EMPTY;
		final boolean showNumColorIds = Configs.Visuals.MAP_METADATA_TOOLTIP_NUM_COLOR_IDS.getBooleanValue();
		final boolean showWaterColors = Configs.Visuals.MAP_METADATA_TOOLTIP_WATER_COLORS.getBooleanValue();
		final boolean showWaterColorsPercent = Configs.Visuals.MAP_METADATA_TOOLTIP_WATER_COLORS_PERCENT.getBooleanValue();
		final boolean showTransparent = Configs.Visuals.MAP_METADATA_TOOLTIP_TRANSPARENT.getBooleanValue();
		final boolean showTransparentPercent = Configs.Visuals.MAP_METADATA_TOOLTIP_TRANSPARENT_PERCENT.getBooleanValue();
		final boolean showVoidShadow = Configs.Visuals.MAP_METADATA_TOOLTIP_VOID_SHADOW.getBooleanValue();
		final boolean showVoidShadowPercent = showVoidShadow;
		final boolean showNoobline = Configs.Visuals.MAP_METADATA_TOOLTIP_NOOBLINE.getBooleanValue();

		if(showColorsId) lines.add(Text.literal(MapGroupUtils.getIdForMapState(state, /*evict=*/true).toString()).formatted(Formatting.WHITE));
		final int numNonTransparentPx = state.colors.length-data.numTransparent();
		if(showStaircased){
			lines.add(Text.translatable("advMode.type").formatted(Formatting.GRAY).append(": ").append(staircased));
			if(showStaircasedPercent && data.height() != 0 && data.numStaircase() < numNonTransparentPx){
				final String pxOrPercent = data.numStaircase() < 10 ? data.numStaircase()+"px" : Math.ceilDiv(data.numStaircase()*100, numNonTransparentPx)+"%";
				lines.add(lines.removeLast().copy().append(" ("+pxOrPercent+")"+(showMaterial?",":"")));
			}
		}
		if(showMaterial){
			if(showStaircased) lines.add(lines.removeLast().copy().append(" "+paletteSymbol(data.palette())));
			else lines.add(Text.translatable("advMode.type").formatted(Formatting.GRAY).append(": "+paletteSymbol(data.palette())));

			if(showCarpetPercent && data.numCarpet() != 0){
				final int percentCarpet = Math.ceilDiv(data.numCarpet()*100, numNonTransparentPx);
				if(percentCarpet < 100){
					final String pxOrPercent = data.numCarpet() < 10 ? data.numCarpet()+"px" : percentCarpet+"%";
					lines.add(lines.removeLast().copy().append(" ("+pxOrPercent+" carpet)"));
				}
			}
		}
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
		if(showWaterColors && data.waterLevels() != 0){
			assert data.waterLevels() >= 1 && data.waterLevels() <= 3;
			// Idea: "_" vs "-" vs X for middle vs deep vs shallow?
			final String waterColorsUsed = data.waterLevels() == 1 ? "-" : data.waterLevels() == 2 ? "=" : "☰";
			lines.add(Text.translatable("block.minecraft.water").formatted(Formatting.BLUE).append(": "+waterColorsUsed));
			if(showWaterColorsPercent && data.numWet() < numNonTransparentPx){
				final String pxOrPercent = data.numWet() < 10 ? data.numWet()+"px" : Math.ceilDiv(data.numWet()*100, state.colors.length-data.numTransparent())+"%";
				lines.add(lines.removeLast().copy().append(Text.literal(" "+pxOrPercent).formatted(Formatting.GRAY)));
			}
		}
		if(showTransparent && data.numTransparent() != 0){
			lines.add(Text.literal("Transparent").formatted(Formatting.AQUA));
			if(showTransparentPercent && numNonTransparentPx != 0){
				final String pxOrPercent = data.numTransparent() < 10 ? data.numTransparent()+"px" : Math.floorDiv(data.numTransparent()*100, state.colors.length)+"%";
				lines.add(lines.removeLast().copy().append(Text.literal(": "+pxOrPercent).formatted(Formatting.GRAY)));
			}
			if(showVoidShadow && data.numSuppressed() != 0){
				lines.add(lines.removeLast().copy().append(Text.literal(" VS").formatted(Formatting.LIGHT_PURPLE)));
				if(showVoidShadowPercent && data.numSuppressed() < numNonTransparentPx){
					final String pxOrPercent = data.numSuppressed() < 10 ? data.numSuppressed()+"px" : Math.ceilDiv(data.numSuppressed()*100, numNonTransparentPx)+"%";
					lines.add(lines.removeLast().copy().append(Text.literal(": "+pxOrPercent).formatted(Formatting.GRAY)));
				}
			}
		}
		if(showNoobline && data.noobline()) lines.add(Text.literal("Noobline").formatted(Formatting.RED));
		tooltipCache.put(item, lines);
	}
}