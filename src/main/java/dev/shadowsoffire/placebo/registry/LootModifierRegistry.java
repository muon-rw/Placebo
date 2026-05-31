package dev.shadowsoffire.placebo.registry;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import net.minecraft.resources.Identifier;

/**
 * Registry of global-loot-modifier serializers, replacing NeoForge's
 * {@code NeoForgeRegistries.GLOBAL_LOOT_MODIFIER_SERIALIZERS}.
 * <p>
 * Fabric has no global-loot-modifier registry, so {@link DeferredHelper#lootModifier} records each modifier's
 * {@link MapCodec serializer codec} here, keyed by its identifier. {@link LootModifierManager} then:
 * <ul>
 * <li>builds the JSON dispatch codec via {@link #dispatchCodec()} to load
 * {@code data/<ns>/loot_modifiers/...} entries, and</li>
 * <li>applies the loaded modifiers from a {@code LootTableEvents.MODIFY_DROPS} listener.</li>
 * </ul>
 * This separation keeps registration (this tier) and runtime application ({@link LootModifierManager}) cleanly decoupled
 * while preserving the {@code DeferredHelper.lootModifier(path, codec)} method shape. The manager is wired up by its
 * {@code bootstrap()} from the common {@code ModInitializer}, so modifiers registered here load and apply end-to-end.
 */
public final class LootModifierRegistry {

    private static final Map<Identifier, MapCodec<? extends IGlobalLootModifier>> SERIALIZERS = new ConcurrentHashMap<>();

    private LootModifierRegistry() {}

    /**
     * Registers a global-loot-modifier serializer codec under the given identifier.
     *
     * @param id    The identifier of the modifier's {@code type}.
     * @param codec The serializer codec.
     * @throws IllegalStateException if a serializer is already registered under {@code id}.
     */
    public static void register(Identifier id, MapCodec<? extends IGlobalLootModifier> codec) {
        MapCodec<? extends IGlobalLootModifier> existing = SERIALIZERS.putIfAbsent(id, codec);
        if (existing != null) {
            throw new IllegalStateException("Duplicate global loot modifier serializer registered for id " + id);
        }
    }

    /**
     * {@return the serializer codec registered under {@code id}, or {@code null} if none}.
     */
    public static MapCodec<? extends IGlobalLootModifier> get(Identifier id) {
        return SERIALIZERS.get(id);
    }

    /**
     * {@return an unmodifiable view of all registered serializer codecs}.
     */
    public static Map<Identifier, MapCodec<? extends IGlobalLootModifier>> all() {
        return Collections.unmodifiableMap(SERIALIZERS);
    }

    /**
     * {@return a dispatch {@link Codec} that reads a global loot modifier by its {@code type} field}.
     * <p>
     * The codec dispatches on {@link IGlobalLootModifier#codec()} so each modifier round-trips through its registered
     * serializer, mirroring NeoForge's {@code IGlobalLootModifier.DIRECT_CODEC}.
     */
    public static Codec<IGlobalLootModifier> dispatchCodec() {
        Codec<MapCodec<? extends IGlobalLootModifier>> byName = Identifier.CODEC.flatXmap(
            id -> {
                MapCodec<? extends IGlobalLootModifier> codec = SERIALIZERS.get(id);
                return codec != null
                    ? com.mojang.serialization.DataResult.success(codec)
                    : com.mojang.serialization.DataResult.error(() -> "Unknown loot modifier serializer: " + id);
            },
            codec -> {
                for (Map.Entry<Identifier, MapCodec<? extends IGlobalLootModifier>> entry : SERIALIZERS.entrySet()) {
                    if (entry.getValue() == codec) {
                        return com.mojang.serialization.DataResult.success(entry.getKey());
                    }
                }
                return com.mojang.serialization.DataResult.error(() -> "Unregistered loot modifier serializer");
            });
        return byName.dispatch("type", IGlobalLootModifier::codec, Function.identity());
    }

}
