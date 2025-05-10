package net.evmodder.KeyBound.Keybinds;

import net.evmodder.EvLib.TextUtils;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

public final class KeybindAIETravelHelper{
	private boolean isEnabled;
	private final MinecraftClient client;
	private final int unsafeDur = 20, unsafeY = 80, kickY = 50;
	private final long dontKickIfEnabledInLastMs = 10_000l;
	private long enabledTs;
	private final boolean waitForSafePitch = true;
	private final float safePitchUpper = -1, safePitchLower = -10, setSafePitch = -5;
	private float lastPitch;

	private final boolean isFreshElytra(ItemStack stack){
		return stack.getItem() == Items.ELYTRA && stack.getMaxDamage() - stack.getDamage() > unsafeDur+10;
	}

	public KeybindAIETravelHelper(){
		client = MinecraftClient.getInstance();
		KeyBindingHelper.registerKeyBinding(new EvKeybind("aie_travel_helper", ()->{
			String status = "AutomaticInfiniteElytra Travel Helper: "+((isEnabled=!isEnabled) ? "enabled" : "disabled");
			client.player.sendMessage(Text.of(status), true);
			enabledTs = System.currentTimeMillis();
		}));
		assert kickY < unsafeY;
		assert safePitchLower < setSafePitch && setSafePitch < safePitchUpper;

		ClientTickEvents.START_CLIENT_TICK.register(_ -> {
			if(!isEnabled) return;
			if(client.player == null || client.world == null){isEnabled = false; return;}

			ItemStack chestStack = client.player.getInventory().getArmorStack(2);
			//Identifier chestItemId = Registries.ITEM.getId(chestStack.getItem());
			if(chestStack.getItem() != Items.ELYTRA){
				client.player.sendMessage(Text.of("Not wearing elytra"), true);
				isEnabled = false;
				return;
			}
			final int durability = chestStack.getMaxDamage() - chestStack.getDamage();
			final boolean tooLowDur = durability <= unsafeDur;
			final boolean tooLowY = client.player.getBlockY() <= unsafeY;
			final float pitch = client.player.getPitch();

			if(tooLowY || tooLowDur){
				if(tooLowY){
					client.player.setPitch(setSafePitch);
					if(client.player.getBlockY() > kickY) return;
				}
				else{
					for(int i=9; i<45; ++i) if(isFreshElytra(client.player.getInventory().getStack(i%36))){
						if(i < 36){
							client.interactionManager.clickSlot(0, i, 1, SlotActionType.PICKUP, client.player); // Pickup fresh elytra
							client.interactionManager.clickSlot(0, 6, 0, SlotActionType.PICKUP, client.player); // Put in armor slot
							client.interactionManager.clickSlot(0, i, 0, SlotActionType.PICKUP, client.player); // Put back used elytra
						}
						else{
							client.interactionManager.clickSlot(0, 6, i-36, SlotActionType.SWAP, client.player); // Swap with hotbar
						}
						return;
					}
					if(waitForSafePitch && (pitch < safePitchLower || pitch > safePitchUpper || pitch <= lastPitch)){
						client.player.sendMessage(Text.of("Current AIE helper settings will trigger disconnect due to too low dur"), true);
						return;
					}
				}
				final long timeSinceEnabled = System.currentTimeMillis() - enabledTs;
				if(timeSinceEnabled <= dontKickIfEnabledInLastMs){
					client.player.sendMessage(Text.of("Current AIE helper settings will trigger disconnect due to too low "+(tooLowY?"Y":"dur")+" in: "
							+TextUtils.formatTime(dontKickIfEnabledInLastMs-timeSinceEnabled)), true);
					return;
				}
				client.world.disconnect();
				isEnabled = false;
			}
			lastPitch = client.player.getPitch();
		});
	}
}