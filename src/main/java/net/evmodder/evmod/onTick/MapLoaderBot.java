package net.evmodder.evmod.onTick;

import static net.evmodder.evmod.compat.MinecraftCompat.sendOverlay;
import static net.evmodder.evmod.compat.MinecraftCompat.selectSlot;

import java.awt.image.BufferedImage;
import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalXZ;
import net.evmodder.EvLib.util.FileIO;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.Main;
import net.evmodder.evmod.apis.MapIdsFromImg;
import net.evmodder.evmod.apis.TickListener;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public final class MapLoaderBot implements TickListener{
	private static byte[] desiredColors;
	private static long lastLoadAttempt;
	private static IBaritone baritone;
	private static boolean isWalking;
	private static int mapSlot;

	private static final boolean pairsMatch(final byte[] data1, final byte[] data2, final int x, final int z, final boolean isInMap){
		final boolean isEven = (z&1)==0; // Top row of map is considered even (cuz 0-indexed)
		final int pos = x + 128*z;
		if(isEven || x != 0){
//			Main.LOGGER.info("checking "+x+","+z+" and "+(x-1)+","+z);
			return data1[pos] == data2[pos] && data1[pos-1] == data2[pos-1];
		}
		else{
//			Main.LOGGER.info("checking "+(isInMap ? "127,"+z : "0,"+z));
			return isInMap ? data1[pos+127] == data2[pos+127] : data1[pos] == data2[pos];
		}
	}

	private static final Boolean isInMap(final MapItemSavedData state){
//		Main.LOGGER.info("checking isInMap");
		Boolean isInMap = null;
		for(MapDecoration d : state.getDecorations()){
			if((d.type() == MapDecorationTypes.PLAYER || d.type() == MapDecorationTypes.PLAYER_OFF_MAP) && d.x() <= -120){
				if(isInMap != null) return null;
				isInMap = d.x() > -128;
//				Main.LOGGER.info("d.x()="+d.x()+", isInMap="+isInMap);
			}
		}
		return isInMap;
	}

	private static final void walkTo(Player player, final int x, final int z){
		selectSlot(player.getInventory(), (mapSlot+1)%9);
		isWalking = true;
		baritone.getCustomGoalProcess().setGoalAndPath(new GoalXZ(x, z));
	}

	@Override public final void onTickStart(final Minecraft client){
		if(!Configs.Generic.MAPART_SUPPRESS_BOT.getBooleanValue()) return;
		if(client.player == null || client.level == null) return;

		if(baritone == null) baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
		if(baritone.getCustomGoalProcess().isActive()) return;
		if(isWalking){
			isWalking = false;
			selectSlot(client.player.getInventory(), mapSlot);
//			Main.LOGGER.info("debug: finished walking, setting slot back to "+mapSlot);
		}
//		Main.LOGGER.info("debug: baritone pathing is available");

		if(client.player.getMainHandItem().getItem() != Items.FILLED_MAP) return;

//		final int topY = client.world.getBottomY() + client.world.getHeight();
//		final int addToReachTopY = topY-client.player.getBlockY();
//		BlockPos bp = client.player.getBlockPos().add(127, addToReachTopY, 0);
//		while(true){
//			MapColor c = client.world.getBlockState(bp).getMapColor(client.world, bp);
//		}
		final MapId mapId = client.player.getMainHandItem().get(DataComponents.MAP_ID);
		final MapItemSavedData state = client.level.getMapData(mapId);
		final Boolean isInMap;
		if(state == null || state.locked || (isInMap=isInMap(state)) == null) return;
//		Main.LOGGER.info("debug: client is holding unlocked map with a player symbol, isInMap="+isInMap);

		if(desiredColors == null){
			final long ts = System.currentTimeMillis();
			if(ts-lastLoadAttempt < 5000) return;
//			Main.LOGGER.info("debug: img cooldown");
			lastLoadAttempt = ts;
			BufferedImage img = MapIdsFromImg.getValidCompositeMapImg(FileIO.DIR+"canvas.png");
			if(img == null) return;
//			Main.LOGGER.info("debug: got img");
			desiredColors = MapIdsFromImg.colorsFromImg(img, 0, 0);
			if(desiredColors == null) return;
			Main.LOGGER.info("debug: loaded colors from img");
		}
//		Main.LOGGER.info("debug: image is loaded");

		//========== FOR PAIRS, EW ==========
		// todo: verify player decoration symbol is on LHS of map(x=0), and matches player-Z
		final int playerX = client.player.getBlockX(), playerZ = client.player.getBlockZ();
		final int pixelX = Math.floorMod(playerX+127+64, 128), pixelZ = Math.floorMod(playerZ+64, 128);
//		Main.LOGGER.info("debug: client is handling pixel "+pixelX+","+pixelZ);

		if((pixelX&1) == (pixelZ&1)) return; // Must be standing in a position relative(x-127) to a "dominant" pixel (checkerboard dom/sub)
//		Main.LOGGER.info("debug: client is on a dom pixel");


		if(!pairsMatch(state.colors, desiredColors, pixelX, pixelZ, isInMap)){
			final boolean isEven = (pixelZ&1)==0;
			if(isEven ? (pixelZ == 0 || pixelZ == 126) : (pixelZ == 1 || pixelZ == 127)){
				final int prevColX = pixelX+1, prevColZ = pixelZ + (pixelZ == 0 || pixelZ == 126 ? +1 : -1);
				final boolean atEnd = prevColX + 128*prevColZ == 128*128;
				if(atEnd || pairsMatch(state.colors, desiredColors, prevColX, prevColZ, /*TODO: detemine for prevXZ!*/false)){
					sendOverlay(client.player, Component.literal("Waiting for next column"));
				}
				else{
					sendOverlay(client.player, Component.literal("Previous column mismatch!"));
//					walkTo(client.player, playerX+1, playerZ+prevColZ-pixelZ);
				}
			}
			else sendOverlay(client.player, Component.literal("Waiting for correct color"));
			return;
		}
		mapSlot = client.player.getInventory().getSelectedSlot();

		int z;
		for(z=pixelZ-2; z>=0 && pairsMatch(state.colors, desiredColors, pixelX, z, isInMap); z-=2);
		if(z < 0) for(z=pixelZ+2; z<128 && pairsMatch(state.colors, desiredColors, pixelX, z, isInMap); z+=2);
		if(z < 128){
			sendOverlay(client.player, Component.literal("Walking to next incomplete row"));
			walkTo(client.player, playerX, playerZ + (z-pixelZ));
		}
		else{
			final boolean isEven = (pixelZ&1)==0;
			if(isEven) z = pixelZ < 64 ? 1 : 127;
			else z = pixelZ < 64 ? 0 : 126;
			sendOverlay(client.player, Component.literal("Walking to start of next column"));
			walkTo(client.player, playerX-1, playerZ+(z-pixelZ));
		}
	}
}