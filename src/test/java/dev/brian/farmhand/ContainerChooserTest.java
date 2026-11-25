package dev.brian.farmhand;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.brian.farmhand.ContainerChooser.Candidate;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class ContainerChooserTest {
	private static final double R = 12.0;

	@Test
	void noCandidatesGivesEmpty() {
		assertEquals(OptionalInt.empty(), ContainerChooser.choose(List.of(), R));
	}

	@Test
	void picksTheNearestWithRoom() {
		List<Candidate> c = List.of(new Candidate(1, 49.0, true), new Candidate(2, 9.0, true), new Candidate(3, 25.0, true));
		assertEquals(OptionalInt.of(2), ContainerChooser.choose(c, R));
	}

	@Test
	void skipsFullContainers() {
		List<Candidate> c = List.of(new Candidate(1, 4.0, false), new Candidate(2, 36.0, true));
		assertEquals(OptionalInt.of(2), ContainerChooser.choose(c, R));
	}

	@Test
	void ignoresContainersBeyondTheRadius() {
		List<Candidate> c = List.of(new Candidate(1, R * R + 0.01, true));
		assertEquals(OptionalInt.empty(), ContainerChooser.choose(c, R));
	}

	@Test
	void containerExactlyAtTheRadiusCounts() {
		List<Candidate> c = List.of(new Candidate(1, R * R, true));
		assertEquals(OptionalInt.of(1), ContainerChooser.choose(c, R));
	}

	@Test
	void allFullGivesEmpty() {
		List<Candidate> c = List.of(new Candidate(1, 1.0, false), new Candidate(2, 2.0, false));
		assertEquals(OptionalInt.empty(), ContainerChooser.choose(c, R));
	}

	@Test
	void tieBreaksOnLowestId() {
		List<Candidate> c = List.of(new Candidate(7, 16.0, true), new Candidate(3, 16.0, true));
		assertEquals(OptionalInt.of(3), ContainerChooser.choose(c, R));
	}
}
