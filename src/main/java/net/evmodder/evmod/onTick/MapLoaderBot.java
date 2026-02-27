package net.evmodder.evmod.onTick;

import java.awt.image.BufferedImage;
import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalXZ;
import net.evmodder.EvLib.util.FileIO;
import net.evmodder.evmod.Configs;
import net.evmodder.evmod.Main;
import net.evmodder.evmod.apis.MapIdsFromImg;
import net.evmodder.evmod.apis.TickListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapDecoration;
import net.minecraft.item.map.MapDecorationTypes;
import net.minecraft.item.map.MapState;
import net.minecraft.text.Text;

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

	private static final Boolean isInMap(final MapState state){
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

	private static final void walkTo(PlayerEntity player, final int x, final int z){
		player.getInventory().setSelectedSlot((mapSlot+1)%9);
		isWalking = true;
		baritone.getCustomGoalProcess().setGoalAndPath(new GoalXZ(x, z));
	}

	@Override public final void onTickStart(final MinecraftClient client){
		if(!Configs.Generic.MAPART_SUPPRESS_BOT.getBooleanValue()) return;
		if(client.player == null || client.world == null) return;

		if(baritone == null) baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
		if(baritone.getCustomGoalProcess().isActive()) return;
		if(isWalking){
			isWalking = false;
			client.player.getInventory().setSelectedSlot(mapSlot);
//			Main.LOGGER.info("debug: finished walking, setting slot back to "+mapSlot);
		}
//		Main.LOGGER.info("debug: baritone pathing is available");

		if(client.player.getMainHandStack().getItem() != Items.FILLED_MAP) return;

//		final int topY = client.world.getBottomY() + client.world.getHeight();
//		final int addToReachTopY = topY-client.player.getBlockY();
//		BlockPos bp = client.player.getBlockPos().add(127, addToReachTopY, 0);
//		while(true){
//			MapColor c = client.world.getBlockState(bp).getMapColor(client.world, bp);
//		}
		final MapIdComponent mapId = client.player.getMainHandStack().get(DataComponentTypes.MAP_ID);
		final MapState state = client.world.getMapState(mapId);
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
				client.player.sendMessage(Text.literal("Waiting for next column"), true);
			}
			else client.player.sendMessage(Text.literal("Waiting for correct color"), true);
			return;
		}
		mapSlot = client.player.getInventory().selectedSlot;

		int z;
		for(z=pixelZ-2; z>=0 && pairsMatch(state.colors, desiredColors, pixelX, z, isInMap); z-=2);
		if(z < 0) for(z=pixelZ+2; z<128 && pairsMatch(state.colors, desiredColors, pixelX, z, isInMap); z+=2);
		if(z < 128){
			client.player.sendMessage(Text.literal("Walking to next incomplete row"), true);
			walkTo(client.player, playerX, playerZ + (z-pixelZ));
		}
		else{
			final boolean isEven = (pixelZ&1)==0;
			if(isEven) z = pixelZ < 64 ? 1 : 127;
			else z = pixelZ < 64 ? 0 : 126;
			client.player.sendMessage(Text.literal("Walking to start of next column"), true);
			walkTo(client.player, playerX-1, playerZ + (z-pixelZ));
		}
	}
}