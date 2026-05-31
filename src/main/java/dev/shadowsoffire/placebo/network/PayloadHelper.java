package dev.shadowsoffire.placebo.network;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.common.base.Preconditions;

import dev.shadowsoffire.placebo.Placebo;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Central registry and dispatcher for Placebo's {@link PayloadProvider}-based custom payloads.
 * <p>
 * Porting note (NeoForge to Fabric): NeoForge registered payloads from {@code RegisterPayloadHandlersEvent} via
 * {@code NetworkRegistry.register}. Fabric splits this into {@link #bootstrap()} (common init: registers types/codecs
 * with {@link PayloadTypeRegistry} on both sides and wires server receivers) and {@link #bootstrapClient()} (client init:
 * wires client receivers, since {@link ClientPlayNetworking} is client-only). Downstream still calls
 * {@link #registerPayload(PayloadProvider)} before {@link #bootstrap()}.
 */
public class PayloadHelper {

    private static final Map<CustomPacketPayload.Type<?>, PayloadProvider<?>> ALL_PROVIDERS = new HashMap<>();
    private static boolean locked = false;

    /**
     * Registers a payload using a {@link PayloadProvider}. Must be called before {@link #bootstrap()} runs.
     *
     * @param prov An instance of the payload provider.
     */
    public static <T extends CustomPacketPayload> void registerPayload(PayloadProvider<T> prov) {
        Preconditions.checkNotNull(prov);
        synchronized (ALL_PROVIDERS) {
            if (locked) {
                throw new UnsupportedOperationException("Attempted to register a payload provider after registration has finished.");
            }
            if (ALL_PROVIDERS.containsKey(prov.getType())) {
                throw new UnsupportedOperationException("Attempted to register payload provider with duplicate ID: " + prov.getType().id());
            }
            ALL_PROVIDERS.put(prov.getType(), prov);
        }
    }

    /**
     * Common bootstrap. Call once from the common {@link net.fabricmc.api.ModInitializer}.
     * <p>
     * Registers every staged payload's type/codec with {@link PayloadTypeRegistry} and wires server-side receivers.
     * After this runs, no further payloads may be registered via {@link #registerPayload(PayloadProvider)}.
     */
    public static void bootstrap() {
        synchronized (ALL_PROVIDERS) {
            if (locked) {
                return;
            }
            for (PayloadProvider<?> prov : ALL_PROVIDERS.values()) {
                registerType(prov);
                registerServerReceiver(prov);
            }
            locked = true;
        }
    }

    /**
     * Client bootstrap. Call once from the {@link net.fabricmc.api.ClientModInitializer}, after {@link #bootstrap()}.
     * <p>
     * Wires client-side receivers; the payload types are already registered by {@link #bootstrap()}.
     */
    public static void bootstrapClient() {
        synchronized (ALL_PROVIDERS) {
            for (PayloadProvider<?> prov : ALL_PROVIDERS.values()) {
                registerClientReceiver(prov);
            }
        }
    }

    /**
     * Registers the payload's type/codec with the directional {@link PayloadTypeRegistry registries} implied by its
     * declared flow and supported protocols.
     */
    @SuppressWarnings("unchecked")
    private static <T extends CustomPacketPayload> void registerType(PayloadProvider<T> prov) {
        CustomPacketPayload.Type<T> type = prov.getType();
        StreamCodec<? super RegistryFriendlyByteBuf, T> codec = prov.getCodec();
        Optional<PacketFlow> flow = prov.getFlow();
        List<ConnectionProtocol> protocols = prov.getSupportedProtocols();
        Preconditions.checkArgument(!protocols.isEmpty(), "The payload registration for %s must specify at least one valid protocol.", type.id());

        boolean serverbound = flow.isEmpty() || flow.get() == PacketFlow.SERVERBOUND;
        boolean clientbound = flow.isEmpty() || flow.get() == PacketFlow.CLIENTBOUND;

        if (protocols.contains(ConnectionProtocol.PLAY)) {
            if (serverbound) {
                PayloadTypeRegistry.serverboundPlay().register(type, codec);
            }
            if (clientbound) {
                PayloadTypeRegistry.clientboundPlay().register(type, codec);
            }
        }
        if (protocols.contains(ConnectionProtocol.CONFIGURATION)) {
            // Fabric's configuration registries are typed for plain FriendlyByteBuf; Placebo codecs are declared against
            // RegistryFriendlyByteBuf (the NeoForge convention). The cast is safe only if the codec does not need
            // registry access during the configuration phase. No Placebo payload uses configuration; kept for parity.
            StreamCodec<? super FriendlyByteBuf, T> configCodec = (StreamCodec<? super FriendlyByteBuf, T>) codec;
            if (serverbound) {
                PayloadTypeRegistry.serverboundConfiguration().register(type, configCodec);
            }
            if (clientbound) {
                PayloadTypeRegistry.clientboundConfiguration().register(type, configCodec);
            }
        }
    }

    /**
     * Registers server-side receivers (play and/or configuration) for any payload the server may receive.
     */
    private static <T extends CustomPacketPayload> void registerServerReceiver(PayloadProvider<T> prov) {
        Optional<PacketFlow> flow = prov.getFlow();
        // The server receives a payload when it is serverbound, or when the payload is bidirectional.
        boolean receivedOnServer = flow.isEmpty() || flow.get() == PacketFlow.SERVERBOUND;
        if (!receivedOnServer) {
            return;
        }
        List<ConnectionProtocol> protocols = prov.getSupportedProtocols();
        if (protocols.contains(ConnectionProtocol.PLAY)) {
            ServerPlayNetworking.registerGlobalReceiver(prov.getType(), (payload, ctx) -> {
                dispatchServer(prov, payload, new ServerPlayPayloadContext(ctx, ConnectionProtocol.PLAY));
            });
        }
        if (protocols.contains(ConnectionProtocol.CONFIGURATION)) {
            ServerConfigurationNetworking.registerGlobalReceiver(prov.getType(), (payload, ctx) -> {
                dispatchServer(prov, payload, new ServerConfigPayloadContext(ctx));
            });
        }
    }

    /**
     * Registers client-side receivers (play and/or configuration) for any payload the client may receive.
     */
    private static <T extends CustomPacketPayload> void registerClientReceiver(PayloadProvider<T> prov) {
        Optional<PacketFlow> flow = prov.getFlow();
        // The client receives a payload when it is clientbound, or when the payload is bidirectional.
        boolean receivedOnClient = flow.isEmpty() || flow.get() == PacketFlow.CLIENTBOUND;
        if (!receivedOnClient) {
            return;
        }
        List<ConnectionProtocol> protocols = prov.getSupportedProtocols();
        if (protocols.contains(ConnectionProtocol.PLAY)) {
            ClientPlayNetworking.registerGlobalReceiver(prov.getType(), (payload, ctx) -> {
                dispatchClient(prov, payload, new ClientPlayPayloadContext(ctx, ConnectionProtocol.PLAY));
            });
        }
        if (protocols.contains(ConnectionProtocol.CONFIGURATION)) {
            ClientConfigurationNetworking.registerGlobalReceiver(prov.getType(), (payload, ctx) -> {
                dispatchClient(prov, payload, new ClientConfigPayloadContext(ctx));
            });
        }
    }

    /**
     * Validates the received payload then dispatches to {@code handleServer}.
     * <p>
     * NeoForge bounced via {@code context.enqueueWork} per {@link HandlerThread}; Fabric receivers already fire on the
     * main thread, so no bounce is needed and {@link HandlerThread#NETWORK} is treated as {@link HandlerThread#MAIN}.
     */
    private static <T extends CustomPacketPayload> void dispatchServer(PayloadProvider<T> prov, T payload, IPayloadContext ctx) {
        if (!validate(prov, payload, ctx)) {
            return;
        }
        prov.handleServer(payload, ctx);
    }

    private static <T extends CustomPacketPayload> void dispatchClient(PayloadProvider<T> prov, T payload, IPayloadContext ctx) {
        if (!validate(prov, payload, ctx)) {
            return;
        }
        prov.handleClient(payload, ctx);
    }

    /**
     * Re-validates the flow and protocol of an incoming payload, preserving the original NeoForge handler's guard rails.
     */
    private static boolean validate(PayloadProvider<?> prov, CustomPacketPayload payload, IPayloadContext ctx) {
        Optional<PacketFlow> flow = prov.getFlow();
        if (flow.isPresent() && flow.get() != ctx.flow()) {
            Placebo.LOGGER.error("Received a payload {} on the incorrect side.", payload.type().id());
            return false;
        }
        if (!prov.getSupportedProtocols().contains(ctx.protocol())) {
            Placebo.LOGGER.error("Received a payload {} on the incorrect protocol.", payload.type().id());
            return false;
        }
        return true;
    }
}
