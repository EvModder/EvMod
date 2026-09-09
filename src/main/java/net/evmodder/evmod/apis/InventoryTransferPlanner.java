package net.evmodder.evmod.apis;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import net.evmodder.evmod.apis.ClickUtils.ActionType;
import net.evmodder.evmod.apis.ClickUtils.InvAction;

/** Shared stack-transfer and swap plans for inventory automations. */
public final class InventoryTransferPlanner{
	/** A menu slot that can participate in a {@link ActionType#HOTBAR_SWAP}. */
	public record HotbarSlot(int menuSlot, int button, int count){}

	private InventoryTransferPlanner(){}

	/**
	 * Swap whole stacks, preserving the cursor and temporary hotbar/offhand slot.
	 * Endpoint buttons are -1 for non-hotbar slots. Otherwise one swap suffices.
	 * With neither endpoint in the hotbar, the temporary button must name a third
	 * slot; restoration is needed unless both destination and temporary are empty.
	 * All involved slots must accept the exchanged stacks without truncation.
	 */
	public static void swapStacks(final Queue<InvAction> plan, final int sourceSlot, final int destinationSlot,
			final int sourceHotbarButton, final int destinationHotbarButton, final int temporaryButton, final boolean restoreTemporary){
		if(sourceSlot == destinationSlot) return;
		if(destinationHotbarButton >= 0) plan.add(new InvAction(sourceSlot, destinationHotbarButton, ActionType.HOTBAR_SWAP));
		else if(sourceHotbarButton >= 0) plan.add(new InvAction(destinationSlot, sourceHotbarButton, ActionType.HOTBAR_SWAP));
		else{
			plan.add(new InvAction(sourceSlot, temporaryButton, ActionType.HOTBAR_SWAP));
			plan.add(new InvAction(destinationSlot, temporaryButton, ActionType.HOTBAR_SWAP));
			if(restoreTemporary) plan.add(new InvAction(sourceSlot, temporaryButton, ActionType.HOTBAR_SWAP));
		}
	}

	private static int pickupCost(final int count, final int amount){
		final int half = Math.ceilDiv(count, 2);
		return 1 + (amount <= half ? Math.min(half-amount, count-amount) : count-amount);
	}

	/** Search only plans shorter than the arithmetic candidates, for standard stack sizes. */
	private static ArrayDeque<InvAction> shorterPickupPlan(final int sourceSlot, final int destinationSlot,
			final int source, final int destination, final int max, final int amount, final int bound){
		final int width = max+1, states = width*width, total = source+destination;
		final int start = source*width+destination, goal = (source-amount)*width+destination+amount;
		// Cursor count is implied by conservation; each edge costs one packet.
		final int[] previous = new int[states], queue = new int[states];
		final byte[] action = new byte[states];
		Arrays.fill(previous, -1);
		previous[start] = start;
		queue[0] = start;
		int head = 0, tail = 1;
		for(int depth=1; depth<bound && head<tail; ++depth){
			final int end = tail;
			while(head < end){
				final int key = queue[head++], s = key/width, d = key%width, cursor = total-s-d;
				for(int input=0; input<4; ++input){
					final int count = input < 2 ? s : d;
					final int delta = cursor == 0 ? -((input&1) == 0 ? count : Math.ceilDiv(count, 2))
							: Math.min(max-count, (input&1) == 0 ? cursor : 1);
					final int next = key+(input < 2 ? delta*width : delta);
					if(previous[next] >= 0) continue;
					previous[next] = key;
					action[next] = (byte)input;
					if(next == goal){
						final ArrayDeque<InvAction> result = new ArrayDeque<>();
						for(int step=goal; step!=start; step=previous[step]){
							final int move = action[step];
							result.addFirst(new InvAction(move < 2 ? sourceSlot : destinationSlot, move&1, ActionType.CLICK));
						}
						return result;
					}
					queue[tail++] = next;
				}
			}
		}
		return null;
	}

