package dev.brian.farmhand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DepositScheduleTest {
	@Test
	void eachVillagerIsDueOncePerInterval() {
		int due = 0;
		for (long t = 0; t < DepositSchedule.INTERVAL_TICKS; t++) {
			if (DepositSchedule.isDue(t, 42)) due++;
		}
		assertEquals(1, due);
	}

	@Test
	void villagersAreSpreadAcrossTheInterval() {
		int dueAtTickZero = 0;
		for (int id = 0; id < DepositSchedule.INTERVAL_TICKS; id++) {
			if (DepositSchedule.isDue(0, id)) dueAtTickZero++;
		}
		assertEquals(1, dueAtTickZero);
	}

	@Test
	void sameVillagerIsDueExactlyOneIntervalLater() {
		long first = -1;
		for (long t = 0; t < DepositSchedule.INTERVAL_TICKS; t++) {
			if (DepositSchedule.isDue(t, 7)) first = t;
		}
		assertTrue(DepositSchedule.isDue(first + DepositSchedule.INTERVAL_TICKS, 7));
	}

	@Test
	void bucketIsTheIdModuloTheInterval() {
		assertEquals(5, DepositSchedule.bucketOf(205));
		assertEquals(0, DepositSchedule.bucketOf(0));
		assertEquals(DepositSchedule.INTERVAL_TICKS - 1, DepositSchedule.bucketOf(-1));
	}

	@Test
	void theDueBucketVisitsEveryBucketExactlyOncePerInterval() {
		boolean[] seen = new boolean[DepositSchedule.INTERVAL_TICKS];
		for (long t = 1234; t < 1234 + DepositSchedule.INTERVAL_TICKS; t++) {
			int b = DepositSchedule.dueBucket(t);
			assertFalse(seen[b], "bucket " + b + " came up twice");
			seen[b] = true;
		}
		for (boolean s : seen) assertTrue(s);
	}

	@Test
	void bucketsAgreeWithIsDue() {
		for (long t = 0; t < 350; t++) {
			for (int id = -150; id < 260; id += 7) {
				assertEquals(DepositSchedule.isDue(t, id), DepositSchedule.bucketOf(id) == DepositSchedule.dueBucket(t),
					"tick " + t + " id " + id);
			}
		}
	}

	@Test
	void serveBudgetIsSmallAndPositive() {
		assertTrue(DepositSchedule.MAX_SERVES_PER_TICK > 0 && DepositSchedule.MAX_SERVES_PER_TICK <= 32);
	}

	@Test
	void fullCachePrunesExpiredEntries() {
		DepositSchedule.FullCache cache = new DepositSchedule.FullCache();
		cache.markFull(1L, 0);
		cache.markFull(2L, 0);
		cache.markFull(3L, 400);
		cache.prune(DepositSchedule.FULL_COOLDOWN_TICKS);
		assertEquals(1, cache.size());
		assertTrue(cache.isCoolingDown(3L, DepositSchedule.FULL_COOLDOWN_TICKS));
	}

	@Test
	void fullCooldownBlocksUntilItExpires() {
		DepositSchedule.FullCache cache = new DepositSchedule.FullCache();
		assertFalse(cache.isCoolingDown(5L, 1000));
		cache.markFull(5L, 1000);
		assertTrue(cache.isCoolingDown(5L, 1000 + DepositSchedule.FULL_COOLDOWN_TICKS - 1));
		assertFalse(cache.isCoolingDown(5L, 1000 + DepositSchedule.FULL_COOLDOWN_TICKS));
	}

	@Test
	void fullCooldownIsPerContainer() {
		DepositSchedule.FullCache cache = new DepositSchedule.FullCache();
		cache.markFull(5L, 0);
		assertFalse(cache.isCoolingDown(6L, 1));
	}

	@Test
	void clearForgetsEverything() {
		DepositSchedule.FullCache cache = new DepositSchedule.FullCache();
		cache.markFull(5L, 0);
		cache.clear();
		assertFalse(cache.isCoolingDown(5L, 1));
	}
}
