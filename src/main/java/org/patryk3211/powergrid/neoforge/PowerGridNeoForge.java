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
package org.patryk3211.powergrid.neoforge;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.NewDatapackRegistryEvent;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;
import org.patryk3211.powergrid.PowerGrid;
import org.patryk3211.powergrid.circuits.components.ComponentRegistry;
import org.patryk3211.powergrid.collections.ModdedBlockEntities;
import org.patryk3211.powergrid.electricity.solarpanel.registry.SolarBiomeEntry;
import org.patryk3211.powergrid.electricity.solarpanel.registry.SolarBiomeRegistry;
import org.patryk3211.powergrid.electricity.wire.registry.WireItemEntry;
import org.patryk3211.powergrid.electricity.wire.registry.WireRegistry;
import org.patryk3211.powergrid.neoforge.gametest.PowerGridGameTests;
import org.patryk3211.powergrid.network.PacketSetImpl;

/**
 * NeoForge entrypoint, replacing the Fabric {@code main} entrypoint ({@code fabric.PowerGridFabric}). Create's early
 * registration has already called {@link PowerGridCreatePlugin} during vanilla bootstrap; the rest of Power Grid's
 * content is registered straight into the vanilla registries here, as Create itself does on NeoForge.
 */
@Mod(PowerGrid.MOD_ID)
public final class PowerGridNeoForge {
    public PowerGridNeoForge(IEventBus modBus) {
        PowerGridCreatePlugin.verifyEarlyRegistrationComplete();
        // Vanilla registries are frozen again by the time mods are constructed (Create registers its own content at
        // BuiltInRegistries#freeze during bootstrap). NeoForge unfreezes every registry while it fires RegisterEvent,
        // so the rest of Power Grid's direct registrations run at the start of the first one, as the Fabric
        // ModInitializer did before Fabric API froze the registries.
        modBus.addListener(EventPriority.HIGHEST, RegisterEvent.class, event -> initOnce());

        PowerGridFluidTypes.register(modBus);
        modBus.addListener(PowerGridNeoForge::newRegistries);
        modBus.addListener(PowerGridNeoForge::newDatapackRegistries);
        modBus.addListener(PowerGridNeoForge::registerCapabilities);
        modBus.addListener(RegisterPayloadHandlersEvent.class, PacketSetImpl::registerPayloads);
        PowerGridGameTests.register(modBus);
    }

    private static boolean initialized;

    private static void initOnce() {
        if (initialized)
            return;
        initialized = true;
        PowerGrid.init();
    }

    /** Fabric's {@code FabricRegistryBuilder#buildAndRegister} for the circuit component registry. */
    private static void newRegistries(NewRegistryEvent event) {
        ComponentRegistry.REGISTRY = event.create(new RegistryBuilder<>(ComponentRegistry.REGISTRY_KEY).sync(true));
    }

    /** Fabric's {@code DynamicRegistries.register/registerSynced}. */
    private static void newDatapackRegistries(NewDatapackRegistryEvent event) {
        event.worldRegistry(ComponentRegistry.ITEM_REGISTRY_KEY, ComponentRegistry.ITEM_CODEC, ComponentRegistry.ITEM_CODEC);
        event.worldRegistry(SolarBiomeRegistry.KEY, SolarBiomeEntry.CODEC);
        event.worldRegistry(WireRegistry.KEY, WireItemEntry.CODEC, WireItemEntry.CODEC);
    }

    /**
     * Team Reborn Energy's {@code EnergyStorage.SIDED} became NeoForge's energy capability. Only the FE inverter
     * accepts energy from other mods, as on Fabric; the device connector's FE bridge pushes into its neighbour.
     */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModdedBlockEntities.FE_INVERTER.get(),
                (be, side) -> be.getEnergyStorage(side));
    }
}
