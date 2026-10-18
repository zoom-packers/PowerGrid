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

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.patryk3211.powergrid.PowerGrid;

import java.util.function.Supplier;

/**
 * NeoForge needs a {@link FluidType} for every fluid. Fabric had none, so the acid's properties follow the water-like
 * block it was registered with ({@code Properties.ofFullCopy(Blocks.WATER)}): swimmable, drownable, no boats.
 */
public final class PowerGridFluidTypes {
    private static final DeferredRegister<FluidType> TYPES = DeferredRegister.create(NeoForgeRegistries.FLUID_TYPES, PowerGrid.MOD_ID);

    public static final Supplier<FluidType> ACID = TYPES.register("acid", () -> new FluidType(FluidType.Properties.create()
            .descriptionId("fluid.powergrid.acid")
            .canExtinguish(false)
            .supportsBoating(false)
            .canDrown(true)
            .canSwim(true)
            .density(1100)
            .viscosity(1000)));

    private PowerGridFluidTypes() {
    }

    static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }
}
