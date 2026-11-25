package dev.brian.farmhand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScanBackoffTest {
	@Test
	void startsWithoutBackoff() {
		assertFalse(new ScanBackoff().isBackingOff(1, 0));
	}

	@Test
	void backsOffForTheConfiguredTimeThenResumes() {
		ScanBackoff b = new ScanBackoff();
		b.markEmpty(7, 1000);
		assertTrue(b.isBackingOff(7, 1000));
		assertTrue(b.isBackingOff(7, 1000 + ScanBackoff.BACKOFF_TICKS - 1));
		assertFalse(b.isBackingOff(7, 1000 + ScanBackoff.BACKOFF_TICKS));
	}

	@Test
	void eachVillagerHasItsOwnBackoff() {
		ScanBackoff b = new ScanBackoff();
		b.markEmpty(7, 0);
		assertFalse(b.isBackingOff(8, 1));
	}

	@Test
	void forgetEndsTheBackoffImmediately() {
		ScanBackoff b = new ScanBackoff();
		b.markEmpty(7, 0);
		b.forget(7);
		assertFalse(b.isBackingOff(7, 1));
	}

	@Test
	void pruneDropsOnlyExpiredEntries() {
		ScanBackoff b = new ScanBackoff();
		b.markEmpty(1, 0);
		b.markEmpty(2, 500);
		b.prune(ScanBackoff.BACKOFF_TICKS);
		assertEquals(1, b.size());
		assertTrue(b.isBackingOff(2, ScanBackoff.BACKOFF_TICKS));
	}

	@Test
	void clearForgetsEveryone() {
		ScanBackoff b = new ScanBackoff();
		b.markEmpty(1, 0);
		b.markEmpty(2, 0);
		b.clear();
		assertEquals(0, b.size());
	}
}
