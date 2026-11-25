package dev.brian.farmhand.client;

import dev.brian.farmhand.Farmhand;
import dev.brian.farmhand.DepositPlanner;
import dev.brian.farmhand.DepositSchedule;
import dev.brian.farmhand.FarmerDepositor;
import dev.brian.farmhand.SeedRefill;
import com.terraformersmc.modmenu.ModMenu;
import com.terraformersmc.modmenu.gui.ModsScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.advancements.predicates.ItemPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.LockCode;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;

/**
 * In-game smoke test: boots the client, joins a world, drives input, takes screenshots.
 * Run with {@code .\gradlew.bat runClientGameTest}; screenshots land in
 * {@code build\run\clientGameTest\screenshots}.
 *
 * <p>Mixin check: both mixin configs use {@code "defaultRequire": 1}, so a mixin that fails to
 * apply crashes the boot, which fails this test.
 */
public class FarmhandClientGameTest implements FabricClientGameTest {
	/** Starting seeds per test farmer; more than the reserve so deposits actually happen. */
	private static final int START_SEEDS = 50;

	private static void check(boolean ok, String message) {
		if (!ok) throw new AssertionError(message);
	}

	private static int count(Container c, Item item) {
		int n = 0;
		for (int i = 0; i < c.getContainerSize(); i++) {
			ItemStack s = c.getItem(i);
			if (s.is(item)) n += s.getCount();
		}
		return n;
	}

	private static void expectCounts(String step, Container farmer, Container box, int bread, int seeds, int wheat, int emeralds) {
		check(count(box, Items.BREAD) == 40 - bread && count(farmer, Items.BREAD) == bread,
			step + ": bread farmer=" + count(farmer, Items.BREAD) + " box=" + count(box, Items.BREAD) + ", expected farmer=" + bread);
		check(count(box, Items.WHEAT_SEEDS) == START_SEEDS - seeds && count(farmer, Items.WHEAT_SEEDS) == seeds,
			step + ": seeds farmer=" + count(farmer, Items.WHEAT_SEEDS) + " box=" + count(box, Items.WHEAT_SEEDS) + ", expected farmer=" + seeds);
		check(count(box, Items.WHEAT) == 20 - wheat && count(farmer, Items.WHEAT) == wheat,
			step + ": wheat farmer=" + count(farmer, Items.WHEAT) + " box=" + count(box, Items.WHEAT) + ", expected farmer=" + wheat);
		check(count(farmer, Items.EMERALD) == emeralds && count(box, Items.EMERALD) == 5 - emeralds,
			step + ": emeralds farmer=" + count(farmer, Items.EMERALD) + " box=" + count(box, Items.EMERALD));
	}

	private static Villager spawnFarmer(ServerLevel level, BlockPos pos) {
		Villager v = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
		check(v != null, "could not create villager");
		v.setVillagerData(v.getVillagerData().withProfession(level.registryAccess(), VillagerProfession.FARMER));
		v.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		v.getInventory().addItem(new ItemStack(Items.BREAD, 40));
		v.getInventory().addItem(new ItemStack(Items.WHEAT_SEEDS, START_SEEDS));
		v.getInventory().addItem(new ItemStack(Items.WHEAT, 20));
		v.getInventory().addItem(new ItemStack(Items.EMERALD, 5));
		return v;
	}

