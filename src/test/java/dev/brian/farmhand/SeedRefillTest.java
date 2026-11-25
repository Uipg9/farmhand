package dev.brian.farmhand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.brian.farmhand.DepositPlanner.ItemClass;
import dev.brian.farmhand.DepositPlanner.Slot;
import dev.brian.farmhand.SeedRefill.Stack;
import dev.brian.farmhand.SeedRefill.Take;
import java.util.List;
import org.junit.jupiter.api.Test;

class SeedRefillTest {
	private static final int TARGET = SeedRefill.TARGET;
	private static final int BELOW = SeedRefill.REFILL_BELOW;

	@Test
	void thresholdsAreOrderedAndMatchTheDepositReserve() {
		assertTrue(BELOW > 0 && BELOW < TARGET, "refill threshold must sit below the target (hysteresis)");
		assertEquals(DepositPlanner.SEED_RESERVE, TARGET, "refill must top up to exactly what deposit keeps");
	}

	@Test
	void farmerWithEnoughSeedsTakesNothing() {
		assertTrue(SeedRefill.plan(BELOW, List.of(new Stack(0, 100))).isEmpty());
		assertTrue(SeedRefill.plan(TARGET, List.of(new Stack(0, 100))).isEmpty());
	}

	@Test
	void emptyHandedFarmerTopsUpToTarget() {
		assertEquals(List.of(new Take(0, TARGET)), SeedRefill.plan(0, List.of(new Stack(0, 200))));
	}

	@Test
	void takesOnlyWhatIsMissing() {
		assertEquals(List.of(new Take(3, TARGET - 10)), SeedRefill.plan(10, List.of(new Stack(3, 200))));
	}

	@Test
	void justBelowTheThresholdStillRefills() {
		assertEquals(List.of(new Take(0, TARGET - (BELOW - 1))), SeedRefill.plan(BELOW - 1, List.of(new Stack(0, 200))));
	}

	@Test
	void spansSeveralChestStacksInSlotOrder() {
		List<Stack> chest = List.of(new Stack(5, 20), new Stack(2, 20), new Stack(9, 64));
		// Slot order 2, 5, 9: 20 + 12 = 32.
		assertEquals(List.of(new Take(2, 20), new Take(5, TARGET - 20)), SeedRefill.plan(0, chest));
	}

	@Test
	void takesEverythingWhenTheChestHasLessThanNeeded() {
		assertEquals(List.of(new Take(1, 7), new Take(4, 3)), SeedRefill.plan(0, List.of(new Stack(4, 3), new Stack(1, 7))));
	}

	@Test
	void emptyChestGivesNothing() {
		assertTrue(SeedRefill.plan(0, List.of()).isEmpty());
	}

	@Test
	void ignoresZeroAndNegativeStacks() {
		assertEquals(List.of(new Take(2, 5)), SeedRefill.plan(0, List.of(new Stack(0, 0), new Stack(1, -4), new Stack(2, 5))));
	}

	@Test
	void negativeHeldCountIsTreatedAsZero() {
		assertEquals(List.of(new Take(0, TARGET)), SeedRefill.plan(-3, List.of(new Stack(0, 200))));
	}

	@Test
	void aToppedUpFarmerIsNotAskedToDepositTheSameSeedsAgain() {
		// After a refill the farmer holds exactly TARGET seeds: the deposit planner must leave them alone.
		assertTrue(DepositPlanner.plan(List.of(new Slot(0, ItemClass.SEED, TARGET, 0))).isEmpty());
	}
}
