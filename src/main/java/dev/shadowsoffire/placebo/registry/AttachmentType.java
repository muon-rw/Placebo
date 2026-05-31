package dev.shadowsoffire.placebo.registry;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import dev.shadowsoffire.placebo.Placebo;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/**
 * Fabric replacement for NeoForge's {@code net.neoforged.neoforge.attachment.AttachmentType}.
 * <p>
 * Wraps a {@linkplain net.fabricmc.fabric.api.attachment.v1.AttachmentType Fabric attachment type} and exposes a builder whose
 * fluent surface mirrors NeoForge's, so that {@code DeferredHelper.attachment(...)} call sites port unchanged. Instances are
 * created and registered through {@link DeferredHelper#attachment}; the wrapped Fabric type is registered eagerly at build time
 * (Fabric has no registration event phase).
 * <p>
 * <b>Subsystem boundary:</b> the read/write side of attachments (the equivalent of NeoForge's {@code getData}/{@code setData})
 * is provided by the capability subsystem against Fabric's {@link AttachmentTarget}. Use {@link #fabric()} to obtain the
 * underlying Fabric type for those calls.
 * <p>
 * <b>Behavioral notes vs NeoForge:</b>
 * <ul>
 * <li>{@link Builder#serialize(MapCodec, Predicate)}: the {@code shouldSerialize} predicate is a NeoForge write-skipping
 * optimization. Fabric's codec-based persistence always writes the value, so the predicate is accepted but not enforced. This
 * affects on-disk size only, never loaded values.</li>
 * <li>{@link Builder#copyHandler}: Fabric provides no per-type copy override. Fabric copies persistent attachments by
 * re-serializing (matching NeoForge's default handler) and otherwise copies the value reference; a custom handler is accepted
 * for source compatibility but not applied. To keep this divergence from being completely silent, supplying a non-{@code null}
 * cloner logs a one-time {@link Placebo#LOGGER WARN} naming the offending attachment id at build time. The only known consumer
 * supplies the identity handler, which Fabric's default {@link Builder#copyOnDeath()} behavior already reproduces.</li>
 * </ul>
 *
 * @param <T> The type of the attached data.
 */
public final class AttachmentType<T> {

    /**
     * Guards the one-time {@link Builder#copyHandler} divergence warning so the (potentially many) attachment types that
     * call it do not spam the log. The first attachment id that requests an unsupported cloner is named; the rest are
     * silently ignored, exactly as before.
     */
    private static final AtomicBoolean WARNED_COPY_HANDLER = new AtomicBoolean(false);

    private final net.fabricmc.fabric.api.attachment.v1.AttachmentType<T> fabric;

    private AttachmentType(net.fabricmc.fabric.api.attachment.v1.AttachmentType<T> fabric) {
        this.fabric = fabric;
    }

    /**
     * {@return the underlying Fabric attachment type}, used by the capability subsystem to read and write attachment data on a
     * {@link AttachmentTarget}.
     */
    public net.fabricmc.fabric.api.attachment.v1.AttachmentType<T> fabric() {
        return this.fabric;
    }

    /**
     * {@return the identifier uniquely identifying this attachment type}
     */
    public Identifier id() {
        return this.fabric.identifier();
    }

    /**
     * Creates a builder for an attachment type with a holder-independent default value.
     *
     * @param defaultValueSupplier A supplier for a new default value of this attachment type.
     */
    public static <T> Builder<T> builder(Supplier<T> defaultValueSupplier) {
        Objects.requireNonNull(defaultValueSupplier, "defaultValueSupplier");
        return new Builder<>(holder -> defaultValueSupplier.get());
    }

    /**
     * Creates a builder for an attachment type whose default value may inspect its holder.
     * <p>
     * Note that the Fabric initializer provides no holder; the function is invoked with {@code null}. See
     * {@link IAttachmentHolder}.
     *
     * @param defaultValueConstructor A constructor for a new default value of this attachment type.
     */
    public static <T> Builder<T> builder(Function<IAttachmentHolder, T> defaultValueConstructor) {
        Objects.requireNonNull(defaultValueConstructor, "defaultValueConstructor");
        return new Builder<>(defaultValueConstructor);
    }

