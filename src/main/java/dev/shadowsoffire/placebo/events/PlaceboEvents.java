package dev.shadowsoffire.placebo.events;

import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.Unit;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Fabric bootstrap wiring for {@link ResourceReloadEvent}.
 * <p>
 * On NeoForge, {@code ResourceReloadEvent} was posted from reload-listener lambdas added per-reload via
 * {@code AddServerReloadListenersEvent}/{@code AddClientReloadListenersEvent}. Fabric registers reload listeners once at
 * init, so this class registers them against {@code fabric-resource-loader-v1} and posts the event from {@code apply}.
 * {@link AnvilLandEvent} needs no bootstrap; it is posted directly from {@code AnvilBlockMixin}.
 */
public class PlaceboEvents {

    /** Mirrors the NeoForge listener id {@code placebo:placebo_reload_event}. */
    public static final Identifier SERVER_RELOAD_LISTENER = Identifier.fromNamespaceAndPath("placebo", "placebo_reload_event");

    /** Mirrors the NeoForge listener id {@code placebo:client_reload_event}. */
    public static final Identifier CLIENT_RELOAD_LISTENER = Identifier.fromNamespaceAndPath("placebo", "client_reload_event");

    /** Call once from the common {@code ModInitializer}. */
    public static void bootstrap() {
        ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(SERVER_RELOAD_LISTENER, new ReloadPoster(LogicalSide.SERVER));
    }

    /** Call once from the {@code ClientModInitializer}. */
    public static void bootstrapClient() {
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(CLIENT_RELOAD_LISTENER, new ReloadPoster(LogicalSide.CLIENT));
    }

    private static class ReloadPoster extends SimplePreparableReloadListener<Unit> {

        private final LogicalSide side;

        private ReloadPoster(LogicalSide side) {
            this.side = side;
        }

        @Override
        protected Unit prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
            return Unit.INSTANCE;
        }

        @Override
        protected void apply(Unit data, ResourceManager resourceManager, ProfilerFiller profiler) {
            ResourceReloadEvent.post(resourceManager, this.side);
        }
    }

}
