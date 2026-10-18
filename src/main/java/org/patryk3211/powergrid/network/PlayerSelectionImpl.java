package org.patryk3211.powergrid.network;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.patryk3211.powergrid.neoforge.PlayerLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Predicate;

public class PlayerSelectionImpl extends PlayerSelection {
    private static MinecraftServer currentServer;

    static {
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> currentServer = event.getServer());
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> currentServer = null);
    }

    /**
     * No-op: calling this forces this class's static initializer to run (registering the
     * SERVER_STARTED/STOPPED listeners above) before any server can possibly start. Without
     * an explicit early reference, this class only loads on first actual use - e.g. via
     * {@link #of} from a player-join hook - which happens after SERVER_STARTED has already
     * fired, permanently missing it and leaving currentServer null for the whole session.
     */
    public static void init() {
    }

    private final Collection<ServerPlayer> players;

    Collection<ServerPlayer> powergrid$getPlayers() {
        return players;
    }

    private PlayerSelectionImpl(Collection<ServerPlayer> players) {
        this.players = players;
    }

    @Override
    public void accept(Identifier id, FriendlyByteBuf buffer) {
        byte[] data = new byte[buffer.readableBytes()];
        buffer.getBytes(buffer.readerIndex(), data);
        PacketSetImpl.PowerGridPayload payload = new PacketSetImpl.PowerGridPayload(PacketSetImpl.payloadType(id), data);
        for (ServerPlayer player : players)
            PacketDistributor.sendToPlayer(player, payload);
    }

    public static PlayerSelection all() {
        if (currentServer == null)
            return new PlayerSelectionImpl(Collections.emptyList());
        return new PlayerSelectionImpl(PlayerLookup.all(currentServer));
    }

    public static PlayerSelection allWith(Predicate<ServerPlayer> condition) {
        if (currentServer == null)
            return new PlayerSelectionImpl(Collections.emptyList());
        return new PlayerSelectionImpl(PlayerLookup.all(currentServer).stream().filter(condition).toList());
    }

    public static PlayerSelection of(ServerPlayer player) {
        return new PlayerSelectionImpl(Collections.singleton(player));
    }

    public static PlayerSelection tracking(Entity entity) {
        return new PlayerSelectionImpl(PlayerLookup.tracking(entity));
    }

    public static PlayerSelection trackingWith(Entity entity, Predicate<ServerPlayer> condition) {
        return new PlayerSelectionImpl(PlayerLookup.tracking(entity).stream().filter(condition).toList());
    }

    public static PlayerSelection tracking(BlockEntity be) {
        return new PlayerSelectionImpl(PlayerLookup.tracking(be));
    }

    public static PlayerSelection tracking(ServerLevel level, BlockPos pos) {
        return new PlayerSelectionImpl(PlayerLookup.tracking(level, pos));
    }

    public static PlayerSelection trackingAndSelf(ServerPlayer player) {
        ArrayList<ServerPlayer> players = new ArrayList<>(PlayerLookup.tracking(player));
        players.add(player);
        return new PlayerSelectionImpl(players);
    }
}
