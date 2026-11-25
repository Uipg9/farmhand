# Farmhand

Farmer villagers keep replanting, and their surplus crops end up in a chest instead of being wasted.
Vanilla harvesting and replanting are untouched. No new items, blocks or mobs.

**Minecraft 26.3 · Fabric · Java 25**

## How to use it

Place a chest, trapped chest or barrel next to the farmer's composter. When a working farmer is within
4 blocks of it:

- **Surplus goes in.** Anything beyond what the farmer needs for vanilla farming is stored in the container.
- **Seeds come back out.** A farmer holding fewer than 16 seeds pulls seeds from the container and tops up to 32,
  so it can replant right after harvesting. Seeds are only taken if the farmer has room to carry them.

| Item | A farmer keeps |
|---|---|
| Seeds (anything farmers can plant) | 32 (refilled from the container when below 16) |
| Wheat (for baking bread) | 6 |
| Food (bread, etc.) | 24 nutrition points in total, so breeding (12) and food sharing (24) still work |
| Anything else (emeralds, gifts, ...) | never moved |

Locked containers, minecart chests and shulker boxes are ignored. A full container is skipped and not
rescanned for 30 seconds. Farmers that are asleep, trading or not working never deposit. As in vanilla,
farmers cannot farm at all while `mobGriefing` is off.

## Performance

Cost is kept flat on purpose: villagers are spread over 100 buckets and a tick only looks at one of them, at
most 16 farmers are served per tick, containers are found through the block entities of already-loaded chunks
(never a block scan, never a chunk load), and a farmer with no container nearby backs off for 20 seconds. In the
in-game test, 300 farmers cost about 0.03 ms per tick.

## Requirements

- Minecraft 26.3, Fabric Loader 0.20.1 or newer, Fabric API, Java 25.
- All logic is server-side and uses no Mixins, so it should coexist with other farming mods.

## Building

```
.\gradlew.bat build          # compile + unit tests
.\scripts\verify.ps1         # build + unit tests + in-game test (opens a Minecraft window, about 3 minutes)
```

The unit tests cover the pure logic (`DepositPlanner`, `SeedRefill`, `ContainerChooser`, `DepositSchedule`,
`ScanBackoff`). The in-game test (`FarmhandClientGameTest`) checks real chests, barrels, double chests, locked and
full containers, seed refill, and a 300-farmer cost guard. Mod Menu is a dev-only dependency and is never shipped.

## License

CC0-1.0.