	/** Leaves exactly {@code amount} items on the cursor; returns the first click for sequence reservations. */
	public static InvAction pickupAmount(final Queue<InvAction> clicks, final int sourceSlot, final int sourceCount, final int amount){
		if(amount <= 0 || amount > sourceCount) throw new IllegalArgumentException("Invalid partial transfer amount");
		final InvAction firstClick;
		if(amount == sourceCount){
			clicks.add(firstClick=new InvAction(sourceSlot, 0, ActionType.CLICK));
			return firstClick;
		}
		final int half = Math.ceilDiv(sourceCount, 2);
		final int subtractFromHalf = amount <= half ? half-amount : Integer.MAX_VALUE;
		final int subtractFromAll = sourceCount-amount;
		if(subtractFromHalf <= subtractFromAll){
			clicks.add(firstClick=new InvAction(sourceSlot, 1, ActionType.CLICK));
			for(int i=0; i<subtractFromHalf; ++i) clicks.add(new InvAction(sourceSlot, 1, ActionType.CLICK));
		}
		else{
			clicks.add(firstClick=new InvAction(sourceSlot, 0, ActionType.CLICK));
			for(int i=0; i<subtractFromAll; ++i) clicks.add(new InvAction(sourceSlot, 1, ActionType.CLICK));
		}
		return firstClick;
	}

	public static ArrayDeque<InvAction> pickupAmount(final int sourceSlot, final int sourceCount, final int amount){
		final ArrayDeque<InvAction> clicks = new ArrayDeque<>();
		pickupAmount(clicks, sourceSlot, sourceCount, amount);
		return clicks;
	}

	/**
	 * Shift-moves exactly {@code amount}, leaving the remainder in the source and the cursor empty.
	 * The caller must ensure quick-move accepts that entire amount and routes it to the intended slots.
	 * Returns the first click for sequence reservations.
	 */
	public static InvAction quickMoveAmount(final Queue<InvAction> clicks, final int sourceSlot, final int sourceCount, final int amount){
		if(amount <= 0 || amount > sourceCount) throw new IllegalArgumentException("Invalid quick-move amount");
		final InvAction firstClick;
		if(amount == sourceCount){
			clicks.add(firstClick=new InvAction(sourceSlot, 0, ActionType.SHIFT_CLICK));
			return firstClick;
		}
		final int remainder = sourceCount-amount;
		if(pickupCost(sourceCount, remainder) <= amount+1){
			firstClick = pickupAmount(clicks, sourceSlot, sourceCount, remainder);
		}
		else{
			clicks.add(firstClick=new InvAction(sourceSlot, 0, ActionType.CLICK));
			for(int i=0; i<amount; ++i) clicks.add(new InvAction(sourceSlot, 1, ActionType.CLICK));
		}
		clicks.add(new InvAction(sourceSlot, 0, ActionType.SHIFT_CLICK));
		clicks.add(new InvAction(sourceSlot, 0, ActionType.CLICK));
		return firstClick;
	}

