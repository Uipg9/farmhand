package dev.brian.farmhand;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Decides how many seeds a farmer should pull back out of a nearby container so it can keep
 * replanting right after it harvests. Pure logic with no Minecraft types, so it is unit-testable.
 *
 * <p>It refills only below {@link #REFILL_BELOW} and tops up to {@link #TARGET}, which equals what
 * {@link DepositPlanner} lets a farmer keep. The gap stops it from taking one seed at a time, and the
 * equal target means a refilled farmer is never asked to deposit those seeds again.
 */
public final class SeedRefill {
	public static final int REFILL_BELOW = 16;
	public static final int TARGET = DepositPlanner.SEED_RESERVE;

	/** One container slot that holds plantable seeds. */
	public record Stack(int index, int count) {}

	public record Take(int slot, int amount) {}

	private SeedRefill() {}

	public static List<Take> plan(int seedsHeld, List<Stack> chestSeeds) {
		int held = Math.max(0, seedsHeld);
		if (held >= REFILL_BELOW) return List.of();

		int need = TARGET - held;
		List<Stack> ordered = new ArrayList<>(chestSeeds);
		ordered.sort(Comparator.comparingInt(Stack::index));

		List<Take> takes = new ArrayList<>();
		for (Stack s : ordered) {
			if (need <= 0) break;
			if (s.count() <= 0) continue;
			int take = Math.min(s.count(), need);
			takes.add(new Take(s.index(), take));
			need -= take;
		}
		return takes;
	}
}
