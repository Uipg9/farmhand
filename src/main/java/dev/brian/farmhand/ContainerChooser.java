package dev.brian.farmhand;

import java.util.Comparator;
import java.util.List;
import java.util.OptionalInt;

/** Picks which nearby container a farmer should deposit into. Pure logic, no Minecraft types. */
public final class ContainerChooser {
	/** {@code distSq} is the squared distance from the farmer's job site to the container. */
	public record Candidate(int id, double distSq, boolean hasRoom) {}

	private ContainerChooser() {}

	/** Nearest container with room within {@code maxDist} blocks; ties go to the lowest id. */
	public static OptionalInt choose(List<Candidate> candidates, double maxDist) {
		double maxSq = maxDist * maxDist;
		return candidates.stream()
			.filter(c -> c.hasRoom() && c.distSq() <= maxSq)
			.min(Comparator.comparingDouble(Candidate::distSq).thenComparingInt(Candidate::id))
			.map(c -> OptionalInt.of(c.id()))
			.orElse(OptionalInt.empty());
	}
}
