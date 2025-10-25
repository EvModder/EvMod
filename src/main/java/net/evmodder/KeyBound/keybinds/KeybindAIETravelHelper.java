package net.evmodder.KeyBound.keybinds;

import net.evmodder.EvLib.TextUtils;
import net.evmodder.KeyBound.Main;
import net.evmodder.KeyBound.config.Configs;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

public final class KeybindAIETravelHelper{
//	private boolean isEnabled;
	private int unsafeDur = 20, kickDur = 5, unsafeYinEnd = 60, kickYinEnd = 40; // TODO: put all these in config
	private final long SAFE_KICK_DELAY = 10_000l;
	private long enabledTs, stoppedFlyingTs;
	private final boolean waitForSafePitch = true, kickIfStopsFlying = true;
	private final float safePitchUpper = -1, safePitchLower = -10, setSafePitch = -5;
	private float lastPitch;
	private double lastY;
	private MinecraftClient client;

	private final int getElytraDur(ItemStack stack){
		return stack.getItem() == Items.ELYTRA ? stack.getMaxDamage() - stack.getDamage() : -1;
	}

	@SuppressWarnings("unused")
	private void registerClientTickListener(){
		ClientTickEvents.START_CLIENT_TICK.register(_0 -> {
			if(!Configs.Hotkeys.AIE_TRAVEL_HELPER.getBooleanValue()) return;
			if(client.player == null || client.world == null){Configs.Hotkeys.AIE_TRAVEL_HELPER.setBooleanValue(false); return;}

			ItemStack chestStack = client.player.getInventory().getArmorStack(2);
//			//Identifier chestItemId = Registries.ITEM.getId(chestStack.getItem()); // Not really needed thanks to 
//			if(chestStack.getItem() != Items.ELYTRA){
//				client.player.sendMessage(Text.literal("Not wearing elytra"), true);
//				isEnabled = false;
//				return;
//			}
			final double y = client.player.getY();
			final int dur = chestStack.getMaxDamage() - chestStack.getDamage();
			final boolean tooLowDur = dur <= unsafeDur;
			final boolean isInEnd = client.world.getRegistryKey() == World.END;
			final boolean atUnsafeY = isInEnd && y <= unsafeYinEnd;
			final boolean atKickY = isInEnd && y <= kickYinEnd;
			final float pitch = client.player.getPitch();

			final boolean goingDownInEnd = isInEnd && !client.player.isOnGround() && lastY < y;
			if(!client.player.isGliding() && (kickIfStopsFlying || goingDownInEnd)){
				if(pitch > safePitchUpper || pitch < safePitchLower) client.player.setPitch(setSafePitch);
				if(!goingDownInEnd){
					if(stoppedFlyingTs == 0) stoppedFlyingTs = System.currentTimeMillis();
					final long countDownToKick = SAFE_KICK_DELAY - (System.currentTimeMillis() - stoppedFlyingTs);
					if(countDownToKick > 0){
						client.player.sendMessage(Text.literal("AIE helper will disconnect due to not-flying in: " + TextUtils.formatTime(countDownToKick)), true);
						return;
					}
					stoppedFlyingTs = 0;
				}
				Main.LOGGER.warn("Disconnecting player: "+(goingDownInEnd?"FALLING!":"no longer flying")+", y="+y+", dur="+dur);
				client.world.disconnect();
//				Configs.Hotkeys.AIE_TRAVEL_HELPER.setBooleanValue(false); // Will be done automatically once client.world == null
			}

			if(atUnsafeY || tooLowDur){
				if(atUnsafeY) client.player.setPitch(setSafePitch);
				if(tooLowDur){
					for(int i=9; i<45; ++i) if(getElytraDur(client.player.getInventory().getStack(i%36)) > unsafeDur){
						if(i < 36){
							client.interactionManager.clickSlot(0, i, 1, SlotActionType.PICKUP, client.player); // Pickup fresh elytra
							client.interactionManager.clickSlot(0, 6, 0, SlotActionType.PICKUP, client.player); // Put in armor slot
							client.interactionManager.clickSlot(0, i, 0, SlotActionType.PICKUP, client.player); // Put back used elytra
						}
						else client.interactionManager.clickSlot(0, 6, i-36, SlotActionType.SWAP, client.player); // Swap with hotbar
						if(!atKickY){
							client.player.sendMessage(Text.literal("Swapped to fresh elytra"), true);
							return;
						}
					}
					if(!atKickY && dur > kickDur && waitForSafePitch && (pitch < safePitchLower || pitch > safePitchUpper || pitch <= lastPitch)){
						client.player.sendMessage(Text.literal("Current AIE helper settings will trigger disconnect due to too low dur"), true);
						return;
					}
				}
				if(!atKickY && dur > kickDur){
					final long countDownToKick = SAFE_KICK_DELAY - (System.currentTimeMillis() - enabledTs);
					if(countDownToKick > 0){
						client.player.sendMessage(Text.literal("Current AIE helper settings will trigger disconnect due to too low "
								+(atUnsafeY?"Y":"dur")+" in: "+TextUtils.formatTime(countDownToKick)), true);
						return;
					}
				}
				Main.LOGGER.warn("Disconnecting player: y="+y+", dur="+dur);
				client.world.disconnect();
//				Configs.Hotkeys.AIE_TRAVEL_HELPER.setBooleanValue(false); // Will be done automatically once client.world == null
			}
			lastPitch = client.player.getPitch();
			lastY = client.player.getY();
		});
	}

	public void toggle(){
//		Main.LOGGER.info("aie_travel_helper key pressed");
		if(client == null){
			Main.LOGGER.info("aie_travel_helper registered");
			client = MinecraftClient.getInstance();
			registerClientTickListener();
		}
		final boolean isEnabled = Configs.Hotkeys.AIE_TRAVEL_HELPER.getBooleanValue();
		if(!isEnabled){
			if(client.player == null || client.world == null) return;
			if(!client.player.isGliding()){client.player.sendMessage(Text.literal("You need to be flying first"), true); return;}
			enabledTs = System.currentTimeMillis();
			lastY = client.player.getY();
		}
		Configs.Hotkeys.AIE_TRAVEL_HELPER.setBooleanValue(!isEnabled);
		client.player.sendMessage(Text.literal("AutomaticInfiniteElytra Travel Helper: "+(isEnabled ? "disabled" : "enabled")), true);
	}

	//242-170, 251-183, 267-195
	//72, 68, 72

	public KeybindAIETravelHelper(){
		assert kickYinEnd < unsafeYinEnd;
		assert kickDur < unsafeDur;
		assert kickDur >= 0 && unsafeDur < 432;
		assert kickYinEnd >= -64 && unsafeYinEnd < 256;
		assert safePitchLower < setSafePitch && setSafePitch < safePitchUpper;

//		new Keybind("aie_travel_helper", this::toggle, null, GLFW.GLFW_KEY_SEMICOLON);
	}
}