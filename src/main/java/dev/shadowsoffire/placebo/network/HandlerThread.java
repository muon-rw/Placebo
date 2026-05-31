package dev.shadowsoffire.placebo.network;

/**
 * Mirror of NeoForge's {@code net.neoforged.neoforge.network.registration.HandlerThread}, kept so the
 * {@link PayloadProvider} API is unchanged.
 * <p>
 * Fabric note: Fabric play/configuration receivers always fire on the main thread, so {@link #NETWORK} cannot be honored
 * and is treated identically to {@link #MAIN}. The constant is preserved for parity.
 */
public enum HandlerThread {
    MAIN,
    NETWORK;
}
