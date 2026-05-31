package dev.shadowsoffire.placebo.transfer.resource;

import net.minecraft.core.component.DataComponentHolder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;

import java.util.function.Supplier;

/**
 * Mirror of {@code net.neoforged.neoforge.transfer.resource.DataComponentHolderResource}.
 * <p>
 * A {@link Resource} that additionally carries an immutable {@link net.minecraft.core.component.DataComponentMap}
 * (via vanilla {@link DataComponentHolder}) and exposes copy-on-write component mutators.
 * <p>
 * Apotheosis never names this type directly (only {@link dev.shadowsoffire.placebo.transfer.item.ItemResource}). It
 * is mirrored as a thin interface purely so {@code ItemResource implements DataComponentHolderResource<Item>}
 * compiles with the same supertype shape a NeoForge reader expects.
 *
 * <h2>Deviation</h2>
 * NeoForge interposes a {@code RegisteredResource<T>} (adding {@code value()} / {@code is(Predicate)}) between this and
 * {@link Resource}. That extra interface is flattened away here (this extends {@link Resource} directly) to avoid an
 * unforced abstraction no consumer names; {@code ItemResource} still declares {@code value()} directly, so the consumer
 * surface is unchanged.
 *
 * @param <T> the registered object type held by the resource (e.g. {@link net.minecraft.world.item.Item}).
 */
public interface DataComponentHolderResource<T> extends Resource, DataComponentHolder {

    /**
     * {@return whether the resource's component patch is empty (i.e. only default components)}
     */
    boolean isComponentsPatchEmpty();

    /**
     * {@return a copy of this resource with the given patch applied on top of the existing components}
     */
    DataComponentHolderResource<T> withMergedPatch(DataComponentPatch patch);

    /**
     * {@return a copy of this resource with the given component set to {@code data}}
     */
    <D> DataComponentHolderResource<T> with(DataComponentType<D> type, D data);

    /**
     * {@return a copy of this resource with the given component removed}
     */
    DataComponentHolderResource<T> without(DataComponentType<?> type);

    /**
     * {@return the component patch describing how this resource differs from the item's defaults}
     */
    DataComponentPatch getComponentsPatch();

    /**
     * {@return a copy of this resource with the given (deferred) component set to {@code data}}
     */
    default <D> DataComponentHolderResource<T> with(Supplier<? extends DataComponentType<D>> type, D data) {
        return this.with(type.get(), data);
    }

    /**
     * {@return a copy of this resource with the given (deferred) component removed}
     */
    default DataComponentHolderResource<T> without(Supplier<? extends DataComponentType<?>> type) {
        return this.without(type.get());
    }
}
