package dev.shadowsoffire.placebo.network;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

/**
 * Mirror of NeoForge's {@code net.neoforged.neoforge.network.handling.IPayloadContext}, exposing the members Placebo and
 * downstream consumers call so that porting a payload handler is an import swap rather than an API change.
 */
public interface IPayloadContext {

    /**
     * Returns the player associated with this connection. On the server this is the sending
     * {@link net.minecraft.server.level.ServerPlayer}; on the client, the receiving
     * {@link net.minecraft.client.player.LocalPlayer}.
     */
    Player player();

    /**
     * The {@link PacketFlow direction} this payload was received in.
     */
    PacketFlow flow();

    /**
     * The protocol the payload was received on.
     */
    ConnectionProtocol protocol();

    /**
     * The raw {@link Connection} backing this context.
     */
    Connection connection();

    /**
     * Schedules work to run on the receiving side's main thread.
     * <p>
     * Fabric note: payload receivers already execute on the main thread, so this runs the task immediately and returns a
     * completed future.
     */
    default CompletableFuture<Void> enqueueWork(Runnable task) {
        task.run();
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Schedules result-producing work on the receiving side's main thread (runs immediately on Fabric).
     */
    default <T> CompletableFuture<T> enqueueWork(Supplier<T> task) {
        return CompletableFuture.completedFuture(task.get());
    }

    /**
     * Sends a payload back along this connection.
     */
    void reply(CustomPacketPayload payload);

    /**
     * Disconnects the connection backing this context with the given reason.
     */
    default void disconnect(Component reason) {
        this.connection().disconnect(reason);
    }

}
