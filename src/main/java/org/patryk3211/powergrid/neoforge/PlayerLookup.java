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

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The parts of Fabric API's {@code PlayerLookup} Power Grid uses, on vanilla's chunk map. "Tracking" an entity means
 * watching the chunk it is in, which is what NeoForge's own {@code PacketDistributor} offers for block positions.
 */
public final class PlayerLookup {
    private PlayerLookup() {
    }

    public static Collection<ServerPlayer> all(MinecraftServer server) {
        return server.getPlayerList().getPlayers();
    }

    public static Collection<ServerPlayer> tracking(ServerLevel level, ChunkPos pos) {
        return level.getChunkSource().chunkMap.getPlayers(pos, false);
    }

    public static Collection<ServerPlayer> tracking(ServerLevel level, BlockPos pos) {
        return tracking(level, ChunkPos.containing(pos));
    }

    public static Collection<ServerPlayer> tracking(BlockEntity be) {
        if (!(be.getLevel() instanceof ServerLevel level))
            return List.of();
        return tracking(level, be.getBlockPos());
    }

    public static Collection<ServerPlayer> tracking(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level))
            return List.of();
        var players = new ArrayList<>(tracking(level, entity.chunkPosition()));
        if (entity instanceof ServerPlayer self)
            players.remove(self);
        return players;
    }

    public static Collection<ServerPlayer> around(ServerLevel level, Vec3 pos, double radius) {
        double radiusSq = radius * radius;
        return level.players().stream().filter(player -> player.distanceToSqr(pos) <= radiusSq).toList();
    }
}
