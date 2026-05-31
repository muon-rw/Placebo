package dev.shadowsoffire.placebo.util;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

/**
 * Accessor shim for the current {@link MinecraftServer}, replacing NeoForge's {@code ServerLifecycleHooks.getCurrentServer()}.
 * Fabric has no global server accessor, so the value is cached from {@link ServerLifecycleEvents}.
 */
public class PlaceboServer {

    // Volatile: lifecycle callbacks may run on a different thread than readers (e.g. client render thread).
    @Nullable
    private static volatile MinecraftServer currentServer = null;

    /**
     * @return The currently running server, or {@code null} if none is running.
     */
    @Nullable
    public static MinecraftServer getCurrentServer() {
        return currentServer;
    }

    /**
     * Registers the lifecycle listeners that maintain the cached server reference. Call once during mod initialization.
     */
    public static void bootstrap() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> currentServer = server);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> currentServer = server);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> currentServer = null);
    }

}
