package dev.brian.farmhand;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.food.VillagerFood;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import dev.brian.farmhand.ContainerChooser.Candidate;
import dev.brian.farmhand.DepositPlanner.ItemClass;
import dev.brian.farmhand.DepositPlanner.Move;
import dev.brian.farmhand.DepositPlanner.Slot;

/**
 * Keeps working farmers supplied with seeds and moves their surplus into a chest or barrel they are
 * standing next to.
 *
 * <p>Vanilla farming is untouched (harvest, replant, bread, food sharing all run as usual). Cost is kept
 * flat on purpose: villagers sit in {@value DepositSchedule#INTERVAL_TICKS} buckets and a tick only
 * looks at one bucket, at most {@value DepositSchedule#MAX_SERVES_PER_TICK} farmers are served per tick,
 * container lookup reads the block entities of the (already loaded) chunks around the farmer instead of
 * scanning blocks, and a farmer that finds no container backs off for {@value ScanBackoff#BACKOFF_TICKS} ticks.
 */
public final class FarmerDepositor {
	/** How close (blocks) a farmer must be to a container for it to act. */
	public static final int REACH = 4;
	/** How often (ticks) the small caches are pruned. */
	private static final int PRUNE_EVERY_TICKS = 1200;

	@SuppressWarnings("unchecked")
	private final Set<Villager>[] buckets = new Set[DepositSchedule.INTERVAL_TICKS];
	private final DepositSchedule.FullCache fullCache = new DepositSchedule.FullCache();
	private final ScanBackoff scanBackoff = new ScanBackoff();
	private Predicate<Villager> workGate = FarmerDepositor::isWorkingFarmer;

	private int tracked;
	private long servedTotal;
	private long scansTotal;
	private int maxServedInOneTick;

	public FarmerDepositor() {
		for (int i = 0; i < buckets.length; i++) buckets[i] = new LinkedHashSet<>();
	}

	public void track(Villager villager) {
		if (buckets[DepositSchedule.bucketOf(villager.getId())].add(villager)) tracked++;
	}

	public void untrack(Villager villager) {
		if (buckets[DepositSchedule.bucketOf(villager.getId())].remove(villager)) tracked--;
		scanBackoff.forget(villager.getId());
	}

	public void clear() {
		for (Set<Villager> b : buckets) b.clear();
		tracked = 0;
		fullCache.clear();
		scanBackoff.clear();
	}

	public void tick(MinecraftServer server) {
		tick(server.getTickCount());
	}

	/** One server tick at game time {@code now}: touches a single bucket and serves at most the per-tick cap. */
	public void tick(long now) {
		if (tracked == 0) return;
		if (now % PRUNE_EVERY_TICKS == 0) {
			fullCache.prune(now);
			scanBackoff.prune(now);
		}

		Set<Villager> bucket = buckets[DepositSchedule.dueBucket(now)];
		if (bucket.isEmpty()) return;

		int served = 0;
		List<Villager> rotate = null;
		for (Villager farmer : bucket) {
			if (served >= DepositSchedule.MAX_SERVES_PER_TICK) break;
			if (!(farmer.level() instanceof ServerLevel level) || !workGate.test(farmer)) continue;
			serve(level, farmer, now);
			served++;
			if (rotate == null) rotate = new ArrayList<>();
			rotate.add(farmer);
		}
		// Fairness if a bucket ever holds more farmers than the cap: served ones go to the back of the line.
		if (served >= DepositSchedule.MAX_SERVES_PER_TICK && rotate != null) {
			for (Villager v : rotate) {
				bucket.remove(v);
				bucket.add(v);
			}
		}
		servedTotal += served;
		if (served > maxServedInOneTick) maxServedInOneTick = served;
	}

	private static boolean isWorkingFarmer(Villager v) {
		return v.getVillagerData().profession().is(VillagerProfession.FARMER)
			&& v.isAlive()
			&& !v.isSleeping()
			&& v.getTradingPlayer() == null
			&& v.getBrain().isActive(Activity.WORK);
	}

	/** Test hook: replaces the "is this farmer working right now" check. */
	public void setWorkGate(Predicate<Villager> gate) {
		this.workGate = gate;
	}

	public int trackedCount() { return tracked; }
	public long servedTotal() { return servedTotal; }
	public long scansTotal() { return scansTotal; }
	public int maxServedInOneTick() { return maxServedInOneTick; }

	public void resetStats() {
		servedTotal = 0;
		scansTotal = 0;
		maxServedInOneTick = 0;
	}

	/** Runs one pass immediately, bypassing activity gating and throttling (used by the gametest). */
	public void depositNow(ServerLevel level, Villager farmer, long now) {
		serve(level, farmer, now);
	}

	/** One pass for a farmer: top up seeds from a nearby container first (replanting wins), then deposit surplus. */
	void serve(ServerLevel level, Villager farmer, long now) {
		SimpleContainer inventory = farmer.getInventory();
		boolean wantsSeeds = countSeeds(inventory) < SeedRefill.REFILL_BELOW;
		if (!wantsSeeds && DepositPlanner.plan(slotsOf(inventory)).isEmpty()) return;
		if (scanBackoff.isBackingOff(farmer.getId(), now)) return;

		List<BlockPos> positions = new ArrayList<>();
		List<Container> containers = new ArrayList<>();
		scansTotal++;
		findContainers(level, farmer.blockPosition(), positions, containers);
		if (positions.isEmpty()) {
			scanBackoff.markEmpty(farmer.getId(), now);
			return;
		}

		if (wantsSeeds) refillSeeds(farmer, inventory, positions, containers);

		List<Move> plan = DepositPlanner.plan(slotsOf(inventory));
		if (!plan.isEmpty()) deposit(farmer, inventory, plan, positions, containers, now);
	}

