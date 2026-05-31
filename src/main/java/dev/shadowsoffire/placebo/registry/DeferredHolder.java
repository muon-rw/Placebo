package dev.shadowsoffire.placebo.registry;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

import com.mojang.datafixers.util.Either;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;

/**
 * Fabric replacement for NeoForge's {@code net.neoforged.neoforge.registries.DeferredHolder}.
 * <p>
 * A {@link DeferredHolder} is a lazily-resolved reference to a registry object identified by a {@link ResourceKey registry key}
 * and an {@link Identifier name}. It implements both {@link Holder} and {@link Supplier} (matching the NeoForge type) so that it
 * is a drop-in replacement at every call site: {@link #value()} / {@link #get()} return the registered object, and all
 * {@link Holder} methods are forwarded to the live {@link Holder.Reference} once the target registry has been populated.
 * <p>
 * Unlike NeoForge, Fabric registers eagerly during mod init, so by the time consumers dereference a handle the underlying
 * object normally already exists. The handle resolves the backing {@link Holder.Reference} on first use and caches it. The
 * resolved reference is fetched from the root registry ({@link BuiltInRegistries#REGISTRY}) for the stored registry key, which
 * covers every built-in registry that {@link DeferredHelper} targets.
 *
 * @param <R> The type of the target registry.
 * @param <T> The type of the held object.
 */
public class DeferredHolder<R, T extends R> implements Holder<T>, Supplier<T> {

    /**
     * The key of the registry that holds this object.
     */
    protected final ResourceKey<? extends Registry<R>> registryKey;

    /**
     * The resource location identifying this object within its registry.
     */
    protected final Identifier id;

    /**
     * The resolved delegate. {@code null} until first resolution, then cached for the lifetime of the handle.
     */
    private Holder.@Nullable Reference<T> delegate;

    protected DeferredHolder(ResourceKey<? extends Registry<R>> registryKey, Identifier id) {
        this.registryKey = Objects.requireNonNull(registryKey, "registryKey");
        this.id = Objects.requireNonNull(id, "id");
    }

    /**
     * Creates a new {@link DeferredHolder} targeting the object with the given {@code id} in the registry identified by
     * {@code registryKey}.
     *
     * @param registryKey The key of the registry that holds the object.
     * @param id          The name of the object.
     * @return A new lazily-resolved handle.
     */
    public static <R, T extends R> DeferredHolder<R, T> create(ResourceKey<? extends Registry<R>> registryKey, Identifier id) {
        return new DeferredHolder<>(registryKey, id);
    }

    /**
     * Creates a new {@link DeferredHolder} targeting the object identified by the given {@code key}.
     *
     * @param key The key of the object.
     * @return A new lazily-resolved handle.
     */
    public static <R, T extends R> DeferredHolder<R, T> create(ResourceKey<R> key) {
        return new DeferredHolder<>(ResourceKey.createRegistryKey(key.registry()), key.identifier());
    }

    /**
     * {@return the registry key targeted by this handle}
     */
    public ResourceKey<? extends Registry<R>> getRegistryKey() {
        return this.registryKey;
    }

    /**
     * {@return the resource location identifying this object}
     */
    public Identifier getId() {
        return this.id;
    }

    /**
     * {@return the {@link ResourceKey} of this object within its registry}
     */
    public ResourceKey<T> getKey() {
        return ResourceKey.create(castKey(this.registryKey), this.id);
    }

    @SuppressWarnings("unchecked")
    private static <R, T extends R> ResourceKey<Registry<T>> castKey(ResourceKey<? extends Registry<R>> key) {
        return (ResourceKey<Registry<T>>) (ResourceKey<?>) key;
    }

    /**
     * {@return the held value}
     *
     * @throws IllegalStateException if the object has not been registered.
     */
    @Override
    public T get() {
        return this.value();
    }

    /**
     * {@return the held value}
     *
     * @throws IllegalStateException if the object has not been registered.
     */
    @Override
    public T value() {
        return this.resolve().value();
    }

    /**
     * {@return {@code true} if the underlying object has been registered and this handle can be dereferenced}
     */
    public boolean isBound() {
        Holder.Reference<T> ref = this.resolveOrNull();
        return ref != null && ref.isBound();
    }

    /**
     * Resolves and returns the backing {@link Holder.Reference}, throwing if the object is not yet registered.
     */
    protected Holder.Reference<T> resolve() {
        Holder.Reference<T> ref = this.resolveOrNull();
        if (ref == null) {
            throw new IllegalStateException("Trying to access unbound value: " + this.id + " in registry " + this.registryKey.identifier());
        }
        return ref;
    }

    /**
     * Resolves the backing {@link Holder.Reference}, returning {@code null} if the target registry or object does not (yet) exist.
     */
    @SuppressWarnings("unchecked")
    protected Holder.@Nullable Reference<T> resolveOrNull() {
        if (this.delegate == null) {
            Registry<R> registry = (Registry<R>) BuiltInRegistries.REGISTRY.getValue(this.registryKey.identifier());
            if (registry == null) {
                return null;
            }
            Optional<? extends Holder.Reference<? extends R>> opt = registry.get(this.id);
            this.delegate = (Holder.Reference<T>) opt.orElse(null);
        }
        return this.delegate;
    }

    // --- Holder<T> delegation ---

    @Override
    public boolean areComponentsBound() {
        return this.resolve().areComponentsBound();
    }

    @Override
    public boolean is(Identifier key) {
        return this.id.equals(key);
    }

    @Override
    public boolean is(ResourceKey<T> key) {
        return this.getKey().equals(key);
    }

    @Override
    public boolean is(Predicate<ResourceKey<T>> predicate) {
        return predicate.test(this.getKey());
    }

    @Override
    public boolean is(TagKey<T> tag) {
        return this.resolve().is(tag);
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean is(Holder<T> holder) {
        return this.resolve().is(holder);
    }

    @Override
    public Stream<TagKey<T>> tags() {
        return this.resolve().tags();
    }

    @Override
    public DataComponentMap components() {
        return this.resolve().components();
    }

    @Override
    public Either<ResourceKey<T>, T> unwrap() {
        return this.resolve().unwrap();
    }

    @Override
    public Optional<ResourceKey<T>> unwrapKey() {
        return Optional.of(this.getKey());
    }

    @Override
    public Kind kind() {
        return Kind.REFERENCE;
    }

    @Override
    public boolean canSerializeIn(HolderOwner<T> owner) {
        return this.resolve().canSerializeIn(owner);
    }

    @Override
    public String getRegisteredName() {
        return this.id.toString();
    }

    /**
     * {@return the bound tags for the underlying reference}
     */
    public Set<TagKey<T>> getTags() {
        return this.resolve().tags().collect(java.util.stream.Collectors.toSet());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof DeferredHolder<?, ?> other)) {
            return false;
        }
        return this.registryKey.identifier().equals(other.registryKey.identifier()) && this.id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.registryKey.identifier(), this.id);
    }

    @Override
    public String toString() {
        return "DeferredHolder{" + this.registryKey.identifier() + " / " + this.id + "}";
    }

}
