package dev.shadowsoffire.placebo.registry;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;

/**
 * Registry and value store for {@link DataMapType data maps}, replacing NeoForge's data-map subsystem (which Fabric lacks).
 * <p>
 * {@link DeferredHelper#dataMap} registers each {@link DataMapType} here. {@link DataMapManager} owns a reload listener that
 * scans {@code data/<ns>/data_maps/<registry-folder>/<id>.json}, decodes values with {@link DataMapType#codec()}, and
 * publishes them via {@link #setValues}. Reads go through {@link #get(DataMapType, Holder)} /
 * {@link #get(DataMapType, ResourceKey)}, replacing NeoForge's {@code Holder#getData(DataMapType)}.
 * <p>
 * This registry only owns <i>registration and storage</i>; the JSON folder convention, condition gating, target/tag
 * resolution, and client sync of values are implemented by {@link DataMapManager}, keeping the
 * {@code DeferredHelper.dataMap(...)} method shape intact here. The same {@link #setValues} entry point is used by the
 * manager both server-side (after a datapack reload) and client-side (on receipt of a {@link DataMapSyncPayload}).
 */
public final class DataMapRegistry {

    /**
     * All registered data map types, in registration order.
     */
    private static final List<DataMapType<?, ?>> TYPES = new CopyOnWriteArrayList<>();

    /**
     * Loaded values, keyed by data map type then by the target entry's {@link ResourceKey}.
     */
    private static final Map<DataMapType<?, ?>, Map<ResourceKey<?>, Object>> VALUES = new HashMap<>();

    private DataMapRegistry() {}

    /**
     * Registers a data map type. Called by {@link DeferredHelper#dataMap}.
     */
    public static void register(DataMapType<?, ?> type) {
        for (DataMapType<?, ?> existing : TYPES) {
            if (existing.registryKey().equals(type.registryKey()) && existing.id().equals(type.id())) {
                throw new IllegalStateException("Duplicate data map registered: " + type.id() + " for registry " + type.registryKey().identifier());
            }
        }
        TYPES.add(type);
    }

    /**
     * {@return an unmodifiable view of all registered data map types}.
     */
    public static List<DataMapType<?, ?>> all() {
        return Collections.unmodifiableList(TYPES);
    }

    /**
     * Publishes the loaded values for a data map type, replacing any previously loaded values for that type. Called by the data
     * subsystem's reload listener.
     *
     * @param type   The data map type.
     * @param values A map from each target entry's {@link ResourceKey} to its loaded value.
     */
    public static <R, T> void setValues(DataMapType<R, T> type, Map<ResourceKey<R>, T> values) {
        Map<ResourceKey<?>, Object> copy = new HashMap<>();
        for (Map.Entry<ResourceKey<R>, T> entry : values.entrySet()) {
            copy.put(entry.getKey(), entry.getValue());
        }
        synchronized (VALUES) {
            VALUES.put(type, copy);
        }
    }

    /**
     * Clears all loaded values for every data map type (e.g. on server stop or before a reload).
     */
    public static void clearValues() {
        synchronized (VALUES) {
            VALUES.clear();
        }
    }

    /**
     * {@return an immutable snapshot of the entry keys with a loaded value for the given data map type}.
     * <p>
     * Used by the data subsystem's sync to iterate the entries that must be sent to clients. The returned set is a copy, so
     * iterating it is safe against a concurrent reload replacing the underlying value map.
     */
    @SuppressWarnings("unchecked")
    public static <R, T> java.util.Set<ResourceKey<R>> keys(DataMapType<R, T> type) {
        synchronized (VALUES) {
            Map<ResourceKey<?>, Object> values = VALUES.get(type);
            if (values == null) {
                return java.util.Set.of();
            }
            java.util.Set<ResourceKey<R>> copy = new java.util.HashSet<>();
            for (ResourceKey<?> key : values.keySet()) {
                copy.add((ResourceKey<R>) key);
            }
            return copy;
        }
    }

    /**
     * {@return the value associated with the given entry key in the data map, or {@code null} if none}.
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public static <R, T> T get(DataMapType<R, T> type, ResourceKey<R> key) {
        synchronized (VALUES) {
            Map<ResourceKey<?>, Object> values = VALUES.get(type);
            return values == null ? null : (T) values.get(key);
        }
    }

    /**
     * {@return the value associated with the given holder in the data map, or {@code null} if the holder is unkeyed or has no
     * value}. This replaces NeoForge's {@code Holder#getData(DataMapType)}.
     */
    @Nullable
    public static <R, T> T get(DataMapType<R, T> type, Holder<R> holder) {
        return holder.unwrapKey().map(key -> get(type, key)).orElse(null);
    }

}
