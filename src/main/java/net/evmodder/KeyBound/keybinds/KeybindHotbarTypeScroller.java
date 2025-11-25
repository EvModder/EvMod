package net.evmodder.KeyBound.keybinds;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;
import net.evmodder.KeyBound.Main;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Identifier;

public final class KeybindHotbarTypeScroller{
	//private final static String COLOR_SCROLL_CATEGORY = "key.categories."+Main.MOD_ID+".color_scroll";

	// e.g. [[tube, brain, bubble, fire, horn],...]
	private List<String[]> variantLists;
	// {*_wool"->[white,red,...], *_coral_fan->[tube,brain,...], rail->[rail,powered_rail,...], *_rail->[rail,powered_rail,...]}
	// replace * with variant_name
	private HashMap<String, String[]> scrollableItems;

	private void loadScrollableItems(String[] colors){
		if(colors.length < 2){
			Main.LOGGER.warn("Scroll list is too short: "+colors.toString());
			return; 
		}
		final boolean hasEmpty = colors[0].isEmpty();//TODO: icky
		final String colorA = hasEmpty ? colors[1] : colors[0];
		final String[] colorsB = Arrays.copyOfRange(colors, hasEmpty? 2 : 1, colors.length);

		Stream<Identifier> s = Registries.ITEM.getIds().stream().filter(id -> id.getPath().contains(colorA));
		if(hasEmpty) s = s.filter(id -> Registries.ITEM.containsId(Identifier.of(id.getNamespace(), id.getPath().replace(colorA+"_", ""))));//TODO: icky
		s = s.filter(id -> Arrays.stream(colorsB).allMatch(b -> Registries.ITEM.containsId(Identifier.of(id.getNamespace(), id.getPath().replace(colorA, b)))));
		List<Identifier> ids = s.toList();
		if(ids.isEmpty()){
			Main.LOGGER.warn("Could not find items for the given scroll list: ["+String.join(",", colors)+"]");
			return;
		}
		variantLists.add(colors);
		ids.stream().map(id -> id.getNamespace()+":"+id.getPath().replace(colorA, "*")).forEach(name -> {
			scrollableItems.put(name, colors);
			if(hasEmpty) scrollableItems.put(name.replace("*_", ""), colors);//TODO: icky
		});
	}

	public void scrollHotbarSlot(boolean upOrDown){
		final MinecraftClient client = MinecraftClient.getInstance();
		PlayerInventory inventory = client.player.getInventory();
		if(!PlayerInventory.isValidHotbarIndex(inventory.selectedSlot)) return;
		ItemStack is = inventory.getMainHandStack();
		Identifier id = Registries.ITEM.getId(is.getItem());
		if(!ItemStack.areItemsAndComponentsEqual(is, new ItemStack(Registries.ITEM.get(id)))) return;  // don't scroll if has custom NBT
		String path = id.getPath();
		String[] colors = scrollableItems.get(id.toString());//e.g., "rail" -> [,powered,detector,activator]"
		int i = 0;
		if(colors == null) for(String[] cs : variantLists){
			for(i=0; !path.contains(cs[i]+"_") && ++i < cs.length;);
			if(i != cs.length && scrollableItems.get(id.getNamespace()+":"+path.replace(cs[i], "*")) == cs){
				colors = cs; break;
			}
		}
		if(colors == null) return; // not a supported item (eg: "red_sandstone")

		final int original_i = i;
		i = upOrDown ? (i == colors.length-1 ? 0 : i+1) : (i == 0 ? colors.length-1 : i-1);
		id = Identifier.of(id.getNamespace(), id.getPath().replace(colors[original_i], colors[i]));
		if(client.player.isInCreativeMode()){
			inventory.setStack(inventory.selectedSlot, new ItemStack(Registries.ITEM.get(id), is.getCount()));//TODO: doesn't seem to work on servers (visually yes, but not when u place the block)
			//client.interactionManager.clickCreativeStack(new ItemStack(Registries.ITEM.get(id)), inventory.selectedSlot);//TODO: this doesn't work either :sob:
			//inventory.markDirty();
		}
		else{// survival mode
			do{
				int j = 0;
				for(; j<inventory.main.size(); ++j){
					ItemStack jis = inventory.main.get(j);
					if(jis.isEmpty()) continue;
					Identifier jid = Registries.ITEM.getId(jis.getItem());
					if(!jid.equals(id)) continue;
					if(!ItemStack.areItemsAndComponentsEqual(jis, new ItemStack(Registries.ITEM.get(jid)))) continue;
					//found an item to use
					break;
				}
				if(j != inventory.main.size()){
					//use the item (change selected hotbar slot or swap with main inv)
					if(PlayerInventory.isValidHotbarIndex(j)) inventory.selectedSlot = j;
					else{
						inventory.main.set(inventory.selectedSlot, inventory.main.get(j));
						inventory.main.set(j, is);
						client.interactionManager.clickSlot(/*client.player.playerScreenHandler.syncId*/0, j, inventory.selectedSlot, SlotActionType.SWAP, client.player);
					}
					//Main.LOGGER.error("did swap");
					break;
				}
				//else Main.LOGGER.error("no "+id.getPath()+", continuing for next item");
				final int new_i = upOrDown ? (i == colors.length-1 ? 0 : i+1) : (i == 0 ? colors.length-1 : i-1);
				id = Identifier.of(id.getNamespace(), id.getPath().replace(colors[i], colors[new_i]));
				i = new_i;
			}while(i != original_i);
			//Main.LOGGER.error("full wrap-around: "+i);
		}
	}

	public void refreshColorLists(List<String> colorLists){
		if(variantLists == null){
			variantLists = new ArrayList<>();
			scrollableItems = new HashMap<>();
		}
		else{
			variantLists.clear();
			scrollableItems.clear();
		}
		for(String colors : colorLists) loadScrollableItems(colors.replaceAll("\\s", "").split(","));

		Main.LOGGER.info("Defined scrollable variants: ["+String.join("], [", variantLists.stream().map(l -> String.join(",", l)).toList())+"]");
		Main.LOGGER.debug("Found matching items: "+String.join(", ", scrollableItems.keySet()));
	}
}