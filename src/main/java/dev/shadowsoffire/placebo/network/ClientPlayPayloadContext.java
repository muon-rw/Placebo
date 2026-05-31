package dev.shadowsoffire.placebo.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

/**
 * {@link IPayloadContext} backed by a Fabric client-side play {@link ClientPlayNetworking.Context}. Client-only;
 * referenced solely from {@link PayloadHelper#bootstrapClient()}.
 */
class ClientPlayPayloadContext implements IPayloadContext {

    private final ClientPlayNetworking.Context ctx;
    private final ConnectionProtocol protocol;

    ClientPlayPayloadContext(ClientPlayNetworking.Context ctx, ConnectionProtocol protocol) {
        this.ctx = ctx;
        this.protocol = protocol;
    }

    @Override
    public Player player() {
        return this.ctx.player();
    }

    @Override
    public PacketFlow flow() {
        return PacketFlow.CLIENTBOUND;
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
