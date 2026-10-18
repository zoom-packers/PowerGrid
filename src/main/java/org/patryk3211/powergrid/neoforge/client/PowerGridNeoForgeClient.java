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
package org.patryk3211.powergrid.neoforge.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.patryk3211.powergrid.PowerGrid;
import org.patryk3211.powergrid.PowerGridClient;
import org.patryk3211.powergrid.network.PacketSetImpl;
import org.patryk3211.powergrid.registrate.client.RegistrateClient;

/**
 * NeoForge client entrypoint, replacing the Fabric {@code client} entrypoint ({@code fabric.PowerGridClientFabric}).
 * NeoForge constructs it right after {@code PowerGridNeoForge}, before {@code Minecraft} exists.
 */
@Mod(value = PowerGrid.MOD_ID, dist = Dist.CLIENT)
public final class PowerGridNeoForgeClient {
    public PowerGridNeoForgeClient(IEventBus modBus) {
        ClientRegistration.register(modBus);
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, PacketSetImpl::registerClientHandlers);
        // Runs right after PowerGridNeoForge's registration, still inside the first RegisterEvent and before the
        // client registration events (key mappings, renderers, particles, ...) that ClientRegistration replays into
        modBus.addListener(EventPriority.LOWEST, RegisterEvent.class, event -> initOnce());
    }

    private static boolean initialized;

    private static void initOnce() {
        if (initialized)
            return;
        initialized = true;
        RegistrateClient.flush(PowerGrid.REGISTRATE);
        PowerGridClient.initClient();
    }
}
