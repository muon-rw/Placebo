package dev.shadowsoffire.placebo.registry;

import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;

import dev.shadowsoffire.placebo.Placebo;
import dev.shadowsoffire.placebo.json.JsonUtil;
import dev.shadowsoffire.placebo.util.PlaceboServer;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Runtime loader and applier for {@link IGlobalLootModifier global loot modifiers}, replacing NeoForge's
 * {@code net.neoforged.neoforge.common.loot.LootModifierManager} (whose modifiers were applied by a patch in
 * {@code LootTable.getRandomItems} via {@code CommonHooks.modifyLoot}).
 * <p>
 * This manager:
 * <ol>
 * <li>is a {@link SimplePreparableReloadListener} registered on {@link PackType#SERVER_DATA} via the
 * {@code fabric-resource-loader-v1} {@link ResourceLoader}, scanning {@code data/<namespace>/loot_modifiers/*.json};</li>
 * <li>parses each JSON off-thread in {@link #prepare}, then on the main thread in {@link #apply} applies the
 * {@code fabric:load_conditions} gate via {@link JsonUtil#checkConditions} and decodes each surviving entry through
 * {@link LootModifierRegistry#dispatchCodec()} into an {@link IGlobalLootModifier};</li>
 * <li>holds the decoded modifiers sorted by {@link IGlobalLootModifier#priority()} (higher priority first); and</li>
 * <li>runs every loaded modifier, in priority order, from a {@link LootTableEvents#MODIFY_DROPS} listener — the post-roll
 * Fabric analog of NeoForge's {@code CommonHooks.modifyLoot}, which provides the generated drops list and the
 * {@link LootContext}.</li>
 * </ol>
 * Loot modifiers apply server-side at loot generation and need no client sync.
 * <p>
 * Call {@link #bootstrap()} exactly once from the common {@code ModInitializer}.
 */
public final class LootModifierManager extends SimplePreparableReloadListener<Map<Identifier, JsonElement>> {

    private static final Logger LOGGER = LoggerFactory.getLogger("Placebo Loot Modifiers");

    /**
     * Identifier of this reload listener, mirroring NeoForge's {@code neoforge:loot_modifiers} manager id under Placebo's
     * own namespace.
     */
    public static final Identifier ID = Identifier.fromNamespaceAndPath(Placebo.MODID, "loot_modifiers");

    /**
     * Datapack directory scanned for loot modifier JSON files: {@code data/<namespace>/loot_modifiers/<path>.json}.
     */
    private static final FileToIdConverter LISTER = FileToIdConverter.json("loot_modifiers");

    /**
     * The single live instance, created by {@link #bootstrap()}.
     */
    private static final LootModifierManager INSTANCE = new LootModifierManager();

    /**
     * The loaded modifiers, sorted by descending {@link IGlobalLootModifier#priority()}.
     * <p>
     * {@link CopyOnWriteArrayList} because {@link #apply} (reload, main thread) replaces the contents while
     * {@link #modifyDrops} (loot generation, server thread) iterates them.
     */
    private final CopyOnWriteArrayList<IGlobalLootModifier> modifiers = new CopyOnWriteArrayList<>();

    private LootModifierManager() {}

    /**
     * Registers the reload listener and the {@link LootTableEvents#MODIFY_DROPS} application hook.
     * <p>
     * Must be called exactly once during common mod initialization.
     */
    public static void bootstrap() {
        ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(ID, INSTANCE);
        LootTableEvents.MODIFY_DROPS.register(INSTANCE::modifyDrops);
    }

    @Override
    protected Map<Identifier, JsonElement> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<Identifier, JsonElement> result = new HashMap<>();
        for (Map.Entry<Identifier, Resource> entry : LISTER.listMatchingResources(manager).entrySet()) {
            Identifier location = entry.getKey();
            Identifier entryId = LISTER.fileToId(location);
            try (var reader = entry.getValue().openAsReader()) {
                JsonElement json = JsonParser.parseReader(reader);
                result.put(entryId, json);
            }
            catch (JsonParseException | IOException e) {
                LOGGER.error("Couldn't parse loot modifier '{}' from '{}': {}", entryId, location, e);
            }
        }
        return result;
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> objects, ResourceManager manager, ProfilerFiller profiler) {
        RegistryOps.RegistryInfoLookup registryInfo = lookupRegistryInfo();
        Codec<IGlobalLootModifier> codec = LootModifierRegistry.dispatchCodec();

        List<IGlobalLootModifier> loaded = new java.util.ArrayList<>();
        objects.forEach((key, json) -> {
            try {
                if (!JsonUtil.checkAndLogEmpty(json, key, ID, LOGGER)) {
                    return;
                }
                if (!JsonUtil.checkConditions(json, key, ID, LOGGER, registryInfo)) {
                    return;
                }
                IGlobalLootModifier modifier = codec.parse(JsonOps.INSTANCE, json)
                    .getOrThrow(msg -> new JsonParseException("Failed to decode loot modifier " + key + ": " + msg));
                loaded.add(modifier);
            }
            catch (Exception e) {
                LOGGER.error("Failed parsing loot modifier file {}.", key, e);
            }
        });

        // Higher priority applies first, so sort descending. Equal priorities keep an undefined (encounter) order.
        loaded.sort(Comparator.comparingInt(IGlobalLootModifier::priority).reversed());

        this.modifiers.clear();
        this.modifiers.addAll(loaded);
        LOGGER.info("Loaded {} loot modifiers.", this.modifiers.size());
    }

    /**
     * {@link LootTableEvents.ModifyDrops} listener. Runs every loaded modifier, in priority order, against the post-roll
     * drops list, mutating it in place (as {@code MODIFY_DROPS} requires).
     *
     * @param key     The holder of the loot table that produced these drops.
     * @param context The loot context, forwarded to each modifier unchanged.
     * @param drops   The mutable, post-roll generated drops list.
     */
    private void modifyDrops(Holder<LootTable> key, LootContext context, List<ItemStack> drops) {
        if (this.modifiers.isEmpty()) {
            return;
        }
        // IGlobalLootModifier#apply takes/returns a fastutil ObjectArrayList (matching the NeoForge signature). Bridge the
        // mutable List the event hands us through that type, then write the final result back into the event's list.
        ObjectArrayList<ItemStack> generated = new ObjectArrayList<>(drops);
        for (IGlobalLootModifier modifier : this.modifiers) {
            generated = modifier.apply(generated, context);
        }
        drops.clear();
        drops.addAll(generated);
    }

    /**
     * Builds the {@link RegistryOps.RegistryInfoLookup} used by {@link JsonUtil#checkConditions} to evaluate
     * {@code fabric:load_conditions} gates.
     * <p>
     * The {@link SimplePreparableReloadListener#reload reload} method is {@code final} and does not expose the v1
     * {@code SharedState} (and thus {@link ResourceLoader#REGISTRY_LOOKUP_KEY}) to {@link #apply}, so the registry lookup
     * is taken from the cached {@link MinecraftServer} (the approach documented in the integration map §4.3). When no
     * server is available the lookup is {@code null}, matching the
     * {@link net.fabricmc.fabric.api.resource.conditions.v1.ResourceCondition#test} contract.
     */
    private static RegistryOps.RegistryInfoLookup lookupRegistryInfo() {
        MinecraftServer server = PlaceboServer.getCurrentServer();
        if (server == null) {
            return null;
        }
        HolderLookup.Provider provider = server.registryAccess();
        return new RegistryOps.RegistryInfoLookup() {

            @Override
            public <T> Optional<RegistryOps.RegistryInfo<T>> lookup(ResourceKey<? extends Registry<? extends T>> registryKey) {
                return provider.lookup(registryKey).map(RegistryOps.RegistryInfo::fromRegistryLookup);
            }
        };
    }

}
