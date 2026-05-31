package dev.shadowsoffire.placebo.patreon;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.lwjgl.glfw.GLFW;

import dev.shadowsoffire.placebo.Placebo;
import dev.shadowsoffire.placebo.PlaceboClient;
import dev.shadowsoffire.placebo.patreon.PatreonUtils.PatreonParticleType;
import dev.shadowsoffire.placebo.payloads.PatreonDisablePayload;
import dev.shadowsoffire.placebo.payloads.PatreonDisablePayload.CosmeticType;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

public class TrailsManager {

    static Map<UUID, PatreonParticleType> TRAILS = new HashMap<>();
    public static final KeyMapping TOGGLE = new KeyMapping("placebo.toggleTrails", GLFW.GLFW_KEY_KP_9, PlaceboClient.KEY_CATEGORY);
    public static final Set<UUID> DISABLED = new HashSet<>();

    public static void init() {
        new Thread(() -> {
            Placebo.LOGGER.info("Loading patreon trails data...");
            try {
                URL url = new URI("https://raw.githubusercontent.com/Shadows-of-Fire/Placebo/1.16/PatreonTrails.txt").toURL();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()))) {
                    String s;
                    while ((s = reader.readLine()) != null) {
                        String[] split = s.split(" ", 2);
                        if (split.length != 2) {
                            Placebo.LOGGER.error("Invalid patreon trail entry {} will be ignored.", s);
                            continue;
                        }
                        TRAILS.put(UUID.fromString(split[0]), PatreonParticleType.valueOf(split[1]));
                    }
                    reader.close();
                }
                catch (IOException ex) {
                    Placebo.LOGGER.error("Exception loading patreon trails data!");
                    ex.printStackTrace();
                }
            }
            catch (Exception k) {
                // not possible
            }
            Placebo.LOGGER.info("Loaded {} patreon trails.", TRAILS.size());
            if (TRAILS.size() > 0) {
                // Fabric: NeoForge.EVENT_BUS.register(TrailsManager.class) -> register the particle + key-poll tick callbacks.
                ClientTickEvents.END_CLIENT_TICK.register(mc -> {
                    clientTick();
                    keys();
                });
            }
        }, "Placebo Patreon Trail Loader").start();
    }

    /**
     * Spawns the per-player trail particles each client tick. Replaces NeoForge's {@code ClientTickEvent.Post} handler.
     */
    public static void clientTick() {
        PatreonParticleType t = null;
        if (Minecraft.getInstance().level != null && !Minecraft.getInstance().isPaused()) {
            for (Player player : Minecraft.getInstance().level.players()) {
                if (!player.isInvisible() && player.tickCount * 3 % 2 == 0 && !DISABLED.contains(player.getUUID()) && (t = TRAILS.get(player.getUUID())) != null) {
                    ClientLevel world = (ClientLevel) player.level();
                    RandomSource rand = world.getRandom();
                    ParticleOptions type = t.type.get();
                    world.addParticle(type, player.getX() + rand.nextDouble() * 0.4 - 0.2, player.getY() + 0.1, player.getZ() + rand.nextDouble() * 0.4 - 0.2, 0, 0, 0);
                }
            }
        }
    }

    /**
     * Polls the toggle key each client tick. Replaces NeoForge's {@code InputEvent.Key} handler + {@code TOGGLE.matches}:
     * on Fabric the keybind is registered and drained via {@link KeyMapping#consumeClick()}.
     */
    public static void keys() {
        while (TOGGLE.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() != null) {
                ClientPlayNetworking.send(new PatreonDisablePayload(CosmeticType.TRAILS, mc.player.getUUID()));
            }
        }
    }
}