	/**
	 * Moves an exact amount between distinct, compatible ordinary slots, starting
	 * and ending with an empty cursor. Both slots must allow pickup/placement and
	 * use the supplied stack limit. Hotbar candidates must be empty or compatible.
	 * Result slots, bundles and menu-specific quick-move routing are caller concerns.
	 * For stack limits up to 64, also considers every shorter two-slot PICKUP path.
	 * This is not a global optimum over other inventory slots or arbitrary swaps.
	 *
	 * Candidate paths include source-side subtraction, one-at-a-time destination
	 * placement, destination-side subtraction after temporarily moving a larger
	 * amount, filling the destination to capacity, direct hotbar swaps, and
	 * two-swap hotbar-temporary paths. Counts are
	 * the actual observed counts; neither source nor destination is assumed full.
	 */
	public static void transferAmount(final Queue<InvAction> plan,
			final int sourceSlot, final int destinationSlot, final int sourceCount, final int destinationCount,
			final int maxStackSize, final int amount, final int sourceHotbarButton,
			final int destinationHotbarButton, final List<HotbarSlot> hotbarSlots){
		if(sourceSlot == destinationSlot || sourceCount > maxStackSize || amount <= 0 || amount > sourceCount || destinationCount < 0
				|| destinationCount+amount > maxStackSize) throw new IllegalArgumentException("Invalid exact transfer");
		final int remainder = sourceCount-amount;

		// A swap is an exact one-click partial transfer when the destination holds
		// exactly the amount that must remain in the source.
		if(destinationCount == remainder){
			if(destinationHotbarButton >= 0){
				plan.add(new InvAction(sourceSlot, destinationHotbarButton, ActionType.HOTBAR_SWAP));
				return;
			}
			if(sourceHotbarButton >= 0){
				plan.add(new InvAction(destinationSlot, sourceHotbarButton, ActionType.HOTBAR_SWAP));
				return;
			}
		}

		// If neither endpoint is in the hotbar, an equally-sized compatible hotbar
		// stack can act as temporary storage without leaving an item on the cursor.
		if(sourceHotbarButton < 0 && destinationHotbarButton < 0 && destinationCount == remainder){
			for(final HotbarSlot hotbar : hotbarSlots){
				if(hotbar.menuSlot == sourceSlot || hotbar.menuSlot == destinationSlot || hotbar.count != remainder) continue;
				plan.add(new InvAction(sourceSlot, hotbar.button, ActionType.HOTBAR_SWAP));
				plan.add(new InvAction(destinationSlot, hotbar.button, ActionType.HOTBAR_SWAP));
				return;
			}
		}

		final int half = Math.ceilDiv(sourceCount, 2);
		if(amount == sourceCount || amount == half){
			plan.add(new InvAction(sourceSlot, amount == sourceCount ? 0 : 1, ActionType.CLICK));
			plan.add(new InvAction(destinationSlot, 0, ActionType.CLICK));
			return;
		}
		// Score candidates before allocating actions; only emit the winning sequence.
		int bestCost = Integer.MAX_VALUE, bestPrefix = 0, bestStrategy = 0;
		for(int prefix=0; prefix<2; ++prefix){
			final int cursorCount = prefix == 0 ? sourceCount : half;
			if(cursorCount < amount) continue;
			final int excess = cursorCount-amount;
			for(int strategy=0; strategy<4; ++strategy){
				final int cost = switch(strategy){
					case 0 -> 2+excess; // Return singles to source, then place cursor.
					case 1 -> 1+amount+(excess > 0 ? 1 : 0); // Place singles, return remainder.
					case 2 -> destinationCount+cursorCount > maxStackSize ? Integer.MAX_VALUE
							: 2+(excess > 0 ? pickupCost(destinationCount+cursorCount, excess)+1 : 0);
					default -> destinationCount+amount == maxStackSize ? 2+(excess > 0 ? 1 : 0) : Integer.MAX_VALUE;
				};
				if(cost < bestCost){bestCost=cost; bestPrefix=prefix; bestStrategy=strategy;}
			}
		}
		// One/two-click swaps return above. The ordinary two-click possibilities are
		// exhausted by the arithmetic candidates, so a three-click winner is optimal.
		if(bestCost > 3 && maxStackSize <= 64){
			final ArrayDeque<InvAction> shorter = shorterPickupPlan(
					sourceSlot, destinationSlot, sourceCount, destinationCount, maxStackSize, amount, bestCost);
			if(shorter != null){plan.addAll(shorter); return;}
		}
		final int cursorCount = bestPrefix == 0 ? sourceCount : half, excess = cursorCount-amount;
		plan.add(new InvAction(sourceSlot, bestPrefix, ActionType.CLICK));
		switch(bestStrategy){
			case 0 -> {
				for(int i=0; i<excess; ++i) plan.add(new InvAction(sourceSlot, 1, ActionType.CLICK));
				plan.add(new InvAction(destinationSlot, 0, ActionType.CLICK));
			}
			case 1 -> {
				for(int i=0; i<amount; ++i) plan.add(new InvAction(destinationSlot, 1, ActionType.CLICK));
				if(excess > 0) plan.add(new InvAction(sourceSlot, 0, ActionType.CLICK));
			}
			case 2 -> {
				plan.add(new InvAction(destinationSlot, 0, ActionType.CLICK));
				if(excess > 0){
					pickupAmount(plan, destinationSlot, destinationCount+cursorCount, excess);
					plan.add(new InvAction(sourceSlot, 0, ActionType.CLICK));
				}
			}
			case 3 -> {
				plan.add(new InvAction(destinationSlot, 0, ActionType.CLICK));
				if(excess > 0) plan.add(new InvAction(sourceSlot, 0, ActionType.CLICK));
			}
		}
	}

	public static ArrayDeque<InvAction> transferAmount(
			final int sourceSlot, final int destinationSlot, final int sourceCount, final int destinationCount,
			final int maxStackSize, final int amount, final int sourceHotbarButton,
			final int destinationHotbarButton, final List<HotbarSlot> hotbarSlots){
		final ArrayDeque<InvAction> plan = new ArrayDeque<>();
		transferAmount(plan, sourceSlot, destinationSlot, sourceCount, destinationCount, maxStackSize, amount,
				sourceHotbarButton, destinationHotbarButton, hotbarSlots);
		return plan;
	}

	/** Compatibility overload for an empty, non-hotbar destination. */
	public static ArrayDeque<InvAction> transferAmount(
			final int sourceSlot, final int destinationSlot, final int sourceCount, final int amount){
		return transferAmount(sourceSlot, destinationSlot, sourceCount, 0, Math.max(sourceCount, amount), amount,
				-1, -1, List.of());
	}
}