package net.evmodder.evmod.keybinds;

import net.evmodder.EvLib.util.TextUtils_New;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.Main;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public final class KeybindAIETravelHelper{
//	private boolean isEnabled;
	private int unsafeDur = 20, kickDur = 5, unsafeYinEnd = 60, kickYinEnd = 40; // TODO: put all these in config
	private final long SAFE_KICK_DELAY = 10_000l;
	private long enabledTs, stoppedFlyingTs;
	private final boolean waitForSafePitch = true, kickIfStopsFlying = true;
	private final float safePitchUpper = -1, safePitchLower = -10, setSafePitch = -5;
	private float lastPitch;
	private double lastY;
	private Minecraft client;

	private final int getElytraDur(ItemStack stack){
		return stack.getItem() == Items.ELYTRA ? stack.getMaxDamage() - stack.getDamageValue() : -1;
	}

	@SuppressWarnings("unused")
	private void registerClientTickListener(){
		ClientTickEvents.START_CLIENT_TICK.register(_0 -> {
			if(!Configs.Hotkeys.AIE_TRAVEL_HELPER.getBooleanValue()) return;
			if(client.player == null || client.level == null){
				Main.LOGGER.info("AIE Helper: disabled due to client disconnect");
				Configs.Hotkeys.AIE_TRAVEL_HELPER.setBooleanValue(false);
				return;
			}
			if(!client.player.isAlive() || client.player.onGround() || client.player.isSpectator()){
				Main.LOGGER.info("AIE Helper: disabled due to player dead/onGround/isSpectator");
				Configs.Hotkeys.AIE_TRAVEL_HELPER.setBooleanValue(false);
				return;
			}

			ItemStack chestStack = client.player.getItemBySlot(EquipmentSlot.CHEST);
//			//Identifier chestItemId = Registries.ITEM.getId(chestStack.getItem()); // Not really needed thanks to 
//			if(chestStack.getItem() != Items.ELYTRA){
//				client.player.sendMessage(Text.literal("Not wearing elytra"), true);
//				isEnabled = false;
//				return;
//			}
			final double y = client.player.getY();
			final int dur = chestStack.getMaxDamage() - chestStack.getDamageValue();
			final boolean tooLowDur = dur <= unsafeDur;
			final boolean isInEnd = client.level.dimension() == Level.END;
			final boolean atUnsafeY = isInEnd && y <= unsafeYinEnd;
			final boolean atKickY = isInEnd && y <= kickYinEnd;
			final float pitch = client.player.getXRot();

			final boolean goingDownInEnd = isInEnd && !client.player.onGround() && lastY < y;
			if(!client.player.isFallFlying() && (kickIfStopsFlying || goingDownInEnd)){
				if(pitch > safePitchUpper || pitch < safePitchLower) client.player.setXRot(setSafePitch);
				if(!goingDownInEnd){
					if(stoppedFlyingTs == 0) stoppedFlyingTs = System.currentTimeMillis();
					final long countDownToKick = SAFE_KICK_DELAY - (System.currentTimeMillis() - stoppedFlyingTs);
					if(countDownToKick > 0){
						client.player.sendOverlayMessage(Component.literal("AIE Helper: will disconnect due to not-flying in: " + TextUtils_New.formatTime(countDownToKick)));
						return;
					}
					stoppedFlyingTs = 0;
				}
				String disconnectMsg = (goingDownInEnd?"FALLING!":"no longer flying")+", y="+y+", dur="+dur;
				Main.LOGGER.warn("AIE Helper: Disconnecting player: "+disconnectMsg);
				Configs.Hotkeys.AIE_TRAVEL_HELPER.setBooleanValue(false);
				client.disconnectFromWorld(Component.literal("[AIE Helper] "+disconnectMsg));
				client.level.disconnect(Component.literal("[AIE Helper] "+disconnectMsg));
				return;
			}

			if(atUnsafeY || tooLowDur){
				if(atUnsafeY) client.player.setXRot(setSafePitch);
				if(tooLowDur){
					for(int i=9; i<45; ++i) if(getElytraDur(client.player.getInventory().getItem(i%36)) > unsafeDur){
						if(i < 36){
							client.gameMode.handleContainerInput(0, i, 1, ContainerInput.PICKUP, client.player); // Pickup fresh elytra
							client.gameMode.handleContainerInput(0, 6, 0, ContainerInput.PICKUP, client.player); // Put in armor slot
							client.gameMode.handleContainerInput(0, i, 0, ContainerInput.PICKUP, client.player); // Put back used elytra
						}
						else client.gameMode.handleContainerInput(0, 6, i-36, ContainerInput.SWAP, client.player); // Swap with hotbar
						if(!atKickY){
							client.player.sendOverlayMessage(Component.literal("AIE Helper: Swapped to fresh elytra"));
							return;
						}
					}
					if(!atKickY && dur > kickDur && waitForSafePitch && (pitch < safePitchLower || pitch > safePitchUpper || pitch <= lastPitch)){
						client.player.sendOverlayMessage(Component.literal("AIE Helper: will trigger disconnect due to too low dur"));
						return;
					}
				}
				if(!atKickY && dur > kickDur){
					final long countDownToKick = SAFE_KICK_DELAY - (System.currentTimeMillis() - enabledTs);
					if(countDownToKick > 0){
						client.player.sendOverlayMessage(Component.literal("AIE Helper: will trigger disconnect due to too low "
								+(atUnsafeY?"Y":"dur")+" in: "+TextUtils_New.formatTime(countDownToKick)));
						return;
					}
				}
				Main.LOGGER.warn("AIE Helper: Disconnecting player: y="+y+", dur="+dur);
				Configs.Hotkeys.AIE_TRAVEL_HELPER.setBooleanValue(false);
				client.disconnectFromWorld(Component.literal("[AIE Helper] y="+y+", dur="+dur));
				client.level.disconnect(Component.literal("[AIE Helper] y="+y+", dur="+dur));
				return;
			}
			lastPitch = client.player.getXRot();
			lastY = client.player.getY();
		});
	}

	public void updateEnabled(final boolean enable){
//		Main.LOGGER.info("aie_travel_helper key pressed");
		if(client == null){
			client = Minecraft.getInstance();
			registerClientTickListener();
			Main.LOGGER.info("AIE Helper: registered");
		}
		if(enable){
			if(!client.player.isFallFlying()){
				client.player.sendOverlayMessage(Component.literal("AIE Helper: You need to be flying first"));
				Configs.Hotkeys.AIE_TRAVEL_HELPER.setBooleanValue(false);
				return;
			}
			enabledTs = System.currentTimeMillis();
			lastY = client.player.getY();
		}
		Main.LOGGER.info("AIE Helper: "+(enable ? "enabled" : "disabled"));
//		if(client.player != null) client.player.sendMessage(Text.literal("AIE Helper: "+(setEnabled ? "enabled" : "disabled")), true);
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