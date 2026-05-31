package dev.shadowsoffire.placebo.datagen;

import org.jetbrains.annotations.ApiStatus;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/**
 * Fired during datagen to allow mods to register {@link FieldOrderingFactory} instances.
 * <p>
 * Listeners can register factories that customize the JSON field ordering for objects written by
 * {@code DataProvider.saveStable}. This is the preferred replacement for mutating
 * {@link net.minecraft.data.DataProvider#FIXED_ORDER_FIELDS} directly, since registered factories can be scoped by
 * output path or JSON content via {@link FilteredOrderingFactory}.
 * <p>
 * Ported from a NeoForge mod-bus {@code Event}; Fabric has no event bus, so this is a plain data carrier paired with a
 * Fabric {@link Event} ({@link #EVENT}). It is fired once, lazily, by {@link FieldOrderingFactory.Impl#getComparatorFor}
 * the first time a comparator is requested (see {@link FieldOrderingFactory.Impl} for why it can't fire eagerly).
 */
public class RegisterFieldOrderingsEvent {

    public static final Event<Callback> EVENT = EventFactory.createArrayBacked(Callback.class, listeners -> event -> {
        for (Callback listener : listeners) {
            listener.onRegisterFieldOrderings(event);
        }
    });

    @ApiStatus.Internal
    public RegisterFieldOrderingsEvent() {}

    /**
     * Registers a {@link FieldOrderingFactory}. See {@link FieldOrderingFactory#forType} and
     * {@link FieldOrderingFactory#forSubtypedObject} for common factory builders.
     */
    public void register(FieldOrderingFactory factory) {
        // TODO: Maybe a priority? Currently hard for things to win and it's winner-takes-all atm.
        FieldOrderingFactory.register(factory);
    }

    /**
     * Fires {@link #EVENT}, mirroring the NeoForge {@code ModLoader.postEvent(new RegisterFieldOrderingsEvent())} call.
     */
    public static RegisterFieldOrderingsEvent post() {
        RegisterFieldOrderingsEvent event = new RegisterFieldOrderingsEvent();
        EVENT.invoker().onRegisterFieldOrderings(event);
        return event;
    }

    @FunctionalInterface
    public interface Callback {
        void onRegisterFieldOrderings(RegisterFieldOrderingsEvent event);
    }

}
