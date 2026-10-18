/*
 * Copyright 2025 patryk3211
 * Modified 2026 by chaevsfe for the unofficial Fabric / Create Fly 26.2 port,
 * and by Zoom Packers for the NeoForge 26.3 port.
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

import com.zurrtum.create.api.registry.CreateRegisterPlugin;
import org.patryk3211.powergrid.PowerGrid;
import org.patryk3211.powergrid.collections.ModdedFluids;

/**
 * Create's early registration hook. ZP5's NeoForge Create port finds plugins through
 * {@code META-INF/services/com.zurrtum.create.api.registry.CreateRegisterPlugin} (the Fabric build used the
 * {@code create_plugin} entrypoint) and calls them during vanilla bootstrap, before any mod is constructed.
 */
public final class PowerGridCreatePlugin implements CreateRegisterPlugin {
    private static boolean blocksRegistered;
    private static boolean fluidsRegistered;

    @Override
    public void onBlockRegister() {
        if (blocksRegistered) {
            throw new IllegalStateException("Create invoked Power Grid block registration more than once");
        }
        PowerGrid.registerBlocksEarly();
        blocksRegistered = true;
    }

    @Override
    public void onFluidRegister() {
        if (fluidsRegistered) {
            throw new IllegalStateException("Create invoked Power Grid fluid registration more than once");
        }
        ModdedFluids.register();
        fluidsRegistered = true;
    }

    public static void verifyEarlyRegistrationComplete() {
        if (!blocksRegistered || !fluidsRegistered) {
            throw new IllegalStateException("Create did not invoke Power Grid early registration (blocks=" + blocksRegistered + ", fluids=" + fluidsRegistered + ")");
        }
    }
}
