package dev.shadowsoffire.placebo.util;

import java.nio.file.Path;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Physical environment accessor, replacing NeoForge's {@code net.neoforged.fml.loading.FMLEnvironment}.
 * <p>
 * NeoForge resolves {@code FMLEnvironment.getDist()} at boot; the Fabric analogue is
 * {@link FabricLoader#getEnvironmentType()} (an {@link EnvType}). The method shape ({@link #getDist()},
 * {@link #isClient()}, {@link #isDedicatedServer()}) mirrors the old NeoForge surface so consumers keep the
 * call sites they had on NeoForge.
 */
public class PlaceboEnvironment {

    /**
     * @return The physical environment type of the current process.
     */
    public static EnvType getDist() {
        return FabricLoader.getInstance().getEnvironmentType();
    }

    /**
     * @return True if running in a physical-client environment (the {@code minecraft} client jar is present).
     */
    public static boolean isClient() {
        return getDist() == EnvType.CLIENT;
    }

    /**
     * @return True if running on a physical dedicated server.
     */
    public static boolean isDedicatedServer() {
        return getDist() == EnvType.SERVER;
    }

    /**
     * @return The game (run) directory, replacing NeoForge's {@code FMLPaths.GAMEDIR.get()}.
     */
    public static Path getGameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

}
