# Porting notes: Create Fly: Power Grid (Fabric 26.2) → NeoForge 26.3

Branch `zp5/26.3` of this clone is a Zoompack 5 (ZP5) port of [chaevsfe/PowerGrid](https://github.com/chaevsfe/PowerGrid)
(the unofficial Fabric / Create Fly port of [patryk3211/PowerGrid](https://github.com/patryk3211/PowerGrid)) to
**Minecraft 26.3 on NeoForge 26.3.0.89**, running on ZP5's NeoForge port of Create Fly (`mods/create-fly-neoforge` in
the ZP5 repo, mod id `create`). It starts from upstream branch `fabric-26.2`, commit `71f054a7` (version 0.6.2.3).

Unofficial: do not report issues from this build to the Power Grid, chaevsfe or Create Fly developers.

## Licence

Apache-2.0 (see `LICENSE`, `NOTICE`, `licenses/`). Parts derived from Create are MIT. EJML (Apache-2.0) is bundled
as jar-in-jar, as upstream does. Team Reborn Energy is no longer bundled (replaced by NeoForge's energy capability).
New files carry an Apache-2.0 header naming Zoom Packers; edited upstream files keep their header.

## Route: native NeoForge port, not Sinytra Connector

- There is no Fabric Create Fly for 26.3, and the 26.2 Fabric Create jar does not load under Connector on 26.3
  (version check, then 9 failed mixin patches, then a fatal mixin apply). Power Grid needs Create, so Connector would
  first need a Fabric 26.3 Create Fly.
- Even then, Power Grid links against Create Fly's Fabric transfer classes (`infrastructure/transfer/*`), which ZP5's
  NeoForge Create port replaced with `ResourceHandler` capabilities, and its 26.2 data no longer loads on 26.3.
- Power Grid's Fabric API surface is small (about 25 files beyond `@Environment` annotations), so a native port was
  the cheaper route and gives native NeoForge energy interop.

## Build

```bash
./gradlew build
```

```bash
./gradlew runGameTestServer
```

- ModDevGradle 2.0.148, NeoForge 26.3.0.89, Java 25 (`JAVA_HOME` must point at a JDK 25), Gradle 9.2.1.
- Create is consumed as a local jar: `libs/create-fly-neoforge-6.0.9+neoforge-26.3.jar` (gitignored), copied from the
  ZP5 repo's `mods/create-fly-neoforge/build/libs/` (built from that repo's `neoforge-26.3` at `8da5b8c6`). `libs/`
  also holds Create's `interfaces.json` (interface injection) and its extracted `accesstransformer.cfg`; both are
  applied at compile time, Create applies them itself at runtime. Re-copy all three after rebuilding Create.
- Iris (NeoForge, Modrinth `Ag5S66CF`) is compile-only for `compat/iris`.
- Excluded from the build, not deleted (so rebases on upstream stay clean): `fabric/**` (entrypoints), `compat/rei/**`,
  `compat/viewer/**` (Create REI Viewer), `compat/fabric/**`, `compat/farmersdelight/**` and its mixin config,
  `fabric.mod.json`, `powergrid.accesswidener`.

## Tools (re-run after a rebase)

| Script | Does |
|---|---|
| `tools/port_26_3_mechanical.py src/main/java` | Drops `@Environment(EnvType.X)` and its imports; rewrites classes 26.3 moved (`com.mojang.blaze3d` → `com.mojang.renderpearl`, ...) from `tools/class-moves-26.3.txt` (copied from the Create port) |
| `python -I tools/data_26_3.py src/generated/resources/data src/main/resources/data src/main/resources/assets` | Converts 26.2 data JSON to 26.3 (from the Create port): 76 advancements and 71 loot tables changed, 0 leftovers |
| `python -I tools/check_mixins.py <MC merged jar> <NeoForge universal jar> libs/create-...jar build/classes/java/main` | Static mixin check (from the Create port) |

## Loader and Fabric API replacements

| Fabric | NeoForge 26.3 |
|---|---|
| `main` / `client` entrypoints | `neoforge.PowerGridNeoForge` / `neoforge.client.PowerGridNeoForgeClient` (`@Mod`) |
| `create_plugin` entrypoint | `META-INF/services/com.zurrtum.create.api.registry.CreateRegisterPlugin` → `neoforge.PowerGridCreatePlugin` |
| `FabricLoader` (mod presence, env) | `neoforge.Platform` |
| `FabricRegistryBuilder` (component registry) | `RegistryBuilder` + `NewRegistryEvent` |
| `DynamicRegistries` (component items, wire types, solar biomes) | `NewDatapackRegistryEvent#worldRegistry` |
| `ArgumentTypeRegistry` | `ArgumentTypeInfos.registerByClass` + direct registry registration |
| Networking (`PayloadTypeRegistry`, `Server/ClientPlayNetworking`) | `RegisterPayloadHandlersEvent` / `RegisterClientPayloadHandlersEvent` in `network.PacketSetImpl` |
| `PlayerLookup` | `neoforge.PlayerLookup` (chunk-map watchers) |
| Server lifecycle, tick, level, chunk, player, command, use callbacks | NeoForge game events in `PowerGridEvents.register()` |
| `FULL_CHUNK_STATUS_CHANGE` | `ChunkTicketLevelUpdatedEvent` → `ChunkLevel.fullStatus`, queued and drained at the next level tick as before |
| `PlayerBlockBreakEvents.BEFORE` | `CommonHooks.fireBlockBreak` |
| `FakePlayer`, `FabricCreativeModeTab` | NeoForge `FakePlayer`, vanilla `CreativeModeTab.builder()` |
| Recipe sync for recipe viewers | `OnDatapackSyncEvent#sendRecipes` for `powergrid:` recipe types (when JEI is present) |
| Client: entity renderers, key mappings/categories, block tints, particle providers/groups, HUD element, render pipelines | Queued in `neoforge.client.ClientRegistration`, replayed from the matching mod-bus events |
| `ArmorRenderer` (worn battery) | `BatteryArmorRenderer` is a `RenderLayer` again, added in `EntityRenderersEvent.AddLayers` to players, mannequins and armor stands |
| `LevelRenderEvents.COLLECT_SUBMITS` | `SubmitCustomGeometryEvent` (fired at the end of `LevelRenderer#submitFeatures` with that method's identity `PoseStack`) |
| Client tick / level / connection / tooltip / entity-load events | NeoForge client events in `PowerGridClientEvents.register()` |

### Energy (Team Reborn Energy → NeoForge)

- `EnergyStorage.SIDED` → `Capabilities.Energy.BLOCK` (`EnergyHandler`), `EnergyStorage.ITEM` → `Capabilities.Energy.ITEM`
  with an `ItemAccess` context. Units are unchanged (1 TR energy = 1 FE). Fabric's `SnapshotParticipant` →
  `SnapshotJournal`, `Transaction.openOuter()` → `Transaction.openRoot()`.
- Same exposure as on Fabric: only the **FE inverter** registers the block capability (input face only). The
  **device connector** FE bridge pushes energy into the neighbour's handler with `EnergyHandlerUtil.move`, and the
  **electromagnet** and **portable battery** charge items through their item energy capability.
- `EnergyHandler` has no `supportsInsertion()`; the device connector now treats any neighbour energy handler as a
  bridge target.
- Agreed with the ZP5 Crafts & Additions port: both mods use only NeoForge's energy capability, no shared library.

### Fluids

Create's `FlowableFluid#getFluidType` only knows Create's fluids, so the acid overrides it with its own `FluidType`
(`neoforge.PowerGridFluidTypes`, water-like: swimmable, drownable, no boats).

## Minecraft 26.2 → 26.3 changes handled

- `PushReaction` constants renamed (`DESTROY` → `POPPED`, `IGNORE` → `IGNORE_ENTITY`).
- `ValueInput#contains` removed → `utility.ValueInputs.contains`.
- `Prediction` argument on `drop` / `placeItemBackInInventory` (`SERVER_ONLY`, as Create does); `swing` takes a
  `SwingAnimation`; `onEquippedItemBroken` takes the stack; `Block#playerDestroy` takes `ServerLevel`/`ServerPlayer`.
- `AxeItem` removed → `SawItem extends Item` with `Properties#axe`.
- `PoseStack#mulPose(Quaternionf)` → `rotate`.
- `BakedQuad.MaterialInfo`: `shade=false` → `shadeDirectionOverride=UP`; glint render types are part of the record.
- `Util.getPlatform().openFile` → `Blaze3D.openPath`.
- Advancement criteria: `ContextAwarePredicate` → `Holder<LootItemCondition>`; `placedBlock` takes a `HolderGetter`.
- `ItemInHandRenderer` → `FirstPersonHandsAndItemsRenderer` (render states). `mixin/client/ItemInHandRendererMixin`
  injects at the head of `submitArmWithItem`; Create's mixin wraps the call site, so they don't conflict.
- Render pipelines register through `RegisterRenderPipelinesEvent`.
- NeoForge: `Player#getDestroySpeed(BlockState)` only delegates; `PlayerDrillMixin` targets the `(BlockState, BlockPos)`
  overload that `getDestroyProgress` calls.

## Disabled or not ported

- **Advancement datagen** (`PowerGridAdvancement#save` throws): the shipped JSON is pre-generated and converted.
- **REI / Create REI Viewer** plugins and the tall-block picture-in-picture renderer they used. JEI is a TODO.
- **Farmer's Delight** heater compat (no FD for NeoForge 26.3 in ZP5's pack).
- Upstream's own deferrals (Sable/Simulated, JEI, the circuit designer's client half) stay deferred (`deferred/`).

## Status (2026-10-18)

Checked against MC 26.3, NeoForge 26.3.0.89 and ZP5's Create port at `8da5b8c6`.

- `./gradlew build`: compiles, 81 JUnit solver tests pass (the FML-dependent recipe codec test is excluded, see
  `build.gradle.kts`), `checkJarContainment` passes. Jar: `build/libs/PowerGrid-0.6.2.3+neoforge-26.3-zp5.1.jar`.
- `./gradlew runGameTestServer`: world loads, **5/5 required tests pass** (3 Power Grid, 2 Create):
  - `powergrid:content_registered`: 77 blocks, 119 items, 60 block entities, 5 entities, 116 recipes, 6 wire types,
    29 component items; the special `light_cord_patterning` recipe loads; the acid has its fluid type.
  - `powergrid:creative_source_drives_resistor`: 100 V creative source, two hanging wires, 10 ohm creative resistor
    → 9.98 A at tick 11.
  - `powergrid:fe_inverter_powers_resistor`: 20000 FE inserted through `Capabilities.Energy.BLOCK` on the inverter's
    input face (null on the opposite face); wired to a 10 ohm resistor → 0.40 A, buffer drains to 19898 FE.
- The built jar plus the Create jar, loaded from a `mods/` folder in a scratch MDG project (no source sets), pass
  the same 5 tests and log no warnings or errors, so jar-in-jar EJML and the service file work in a packaged jar.
- `tools/check_mixins.py`: one false positive (`ConfigBaseAccessor$CValueAccessor` on Create's `CValue.value`, which
  exists). `tools/check_recipe_refs.py`: 0 dangling references.
- Upstream data bug fixed: 148 advancement references to `powergrid:crafting//<name>` (double slash, from upstream
  datagen) now point at `powergrid:crafting/<name>`. 26.3 resolves them as holders, so the data pack failed to load.
- **Not run:** the client (rendering, Flywheel visuals, Ponder, GUI screens, the first-person drill/saw/zapper mixin,
  the worn-battery layer, key mappings). It needs the user's OK because it opens a game window.
