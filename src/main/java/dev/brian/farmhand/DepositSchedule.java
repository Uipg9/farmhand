package dev.brian.farmhand;

import java.util.HashMap;
import java.util.Map;

/** Throttling so many farmers never scan for chests on the same tick. Pure logic. */
public final class DepositSchedule {
	public static final int INTERVAL_TICKS = 100;
	public static final int FULL_COOLDOWN_TICKS = 600;
	/** Hard cap on farmers served in one tick; extras simply wait for their next turn. */
	public static final int MAX_SERVES_PER_TICK = 16;

	private DepositSchedule() {}

	/** The bucket (0..INTERVAL-1) a villager lives in, so each tick only touches one bucket. */
	public static int bucketOf(int entityId) {
		return Math.floorMod(entityId, INTERVAL_TICKS);
	}

	/** The one bucket whose villagers are due on this tick. */
	public static int dueBucket(long gameTime) {
		return (int) Math.floorMod(-gameTime, (long) INTERVAL_TICKS);
	}

	/** True once per {@link #INTERVAL_TICKS} for a given villager, spread out by entity id. */
	public static boolean isDue(long gameTime, int entityId) {
		return Math.floorMod(gameTime + entityId, (long) INTERVAL_TICKS) == 0;
	}

	/** Remembers containers that were full so every farmer does not rescan them each cycle. */
	public static final class FullCache {
		private final Map<Long, Long> until = new HashMap<>();

		public void markFull(long containerKey, long now) {
			until.put(containerKey, now + FULL_COOLDOWN_TICKS);
		}

		public boolean isCoolingDown(long containerKey, long now) {
			Long end = until.get(containerKey);
			if (end == null) return false;
			if (now >= end) {
				until.remove(containerKey);
				return false;
			}
			return true;
		}

		/** Drops expired entries so the map cannot grow without bound. */
		public void prune(long now) {
			until.values().removeIf(end -> now >= end);
		}

		public int size() {
			return until.size();
		}

		public void clear() {
			until.clear();
		}
	}
}
