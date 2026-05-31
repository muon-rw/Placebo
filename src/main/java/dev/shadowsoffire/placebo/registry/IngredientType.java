package dev.shadowsoffire.placebo.registry;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/**
 * Fabric replacement for NeoForge's {@code net.neoforged.neoforge.common.crafting.IngredientType}.
 * <p>
 * Encapsulates the codecs used to (de)serialize a {@link ICustomIngredient}. NeoForge's {@code IngredientType} is a registry
 * object; Fabric's equivalent unit is a {@link CustomIngredientSerializer}, which carries the identifier. Since consumers create
 * the {@code IngredientType} as a {@code static} field <i>before</i> the identifier is known (the identifier is supplied later
 * by {@link DeferredHelper#ingredient}), this class defers building the backing {@link CustomIngredientSerializer} until
 * {@link #register(Identifier)} is called, then caches it so {@link #serializer()} returns the registered instance.
 *
 * @param <T> The custom ingredient type this serializes.
 */
public final class IngredientType<T extends ICustomIngredient> {

    private final MapCodec<T> codec;

    private final StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec;

    @Nullable
    private volatile CustomIngredientSerializer<T> serializer = null;

    /**
     * Creates an ingredient type using the given codecs.
     *
     * @param codec       The codec used to read the ingredient from recipe JSON.
     * @param streamCodec The stream codec used to sync the ingredient (only used when the ingredient is not simple).
     */
    public IngredientType(MapCodec<T> codec, StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.streamCodec = Objects.requireNonNull(streamCodec, "streamCodec");
    }

    /**
     * Creates an ingredient type using the given codec for both JSON and network, mirroring NeoForge's single-arg constructor.
     */
    public IngredientType(MapCodec<T> codec) {
        this(codec, ByteBufCodecs.fromCodecWithRegistries(codec.codec()));
    }

    /**
     * {@return the JSON codec for this ingredient type}
     */
    public MapCodec<T> codec() {
        return this.codec;
    }

    /**
     * {@return the network codec for this ingredient type}
     */
    public StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec() {
        return this.streamCodec;
    }

    /**
     * {@return the backing Fabric {@link CustomIngredientSerializer}}.
     *
     * @throws IllegalStateException if this type has not yet been registered via {@link DeferredHelper#ingredient}.
     */
    public CustomIngredientSerializer<T> serializer() {
        CustomIngredientSerializer<T> s = this.serializer;
        if (s == null) {
            throw new IllegalStateException("IngredientType has not been registered yet; register it through DeferredHelper.ingredient before use");
        }
        return s;
    }

    /**
     * Builds and registers the backing {@link CustomIngredientSerializer} under the given identifier, caching it for
     * {@link #serializer()}. Idempotent per identifier; called by {@link DeferredHelper#ingredient}.
     *
     * @param id The identifier to register the serializer under.
     * @return The registered serializer.
     */
    @SuppressWarnings("unchecked")
    synchronized CustomIngredientSerializer<T> register(Identifier id) {
        if (this.serializer != null) {
            return this.serializer;
        }
        // Fabric's serializer requires an invariant StreamCodec<RegistryFriendlyByteBuf, T>. The wire buffer type is always
        // RegistryFriendlyByteBuf, so narrowing a StreamCodec<? super RegistryFriendlyByteBuf, T> is safe.
        StreamCodec<RegistryFriendlyByteBuf, T> invariantStreamCodec = (StreamCodec<RegistryFriendlyByteBuf, T>) this.streamCodec;
        CustomIngredientSerializer<T> s = new CustomIngredientSerializer<>() {

            @Override
            public Identifier getIdentifier() {
                return id;
            }

            @Override
            public MapCodec<T> getCodec() {
                return IngredientType.this.codec;
            }

            @Override
            public StreamCodec<RegistryFriendlyByteBuf, T> getStreamCodec() {
                return invariantStreamCodec;
            }
        };
        CustomIngredientSerializer.register(s);
        this.serializer = s;
        return s;
    }

}
