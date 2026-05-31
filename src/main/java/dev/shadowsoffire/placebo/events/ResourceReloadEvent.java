package dev.shadowsoffire.placebo.events;

import dev.shadowsoffire.placebo.config.Configuration;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * This event is fired whenever client or server resources are reloaded.
 * It can be used to subscribe to both types without needing register sided handlers, which is useful for reloading {@link Configuration} files.
 * <p>
 * Consumers of this event should not rely on its timing with respect to other {@link PreparableReloadListener}s.
 * <p>
 * Ported from a NeoForge game-bus {@code Event} subclass; Fabric has no global event bus, so this is a plain data carrier
 * paired with a Fabric {@link Event} ({@link #EVENT}). NeoForge's {@code net.neoforged.fml.LogicalSide} has no Fabric
 * analogue and is replaced by Placebo's own {@link LogicalSide}; the carried data and getters are otherwise unchanged.
 */
public class ResourceReloadEvent {

    public static final Event<Callback> EVENT = EventFactory.createArrayBacked(Callback.class, listeners -> event -> {
        for (Callback listener : listeners) {
            listener.onResourceReload(event);
        }
    });

    protected final ResourceManager resourceManager;
    protected final LogicalSide side;

    public ResourceReloadEvent(ResourceManager resourceManager, LogicalSide side) {
        this.resourceManager = resourceManager;
        this.side = side;
    }

    public ResourceManager getResourceManager() {
        return this.resourceManager;
    }

    public LogicalSide getSide() {
        return this.side;
    }

    /**
     * Fabric replacement for {@code NeoForge.EVENT_BUS.post(new ResourceReloadEvent(...))}; posted from {@link PlaceboEvents}.
     */
    public static ResourceReloadEvent post(ResourceManager resourceManager, LogicalSide side) {
        ResourceReloadEvent event = new ResourceReloadEvent(resourceManager, side);
        EVENT.invoker().onResourceReload(event);
        return event;
    }

    @FunctionalInterface
    public interface Callback {
        void onResourceReload(ResourceReloadEvent event);
    }

}