	/** Runs FarmerDepositor against real chests/barrels in the integrated server's overworld. */
	private static void depositScenarios(MinecraftServer server) {
		ServerLevel level = server.overworld();
		check(new ItemStack(Items.WHEAT_SEEDS).is(ItemTags.VILLAGER_PLANTABLE_SEEDS), "wheat seeds not in villager plantable seeds tag");
		BlockPos base = server.getPlayerList().getPlayers().get(0).blockPosition().offset(0, 30, 0);
		// Stone platform so nothing is placed in a void; scenarios are 12 blocks apart.
		for (int x = -4; x < 140; x++) {
			for (int z = -4; z <= 4; z++) level.setBlock(base.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
		}
		long now = 1000;

		// 1. Single chest.
		BlockPos s1 = base.offset(0, 0, 0);
		level.setBlock(s1.east(), Blocks.CHEST.defaultBlockState(), 3);
		Villager f1 = spawnFarmer(level, s1);
		new FarmerDepositor().depositNow(level, f1, now);
		expectCounts("single chest", f1.getInventory(), (Container) level.getBlockEntity(s1.east()), 6, DepositPlanner.SEED_RESERVE, 6, 5);

		// 2. Double chest (LEFT at x, RIGHT at x+1 when facing north).
		BlockPos s2 = base.offset(12, 0, 0);
		level.setBlock(s2.east(), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.TYPE, ChestType.LEFT), 3);
		level.setBlock(s2.east(2), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.TYPE, ChestType.RIGHT), 3);
		Container doubleChest = HopperBlockEntity.getContainerAt(level, s2.east());
		check(doubleChest != null && doubleChest.getContainerSize() == 54, "expected a 54-slot double chest container");
		Villager f2 = spawnFarmer(level, s2);
		new FarmerDepositor().depositNow(level, f2, now);
		expectCounts("double chest", f2.getInventory(), doubleChest, 6, DepositPlanner.SEED_RESERVE, 6, 5);

		// 3a. Barrel.
		BlockPos s3 = base.offset(24, 0, 0);
		level.setBlock(s3.south(), Blocks.BARREL.defaultBlockState(), 3);
		Villager f3 = spawnFarmer(level, s3);
		new FarmerDepositor().depositNow(level, f3, now);
		expectCounts("barrel", f3.getInventory(), (Container) level.getBlockEntity(s3.south()), 6, DepositPlanner.SEED_RESERVE, 6, 5);

		// 3b. Locked chest: nothing may move.
		BlockPos s4 = base.offset(36, 0, 0);
		level.setBlock(s4.east(), Blocks.CHEST.defaultBlockState(), 3);
		BaseContainerBlockEntity locked = (BaseContainerBlockEntity) level.getBlockEntity(s4.east());
		LockCode code = new LockCode(ItemPredicate.Builder.item().of(level.registryAccess().lookupOrThrow(Registries.ITEM), Items.STICK).build());
		locked.applyComponents(DataComponentMap.builder().set(DataComponents.LOCK, code).build(), DataComponentPatch.EMPTY);
		check(locked.isLocked(), "test setup: chest should be locked");
		Villager f4 = spawnFarmer(level, s4);
		new FarmerDepositor().depositNow(level, f4, now);
		expectCounts("locked chest", f4.getInventory(), locked, 40, START_SEEDS, 20, 5);

		// 4a. Container 10 blocks away: out of reach.
		BlockPos s5 = base.offset(48, 0, 0);
		level.setBlock(s5.east(10), Blocks.CHEST.defaultBlockState(), 3);
		Villager f5 = spawnFarmer(level, s5);
		new FarmerDepositor().depositNow(level, f5, now);
		expectCounts("far chest", f5.getInventory(), (Container) level.getBlockEntity(s5.east(10)), 40, START_SEEDS, 20, 5);

		// 4b. Completely full chest: nothing moves, farmer inventory unchanged.
		BlockPos s6 = base.offset(64, 0, 0);
		level.setBlock(s6.east(), Blocks.CHEST.defaultBlockState(), 3);
		Container full = (Container) level.getBlockEntity(s6.east());
		for (int i = 0; i < full.getContainerSize(); i++) full.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
		Villager f6 = spawnFarmer(level, s6);
		new FarmerDepositor().depositNow(level, f6, now);
		expectCounts("full chest", f6.getInventory(), full, 40, START_SEEDS, 20, 5);
		check(count(full, Items.COBBLESTONE) == 27 * 64, "full chest contents changed");

