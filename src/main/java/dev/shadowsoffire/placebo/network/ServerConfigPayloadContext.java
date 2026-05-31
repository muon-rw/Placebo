package dev.shadowsoffire.placebo.network;

import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

/**
 * {@link IPayloadContext} backed by a Fabric server-side configuration {@link ServerConfigurationNetworking.Context}.
 * No {@link Player} exists during the configuration phase, so {@link #player()} is unsupported.
 */
class ServerConfigPayloadContext implements IPayloadContext {

    private final ServerConfigurationNetworking.Context ctx;

    ServerConfigPayloadContext(ServerConfigurationNetworking.Context ctx) {
        this.ctx = ctx;
    }

    @Override
    public Player player() {
        throw new UnsupportedOperationException("No player is available during the configuration phase.");
    }

    @Override
    public PacketFlow flow() {
        return PacketFlow.SERVERBOUND;
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
