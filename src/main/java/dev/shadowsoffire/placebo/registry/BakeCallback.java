package dev.shadowsoffire.placebo.registry;

import net.minecraft.core.Registry;

/**
 * Fabric replacement for NeoForge's {@code net.neoforged.neoforge.registries.callback.BakeCallback}.
 * <p>
 * Fired once a custom registry has finished all registration (i.e. is fully populated and about to be frozen). Used by
 * consumers to build derived caches, such as a sorted view of all registered values.
 *
 * @param <T> The type stored in the registry.
 */
@FunctionalInterface
public interface BakeCallback<T> {

    /**
     * Called when the registry has been fully populated.
     *
     * @param registry The registry.
     */
    void onBake(Registry<T> registry);

}
