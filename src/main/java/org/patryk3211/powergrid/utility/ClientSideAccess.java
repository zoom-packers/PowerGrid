/*
 * Copyright 2025 patryk3211
 * Modified 2026 by chaevsfe for the unofficial Fabric / Create Fly 26.2 port.
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
package org.patryk3211.powergrid.utility;

import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

/**
 * The client's state, typed as common classes. Common code reaches the client only through here: the client types
 * (ClientLevel, LocalPlayer) stay inside this class, so verifying a common class on a dedicated server never loads
 * them. ZP5 port: callers that read {@code Minecraft.getInstance()} directly crashed the dedicated server.
 */
public class ClientSideAccess {
    public static Level world() {
        return Minecraft.getInstance().level;
    }

    public static Player player() {
        return Minecraft.getInstance().player;
    }

    public static HitResult getHitResult() {
        return Minecraft.getInstance().hitResult;
    }

    /** The registries the server sent this client. */
    public static RegistryAccess registryAccess() {
        return Minecraft.getInstance().getConnection().registryAccess();
    }

    public static void setSectionDirtyWithNeighbors(int x, int y, int z) {
        var level = Minecraft.getInstance().level;
        if (level != null)
            level.setSectionDirtyWithNeighbors(x, y, z);
    }

    public static boolean hasShiftDown() {
        return Minecraft.getInstance().hasShiftDown();
    }
}
