package dev.shadowsoffire.placebo.patreon;

import java.util.Locale;

import org.apache.commons.lang3.text.WordUtils;

import dev.shadowsoffire.placebo.patreon.PatreonUtils.PatreonParticleType;
import dev.shadowsoffire.placebo.patreon.PatreonUtils.WingType;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Cosmetic preview cycler used during development.
 * <p>
 * NeoForge fired this from {@code @EventBusSubscriber(Dist.CLIENT)} + {@code PlayerTickEvent.Post}; on Fabric it is a
 * client-tick callback over the local player. Both {@link #PARTICLES} and {@link #WINGS} are {@code false}, so the body
 * never runs (dormant) - the port is faithful but does nothing while both flags are off.
 */
@SuppressWarnings("deprecation")
public class PatreonPreview {

    public static final boolean PARTICLES = false;
    public static final boolean WINGS = false;

    private static int counter = 0;

    /** Call once from the {@code ClientModInitializer}. Replaces NeoForge's {@code @EventBusSubscriber} auto-registration. */
    public static void bootstrapClient() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.player != null) {
                tick(mc.player);
            }
        });
    }

    public static void tick(Player player) {
        if (player.level().isClientSide()) {
            if (player.tickCount >= 200) {
                if (player.tickCount % 150 == 0) {
                    Minecraft mc = Minecraft.getInstance();
                    if (PARTICLES) {
                        PatreonParticleType[] arr = PatreonParticleType.values();
                        PatreonParticleType p = arr[counter++ % arr.length];
                        Component type = Component.literal(WordUtils.capitalize(p.name().toLowerCase(Locale.ROOT).replace('_', ' ')));
                        mc.gui.setTimes(0, 40, 20);
                        mc.gui.setSubtitle(type);
                        mc.gui.setTitle(Component.literal(""));
                        TrailsManager.TRAILS.put(player.getUUID(), p);
                    }
                    else if (WINGS) {
                        WingType[] arr = WingType.values();
                        WingType p = arr[counter++ % arr.length];
                        Component type = Component.literal(WordUtils.capitalize(p.name().toLowerCase(Locale.ROOT).replace('_', ' ')));
                        mc.gui.setTimes(0, 40, 20);
                        mc.gui.setSubtitle(type);
                        mc.gui.setTitle(Component.literal(""));
                        WingsManager.WINGS.put(player.getUUID(), p);
                    }
                }
            }
        }
    }

}
