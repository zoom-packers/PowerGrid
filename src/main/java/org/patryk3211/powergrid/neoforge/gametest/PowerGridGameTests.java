/*
 * Copyright 2026 Zoom Packers (NeoForge 26.3 port of Create: Power Grid)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.patryk3211.powergrid.neoforge.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.patryk3211.powergrid.PowerGrid;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.collections.ModdedItems;
import org.patryk3211.powergrid.electricity.creative.CreativeResistorBlockEntity;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.HangingWireEntity;

import java.lang.reflect.Field;
import java.util.function.Consumer;

/**
 * In-world checks for the NeoForge port, run with {@code ./gradlew runGameTestServer}. They use Create's empty
 * 9x7x5 test structure, which ships in ZP5's Create port.
 */
public final class PowerGridGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, PowerGrid.MOD_ID);
    private static final Identifier STRUCTURE = Identifier.fromNamespaceAndPath("create", "gametest/empty_9x7x5");

    private static final ResourceKey<Consumer<GameTestHelper>> SOURCE_DRIVES_RESISTOR = key("creative_source_drives_resistor");
    private static final ResourceKey<Consumer<GameTestHelper>> FE_INVERTER_POWERS_RESISTOR = key("fe_inverter_powers_resistor");
    private static final ResourceKey<Consumer<GameTestHelper>> CONTENT_REGISTERED = key("content_registered");

    static {
        FUNCTIONS.register(SOURCE_DRIVES_RESISTOR.identifier().getPath(), () -> PowerGridGameTests::creativeSourceDrivesResistor);
        FUNCTIONS.register(FE_INVERTER_POWERS_RESISTOR.identifier().getPath(), () -> PowerGridGameTests::feInverterPowersResistor);
        FUNCTIONS.register(CONTENT_REGISTERED.identifier().getPath(), () -> PowerGridGameTests::contentRegistered);
    }

    private PowerGridGameTests() {
    }

    private static ResourceKey<Consumer<GameTestHelper>> key(String path) {
        return ResourceKey.create(Registries.TEST_FUNCTION, PowerGrid.asResource(path));
    }

    public static void register(IEventBus modBus) {
        FUNCTIONS.register(modBus);
        modBus.addListener(PowerGridGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(PowerGrid.asResource("default"));
        for (var test : new ResourceKey[]{SOURCE_DRIVES_RESISTOR, FE_INVERTER_POWERS_RESISTOR, CONTENT_REGISTERED}) {
            @SuppressWarnings("unchecked")
            ResourceKey<Consumer<GameTestHelper>> function = (ResourceKey<Consumer<GameTestHelper>>) test;
            event.registerTest(function.identifier(), new FunctionGameTestInstance(function, new TestData<>(environment, STRUCTURE, 400, 0, true)));
        }
    }

    private static long count(net.minecraft.core.Registry<?> registry) {
        return registry.keySet().stream().filter(id -> id.getNamespace().equals(PowerGrid.MOD_ID)).count();
    }

    /**
     * Power Grid's blocks, items, block entities, entities and recipes are registered and loaded, the acid has its
     * NeoForge fluid type, and the three datapack registries (wire types, component items, solar biomes) loaded.
     */
    private static void contentRegistered(GameTestHelper helper) {
        var level = helper.getLevel();
        var access = level.registryAccess();
        long blocks = count(net.minecraft.core.registries.BuiltInRegistries.BLOCK);
        long items = count(net.minecraft.core.registries.BuiltInRegistries.ITEM);
        long blockEntities = count(net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE);
        long entities = count(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE);
        long recipes = level.getServer().getRecipeManager().getRecipes().stream()
                .filter(r -> r.id().identifier().getNamespace().equals(PowerGrid.MOD_ID)).count();
        long wires = access.lookupOrThrow(org.patryk3211.powergrid.electricity.wire.registry.WireRegistry.KEY).listElementIds().count();
        long components = access.lookupOrThrow(org.patryk3211.powergrid.circuits.components.ComponentRegistry.ITEM_REGISTRY_KEY).listElementIds().count();
        long solarBiomes = access.lookupOrThrow(org.patryk3211.powergrid.electricity.solarpanel.registry.SolarBiomeRegistry.KEY).listElementIds().count();
        String state = blocks + " blocks, " + items + " items, " + blockEntities + " block entities, " + entities + " entities, "
                + recipes + " recipes, " + wires + " wire types, " + components + " component items, " + solarBiomes + " solar biome overrides";
        helper.assertTrue(blocks > 50 && items > 50 && blockEntities > 20 && entities > 0 && recipes > 50 && wires > 0 && components > 0, state);
        var cordRecipe = level.getServer().getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, PowerGrid.asResource("crafting/light_cord_patterning")));
        helper.assertTrue(cordRecipe.isPresent() && cordRecipe.get().value().getSerializer() == org.patryk3211.powergrid.collections.ModdedRecipeTypes.STRING_LIGHT_CORD_SERIALIZER,
                "special recipe powergrid:crafting/light_cord_patterning did not load with its serializer");
        helper.assertTrue(org.patryk3211.powergrid.collections.ModdedFluids.ACID.getFluidType() == org.patryk3211.powergrid.neoforge.PowerGridFluidTypes.ACID.get(),
                "acid fluid type");
        PowerGrid.LOGGER.info("content_registered passed: {}", state);
        helper.succeed();
    }

    private static void floor(GameTestHelper helper) {
        for (int x = 0; x < 9; x++)
            for (int z = 0; z < 5; z++)
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
    }

    /** Spawns a copper hanging wire between two block terminals, the way a player's wire placement does. */
    private static void wire(GameTestHelper helper, BlockPos pos1, int terminal1, BlockPos pos2, int terminal2) {
        var level = helper.getLevel();
        var entity = HangingWireEntity.create(level,
                new BlockWireEndpoint(helper.absolutePos(pos1), terminal1),
                new BlockWireEndpoint(helper.absolutePos(pos2), terminal2),
                ModdedItems.WIRE.asStack(), 0.01f);
        helper.assertTrue(level.tryAddFreshEntityWithPassengers(entity), "wire entity was not added");
    }

    private static double current(ResistorBlockEntity resistor) {
        try {
            Field field = ResistorBlockEntity.class.getDeclaredField("wire");
            field.setAccessible(true);
            return ((ElectricWire) field.get(resistor)).current();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * A creative voltage source set to 100 V feeds a 10 ohm creative resistor through two hanging wires. The
     * simulated current must settle at about 10 A.
     */
    private static void creativeSourceDrivesResistor(GameTestHelper helper) {
        floor(helper);
        BlockPos source = new BlockPos(2, 1, 2);
        BlockPos resistor = new BlockPos(6, 1, 2);
        helper.setBlock(source, ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
        helper.setBlock(resistor, ModdedBlocks.CREATIVE_RESISTOR.get());

        helper.runAfterDelay(5, () -> {
            wire(helper, source, 0, resistor, 0);
            wire(helper, source, 1, resistor, 1);
            helper.getBlockEntity(source, CreativeSourceBlockEntity.class).setValue(100f);
            helper.getBlockEntity(resistor, CreativeResistorBlockEntity.class).setValue(10.0);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getTick() > 10, "waiting for the circuit");
            double current = Math.abs(current(helper.getBlockEntity(resistor, CreativeResistorBlockEntity.class)));
            helper.assertTrue(Math.abs(current - 10.0) < 0.5, "resistor current " + current + " A, expected about 10 A");
            PowerGrid.LOGGER.info("creative_source_drives_resistor passed at tick {}: {} A", helper.getTick(), current);
        });
    }

    /**
     * Forge Energy goes in through NeoForge's energy capability on the FE inverter's input face (the capability is
     * absent on other faces), and the inverter turns it into a voltage that drives a resistor. The resistor must
     * carry current and the inverter's buffer must drain.
     */
    private static void feInverterPowersResistor(GameTestHelper helper) {
        floor(helper);
        BlockPos inverter = new BlockPos(2, 1, 2);
        BlockPos resistor = new BlockPos(6, 1, 2);
        helper.setBlock(inverter, ModdedBlocks.FE_INVERTER.get());
        helper.setBlock(resistor, ModdedBlocks.CREATIVE_RESISTOR.get());

        var level = helper.getLevel();
        var absolute = helper.absolutePos(inverter);
        Direction input = level.getBlockState(absolute).getValue(BlockStateProperties.FACING);
        long[] inserted = {0};

        helper.runAfterDelay(5, () -> {
            helper.assertTrue(level.getCapability(Capabilities.Energy.BLOCK, absolute, input.getOpposite()) == null,
                    "FE inverter exposes energy on a face other than its input " + input);
            var energy = level.getCapability(Capabilities.Energy.BLOCK, absolute, input);
            helper.assertTrue(energy != null, "FE inverter has no energy capability on its input face " + input);
            try (var tx = Transaction.openRoot()) {
                inserted[0] = energy.insert(Integer.MAX_VALUE, tx);
                tx.commit();
            }
            helper.assertTrue(inserted[0] > 0 && energy.getAmountAsLong() == inserted[0],
                    "inserted " + inserted[0] + " FE, inverter holds " + energy.getAmountAsLong());
            helper.getBlockEntity(resistor, CreativeResistorBlockEntity.class).setValue(10.0);
            wire(helper, inverter, 0, resistor, 0);
            wire(helper, inverter, 1, resistor, 1);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getTick() > 10, "waiting for the circuit");
            var energy = level.getCapability(Capabilities.Energy.BLOCK, absolute, input);
            double current = Math.abs(current(helper.getBlockEntity(resistor, CreativeResistorBlockEntity.class)));
            long stored = energy.getAmountAsLong();
            helper.assertTrue(current > 0.01 && stored < inserted[0],
                    "resistor current " + current + " A, inverter holds " + stored + " of " + inserted[0] + " FE");
            PowerGrid.LOGGER.info("fe_inverter_powers_resistor passed at tick {}: {} A, {} of {} FE left", helper.getTick(), current, stored, inserted[0]);
        });
    }
}
