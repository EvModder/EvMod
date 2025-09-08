package net.evmodder.KeyBound.events;

import net.evmodder.KeyBound.MapRelationUtils;
import net.evmodder.KeyBound.MapRelationUtils.RelatedMapsData;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class MapAutoplacer{
	private static boolean isActive;
	private static ItemStack[] slots = new ItemStack[3];
	private static RelatedMapsData data;

	public static final boolean canAutoplace(ItemFrameEntity lastIfe2, ItemFrameEntity lastIfe, ItemFrameEntity currIfe, ItemStack currStack){
		assert currIfe != null && currStack != null && currStack.getItem() == Items.FILLED_MAP; // All should be verified by caller
		if(lastIfe == null || lastIfe2 == null) return isActive=false;
		if(currStack.getCount() != 1) return isActive=false;
		slots[0] = currStack;
		slots[1] = lastIfe.getHeldItemStack();
		slots[2] = lastIfe2.getHeldItemStack();

//		final MapState state = FilledMapItem.getMapState(currStack, currIfe.getWorld());
//		final String name = currStack.getCustomName().getString();
//		final Boolean locked = state == null ? null : state.locked;
//		data = MapRelationUtils.getRelatedMapsByName(slots, name, /*count=*/1, locked, currIfe.getWorld());
		data = MapRelationUtils.getRelatedMapsByName0(slots, currIfe.getWorld());
		if(data.slots().size() != 3) return isActive=false;

		// TODO: get XYZD of the 3 ifes, make sure they match posStr data of items (if not, return false)
		// and use for fitting in new items to an appropriate XYZD

		return isActive=true;
	}
	public static final void placeNearestMap(){
		if(!isActive) return;

		// Use
	}
}