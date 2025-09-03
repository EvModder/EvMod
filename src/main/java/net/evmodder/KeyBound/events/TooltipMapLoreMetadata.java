package net.evmodder.KeyBound.events;

import java.util.HashMap;
import java.util.List;
import net.minecraft.item.Item.TooltipContext;
import net.evmodder.KeyBound.MapColorUtils;
import net.evmodder.KeyBound.MapColorUtils.MapColorData;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapState;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class TooltipMapLoreMetadata{
	private final boolean showStaircased, showMaterial, showNumColors, showTransparency, showNoobline, showPercentCarpet, showPercentStaircased;
	public TooltipMapLoreMetadata(boolean showStaircased, boolean showMaterial, boolean showNumColors, boolean showTransparency, boolean showNoobline,
			boolean showPercentCarpet, boolean showPercentStaircased)
	{
		this.showStaircased = showStaircased;
		this.showMaterial = showMaterial;
		this.showNumColors = showNumColors;
		this.showTransparency = showTransparency;
		this.showNoobline = showNoobline;
		this.showPercentCarpet = showPercentCarpet;
		this.showPercentStaircased = showPercentStaircased;
		ItemTooltipCallback.EVENT.register(this::tooltipMetadata);
	}

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

	public final void tooltipMetadata(ItemStack item, TooltipContext context, TooltipType type, List<Text> lines){
		int currHash = InventoryHighlightUpdater.mapsInInvHash + ContainerHighlightUpdater.mapsInContainerHash;
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
		if(showNumColors) lines.add(Text.translatable("options.chat.color").formatted(Formatting.GRAY).append(": "+data.uniqueColors()));
		if(showTransparency && data.transparency()) lines.add(Text.literal("Transparency").formatted(Formatting.AQUA));
		if(showNoobline && data.noobline()) lines.add(Text.literal("Noobline").formatted(Formatting.RED));
		tooltipCache.put(item, lines);
	}
}