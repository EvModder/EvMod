package net.evmodder.evmod;

import java.util.Arrays;
import java.util.UUID;
import net.evmodder.EvLib.util.Command;
import net.evmodder.EvLib.util.PacketHelper;
import net.evmodder.evmod.apis.ClickUtils;
import net.evmodder.evmod.apis.RemoteServerSender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerModelPart;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

final class InitUtils{
	static final void refreshClickLimits(){
		ClickUtils.refreshLimits(Configs.Generic.CLICK_LIMIT_COUNT.getIntegerValue(), Configs.Generic.CLICK_LIMIT_DURATION.getIntegerValue());
	}
	static final void refreshRemoteServerSender(){ // Accessor: KeybindCallbacks, Main
		String fullAddress = Configs.Database.ADDRESS.getStringValue();
		final int sep = fullAddress.indexOf(':');
		final String addr;
		final int port;
		if(sep == -1){addr = fullAddress; port = RemoteServerSender.DEFAULT_PORT;}
		else{addr = fullAddress.substring(0, sep).trim(); port = Integer.parseInt(fullAddress.substring(sep+1).trim());}
		Main.getInstance().remoteSender.setConnectionDetails(addr, port,
				Configs.Database.CLIENT_ID.getIntegerValue(), Configs.Database.CLIENT_KEY.getStringValue());
	}

	static final void sendRemoteMsg(RemoteServerSender rms, String msg){ // Accessor: KeybindCallbacks
		String[] arr = msg.split(",");
		if(arr.length < 2){
			Main.LOGGER.error("Invalid remote msg syntax, expected 'COMMAND,UUID...' got: "+msg);
			return;
		}
		final Command command;
		try{command = Command.valueOf(arr[0].toUpperCase());}
		catch(IllegalArgumentException e){
			Main.LOGGER.error("Invalid remote msg syntax, undefined command: "+arr[0]);
			return;
		}
		final byte[] byteMsg;
		try{byteMsg = PacketHelper.toByteArray(Arrays.stream(Arrays.copyOfRange(arr, 1, arr.length)).map(UUID::fromString).toArray(UUID[]::new));}
		catch(IllegalArgumentException e){
			Main.LOGGER.error("Invalid remote msg syntax, unable to parse UUID(s): "+msg.substring(msg.indexOf(',')+1));
			return;
		}
		rms.sendBotMessage(command, /*udp=*/true, /*timeout=*/5000, byteMsg, /*recv=*/null);
	}

	static final void sendChatMsg(String msg){ // Accessor: KeybindCallbacks
		if(msg.isBlank()) return;
		MinecraftClient mc = MinecraftClient.getInstance();
		if(msg.charAt(0) == '/') mc.player.networkHandler.sendChatCommand(msg.substring(1));
		else mc.player.networkHandler.sendChatMessage(msg);
	}

	static final void toggleSkinLayer(PlayerModelPart part){ // Accessor: KeybindCallbacks
		final MinecraftClient client = MinecraftClient.getInstance();
		if(Configs.Hotkeys.SYNC_CAPE_WITH_ELYTRA.getBooleanValue() && part == PlayerModelPart.CAPE
				&& client.player != null && client.options.isPlayerModelPartEnabled(part)){
			ItemStack chestItem = client.player.getInventory().getArmorStack(2);
			// Don't disable cape if we just switched to an elytra
			if(Registries.ITEM.getId(chestItem.getItem()).getPath().equals("elytra")) return;
		}
		client.options.setPlayerModelPart(part, !client.options.isPlayerModelPartEnabled(part));
		client.options.sendClientSettings();
	}
}
