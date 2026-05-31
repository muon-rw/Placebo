package dev.shadowsoffire.placebo.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

/**
 * {@link IPayloadContext} backed by a Fabric server-side play {@link ServerPlayNetworking.Context}.
 * <p>
 * A payload received here was sent by the client, so the {@link #flow()} is {@link PacketFlow#SERVERBOUND}.
 */
class ServerPlayPayloadContext implements IPayloadContext {

    private final ServerPlayNetworking.Context ctx;
    private final ConnectionProtocol protocol;

    ServerPlayPayloadContext(ServerPlayNetworking.Context ctx, ConnectionProtocol protocol) {
        this.ctx = ctx;
        this.protocol = protocol;
    }

    @Override
    public Player player() {
        return this.ctx.player();
    }

    @Override
    public PacketFlow flow() {
        return PacketFlow.SERVERBOUND;
    }

    @Override
    public ConnectionProtocol protocol() {
        return this.protocol;
    }

    @Override
    public Connection connection() {
        return this.ctx.packetContext().get(PacketContext.CONNECTION);
    }

    @Override
    public void reply(CustomPacketPayload payload) {
        this.ctx.responseSender().sendPacket(payload);
    }
}
