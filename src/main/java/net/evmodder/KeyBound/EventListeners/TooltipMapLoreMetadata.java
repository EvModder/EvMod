package net.evmodder.KeyBound.EventListeners;

import java.util.List;
import net.minecraft.item.Item.TooltipContext;
import net.evmodder.KeyBound.MapColorUtils;
import net.evmodder.KeyBound.MapColorUtils.MapColorData;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapState;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class TooltipMapLoreMetadata{
	private final boolean showStaircased, showMaterial, showPercentCarpet, showNumColors, showTransparency, showNoobline;
	public TooltipMapLoreMetadata(boolean showStaircased, boolean showMaterial, boolean showPercentCarpet, boolean showNumColors,
			boolean showTransparency, boolean showNoobline)
	{
		this.showStaircased = showStaircased;
		this.showMaterial = showMaterial;
		this.showPercentCarpet = showPercentCarpet;
		this.showNumColors = showNumColors;
		this.showTransparency = showTransparency;
		this.showNoobline = showNoobline;
		ItemTooltipCallback.EVENT.register(this::tooltipMetadata);
	}

	private final String paletteSymbol(MapColorUtils.Palette palette){
		return palette.name().toLowerCase();
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

	public final void tooltipMetadata(ItemStack item, TooltipContext context, TooltipType type, List<Text> lines){
		final ContainerComponent container = item.get(DataComponentTypes.CONTAINER);
		if(container != null){} // TODO: aggregate map data for nested shulker/bundle

		if(item.getItem() != Items.FILLED_MAP) return;
		final MapIdComponent id = item.get(DataComponentTypes.MAP_ID);
		if(id == null) return;
		final MapState state = context.getMapState(id);
		if(state == null) return;

		final MapColorData data = MapColorUtils.getColorData(state.colors);
		final String layers = data.height() == 0 ? "–" : data.height() == 1 ? "=" : data.height() == 2 ? "☰" : data.height()+"\u1f4f6";

		if(showStaircased) lines.add(Text.literal("\n")
				.append(Text.translatable("advMode.type").formatted(Formatting.GRAY))
				.append(": ").append(Text.literal(layers).formatted(Formatting.GREEN)));
		if(showMaterial) lines.add(Text.literal("\n"+paletteSymbol(data.palette())).formatted(Formatting.GRAY));
		if(showMaterial && showPercentCarpet) lines.add(Text.literal(" ("+data.percentCarpet()+"%)"));
		if(showNumColors) lines.add(Text.literal("\n")
				.append(Text.translatable("options.chat.color").formatted(Formatting.GRAY))
				.append(": "+data.uniqueColors()));
		if(showTransparency && data.transparency()) lines.add(Text.literal("\nTransparency").formatted(Formatting.AQUA));
		if(showNoobline && data.noobline()) lines.add(Text.literal("\nNoobline").formatted(Formatting.RED));
	}
}