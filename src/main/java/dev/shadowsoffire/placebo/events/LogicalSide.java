package dev.shadowsoffire.placebo.events;

/**
 * The logical side of a piece of game logic.
 * <p>
 * Placebo-owned replacement for NeoForge's {@code net.neoforged.fml.LogicalSide}, which has no Fabric analogue. The shape
 * (constants {@link #CLIENT}/{@link #SERVER}, methods {@link #isClient()}/{@link #isServer()}) mirrors that type so
 * consumers of {@link ResourceReloadEvent#getSide()} keep the call surface they had on NeoForge.
 */
public enum LogicalSide {
    CLIENT,
    SERVER;

    public boolean isClient() {
        return this == CLIENT;
    }

    public boolean isServer() {
        return this == SERVER;
    }
}
