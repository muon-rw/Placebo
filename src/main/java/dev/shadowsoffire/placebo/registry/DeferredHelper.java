package dev.shadowsoffire.placebo.registry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import dev.shadowsoffire.placebo.Placebo;
import dev.shadowsoffire.placebo.block_entity.TickingBlockEntity;
import dev.shadowsoffire.placebo.block_entity.TickingBlockEntityType;
import dev.shadowsoffire.placebo.block_entity.TickingBlockEntityType.TickSide;
import dev.shadowsoffire.placebo.menu.IContainerFactory;
import dev.shadowsoffire.placebo.menu.MenuUtil;
import dev.shadowsoffire.placebo.menu.MenuUtil.PosFactory;
import dev.shadowsoffire.placebo.util.DeferredSet;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.predicates.DataComponentPredicate;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.stats.StatFormatter;
import net.minecraft.stats.StatType;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityType.EntityFactory;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.MenuType.MenuSupplier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityType.BlockEntitySupplier;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * Helper class that acts as a single point of entry for deferred registration of all registry entries.
 * <p>
 * Provides methods for the most common types of objects, as well as {@link #custom(String, ResourceKey, Object)} for other
 * types.
 * <p>
 * <b>Fabric port.</b> NeoForge's per-mod event bus and {@code RegisterEvent}/{@code NewRegistryEvent}/
 * {@code RegisterDataMapTypesEvent} have no Fabric analogue; Fabric registries are open during mod init and are registered
 * eagerly. This helper preserves the original staging API (every {@code register} call appends a {@link Registrar}), and a
 * {@link #bootstrap()} call (or the per-instance {@link #flush()}) performs the actual {@code Registry.register} in
 * dependency-safe registry order. Custom registries are created and registered immediately on the {@code registry(...)} call
 * (Fabric registries cannot be deferred), with their bake callbacks fired from {@link #flush()} once all entries are present.
 * <p>
 * Each DeferredHelper instance must be flushed exactly once during mod initialization. The owning mod should either call the
 * static {@link #bootstrap()} (which flushes every created helper) or this instance's {@link #flush()} from its
 * {@code ModInitializer}.
 */
public class DeferredHelper {

    /**
     * Flush priority for known registry keys. Lower values flush first. Registries that other registrations depend on (blocks,
     * effects) are flushed before their dependents (items, block entities, potions). Custom registries and bake callbacks are
     * created/driven separately by {@link #registry} and {@link #flush}.
     */
    private static int flushPriority(ResourceKey<? extends Registry<?>> key) {
        Identifier id = key.identifier();
        if (id.equals(Registries.BLOCK.identifier())) {
            return 0;
        }
        if (id.equals(Registries.MOB_EFFECT.identifier()) || id.equals(Registries.FLUID.identifier())) {
            return 1;
        }
        if (id.equals(Registries.ITEM.identifier())) {
            return 10;
        }
        if (id.equals(Registries.BLOCK_ENTITY_TYPE.identifier())) {
            return 11;
        }
        if (id.equals(Registries.POTION.identifier())) {
            return 11;
        }
        return 5;
    }

    /**
     * All created helpers, flushed by the static {@link #bootstrap()}.
     */
    private static final List<DeferredHelper> HELPERS = new CopyOnWriteArrayList<>();

    protected final String modid;
    protected final Map<ResourceKey<? extends Registry<?>>, List<Registrar<?>>> objects;
    protected final Map<ResourceKey<? extends Registry<?>>, List<Holder<?>>> resolvedObjects;
    protected final List<Runnable> bakeCallbacks;
    private boolean flushed = false;

    /**
     * Creates a new DeferredHelper for the given mod. The helper must be flushed during mod init via {@link #bootstrap()} or
     * {@link #flush()}.
     *
     * @param modid The modid of the owning mod.
     * @return A new DeferredHelper.
     */
    public static DeferredHelper create(String modid) {
        return new DeferredHelper(modid);
    }

    protected DeferredHelper(String modid) {
        this.modid = modid;
        this.objects = new IdentityHashMap<>();
        this.resolvedObjects = new IdentityHashMap<>();
        this.bakeCallbacks = new ArrayList<>();
        HELPERS.add(this);
    }

    /**
     * Flushes every {@link DeferredHelper} that has been {@linkplain #create created}, registering all staged objects.
     * <p>
     * Call once from the common {@code ModInitializer}. Helpers created after this call (e.g. by downstream mods initializing
     * later) must be flushed by their own owner via {@link #flush()} or a later {@link #bootstrap()} call; flushing is
     * idempotent per helper.
     */
    public static void bootstrap() {
        for (DeferredHelper helper : HELPERS) {
            helper.flush();
        }
    }

    /**
     * Registers all objects staged on this helper into their target registries, in dependency-safe order, then fires custom
     * registry bake callbacks. Idempotent: a second call is a no-op.
     */
    public synchronized void flush() {
        if (this.flushed) {
            return;
        }
        this.flushed = true;

        // Custom registries and data maps register eagerly at call time, so the objects map only contains
        // per-registry entry buckets. Flush them in dependency-safe order (blocks before items, etc.).
        //
        // Determinism: the bucket map is an IdentityHashMap, whose iteration order is unspecified and varies between JVM
        // runs. A stable sort on flushPriority alone would therefore leave same-priority registries (e.g. POTION and
        // BLOCK_ENTITY_TYPE, both priority 11, or every priority-5 registry) ordered non-deterministically. To make the
        // whole flush reproducible we order keys by (flushPriority, registry key identifier string); within a registry,
        // registrars flush in call order (see flushRegistry). The flush order is thus a total, run-stable order.
        //
        // INVARIANT: supplier factories must not eagerly dereference other objects registered in the SAME priority bucket.
        // Cross-bucket dependencies are safe because the lower-priority bucket is fully registered first (blocks before
        // items, etc.), but within a single priority bucket the registration order is the (registry-key string, then
        // call) order above/below and carries no dependency guarantee. A factory that needs a same-bucket
        // object must look it up lazily (i.e. when the returned value is first used), never inside Supplier#get.
        List<ResourceKey<? extends Registry<?>>> keys = new ArrayList<>(this.objects.keySet());
        keys.sort(Comparator
            .comparingInt(DeferredHelper::flushPriority)
            .thenComparing(key -> key.identifier().toString()));

        for (ResourceKey<? extends Registry<?>> key : keys) {
            this.flushRegistry(key);
        }

        for (Runnable bake : this.bakeCallbacks) {
            bake.run();
        }
        this.bakeCallbacks.clear();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void flushRegistry(ResourceKey<? extends Registry<?>> key) {
        Registry registry = BuiltInRegistries.REGISTRY.getValue(key.identifier());
        if (registry == null) {
            throw new IllegalStateException("No registry found for key " + key.identifier() + " while registering objects for mod " + this.modid);
        }
        // Registrars flush in call order (the order the DeferredHelper register-methods were invoked), matching upstream
        // NeoForge's RegisterEvent insertion-order semantics and the raw int ids it assigns. See the INVARIANT in flush():
        // a factory must not eagerly dereference another object in the same priority bucket.
        for (Registrar<?> registrar : this.objects.getOrDefault(key, Collections.emptyList())) {
            try {
                Object obj = registrar.factory.get();
                Registry.register(registry, registrar.id, obj);
                this.resolvedObjects.computeIfAbsent(key, k -> new ArrayList<>()).add(registry.wrapAsHolder(obj));
                if (registrar.callback != null) {
                    ((Registrar) registrar).callback.accept(obj);
                }
            }
            catch (Throwable ex) {
                Placebo.LOGGER.error("Exception thrown during registration of {}", registrar.id);
                throw ex;
            }
        }
        this.objects.remove(key);
    }

    /**
     * Creates and registers a {@link Registry} in the current {@link #modid} with the given {@code registryPath}.
     * <p>
     * The registry is registered into the root registry immediately (Fabric registries are open during init). Bake callbacks
     * configured on the builder are fired from {@link #flush()} after all of this helper's entries are registered.
     *
     * @param registryPath The path of the resource location for the new registry.
     * @param config       A registry builder config.
     * @return The newly created registry.
     */
    public <T> Registry<T> registry(String registryPath, UnaryOperator<RegistryBuilder<T>> config) {
        ResourceKey<? extends Registry<T>> registryKey = ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(this.modid, registryPath));
        RegistryBuilder<T> builder = config.apply(new RegistryBuilder<>(registryKey));
        Registry<T> registry = builder.create();
        for (BakeCallback<T> callback : builder.getBakeCallbacks()) {
            this.bakeCallbacks.add(() -> callback.onBake(registry));
        }
        return registry;
    }

    /**
     * Registers a {@link Block} using a supplier.
     */
    public <T extends Block> DeferredBlock<T> block(String path, Supplier<T> factory) {
        this.register(path, Registries.BLOCK, factory);
        return DeferredBlock.createBlock(Identifier.fromNamespaceAndPath(this.modid, path));
    }

    /**
     * Registers a {@link Block} with a reference to its constructor, configuring a new {@link Block.Properties} instance with the supplied operator.
     */
    public <T extends Block> DeferredBlock<T> block(String path, Function<Block.Properties, T> ctor, UnaryOperator<Block.Properties> properties) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(this.modid, path));
        return this.block(path, () -> ctor.apply(properties.apply(Block.Properties.of()).setId(key)));
    }

    /**
     * Registers a {@link Fluid} using a supplier.
     */
    public <T extends Fluid> DeferredHolder<Fluid, T> fluid(String path, Supplier<T> factory) {
        return this.registerDH(path, Registries.FLUID, factory);
    }

    /**
     * Registers an {@link Item} using a supplier.
     */
    public <T extends Item> DeferredItem<T> item(String path, Supplier<T> factory) {
        this.register(path, Registries.ITEM, factory);
        return DeferredItem.createItem(Identifier.fromNamespaceAndPath(this.modid, path));
    }

    /**
     * Registers an {@link Item} with a reference to its constructor, configuring a new {@link Item.Properties} instance with the supplied operator.
     */
    public <T extends Item> DeferredItem<T> item(String path, Function<Item.Properties, T> ctor, UnaryOperator<Item.Properties> properties) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(this.modid, path));
        return item(path, () -> ctor.apply(properties.apply(new Item.Properties()).setId(key)));
    }

    /**
     * Registers an {@link Item} with a reference to its constructor, using a default {@link Item.Properties} instance.
     */
    public <T extends Item> DeferredItem<T> item(String path, Function<Item.Properties, T> ctor) {
        return item(path, ctor, UnaryOperator.identity());
    }

    /**
     * Registers a subclass of {@link BlockItem} given a target block, the constructor, and an {@link Item.Properties} factory.
     */
    public <T extends BlockItem> DeferredItem<T> blockItem(String path, Holder<Block> block, BiFunction<Block, Item.Properties, T> ctor, UnaryOperator<Item.Properties> properties) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(this.modid, path));
        return item(path, () -> ctor.apply(block.value(), properties.apply(new Item.Properties().useBlockDescriptionPrefix()).setId(key)));
    }

    /**
     * Registers a {@link BlockItem} given a target block and an {@link Item.Properties} factory.
     */
    public DeferredItem<BlockItem> blockItem(String path, Holder<Block> block, UnaryOperator<Item.Properties> properties) {
        return blockItem(path, block, BlockItem::new, properties);
    }

    /**
     * Registers a {@link BlockItem} given a target block, using a default {@link Item.Properties} instance.
     */
    public DeferredItem<BlockItem> blockItem(String path, Holder<Block> block) {
        return blockItem(path, block, UnaryOperator.identity());
    }

    /**
     * Registers a {@link MobEffect} using a supplier.
     */
    public <T extends MobEffect> DeferredHolder<MobEffect, T> effect(String path, Supplier<T> factory) {
        return this.registerDH(path, Registries.MOB_EFFECT, factory);
    }

    /**
     * Registers a {@link SoundEvent} using a supplier.
     */
    public DeferredHolder<SoundEvent, SoundEvent> sound(String path, Supplier<SoundEvent> factory) {
        return this.registerDH(path, Registries.SOUND_EVENT, factory);
    }

    /**
     * Immediately creates and stages for registration a {@link SoundEvent} using the given path via {@link SoundEvent#createVariableRangeEvent}.
     */
    public SoundEvent sound(String path) {
        SoundEvent sound = SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(this.modid, path));
        this.sound(path, () -> sound);
        return sound;
    }

    /**
     * Registers a {@link Potion} using a supplier.
     */
    public <T extends Potion> DeferredHolder<Potion, T> potion(String path, Supplier<T> factory) {
        return this.registerDH(path, Registries.POTION, factory);
    }

    /**
     * Registers a {@link Potion} containing only one mob effect, with the language key of the underlying mob effect.
     */
    public DeferredHolder<Potion, Potion> singlePotion(String path, Supplier<MobEffectInstance> factory) {
        return this.registerDH(path, Registries.POTION, () -> {
            MobEffectInstance inst = factory.get();
            Identifier key = inst.getEffect().unwrapKey().orElseThrow().identifier();
            return new Potion(key.toLanguageKey(), inst);
        });
    }

    /**
     * Registers a {@link Potion} containing multiple mob effects, with a language key automatically generated from the path.
     */
    public DeferredHolder<Potion, Potion> multiPotion(String path, Supplier<List<MobEffectInstance>> factory) {
        String key = Identifier.fromNamespaceAndPath(this.modid, path).toLanguageKey("potion");
        return this.registerDH(path, Registries.POTION, () -> new Potion(key, factory.get().toArray(new MobEffectInstance[0])));
    }

    /**
     * Registers an {@link EntityType} using a supplier.
     */
    public <U extends Entity, T extends EntityType<U>> DeferredHolder<EntityType<?>, T> entity(String path, Supplier<T> factory) {
        return this.registerDH(path, Registries.ENTITY_TYPE, factory);
    }

    /**
     * Registers an {@link EntityType} given the {@link EntityFactory}, {@link MobCategory}, and a function to configure the type.
     */
    public <T extends Entity> DeferredHolder<EntityType<?>, EntityType<T>> entity(String path, EntityFactory<T> factory, MobCategory category, UnaryOperator<EntityType.Builder<T>> op) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(this.modid, path));
        return this.entity(path, () -> op.apply(EntityType.Builder.of(factory, category)).build(key));
    }

    /**
     * Registers a {@link BlockEntityType} given the {@link BlockEntitySupplier} and a supplier to the set of valid blocks.
     */
    public <T extends BlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> blockEntity(String path, BlockEntitySupplier<T> factory, Supplier<Set<Block>> validBlocks) {
        return this.registerDH(path, Registries.BLOCK_ENTITY_TYPE, () -> new BlockEntityType<T>(factory, validBlocks.get()));
    }

    /**
     * Registers a {@link BlockEntityType} given the {@link BlockEntitySupplier} and a vararg array of valid blocks.
     * <p>
     * Immediately constructs the {@link BlockEntityType} and returns it. Registration is deferred until {@link #flush()}. The
     * set of valid blocks will not be resolved until registration, by which point the referenced blocks are registered.
     */
    @SafeVarargs
    public final <T extends BlockEntity> BlockEntityType<T> blockEntity(String path, BlockEntitySupplier<T> factory, Holder<Block>... validBlocks) {
        DeferredSet<Block> blocks = new DeferredSet<>(() -> Arrays.stream(validBlocks).map(Holder::value).collect(Collectors.toSet()));
        BlockEntityType<T> type = new BlockEntityType<>(factory, blocks);
        this.register(path, Registries.BLOCK_ENTITY_TYPE, () -> {
            blocks.set(); // Force resolution of the DeferredSet during registration, while the target blocks are registered
            return type;
        });
        return type;
    }

    /**
     * Registers a {@link TickingBlockEntityType} for a {@link TickingBlockEntity} given the {@link BlockEntitySupplier}, the target {@link TickSide}, and a vararg
     * array of valid blocks.
     * <p>
     * Immediately constructs the {@link BlockEntityType} and returns it. Registration is deferred until {@link #flush()}. The
     * set of valid blocks will not be resolved until registration, by which point the referenced blocks are registered.
     */
    @SafeVarargs
    public final <T extends BlockEntity & TickingBlockEntity> TickingBlockEntityType<T> tickingBlockEntity(String path, BlockEntitySupplier<T> factory, TickSide side, Holder<Block>... validBlocks) {
        DeferredSet<Block> blocks = new DeferredSet<>(() -> Arrays.stream(validBlocks).map(Holder::value).collect(Collectors.toSet()));
        TickingBlockEntityType<T> type = new TickingBlockEntityType<>(factory, blocks, side);
        this.register(path, Registries.BLOCK_ENTITY_TYPE, () -> {
            blocks.set(); // Force resolution of the DeferredSet during registration, while the target blocks are registered
            return type;
        });
        return type;
    }

    /**
     * Registers a {@link ParticleType} using a supplier.
     */
    public <U extends ParticleOptions, T extends ParticleType<U>> DeferredHolder<ParticleType<?>, T> particle(String path, Supplier<T> factory) {
        return this.registerDH(path, Registries.PARTICLE_TYPE, factory);
    }

    /**
     * Registers a {@link SimpleParticleType}.
     */
    public SimpleParticleType simpleParticle(String path, boolean overrideLimit) {
        var type = new SimpleParticleType(overrideLimit);
        this.register(path, Registries.PARTICLE_TYPE, () -> type);
        return type;
    }

    /**
     * Registers a {@link ParticleType} with custom serialization. Both the codec and stream codec must be provided.
     */
    public <T extends ParticleOptions> ParticleType<T> particle(String path, boolean overrideLimit, Function<ParticleType<T>, MapCodec<T>> codec,
        Function<ParticleType<T>, StreamCodec<? super RegistryFriendlyByteBuf, T>> streamCodec) {
        var type = new ParticleType<T>(overrideLimit){

            @Override
            public MapCodec<T> codec() {
                return codec.apply(this);
            }

            @Override
            public StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec() {
                return streamCodec.apply(this);
            }

        };

        this.register(path, Registries.PARTICLE_TYPE, () -> type);
        return type;
    }

    /**
     * Registers a {@link MenuType} using a supplier.
     */
    public <U extends AbstractContainerMenu, T extends MenuType<U>> T menuType(String path, T type) {
        this.register(path, Registries.MENU, () -> type);
        return type;
    }

    /**
     * Registers a {@link MenuType} for the provided {@link MenuSupplier} (a menu with no server&rarr;client open data).
     */
    public <T extends AbstractContainerMenu> MenuType<T> menu(String path, MenuSupplier<T> factory) {
        return this.menuType(path, MenuUtil.type(factory));
    }

    /**
     * Registers a {@link MenuType} for the provided {@link PosFactory} (a menu whose open data is a {@link net.minecraft.core.BlockPos}).
     */
    public <T extends AbstractContainerMenu> MenuType<T> menuWithPos(String path, PosFactory<T> factory) {
        return this.menuType(path, MenuUtil.posType(factory));
    }

    /**
     * Registers a {@link MenuType} for the provided {@link IContainerFactory} (a menu with free-form server&rarr;client open data).
     */
    public <T extends AbstractContainerMenu> MenuType<T> menuWithData(String path, IContainerFactory<T> factory) {
        return this.menuType(path, MenuUtil.bufType(factory));
    }

    /**
     * Registers a {@link RecipeType} using a supplier.
     */
    public <C extends RecipeInput, U extends Recipe<C>, T extends RecipeType<U>> DeferredHolder<RecipeType<?>, T> recipe(String path, Supplier<T> factory) {
        return this.registerDH(path, Registries.RECIPE_TYPE, factory);
    }

    /**
     * Registers a simple {@link RecipeType} whose {@code toString} is its identifier.
     * <p>
     * Immediately constructs the {@link RecipeType} and returns it. Registration is deferred until {@link #flush()}.
     * <p>
     * Replaces NeoForge's {@code RecipeType.simple(Identifier)} (which Fabric lacks) by implementing the vanilla {@link RecipeType}
     * interface directly, exactly as vanilla's own {@code RecipeType.register} does internally.
     */
    public <C extends RecipeInput, U extends Recipe<C>> RecipeType<U> recipe(String path) {
        Identifier id = Identifier.fromNamespaceAndPath(this.modid, path);
        RecipeType<U> type = new RecipeType<>() {

            @Override
            public String toString() {
                return id.toString();
            }
        };
        this.recipe(path, () -> type);
        return type;
    }

    /**
     * Registers a {@link RecipeSerializer} using a supplier.
     */
    public <I extends RecipeInput, R extends Recipe<I>> DeferredHolder<RecipeSerializer<?>, RecipeSerializer<R>> recipeSerializer(String path, Supplier<RecipeSerializer<R>> factory) {
        return this.registerDH(path, Registries.RECIPE_SERIALIZER, factory);
    }

    /**
     * Registers an {@link Attribute} using a supplier.
     */
    public <T extends Attribute> DeferredHolder<Attribute, T> attribute(String path, Supplier<T> factory) {
        return this.registerDH(path, Registries.ATTRIBUTE, factory);
    }

    /**
     * Registers a {@link RangedAttribute}.
     */
    public DeferredHolder<Attribute, RangedAttribute> rangedAttribute(String path, double defaultValue, double min, double max) {
        String key = Identifier.fromNamespaceAndPath(this.modid, path).toLanguageKey("attribute");
        return this.attribute(path, () -> new RangedAttribute(key, defaultValue, min, max));
    }

    /**
     * Registers a {@link StatType} using a supplier.
     */
    public <S, U extends StatType<S>, T extends StatType<U>> DeferredHolder<StatType<?>, T> stat(String path, Supplier<T> factory) {
        return this.registerDH(path, Registries.STAT_TYPE, factory);
    }

    /**
     * Creates a custom stat with the given path and formatter.<br>
     * Calling {@link StatType#get} on {@link Stats#CUSTOM} is required for full registration, for some reason.
     * <p>
     * Mirrors vanilla's private {@code Stats.makeCustomStat}, registering the identifier into {@link Registries#CUSTOM_STAT} and
     * priming the {@link Stats#CUSTOM} stat type with the formatter.
     */
    public Identifier customStat(String path, StatFormatter formatter) {
        Identifier id = Identifier.fromNamespaceAndPath(this.modid, path);
        this.register(path, Registries.CUSTOM_STAT, () -> id, key -> {
            Stats.CUSTOM.get(key, formatter);
        });
        return id;
    }

    /**
     * Registers a {@link Feature} using a supplier.
     */
    public <U extends FeatureConfiguration, T extends Feature<U>> DeferredHolder<Feature<?>, T> feature(String path, Supplier<T> factory) {
        return this.registerDH(path, Registries.FEATURE, factory);
    }

    /**
     * Registers a {@link CreativeModeTab} that is configured with the supplied operator.
     * <p>
     * Uses Fabric's {@link FabricCreativeModeTab#builder()} in place of NeoForge's no-arg {@code CreativeModeTab.builder()}
     * (vanilla's {@code CreativeModeTab.builder(Row, int)} requires a fixed grid position, which Fabric assigns automatically).
     */
    public DeferredHolder<CreativeModeTab, CreativeModeTab> creativeTab(String path, UnaryOperator<CreativeModeTab.Builder> operator) {
        return this.registerDH(path, Registries.CREATIVE_MODE_TAB, () -> operator.apply(FabricCreativeModeTab.builder()).build());
    }

    /**
     * Registers an {@linkplain DataComponentType enchantment effect component} that is configured with the supplied operator.
     * <p>
     * Immediately constructs the {@link DataComponentType} and returns it. Registration is deferred until {@link #flush()}.
     */
    public <T> DataComponentType<T> enchantmentEffect(String path, UnaryOperator<DataComponentType.Builder<T>> operator) {
        DataComponentType<T> type = operator.apply(DataComponentType.builder()).build();
        this.register(path, Registries.ENCHANTMENT_EFFECT_COMPONENT_TYPE, () -> type);
        return type;
    }

    /**
     * Registers a {@link DataComponentType} that is configured with the supplied operator.
     * <p>
     * Immediately constructs the {@link DataComponentType} and returns it. Registration is deferred until {@link #flush()}.
     */
    public <T> DataComponentType<T> component(String path, UnaryOperator<DataComponentType.Builder<T>> operator) {
        DataComponentType<T> type = operator.apply(DataComponentType.builder()).build();
        this.register(path, Registries.DATA_COMPONENT_TYPE, () -> type);
        return type;
    }

    /**
     * Registers an {@link AttachmentType} with the specified default value, that is configured with the supplied operator.
     * <p>
     * The backing Fabric attachment type is built and registered immediately (Fabric has no registration event phase); the
     * returned Placebo {@link AttachmentType} wraps it.
     */
    public <T> AttachmentType<T> attachment(String path, Supplier<T> defaultValue, UnaryOperator<AttachmentType.Builder<T>> operator) {
        Identifier id = Identifier.fromNamespaceAndPath(this.modid, path);
        AttachmentType.Builder<T> builder = operator.apply(AttachmentType.builder(defaultValue));
        return AttachmentType.buildAndRegister(id, builder);
    }

    /**
     * Registers an {@link AttachmentType} with the specified default value, that is configured with the supplied operator.
     * <p>
     * The backing Fabric attachment type is built and registered immediately (Fabric has no registration event phase); the
     * returned Placebo {@link AttachmentType} wraps it. See {@link IAttachmentHolder} for the holder caveat.
     */
    public <T> AttachmentType<T> attachment(String path, Function<IAttachmentHolder, T> defaultValue, UnaryOperator<AttachmentType.Builder<T>> operator) {
        Identifier id = Identifier.fromNamespaceAndPath(this.modid, path);
        AttachmentType.Builder<T> builder = operator.apply(AttachmentType.builder(defaultValue));
        return AttachmentType.buildAndRegister(id, builder);
    }

    /**
     * Registers a loot pool entry type (a {@link MapCodec} for a {@link LootPoolEntryContainer}) and returns it.
     */
    public <T extends LootPoolEntryContainer> MapCodec<T> lootPoolEntry(String path, MapCodec<T> codec) {
        this.register(path, Registries.LOOT_POOL_ENTRY_TYPE, () -> codec);
        return codec;
    }

    /**
     * Registers a codec for an {@link IGlobalLootModifier} and returns it.
     * <p>
     * Fabric has no global-loot-modifier registry; the codec is recorded in {@link LootModifierRegistry}, and
     * {@link LootModifierManager} (wired from the common {@code ModInitializer}) loads {@code data/<ns>/loot_modifiers/*.json}
     * entries built from it and applies them, in {@link IGlobalLootModifier#priority() priority} order, via
     * {@code LootTableEvents.MODIFY_DROPS}. Registration happens immediately.
     */
    public <T extends IGlobalLootModifier> MapCodec<T> lootModifier(String path, MapCodec<T> codec) {
        LootModifierRegistry.register(Identifier.fromNamespaceAndPath(this.modid, path), codec);
        return codec;
    }

    /**
     * Registers a loot condition type (a {@link MapCodec} for a {@link LootItemCondition}) and returns it.
     */
    public <T extends LootItemCondition> MapCodec<T> lootCondition(String path, MapCodec<T> codec) {
        this.register(path, Registries.LOOT_CONDITION_TYPE, () -> codec);
        return codec;
    }

    /**
     * Registers an {@link IngredientType} and returns it.
     * <p>
     * The backing Fabric {@link net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer} is built and registered
     * immediately under the helper's namespace.
     */
    public <T extends ICustomIngredient> IngredientType<T> ingredient(String path, IngredientType<T> type) {
        type.register(Identifier.fromNamespaceAndPath(this.modid, path));
        return type;
    }

    /**
     * Registers a {@link CriterionTrigger} and returns it.
     */
    public <T extends CriterionTrigger<?>> T criteriaTrigger(String path, T trigger) {
        this.register(path, Registries.TRIGGER_TYPE, () -> trigger);
        return trigger;
    }

    /**
     * Registers an {@link DataComponentPredicate.Type} and returns it.
     */
    public <T extends DataComponentPredicate> DataComponentPredicate.Type<T> componentPredicate(String path, Codec<T> codec) {
        DataComponentPredicate.Type<T> type = new DataComponentPredicate.ConcreteType<>(codec);
        this.register(path, Registries.DATA_COMPONENT_PREDICATE_TYPE, () -> type);
        return type;
    }

    /**
     * Registers a {@link StructureProcessorType} and returns it.
     */
    public <T extends StructureProcessor> StructureProcessorType<T> structureProcessor(String path, MapCodec<T> codec) {
        StructureProcessorType<T> type = () -> codec;
        this.register(path, Registries.STRUCTURE_PROCESSOR, () -> type);
        return type;
    }

    /**
     * Creates and returns a {@link DataMapType} for the {@code targetRegistry}.
     * <p>
     * Fabric has no data-map subsystem; the type is recorded in {@link DataMapRegistry} immediately, and {@link DataMapManager}
     * (wired from the common {@code ModInitializer}) loads {@code data/<ns>/data_maps/<registry-folder>/<id>.json} into it on
     * datapack reload and, for {@linkplain DataMapType.Builder#synced synced} data maps, sends the values to each player on
     * datapack sync. Values are read back through {@link DataMapRegistry#get}.
     *
     * @param <K>            The key type of the data map, which is also the type of the target registry.
     * @param <V>            The value type of the data map.
     * @param path           The path of the resource location for the data map type. The map always uses {@link #modid} as the namespace.
     * @param targetRegistry The registry that the data map is for.
     * @param codec          The codec used to de/serialize the data map objects.
     * @param config         A builder config used to specify other values.
     * @return The newly created data map type.
     */
    @SuppressWarnings("unchecked") // DataMapType.builder expects ResourceKey<Registry<K>> instead of ? extends Registry.
    public <K, V> DataMapType<K, V> dataMap(String path, ResourceKey<? extends Registry<K>> targetRegistry, Codec<V> codec, UnaryOperator<DataMapType.Builder<V, K>> config) {
        Identifier id = Identifier.fromNamespaceAndPath(this.modid, path);
        DataMapType<K, V> dataMapType = config.apply(DataMapType.builder(id, (ResourceKey<Registry<K>>) targetRegistry, codec)).build();
        DataMapRegistry.register(dataMapType);
        return dataMapType;
    }

    /**
     * Registers a custom object to the target registry using a supplier.
     * <p>
     * This method must have a different name than {@link #custom(String, ResourceKey, Object)} to resolve generic inference issues with javac.
     */
    public <R, T extends R> DeferredHolder<R, T> customDH(String path, ResourceKey<? extends Registry<R>> registry, Supplier<T> factory) {
        return this.registerDH(path, registry, factory);
    }

    /**
     * Stages a custom object for registration to the target registry.
     * <p>
     * This method should be preferred over {@link #customDH(String, ResourceKey, Supplier)} when the object's creation does not need to be deferred.
     */
    public <R, T extends R> T custom(String path, ResourceKey<? extends Registry<R>> registry, T object) {
        this.register(path, registry, () -> object);
        return object;
    }

    /**
     * Returns a list of all objects for a given registry that were registered by this {@link DeferredHelper}.
     * <p>
     * If registration for the target registry has not happened yet, this list will be empty.
     */
    @ApiStatus.Experimental
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public <R> List<Holder<R>> getRegisteredObjects(ResourceKey<? extends Registry<R>> key) {
        return (List) Collections.unmodifiableList(this.resolvedObjects.getOrDefault(key, List.of()));
    }

    /**
     * Stages the supplier for registration without creating a {@link DeferredHolder}.
     */
    protected <R, T extends R> void register(String path, ResourceKey<? extends Registry<R>> regKey, Supplier<T> factory, @Nullable Consumer<T> callback) {
        List<Registrar<?>> registrars = this.objects.computeIfAbsent(regKey, k -> new ArrayList<>());
        Identifier id = Identifier.fromNamespaceAndPath(this.modid, path);
        registrars.add(new Registrar<>(id, factory, callback));
    }

    /**
     * Stages the supplier for registration without creating a {@link DeferredHolder}.
     */
    protected <R, T extends R> void register(String path, ResourceKey<? extends Registry<R>> regKey, Supplier<T> factory) {
        this.register(path, regKey, factory, null);
    }

    /**
     * Stages the supplier for registration and creates a {@link DeferredHolder} pointing to it.
     */
    protected <R, T extends R> DeferredHolder<R, T> registerDH(String path, ResourceKey<? extends Registry<R>> regKey, Supplier<T> factory) {
        this.register(path, regKey, factory);
        return DeferredHolder.create(regKey, Identifier.fromNamespaceAndPath(this.modid, path));
    }

    protected static record Registrar<T>(Identifier id, Supplier<T> factory, @Nullable Consumer<T> callback) {
        protected Registrar(Identifier id, Supplier<T> factory) {
            this(id, factory, null);
        }
    }

}
