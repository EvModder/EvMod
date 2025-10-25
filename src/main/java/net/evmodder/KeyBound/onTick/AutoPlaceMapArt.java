package net.evmodder.KeyBound.onTick;

import java.util.ArrayList;
import java.util.Arrays;
import net.evmodder.KeyBound.apis.MapRelationUtils;
import net.evmodder.KeyBound.apis.MapRelationUtils.RelatedMapsData;
import net.evmodder.KeyBound.config.Configs.Generic;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class AutoPlaceMapArt{
	private static boolean isActive;
	private static ArrayList<ItemStack> slots = new ArrayList<>(Arrays.asList(null, null, null));
	private static RelatedMapsData data;

	public static final boolean canAutoplace(ItemFrameEntity lastIfe2, ItemFrameEntity lastIfe, ItemFrameEntity currIfe, ItemStack currStack){
		assert currIfe != null && currStack != null && currStack.getItem() == Items.FILLED_MAP; // All should be verified by caller
		if(!Generic.PLACEMENT_HELPER_MAPART_AUTOPLACE.getBooleanValue()) return isActive=false;
		if(lastIfe == null || lastIfe2 == null) return isActive=false;
		if(currStack.getCount() != 1) return isActive=false;
		slots.set(0, currStack);
		slots.set(1, lastIfe.getHeldItemStack());
		slots.set(2, lastIfe2.getHeldItemStack());

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

		//Step1: Check available maps in inventory and move 1st applicable result (within printing range) to mainhand
		//Step2: Face the target block face
		//Step3: Right click packet
	}
}