    /**
     * Builds and registers a Placebo {@link AttachmentType} backed by Fabric's attachment registry.
     *
     * @param id      The identifier of the attachment type.
     * @param builder The configured builder.
     * @return The registered Placebo attachment type.
     */
    static <T> AttachmentType<T> buildAndRegister(Identifier id, Builder<T> builder) {
        if (builder.customCopyHandler && WARNED_COPY_HANDLER.compareAndSet(false, true)) {
            // The cloner passed to Builder#copyHandler cannot be honored on Fabric (no per-type copy override). The
            // divergence is otherwise silent, so surface it once with the offending id rather than letting behavior differ
            // from NeoForge without any trace. See the class-level notes for the full caveat.
            Placebo.LOGGER.warn("AttachmentType '{}' supplied a custom copyHandler, which Fabric does not support; the cloner is ignored. " +
                "Persistent attachments are copied by re-serialization and others by reference, which may diverge from NeoForge.", id);
        }
        net.fabricmc.fabric.api.attachment.v1.AttachmentType<T> fabric = AttachmentRegistry.create(id, fabricBuilder -> {
            fabricBuilder.initializer(() -> builder.defaultValueConstructor.apply(null));
            if (builder.persistenceCodec != null) {
                fabricBuilder.persistent(builder.persistenceCodec);
            }
            if (builder.copyOnDeath) {
                fabricBuilder.copyOnDeath();
            }
            if (builder.streamCodec != null) {
                fabricBuilder.syncWith(builder.streamCodec, AttachmentSyncPredicate.all());
            }
        });
        return new AttachmentType<>(fabric);
    }

    /**
     * Builder mirroring NeoForge's {@code AttachmentType.Builder}, translating to Fabric's
     * {@link AttachmentRegistry.Builder} at build time.
     *
     * @param <T> The type of the attached data.
     */
    public static final class Builder<T> {

        private final Function<IAttachmentHolder, T> defaultValueConstructor;

        @Nullable
        private Codec<T> persistenceCodec = null;

        @Nullable
        private StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec = null;

        private boolean copyOnDeath = false;

        private boolean customCopyHandler = false;

        private Builder(Function<IAttachmentHolder, T> defaultValueConstructor) {
            this.defaultValueConstructor = defaultValueConstructor;
        }

        /**
         * Requests that this attachment be persisted to disk, using the given {@link MapCodec}.
         */
        public Builder<T> serialize(MapCodec<T> codec) {
            Objects.requireNonNull(codec, "codec");
            if (this.persistenceCodec != null) {
                throw new IllegalStateException("Serializer already set");
            }
            this.persistenceCodec = codec.codec();
            return this;
        }

        /**
         * Requests that this attachment be persisted to disk, using the given {@link MapCodec}.
         * <p>
         * The {@code shouldSerialize} predicate is accepted for source compatibility but not enforced on Fabric (see the
         * class-level notes).
         */
        public Builder<T> serialize(MapCodec<T> codec, Predicate<? super T> shouldSerialize) {
            return this.serialize(codec);
        }

        /**
         * Requests that this attachment be persisted to disk, using the given {@link Codec}.
         */
        public Builder<T> serialize(Codec<T> codec) {
            Objects.requireNonNull(codec, "codec");
            if (this.persistenceCodec != null) {
                throw new IllegalStateException("Serializer already set");
            }
            this.persistenceCodec = codec;
            return this;
        }

        /**
         * Requests that this attachment persist when a player respawns or when a living entity is converted.
         */
        public Builder<T> copyOnDeath() {
            this.copyOnDeath = true;
            return this;
        }

        /**
         * Accepted for source compatibility; not applied on Fabric (see the class-level notes).
         * <p>
         * The parameter mirrors NeoForge's {@code IAttachmentCopyHandler<T>} so that existing
         * {@code copyHandler((value, holder, provider) -> ...)} call sites port unchanged. Because Fabric has no per-type copy
         * override, a non-{@code null} {@code cloner} is silently dropped; to keep the divergence from NeoForge from being
         * completely invisible, supplying one causes a one-time {@link Placebo#LOGGER WARN} (naming the attachment id) to be
         * logged when the type is {@linkplain AttachmentType#buildAndRegister built}.
         */
        public Builder<T> copyHandler(IAttachmentCopyHandler<T> cloner) {
            if (cloner != null) {
                this.customCopyHandler = true;
            }
            return this;
        }

        /**
         * Requests that this attachment be synchronized to all clients that receive the holding object, using the given
         * stream codec.
         */
        public Builder<T> sync(StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec) {
            Objects.requireNonNull(streamCodec, "streamCodec");
            this.streamCodec = streamCodec;
            return this;
        }

        /**
         * Builds the attachment type without registering it. {@link DeferredHelper} calls
         * {@link AttachmentType#buildAndRegister} instead, which builds and registers in one step.
         *
         * @deprecated Build through {@link DeferredHelper#attachment} so the type is registered with a stable identifier.
         */
        @Deprecated
        public AttachmentType<T> build() {
            throw new UnsupportedOperationException("Placebo attachment types must be registered through DeferredHelper.attachment, which supplies the identifier");
        }

    }

    /**
     * Mirror of NeoForge's {@code net.neoforged.neoforge.attachment.IAttachmentCopyHandler}, kept so the
     * {@link Builder#copyHandler} call sites stay source-compatible. Not invoked on Fabric (see the class-level notes).
     *
     * @param <T> The type of the attached data.
     */
    @FunctionalInterface
    public interface IAttachmentCopyHandler<T> {

        T copy(T value, IAttachmentHolder holder, HolderLookup.Provider provider);

    }

}
