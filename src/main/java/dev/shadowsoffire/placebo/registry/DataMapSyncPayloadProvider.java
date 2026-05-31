package dev.shadowsoffire.placebo.registry;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.ApiStatus;

import dev.shadowsoffire.placebo.network.IPayloadContext;
import dev.shadowsoffire.placebo.network.PayloadProvider;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;

/**
 * {@link PayloadProvider} for {@link DataMapSyncPayload}, wiring the clientbound data-map sync into Placebo's ported
 * networking framework (the same framework used by every other Placebo payload).
 * <p>
 * The payload is clientbound-only over the PLAY protocol. The server send is driven by {@link DataMapManager} on
 * {@code SYNC_DATA_PACK_CONTENTS}; this provider only declares the type/codec and the client receive handler.
 */
@ApiStatus.Internal
public class DataMapSyncPayloadProvider implements PayloadProvider<DataMapSyncPayload> {

    @Override
    public DataMapSyncPayload.Type<DataMapSyncPayload> getType() {
        return DataMapSyncPayload.TYPE;
    }

    @Override
    public StreamCodec<? super RegistryFriendlyByteBuf, DataMapSyncPayload> getCodec() {
        // DataMapSyncPayload.CODEC is typed for FriendlyByteBuf, which RegistryFriendlyByteBuf extends, so it satisfies the
        // ? super RegistryFriendlyByteBuf bound directly. The per-value network codec (which may need registry access) is
        // applied separately in DataMapManager, not here, so a registry-free buffer codec is correct for the envelope.
        return DataMapSyncPayload.CODEC;
    }

    @Override
    public void handleClient(DataMapSyncPayload msg, IPayloadContext ctx) {
        DataMapManager.receiveSync(msg, ctx.player().registryAccess());
    }

    @Override
    public List<ConnectionProtocol> getSupportedProtocols() {
        return List.of(ConnectionProtocol.PLAY);
    }

    @Override
    public Optional<PacketFlow> getFlow() {
        return Optional.of(PacketFlow.CLIENTBOUND);
    }

    @Override
    public String getVersion() {
        return "1";
    }

    @Override
    public boolean isOptional() {
        // Data maps are not mandatory for a connection on Fabric (no required-channel handshake), so the channel is optional
        // even when a DataMapType declares mandatorySync. See DataMapManager's class doc.
        return true;
    }
}
