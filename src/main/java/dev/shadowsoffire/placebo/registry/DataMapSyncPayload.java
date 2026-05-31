package dev.shadowsoffire.placebo.registry;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jetbrains.annotations.ApiStatus;

import dev.shadowsoffire.placebo.Placebo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Clientbound payload that carries the loaded values of a single {@link DataMapType synced data map} to a connecting
 * player, replacing the implicit data-map sync that NeoForge performs as part of its datapack-content sync.
 * <p>
 * Each entry of {@link #encodedValues} maps a target registry entry's {@link Identifier} to the {@link DataMapType#networkCodec()
 * network-codec}-encoded bytes of that entry's value. The value bytes are decoded lazily on the client (in
 * {@link DataMapManager}) rather than inside this payload's {@link StreamCodec}, so the payload codec itself never needs
 * registry access. This mirrors the deferred-decode strategy of Placebo's dynamic-registry {@code Content} payload, where
 * decoding may depend on registries that are populated only on the receiving main thread.
 *
 * @param registryKey   The identifier of the target registry (the {@code <registry-folder>} the data map lives under).
 * @param dataMapId     The identifier of the data map itself.
 * @param encodedValues A map from each target entry's identifier to its network-codec-encoded value bytes.
 */
@ApiStatus.Internal
public record DataMapSyncPayload(Identifier registryKey, Identifier dataMapId, Map<Identifier, byte[]> encodedValues) implements CustomPacketPayload {

    public static final Type<DataMapSyncPayload> TYPE = new Type<>(Placebo.loc("data_map_sync"));

    /**
     * Registry-free stream codec. The per-entry value bytes are opaque here; the matching {@link DataMapType#networkCodec()}
     * decodes them on the client with the client's registry access (see {@link DataMapManager}).
     */
    public static final StreamCodec<FriendlyByteBuf, DataMapSyncPayload> CODEC = StreamCodec.of(DataMapSyncPayload::write, DataMapSyncPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, DataMapSyncPayload payload) {
        buf.writeIdentifier(payload.registryKey);
        buf.writeIdentifier(payload.dataMapId);
        buf.writeVarInt(payload.encodedValues.size());
        for (Map.Entry<Identifier, byte[]> entry : payload.encodedValues.entrySet()) {
            buf.writeIdentifier(entry.getKey());
            buf.writeByteArray(entry.getValue());
        }
    }

    private static DataMapSyncPayload read(FriendlyByteBuf buf) {
        Identifier registryKey = buf.readIdentifier();
        Identifier dataMapId = buf.readIdentifier();
        int size = buf.readVarInt();
        Map<Identifier, byte[]> values = new LinkedHashMap<>(size);
        for (int i = 0; i < size; i++) {
            Identifier key = buf.readIdentifier();
            byte[] bytes = buf.readByteArray();
            values.put(key, bytes);
        }
        return new DataMapSyncPayload(registryKey, dataMapId, values);
    }
}