		refillScenarios(level, base, now);
		performanceScenarios(level, base);
	}

	/**
	 * Cost guard: with hundreds of farmers tracked, each is served once per 100 ticks, never more than the
	 * per-tick cap, a farmer with no container scans once then backs off, and one with nothing to do never scans.
	 */
	private static void performanceScenarios(ServerLevel level, BlockPos base) {
		final int farmers = 300, ticks = 300;
		for (int x = -4; x < 140; x++) {
			for (int z = 20; z <= 40; z++) level.setBlock(base.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
		}

		for (int mode = 0; mode < 2; mode++) {
			boolean hungryForSeeds = mode == 0;
			FarmerDepositor d = new FarmerDepositor();
			d.setWorkGate(v -> true);
			for (int i = 0; i < farmers; i++) {
				BlockPos p = base.offset(i % 140, 0, 24 + (i / 140) * 6);
				d.track(bareFarmer(level, p, hungryForSeeds ? 0 : SeedRefill.TARGET));
			}
			check(d.trackedCount() == farmers, "tracked " + d.trackedCount() + " of " + farmers);

			long start = System.nanoTime();
			for (long t = 5000; t < 5000 + ticks; t++) d.tick(t);
			long micros = (System.nanoTime() - start) / 1000;

			String label = hungryForSeeds ? "no-container farmers" : "idle farmers";
			check(d.servedTotal() == (long) farmers * (ticks / DepositSchedule.INTERVAL_TICKS),
				label + ": served " + d.servedTotal() + ", expected each farmer exactly " + (ticks / DepositSchedule.INTERVAL_TICKS) + " times");
			check(d.maxServedInOneTick() <= DepositSchedule.MAX_SERVES_PER_TICK,
				label + ": " + d.maxServedInOneTick() + " farmers served in one tick, cap is " + DepositSchedule.MAX_SERVES_PER_TICK);
			long expectedScans = hungryForSeeds ? farmers : 0;
			check(d.scansTotal() == expectedScans, label + ": scans=" + d.scansTotal() + " expected " + expectedScans);
			check(micros < 250_000, label + ": " + ticks + " ticks of " + farmers + " farmers took " + micros + " us");
			Farmhand.LOGGER.info("perf {}: {} farmers, {} ticks, {} us total ({} us/tick), serves={}, scans={}, max/tick={}",
				label, farmers, ticks, micros, micros / ticks, d.servedTotal(), d.scansTotal(), d.maxServedInOneTick());
		}
	}

	private static Villager bareFarmer(ServerLevel level, BlockPos pos, int seeds) {
		Villager v = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
		check(v != null, "could not create villager");
		v.setVillagerData(v.getVillagerData().withProfession(level.registryAccess(), VillagerProfession.FARMER));
		v.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		if (seeds > 0) v.getInventory().addItem(new ItemStack(Items.WHEAT_SEEDS, seeds));
		return v;
	}

	private static Container placeChest(ServerLevel level, BlockPos pos) {
		level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
		return (Container) level.getBlockEntity(pos);
	}

	/** Seed pull-back: a farmer low on seeds takes them from a nearby container so it can keep replanting. */
	private static void refillScenarios(ServerLevel level, BlockPos base, long now) {
		int target = SeedRefill.TARGET;

		// R1: empty-handed farmer tops up to the target; chest keeps the rest.
		BlockPos p1 = base.offset(76, 0, 0);
		Container c1 = placeChest(level, p1.east());
		for (int i = 0; i < 3; i++) c1.setItem(i, new ItemStack(Items.WHEAT_SEEDS, 64));
		Villager f1 = bareFarmer(level, p1, 0);
		FarmerDepositor d1 = new FarmerDepositor();
		d1.depositNow(level, f1, now);
		check(count(f1.getInventory(), Items.WHEAT_SEEDS) == target, "R1 farmer seeds=" + count(f1.getInventory(), Items.WHEAT_SEEDS) + " expected " + target);
		check(count(c1, Items.WHEAT_SEEDS) == 192 - target, "R1 chest seeds=" + count(c1, Items.WHEAT_SEEDS));
		// ... and a second pass is a no-op: refill and deposit must not fight each other.
		d1.depositNow(level, f1, now + 100);
		check(count(f1.getInventory(), Items.WHEAT_SEEDS) == target && count(c1, Items.WHEAT_SEEDS) == 192 - target,
			"R1 second pass changed seeds: farmer=" + count(f1.getInventory(), Items.WHEAT_SEEDS) + " chest=" + count(c1, Items.WHEAT_SEEDS));

		// R2: a farmer between the refill threshold and the reserve is left alone.
		BlockPos p2 = base.offset(88, 0, 0);
		Container c2 = placeChest(level, p2.east());
		c2.setItem(0, new ItemStack(Items.WHEAT_SEEDS, 64));
		Villager f2 = bareFarmer(level, p2, SeedRefill.REFILL_BELOW + 4);
		new FarmerDepositor().depositNow(level, f2, now);
		check(count(f2.getInventory(), Items.WHEAT_SEEDS) == SeedRefill.REFILL_BELOW + 4 && count(c2, Items.WHEAT_SEEDS) == 64,
			"R2 should not move seeds: farmer=" + count(f2.getInventory(), Items.WHEAT_SEEDS) + " chest=" + count(c2, Items.WHEAT_SEEDS));

		// R3: chest holds fewer seeds than needed: the farmer takes them all.
		BlockPos p3 = base.offset(100, 0, 0);
		Container c3 = placeChest(level, p3.east());
		c3.setItem(5, new ItemStack(Items.WHEAT_SEEDS, 5));
		Villager f3 = bareFarmer(level, p3, 10);
		new FarmerDepositor().depositNow(level, f3, now);
		check(count(f3.getInventory(), Items.WHEAT_SEEDS) == 15 && count(c3, Items.WHEAT_SEEDS) == 0,
			"R3 farmer=" + count(f3.getInventory(), Items.WHEAT_SEEDS) + " chest=" + count(c3, Items.WHEAT_SEEDS));

		// R4: a chest with no seeds changes nothing and nothing breaks.
		BlockPos p4 = base.offset(112, 0, 0);
		Container c4 = placeChest(level, p4.east());
		c4.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
		Villager f4 = bareFarmer(level, p4, 0);
		new FarmerDepositor().depositNow(level, f4, now);
		check(count(f4.getInventory(), Items.WHEAT_SEEDS) == 0 && count(c4, Items.COBBLESTONE) == 64, "R4 unexpected change");

		// R5: a farmer with no room is never made to lose seeds into nowhere: the chest keeps them.
		BlockPos p5 = base.offset(124, 0, 0);
		Container c5 = placeChest(level, p5.east());
		c5.setItem(0, new ItemStack(Items.WHEAT_SEEDS, 64));
		Villager f5 = bareFarmer(level, p5, 0);
		for (int i = 0; i < f5.getInventory().getContainerSize(); i++) f5.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
		new FarmerDepositor().depositNow(level, f5, now);
		check(count(c5, Items.WHEAT_SEEDS) == 64 && count(f5.getInventory(), Items.WHEAT_SEEDS) == 0,
			"R5 seeds were removed from the chest though the farmer is full: chest=" + count(c5, Items.WHEAT_SEEDS));
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!FabricLoader.getInstance().isModLoaded(Farmhand.MOD_ID)) {
			throw new AssertionError(Farmhand.MOD_ID + " is not loaded");
		}

		context.waitForScreen(TitleScreen.class);
		context.waitTicks(60); // let the menu fade-in finish so the screenshot is clean
		context.takeScreenshot("title_screen");

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitTicks(100);
			boolean inWorld = context.computeOnClient(mc -> mc.player != null && mc.level != null);
			if (!inWorld) {
				throw new AssertionError("player/level missing after world creation");
			}
			context.takeScreenshot("in_world");

			// FarmerDepositor: real chests/barrels on the integrated server (assertions throw on failure).
			world.getServer().runOnServer(FarmhandClientGameTest::depositScenarios);

			// Drive real input: open the inventory with the bound key, then close it again.
			context.getInput().pressKey(options -> options.keyInventory);
			context.waitForScreen(AbstractContainerScreen.class);
			context.takeScreenshot("inventory_open");

			context.getInput().pressKey(options -> options.keyInventory);
			context.waitTicks(10);
			context.takeScreenshot("inventory_closed");

			// Mod Menu (dev-only): the mod must be registered in its list and the Mods screen must open.
			boolean listed = context.computeOnClient(mc -> ModMenu.MODS.containsKey(Farmhand.MOD_ID));
			if (!listed) {
				throw new AssertionError(Farmhand.MOD_ID + " is not listed in Mod Menu");
			}
			context.setScreen(() -> new ModsScreen(null));
			context.waitTicks(20);
			context.takeScreenshot("mod_menu");
		}
	}
}
