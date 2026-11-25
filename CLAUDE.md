# Farmhand: working notes for Claude

- Minecraft 26.3 Fabric mod. Java 25. Unobfuscated: use Mojang names, never add mappings/yarn.
- TDD: write a failing JUnit test in `src/test` first, then implement. In-game checks go in
  `src/client/.../FarmhandClientGameTest.java`.
- Shell is Windows PowerShell: use `.\gradlew.bat`. Filter Gradle output to errors only.
- Verify with `.\scripts\verify.ps1` (build + unit tests + client gametest).
- Never kill processes to stop the client: `runClientGameTest` exits by itself.
- Mod Menu is dev-only (`localRuntime`); do not make it a hard dependency.

## Farmhand specifics

- Vanilla farmers: `HarvestFarmland` breaks the crop (`level.destroyBlock(pos, true, body)`), the drop is an
  item entity, and the villager picks it up (`Villager#wantsToPickUp`; needs `CanPickUpLoot`, which `/summon`
  with custom NBT drops, so add `CanPickUpLoot:1b` to test villagers). Inventory is `Villager#getInventory()`
  (a `SimpleContainer`). Seeds = `ItemTags.VILLAGER_PLANTABLE_SEEDS`; food = `DataComponents.VILLAGER_FOOD`
  (`net.minecraft.world.food.VillagerFood`). Breeding needs 12 food points, farmers share food at 24.
- We never touch harvesting or replanting: `FarmerDepositor` runs from `END_SERVER_TICK` over villagers tracked
  via `ServerEntityEvents`, no Mixin. Villagers sit in 100 buckets by entity id, one bucket per tick, at most
  `DepositSchedule.MAX_SERVES_PER_TICK` served per tick, containers found via loaded chunks' block entities,
  `ScanBackoff` after an empty scan. Keep it cheap: no per-tick scans, no chunk loads.
- Pure logic stays Minecraft-free and unit-tested: `DepositPlanner` (reserves), `SeedRefill` (pull seeds back
  below 16, top up to the 32 deposit reserve; keep `SeedRefill.TARGET == DepositPlanner.SEED_RESERVE` or the
  two fight), `ContainerChooser`, `DepositSchedule`, `ScanBackoff`. Only `FarmerDepositor` uses Minecraft types.
- Minecraft sources for 26.3: run `.\gradlew.bat genSources`; the jar lands in
  `.gradle\loom-cache\minecraftMaven\net\minecraft\minecraft-common-*\26.3\*-sources.jar` of the project.
- Headless soak testing: a datapack plus `.\gradlew.bat runServer` in a throwaway copy works; set
  `function-permission-level=4`, scheduled functions suppress output (use `say`), and summoned villagers need
  `CanPickUpLoot:1b`.
- Not done / ideas: farmers walking to the chest (today they only act when already within `REACH`),
  a config file.
