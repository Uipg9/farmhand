package dev.brian.farmhand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.brian.farmhand.DepositPlanner.ItemClass;
import dev.brian.farmhand.DepositPlanner.Move;
import dev.brian.farmhand.DepositPlanner.Slot;
import java.util.List;
import org.junit.jupiter.api.Test;

class DepositPlannerTest {
	private static Slot seed(int slot, int count) {
		return new Slot(slot, ItemClass.SEED, count, 0);
	}

	private static Slot wheat(int slot, int count) {
		return new Slot(slot, ItemClass.WHEAT, count, 0);
	}

	private static Slot food(int slot, int count, int nutrition) {
		return new Slot(slot, ItemClass.FOOD, count, nutrition);
	}

	@Test
	void emptyInventoryMovesNothing() {
		assertTrue(DepositPlanner.plan(List.of()).isEmpty());
	}

	@Test
	void seedsAtOrUnderReserveStay() {
		assertTrue(DepositPlanner.plan(List.of(seed(0, DepositPlanner.SEED_RESERVE))).isEmpty());
	}

	@Test
	void surplusSeedsMove() {
		assertEquals(List.of(new Move(0, 4)), DepositPlanner.plan(List.of(seed(0, DepositPlanner.SEED_RESERVE + 4))));
	}

	@Test
	void seedSurplusComesFromLaterSlotsFirst() {
		int r = DepositPlanner.SEED_RESERVE;
		List<Slot> slots = List.of(seed(0, r), seed(1, 4));
		assertEquals(List.of(new Move(1, 4)), DepositPlanner.plan(slots));
	}

	@Test
	void seedSurplusCanSpanSlots() {
		int r = DepositPlanner.SEED_RESERVE;
		List<Slot> slots = List.of(seed(0, r), seed(1, 4), seed(2, 3));
		// reserve + 7 seeds -> move 7: all of slot 2 (3), then 4 from slot 1.
		assertEquals(List.of(new Move(2, 3), new Move(1, 4)), DepositPlanner.plan(slots));
	}

	@Test
	void theSeedReserveCoversAFullFieldRefillRound() {
		// A farmer should be able to replant a good stretch of field before it needs a refill.
		assertTrue(DepositPlanner.SEED_RESERVE >= 32);
	}

	@Test
	void wheatKeepsReserveForBread() {
		assertTrue(DepositPlanner.plan(List.of(wheat(0, DepositPlanner.WHEAT_RESERVE))).isEmpty());
		assertEquals(List.of(new Move(0, 3)), DepositPlanner.plan(List.of(wheat(0, DepositPlanner.WHEAT_RESERVE + 3))));
	}

	@Test
	void foodKeepsReservePointsAndMovesWholeItemsOnly() {
		// bread = 4 nutrition. 10 bread = 40 points, reserve 24 -> 16 surplus points = 4 bread.
		assertEquals(List.of(new Move(0, 4)), DepositPlanner.plan(List.of(food(0, 10, 4))));
		// 7 bread = 28 points -> 4 surplus points = exactly 1 bread.
		assertEquals(List.of(new Move(0, 1)), DepositPlanner.plan(List.of(food(0, 7, 4))));
		// 6 bread = 24 points -> nothing.
		assertTrue(DepositPlanner.plan(List.of(food(0, 6, 4))).isEmpty());
	}

	@Test
	void foodSurplusSmallerThanOneItemStays() {
		// 3-point items: 9 of them = 27 points, surplus 3 = exactly 1 item; 8 = 24 points, none.
		assertEquals(List.of(new Move(0, 1)), DepositPlanner.plan(List.of(food(0, 9, 3))));
		assertTrue(DepositPlanner.plan(List.of(food(0, 8, 3))).isEmpty());
		// 5-point items: 5 of them = 25 points, surplus 1 < 5 so nothing moves.
		assertTrue(DepositPlanner.plan(List.of(food(0, 5, 5))).isEmpty());
	}

	@Test
	void foodReserveIsCountedAcrossAllFoodTypes() {
		// 6 bread (24) + 10 carrots (10) = 34 points; surplus 10 comes from the later slot.
		List<Slot> slots = List.of(food(0, 6, 4), food(1, 10, 1));
		assertEquals(List.of(new Move(1, 10)), DepositPlanner.plan(slots));
	}

	@Test
	void unknownItemsAreNeverMoved() {
		assertTrue(DepositPlanner.plan(List.of(new Slot(0, ItemClass.OTHER, 64, 0))).isEmpty());
	}

	@Test
	void neverMovesMoreThanASlotHolds() {
		for (Move m : DepositPlanner.plan(List.of(seed(0, 64), wheat(1, 64), food(2, 64, 4)))) {
			int held = switch (m.slot()) {
				case 0, 1, 2 -> 64;
				default -> 0;
			};
			assertTrue(m.amount() > 0 && m.amount() <= held, "bad move " + m);
		}
	}

	@Test
	void ignoresZeroAndNegativeCounts() {
		assertTrue(DepositPlanner.plan(List.of(seed(0, 0), wheat(1, -3))).isEmpty());
	}
}
