package org.patryk3211.powergrid.network;

import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

// NeoForge: payload types and handlers are registered from the mod-bus events below instead of Fabric's
// PayloadTypeRegistry / ServerPlayNetworking / ClientPlayNetworking. Handlers run on the main thread, as on Fabric.
public class PacketSetImpl extends PacketSet {
    private static final List<PacketSetImpl> SETS = new ArrayList<>();

    protected PacketSetImpl(String id, int version,
                            List<Function<FriendlyByteBuf, S2CPacket>> s2cPackets,
                            Object2IntMap<Class<? extends S2CPacket>> s2cTypes,
                            List<Function<FriendlyByteBuf, C2SPacket>> c2sPackets,
                            Object2IntMap<Class<? extends C2SPacket>> c2sTypes) {
        super(id, version, s2cPackets, s2cTypes, c2sPackets, c2sTypes);
        SETS.add(this);
    }

    /** Called from the mod bus ({@code neoforge.PowerGridNeoForge}). */
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        for (PacketSetImpl set : SETS) {
            event.registrar(String.valueOf(set.version))
                    .playToClient(payloadType(set.s2cPacket), PowerGridPayload.STREAM_CODEC)
                    .playToServer(payloadType(set.c2sPacket), PowerGridPayload.STREAM_CODEC, (payload, context) ->
                            set.handleC2SPacket((ServerPlayer) context.player(), payload.toBuffer()));
        }
    }

    /** Called from the client mod bus ({@code neoforge.client.PowerGridNeoForgeClient}). */
    public static void registerClientHandlers(RegisterClientPayloadHandlersEvent event) {
        for (PacketSetImpl set : SETS) {
            event.register(payloadType(set.s2cPacket), (payload, context) ->
                    set.handleS2CPacket(Minecraft.getInstance(), payload.toBuffer()));
        }
    }

    @Override
    public void registerS2CListener() {
        // NeoForge: see registerClientHandlers
    }

    @Override
    public void registerC2SListener() {
        // NeoForge: see registerPayloads
    }

    @Override
    protected void doSendC2S(FriendlyByteBuf buf) {
        ClientPacketDistributor.sendToServer(new PowerGridPayload(payloadType(c2sPacket), copyBytes(buf)));
    }

    @Override
    public void send(Object packet) {
        Minecraft.getInstance().getConnection().send((Packet<?>) packet);
    }

    @Override
    public void sendTo(ServerPlayer player, Object packet) {
        player.connection.send((Packet<?>) packet);
    }

    @Override
    public void sendTo(PlayerSelection selection, Object packet) {
        if (selection instanceof PlayerSelectionImpl impl) {
            for (ServerPlayer player : impl.powergrid$getPlayers())
                player.connection.send((Packet<?>) packet);
        }
    }

    static CustomPacketPayload.Type<PowerGridPayload> payloadType(Identifier id) {
        return new CustomPacketPayload.Type<>(id);
    }

    private static byte[] copyBytes(FriendlyByteBuf buf) {
        byte[] data = new byte[buf.readableBytes()];
        buf.getBytes(buf.readerIndex(), data);
        return data;
    }

    public record PowerGridPayload(CustomPacketPayload.Type<PowerGridPayload> type, byte[] data) implements CustomPacketPayload {
        public static final StreamCodec<RegistryFriendlyByteBuf, PowerGridPayload> STREAM_CODEC = StreamCodec.ofMember(
                PowerGridPayload::write,
                PowerGridPayload::new
        );

        private PowerGridPayload(RegistryFriendlyByteBuf buf) {
            this(payloadType(buf.readIdentifier()), buf.readByteArray());
        }

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeIdentifier(type.id());
            buf.writeByteArray(data);
        }

        FriendlyByteBuf toBuffer() {
            return new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        }
    }

    @ApiStatus.Internal
    public static PacketSet create(String id, int version,
                                   List<Function<FriendlyByteBuf, S2CPacket>> s2cPackets,
                                   Object2IntMap<Class<? extends S2CPacket>> s2cTypes,
                                   List<Function<FriendlyByteBuf, C2SPacket>> c2sPackets,
                                   Object2IntMap<Class<? extends C2SPacket>> c2sTypes) {
        return new PacketSetImpl(id, version, s2cPackets, s2cTypes, c2sPackets, c2sTypes);
    }
}
