package dev.brian.farmhand;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Decides which stacks of a farmer's inventory are surplus and may go to a chest.
 * Pure logic with no Minecraft types, so it is unit-testable.
 *
 * <p>Reserves keep vanilla working: seeds for replanting, wheat for baking bread, and enough food
 * points that breeding (needs 12) and food sharing (starts at 24) still behave as in vanilla.
 */
public final class DepositPlanner {
	/** Enough to replant a good stretch of field straight after harvesting; see {@link SeedRefill}. */
	public static final int SEED_RESERVE = 32;
	public static final int WHEAT_RESERVE = 6;
	public static final int FOOD_RESERVE_POINTS = 24;

	public enum ItemClass { SEED, WHEAT, FOOD, OTHER }

	/** One inventory slot. {@code nutrition} is points per item and only matters for FOOD. */
	public record Slot(int index, ItemClass kind, int count, int nutrition) {}

	public record Move(int slot, int amount) {}

	private DepositPlanner() {}

	public static List<Move> plan(List<Slot> slots) {
		List<Slot> ordered = new ArrayList<>(slots);
		// Take from later slots first so the earliest stacks (the ones vanilla uses first) stay put.
		ordered.sort(Comparator.comparingInt(Slot::index).reversed());

		List<Move> moves = new ArrayList<>();
		planCounted(ordered, ItemClass.SEED, SEED_RESERVE, moves);
		planCounted(ordered, ItemClass.WHEAT, WHEAT_RESERVE, moves);
		planFood(ordered, moves);
		return moves;
	}

	private static void planCounted(List<Slot> ordered, ItemClass kind, int reserve, List<Move> out) {
		int total = 0;
		for (Slot s : ordered) {
			if (s.kind() == kind && s.count() > 0) total += s.count();
		}
		int surplus = total - reserve;
		for (Slot s : ordered) {
			if (surplus <= 0) break;
			if (s.kind() != kind || s.count() <= 0) continue;
			int take = Math.min(s.count(), surplus);
			out.add(new Move(s.index(), take));
			surplus -= take;
		}
	}

	private static void planFood(List<Slot> ordered, List<Move> out) {
		int points = 0;
		for (Slot s : ordered) {
			if (isFood(s)) points += s.count() * s.nutrition();
		}
		int surplus = points - FOOD_RESERVE_POINTS;
		for (Slot s : ordered) {
			if (surplus <= 0) break;
			if (!isFood(s)) continue;
			int take = Math.min(s.count(), surplus / s.nutrition());
			if (take <= 0) continue;
			out.add(new Move(s.index(), take));
			surplus -= take * s.nutrition();
		}
	}

	private static boolean isFood(Slot s) {
		return s.kind() == ItemClass.FOOD && s.count() > 0 && s.nutrition() > 0;
	}
}