	/**
	 * Chests, trapped chests and barrels within {@link #REACH} of {@code center}, found through the block
	 * entities of chunks that are already loaded (never forces a chunk load, and far cheaper than scanning
	 * every block of the cube). Locked containers, minecart chests and shulker boxes are ignored.
	 */
	private static void findContainers(ServerLevel level, BlockPos center, List<BlockPos> positions, List<Container> containers) {
		int minX = center.getX() - REACH, maxX = center.getX() + REACH;
		int minY = center.getY() - REACH, maxY = center.getY() + REACH;
		int minZ = center.getZ() - REACH, maxZ = center.getZ() + REACH;
		for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
			for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) continue;
				for (BlockEntity be : chunk.getBlockEntities().values()) {
					if (!(be instanceof ChestBlockEntity) && !(be instanceof BarrelBlockEntity)) continue;
					BlockPos p = be.getBlockPos();
					if (p.getX() < minX || p.getX() > maxX || p.getY() < minY || p.getY() > maxY || p.getZ() < minZ || p.getZ() > maxZ) continue;
					if (be.isRemoved() || ((BaseContainerBlockEntity) be).isLocked()) continue;
					Container container = HopperBlockEntity.getContainerAt(level, p);
					if (container == null) continue;
					positions.add(p.immutable());
					containers.add(container);
				}
			}
		}
	}

	private void refillSeeds(Villager farmer, SimpleContainer inventory, List<BlockPos> positions, List<Container> containers) {
		List<Candidate> candidates = new ArrayList<>();
		for (int i = 0; i < positions.size(); i++) {
			candidates.add(new Candidate(i, positions.get(i).distToCenterSqr(farmer.position()), hasSeeds(containers.get(i))));
		}
		OptionalInt choice = ContainerChooser.choose(candidates, REACH);
		if (choice.isEmpty()) return;
		Container source = containers.get(choice.getAsInt());

		List<SeedRefill.Stack> stacks = new ArrayList<>();
		for (int i = 0; i < source.getContainerSize(); i++) {
			ItemStack s = source.getItem(i);
			if (s.is(ItemTags.VILLAGER_PLANTABLE_SEEDS)) stacks.add(new SeedRefill.Stack(i, s.getCount()));
		}
		for (SeedRefill.Take take : SeedRefill.plan(countSeeds(inventory), stacks)) {
			ItemStack peek = source.getItem(take.slot()).copyWithCount(take.amount());
			// Never remove from the container unless the farmer can actually carry it.
			if (peek.isEmpty() || !inventory.canAddItem(peek)) continue;
			ItemStack taken = source.removeItem(take.slot(), take.amount());
			ItemStack rest = inventory.addItem(taken);
			if (!rest.isEmpty()) HopperBlockEntity.addItem(null, source, rest, null);
		}
		source.setChanged();
	}

	private void deposit(Villager farmer, SimpleContainer inventory, List<Move> plan, List<BlockPos> positions, List<Container> containers, long now) {
		List<Candidate> candidates = new ArrayList<>();
		for (int i = 0; i < positions.size(); i++) {
			boolean usable = !fullCache.isCoolingDown(positions.get(i).asLong(), now) && hasRoom(containers.get(i));
			candidates.add(new Candidate(i, positions.get(i).distToCenterSqr(farmer.position()), usable));
		}
		OptionalInt choice = ContainerChooser.choose(candidates, REACH);
		if (choice.isEmpty()) return;
		Container target = containers.get(choice.getAsInt());

		boolean fullNow = false;
		for (Move move : plan) {
			ItemStack held = inventory.getItem(move.slot());
			int amount = Math.min(move.amount(), held.getCount());
			if (amount <= 0) continue;
			ItemStack remainder = HopperBlockEntity.addItem(null, target, held.copyWithCount(amount), null);
			int moved = amount - remainder.getCount();
			if (moved > 0) inventory.removeItem(move.slot(), moved);
			if (!remainder.isEmpty()) fullNow = true;
		}
		if (fullNow) fullCache.markFull(positions.get(choice.getAsInt()).asLong(), now);
	}

	private static int countSeeds(SimpleContainer inventory) {
		int n = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack s = inventory.getItem(i);
			if (s.is(ItemTags.VILLAGER_PLANTABLE_SEEDS)) n += s.getCount();
		}
		return n;
	}

	private static boolean hasSeeds(Container container) {
		for (int i = 0; i < container.getContainerSize(); i++) {
			if (container.getItem(i).is(ItemTags.VILLAGER_PLANTABLE_SEEDS)) return true;
		}
		return false;
	}

	private static boolean hasRoom(Container container) {
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack s = container.getItem(i);
			if (s.isEmpty() || s.getCount() < s.getMaxStackSize()) return true;
		}
		return false;
	}

	private static List<Slot> slotsOf(SimpleContainer inventory) {
		List<Slot> slots = new ArrayList<>();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack s = inventory.getItem(i);
			if (s.isEmpty()) continue;
			VillagerFood food = s.get(DataComponents.VILLAGER_FOOD);
			if (s.is(ItemTags.VILLAGER_PLANTABLE_SEEDS)) {
				slots.add(new Slot(i, ItemClass.SEED, s.getCount(), 0));
			} else if (s.is(Items.WHEAT)) {
				slots.add(new Slot(i, ItemClass.WHEAT, s.getCount(), 0));
			} else if (food != null) {
				slots.add(new Slot(i, ItemClass.FOOD, s.getCount(), food.nutrition()));
			} else {
				slots.add(new Slot(i, ItemClass.OTHER, s.getCount(), 0));
			}
		}
		return slots;
	}
}
