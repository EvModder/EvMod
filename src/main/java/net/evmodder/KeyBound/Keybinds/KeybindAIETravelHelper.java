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
	private final int unsafeDur = 20, kickDur = 5, unsafeY = 80, kickY = 50;
	private final long dontKickIfEnabledInLastMs = 10_000l;
	private long enabledTs;
	private final boolean waitForSafePitch = true;
	private final float safePitchUpper = -1, safePitchLower = -10, setSafePitch = -5;
	private float lastPitch;
	private double lastY;

	private final int getElytraDur(ItemStack stack){
		return stack.getItem() == Items.ELYTRA ? stack.getMaxDamage() - stack.getDamage() : -1;
	}

	public KeybindAIETravelHelper(){
		client = MinecraftClient.getInstance();
		KeyBindingHelper.registerKeyBinding(new EvKeybind("aie_travel_helper", ()->{
			String status = "AutomaticInfiniteElytra Travel Helper: "+((isEnabled=!isEnabled) ? "enabled" : "disabled");
			client.player.sendMessage(Text.of(status), true);
			enabledTs = System.currentTimeMillis();
			lastY = client.player.getY();
		}));
		assert kickY < unsafeY;
		assert kickDur < unsafeDur;
		assert kickDur >= 0 && unsafeDur < 432;
		assert kickY >= -64 && unsafeY < 320;
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
			final double y = client.player.getY();
			final int dur = chestStack.getMaxDamage() - chestStack.getDamage();
			final boolean tooLowDur = dur <= unsafeDur;
			final boolean tooLowY = y <= unsafeY;
			final float pitch = client.player.getPitch();

			if(!client.player.isGliding() && !client.player.isOnGround() && lastY < y){
				if(pitch > safePitchUpper || pitch < safePitchLower) client.player.setPitch(setSafePitch);
				client.world.disconnect();
				isEnabled = false;
			}

			if(tooLowY || tooLowDur){
				if(tooLowY) client.player.setPitch(setSafePitch);
				if(tooLowDur){
					for(int i=9; i<45; ++i) if(getElytraDur(client.player.getInventory().getStack(i%36)) > unsafeDur){
						if(i < 36){
							client.interactionManager.clickSlot(0, i, 1, SlotActionType.PICKUP, client.player); // Pickup fresh elytra
							client.interactionManager.clickSlot(0, 6, 0, SlotActionType.PICKUP, client.player); // Put in armor slot
							client.interactionManager.clickSlot(0, i, 0, SlotActionType.PICKUP, client.player); // Put back used elytra
						}
						else client.interactionManager.clickSlot(0, 6, i-36, SlotActionType.SWAP, client.player); // Swap with hotbar
						if(y > kickY){
							client.player.sendMessage(Text.of("Swapped to fresh elytra"), true);
							return;
						}
					}
					if(y > kickY && dur > kickDur && waitForSafePitch && (pitch < safePitchLower || pitch > safePitchUpper || pitch <= lastPitch)){
						client.player.sendMessage(Text.of("Current AIE helper settings will trigger disconnect due to too low dur"), true);
						return;
					}
				}
				if(y > kickY && dur > kickDur){
					final long countDownToKick = dontKickIfEnabledInLastMs - (System.currentTimeMillis() - enabledTs);
					if(countDownToKick > 0){
						client.player.sendMessage(Text.of("Current AIE helper settings will trigger disconnect due to too low "
								+(tooLowY?"Y":"dur")+" in: "+TextUtils.formatTime(countDownToKick)), true);
						return;
					}
				}
				client.world.disconnect();
				isEnabled = false;
			}
			lastPitch = client.player.getPitch();
			lastY = client.player.getY();
		});
	}
}