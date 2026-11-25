package dev.brian.farmhand;

import java.util.HashMap;
import java.util.Map;

/**
 * Remembers villagers whose last container scan found nothing usable, so a farmer with no chest nearby
 * does not rescan its surroundings every cycle. Pure logic with no Minecraft types.
 */
public final class ScanBackoff {
	public static final int BACKOFF_TICKS = 400;

	private final Map<Integer, Long> until = new HashMap<>();

	public void markEmpty(int villagerId, long now) {
		until.put(villagerId, now + BACKOFF_TICKS);
	}

	public boolean isBackingOff(int villagerId, long now) {
		Long end = until.get(villagerId);
		return end != null && now < end;
	}

	public void forget(int villagerId) {
		until.remove(villagerId);
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
