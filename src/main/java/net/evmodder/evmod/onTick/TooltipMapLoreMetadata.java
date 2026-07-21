package net.evmodder.evmod.onTick;

import java.util.HashMap;
import java.util.List;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.apis.MapColorUtils;
import net.evmodder.evmod.apis.Tooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.evmodder.evmod.apis.MapColorUtils.MapColorData;
import net.evmodder.evmod.apis.MapColorUtils.Palette;
import net.evmodder.evmod.apis.MapGroupUtils;

public final class TooltipMapLoreMetadata implements Tooltip{
	private final String paletteSymbol(MapColorUtils.Palette palette){
		return palette.name().toLowerCase().replace('_', '-');
//		return StringUtils.capitalize(palette.name().toLowerCase().replace('_', '-'));
//		return switch(palette){
//			case CARPET -> "carpet";
//			case EMPTY -> "void";
//			case FULLBLOCK -> "full";
//			case PISTON_CLEAR -> "piston";
//			default -> null;
//		};
	}

	private static final HashMap<ItemStack, List<Component>> tooltipCache = new HashMap<>();
	private static int lastHash;

	@Override public final void get(ItemStack item, TooltipContext context, TooltipFlag type, List<Component> lines){
		final int currHash = UpdateInventoryContents.getMapsInInvHash() + UpdateContainerContents.getMapsInContainerHash();
		if(lastHash != currHash){lastHash = currHash; tooltipCache.clear();}
		List<Component> cachedLines = tooltipCache.get(item);
		if(cachedLines != null){lines.clear(); lines.addAll(cachedLines); return;}

//		final ContainerComponent container = item.get(DataComponentTypes.CONTAINER);
//		if(container != null){} // TODO: aggregate map data for nested shulker/bundle

		if(item.getItem() != Items.FILLED_MAP) return;
		final MapId id = item.get(DataComponents.MAP_ID);
		if(id == null){tooltipCache.put(item, lines); return;}
		final MapItemSavedData state = context.mapData(id);
		if(state == null){tooltipCache.put(item, lines); return;}

		final MapColorData data = MapColorUtils.getColorData(state.colors);
		final Component staircased = Component.literal(
					data.height() == 0 ? "_" : data.height() == 1 ? "=" : data.height() == 2 ? "☰" : data.height()+"\uD83D\uDCF6"
				).withStyle(ChatFormatting.GREEN);

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

		if(showColorsId) lines.add(Component.literal(MapGroupUtils.getIdForMapState(state, /*evict=*/true).toString()).withStyle(ChatFormatting.WHITE));
		final int numNonTransparentPx = state.colors.length-data.numTransparent();
		if(showStaircased){
			lines.add(Component.translatable("advMode.type").withStyle(ChatFormatting.GRAY).append(": ").append(staircased));
			if(showStaircasedPercent && data.height() != 0 && data.numStaircase() < numNonTransparentPx){
				final String pxOrPercent = data.numStaircase() < 10 ? data.numStaircase()+"px" : Math.ceilDiv(data.numStaircase()*100, numNonTransparentPx)+"%";
				lines.add(lines.removeLast().copy().append(" ("+pxOrPercent+")"+(showMaterial?",":"")));
			}
		}
		if(showMaterial){
			if(showStaircased) lines.add(lines.removeLast().copy().append(" "+paletteSymbol(data.palette())));
			else lines.add(Component.translatable("advMode.type").withStyle(ChatFormatting.GRAY).append(": "+paletteSymbol(data.palette())));

			if(showCarpetPercent && data.numCarpet() != 0){
				final int percentCarpet = Math.ceilDiv(data.numCarpet()*100, numNonTransparentPx);
				if(percentCarpet < 100){
					final String pxOrPercent = data.numCarpet() < 10 ? data.numCarpet()+"px" : percentCarpet+"%";
					lines.add(lines.removeLast().copy().append(" ("+pxOrPercent+" carpet)"));
				}
				else if(data.numCarpet() < numNonTransparentPx){
					lines.add(lines.removeLast().copy().append(" (99%)"));
				}
			}
		}
//		if(showStaircased){// If material 1st then staircased, on same line
//			if(showMaterial) lines.add(lines.removeLast().copy().append(" ").append(staircased));
//			else lines.add(Text.translatable("advMode.type").formatted(Formatting.GRAY).append(": ").append(staircased));
//		}
//		if(showStaircased && showPercentStaircased && data.height()>0) lines.add(lines.removeLast().copy().append(" ("+data.percentStaircase()+"%)"));
		if(showNumColors){
			lines.add(Component.translatable("options.chat.color").withStyle(ChatFormatting.GRAY).append(": ")
					.append(Component.literal(""+data.uniqueColors()).withStyle(ChatFormatting.GREEN)));
			if(showNumColorIds && data.uniqueColors() > data.uniqueColorIds()){
				lines.add(lines.removeLast().copy().append(" (").append(Component.translatable("soundCategory.block")).append(": "+data.uniqueColorIds()+")"));
			}
		}
		if(showWaterColors && data.waterLevels() != 0){
			assert data.waterLevels() >= 1 && data.waterLevels() <= 3;
			// Idea: "_" vs "-" vs X for middle vs deep vs shallow?
			final String waterColorsUsed = data.waterLevels() == 1 ? "-" : data.waterLevels() == 2 ? "=" : "☰";
			lines.add(Component.translatable("block.minecraft.water").withStyle(ChatFormatting.BLUE).append(": "+waterColorsUsed));
			if(showWaterColorsPercent && data.numWet() < numNonTransparentPx){
				final String pxOrPercent = data.numWet() < 10 ? data.numWet()+"px" : Math.ceilDiv(data.numWet()*100, state.colors.length-data.numTransparent())+"%";
				lines.add(lines.removeLast().copy().append(Component.literal(" "+pxOrPercent).withStyle(ChatFormatting.GRAY)));
			}
		}
		if(showTransparent && data.numTransparent() != 0){
			lines.add(Component.literal("Transparent").withStyle(ChatFormatting.AQUA));
			if(showTransparentPercent && numNonTransparentPx != 0){
				final String pxOrPercent = data.numTransparent() < 10 ? data.numTransparent()+"px" : Math.floorDiv(data.numTransparent()*100, state.colors.length)+"%";
				lines.add(lines.removeLast().copy().append(Component.literal(": "+pxOrPercent).withStyle(ChatFormatting.GRAY)));
			}
			if(showVoidShadow && data.numSuppressed() != 0){
				lines.add(lines.removeLast().copy().append(Component.literal(" VS").withStyle(ChatFormatting.LIGHT_PURPLE)));
				if(showVoidShadowPercent && data.numSuppressed() < numNonTransparentPx){
					final String pxOrPercent = data.numSuppressed() < 10 ? data.numSuppressed()+"px" : Math.ceilDiv(data.numSuppressed()*100, numNonTransparentPx)+"%";
					lines.add(lines.removeLast().copy().append(Component.literal(": "+pxOrPercent).withStyle(ChatFormatting.GRAY)));
				}
			}
		}
		if(showNoobline && data.noobline()) lines.add(Component.literal("Noobline").withStyle(ChatFormatting.RED));
		tooltipCache.put(item, lines);
	}
}