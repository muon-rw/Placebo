package dev.shadowsoffire.placebo.registry;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.fabricmc.fabric.api.event.registry.RegistryAttribute;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * Fabric replacement for NeoForge's {@code net.neoforged.neoforge.registries.RegistryBuilder}.
 * <p>
 * Accumulates the configuration of a custom registry (sync flag, default key, bake callbacks, ...) and, on {@link #create()},
 * builds and registers a backing {@link Registry} through Fabric's {@link FabricRegistryBuilder}. The fluent API mirrors
 * NeoForge's builder so that existing {@code DeferredHelper.registry(path, config)} call sites port without changes.
 * <p>
 * Differences from NeoForge are documented per-method; the notable one is {@link #maxId(int)}, which has no Fabric equivalent
 * and is therefore recorded but not enforced.
 *
 * @param <T> The type stored in the registry.
 */
public class RegistryBuilder<T> {

    protected final ResourceKey<? extends Registry<T>> registryKey;

    @Nullable
    protected Identifier defaultKey = null;

    protected boolean sync = false;

    protected int maxId = -1;

    protected final List<BakeCallback<T>> bakeCallbacks = new ArrayList<>();

    /**
     * Creates a new builder for a registry identified by the given key.
     *
     * @param registryKey The key of the registry to build.
     */
    public RegistryBuilder(ResourceKey<? extends Registry<T>> registryKey) {
        this.registryKey = registryKey;
    }

    /**
     * Marks this registry as having a default value at the given location. The backing registry will be a
     * {@code DefaultedRegistry}.
     */
    public RegistryBuilder<T> defaultKey(Identifier key) {
        this.defaultKey = key;
        return this;
    }

    /**
     * Marks this registry as having a default value at the given key. The backing registry will be a
     * {@code DefaultedRegistry}.
     */
    public RegistryBuilder<T> defaultKey(ResourceKey<T> key) {
        this.defaultKey = key.identifier();
        return this;
    }

    /**
     * Sets whether this registry is synced from the server to clients. Maps to {@link RegistryAttribute#SYNCED}.
     */
    public RegistryBuilder<T> sync(boolean sync) {
        this.sync = sync;
        return this;
    }

    /**
     * Records a maximum numerical id for this registry.
     * <p>
     * <b>Fabric difference:</b> Fabric's registry system exposes no equivalent of NeoForge's {@code setMaxId}, so this value
     * is stored for API compatibility but not enforced.
     */
    public RegistryBuilder<T> maxId(int maxId) {
        if (maxId < 0) {
            throw new IllegalArgumentException("maxId must be greater than or equal to zero");
        }
        this.maxId = maxId;
        return this;
    }

    /**
     * Registers a callback fired once the registry is fully populated. See {@link DeferredHelper#bootstrap()} for when this
     * runs.
     */
    public RegistryBuilder<T> onBake(BakeCallback<T> callback) {
        this.bakeCallbacks.add(callback);
        return this;
    }

    /**
     * {@return the bake callbacks registered on this builder}
     */
    protected List<BakeCallback<T>> getBakeCallbacks() {
        return this.bakeCallbacks;
    }

    /**
     * Builds and registers the backing {@link Registry}, applying the configured attributes.
     * <p>
     * The registry is registered into the root registry immediately (Fabric registries are open during mod init), so the
     * returned instance is live and may be populated right away.
     *
     * @return The newly created, registered registry.
     */
    @SuppressWarnings("unchecked")
    public Registry<T> create() {
        ResourceKey<Registry<T>> key = (ResourceKey<Registry<T>>) this.registryKey;
        FabricRegistryBuilder<T, ?> builder = this.defaultKey != null
            ? FabricRegistryBuilder.createDefaulted(key, this.defaultKey)
            : FabricRegistryBuilder.create(key);
        if (this.sync) {
            builder.attribute(RegistryAttribute.SYNCED);
        }
        return builder.buildAndRegister();
    }

}
