package dev.shadowsoffire.placebo.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

/**
 * {@link IPayloadContext} backed by a Fabric client-side configuration {@link ClientConfigurationNetworking.Context}.
 * No {@link Player} exists during the configuration phase, so {@link #player()} is unsupported. Client-only; referenced
 * solely from {@link PayloadHelper#bootstrapClient()}.
 */
class ClientConfigPayloadContext implements IPayloadContext {

    private final ClientConfigurationNetworking.Context ctx;

    ClientConfigPayloadContext(ClientConfigurationNetworking.Context ctx) {
        this.ctx = ctx;
    }

    @Override
    public Player player() {
        throw new UnsupportedOperationException("No player is available during the configuration phase.");
    }

    @Override
    public PacketFlow flow() {
        return PacketFlow.CLIENTBOUND;
    }

    @Override
    public ConnectionProtocol protocol() {
        return ConnectionProtocol.CONFIGURATION;
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
