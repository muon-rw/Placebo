package dev.shadowsoffire.placebo.registry;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import com.google.common.base.Preconditions;
import com.mojang.serialization.Codec;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * Fabric replacement for NeoForge's {@code net.neoforged.neoforge.registries.datamaps.DataMapType}.
 * <p>
 * A data map type describes extra data attached to entries of a registry, loaded from
 * {@code data/<ns>/data_maps/<registry-folder>/<id>.json}. Fabric has no data-map subsystem, so Placebo reimplements it: this
 * type is registered through {@link DeferredHelper#dataMap} into {@link DataMapRegistry}, and {@link DataMapManager} owns the
 * reload listener that loads values into a side table (and the optional per-player client sync for
 * {@linkplain Builder#synced synced} data maps).
 * <p>
 * The generic parameters follow NeoForge's convention: {@code R} is the registry key type (the data map's key type) and
 * {@code T} is the value type.
 *
 * @param <R> The key type of the data map, equal to the element type of the target registry.
 * @param <T> The value type of the data map.
 */
public class DataMapType<R, T> {

    private final ResourceKey<Registry<R>> registryKey;
    private final Identifier id;
    private final Codec<T> codec;
    @Nullable
    private final Codec<T> networkCodec;
    private final boolean mandatorySync;

    DataMapType(ResourceKey<Registry<R>> registryKey, Identifier id, Codec<T> codec, @Nullable Codec<T> networkCodec, boolean mandatorySync) {
        Preconditions.checkArgument(networkCodec != null || !mandatorySync, "Mandatory sync cannot be enabled when the data map isn't synchronized");
        this.registryKey = Objects.requireNonNull(registryKey, "registryKey must not be null");
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.codec = Objects.requireNonNull(codec, "codec must not be null");
        this.networkCodec = networkCodec;
        this.mandatorySync = mandatorySync;
    }

    /**
     * Creates a builder for a data map type.
     *
     * @param id       The identifier of the data map.
     * @param registry The target registry key.
     * @param codec    The codec used to (de)serialize values.
     */
    public static <T, R> Builder<T, R> builder(Identifier id, ResourceKey<Registry<R>> registry, Codec<T> codec) {
        return new Builder<>(registry, id, codec);
    }

    /**
     * {@return the key of the registry this data map targets}
     */
    public ResourceKey<Registry<R>> registryKey() {
        return this.registryKey;
    }

    /**
     * {@return the identifier of this data map}
     */
    public Identifier id() {
        return this.id;
    }

    /**
     * {@return the codec used to read values from datapacks}
     */
    public Codec<T> codec() {
        return this.codec;
    }

    /**
     * {@return the codec used to sync values to clients, or {@code null} if this data map is not synced}
     */
    @Nullable
    public Codec<T> networkCodec() {
        return this.networkCodec;
    }

    /**
     * {@return whether this data map must be synced for a client to connect}
     */
    public boolean mandatorySync() {
        return this.mandatorySync;
    }

    /**
     * Builder mirroring NeoForge's {@code DataMapType.Builder}.
     *
     * @param <T> The value type.
     * @param <R> The registry key (data map key) type.
     */
    public static class Builder<T, R> {

        protected final ResourceKey<Registry<R>> registryKey;
        protected final Identifier id;
        protected final Codec<T> codec;
        @Nullable
        protected Codec<T> networkCodec = null;
        protected boolean mandatorySync = false;

        Builder(ResourceKey<Registry<R>> registryKey, Identifier id, Codec<T> codec) {
            this.registryKey = registryKey;
            this.id = id;
            this.codec = codec;
        }

        /**
         * Marks the data map as synced. A synced data map is sent to clients that support it.
         *
         * @param networkCodec A codec used to sync the values.
         * @param mandatory    Whether the sync is mandatory (a connecting client must support it).
         */
        public Builder<T, R> synced(Codec<T> networkCodec, boolean mandatory) {
            this.mandatorySync = mandatory;
            this.networkCodec = networkCodec;
            return this;
        }

        /**
         * {@return a newly built {@link DataMapType}}
         */
        public DataMapType<R, T> build() {
            return new DataMapType<>(this.registryKey, this.id, this.codec, this.networkCodec, this.mandatorySync);
        }

    }

}
