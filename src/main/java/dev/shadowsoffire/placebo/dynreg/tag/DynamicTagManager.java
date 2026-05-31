package dev.shadowsoffire.placebo.dynreg.tag;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;

import dev.shadowsoffire.placebo.Placebo;
import dev.shadowsoffire.placebo.dynreg.DynamicRegistry;
import dev.shadowsoffire.placebo.util.PlaceboServer;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Reload listener responsible for loading tag JSON files for every constructed {@link DynamicRegistry}.
 * <p>
 * Runs after every {@code DynamicRegistry} reload listener via dependency edges added in
 * {@link DynamicRegistry}'s reload-event handler. The {@link #prepare} step scans tag JSON files
 * (off-thread, parallel to other reload listeners' prepare phases). The {@link #apply} step resolves
 * the scanned entries against the now-populated registries and binds the resolved tags — resolution must
 * happen during apply because preparation runs in parallel with content listeners' prepare and the registry
 * content isn't yet populated at that point.
 *
 * @see DynamicRegistry#bindTags(Map)
 */
public class DynamicTagManager extends SimplePreparableReloadListener<Map<DynamicRegistry<?>, ScannedTags<?>>> {

    public static final Identifier ID = Placebo.loc("dynamic_registry_tags");

    public static final DynamicTagManager INSTANCE = new DynamicTagManager();

    /**
     * Registers this singleton as a server-data reload listener.
     * <p>
     * Fabric divergence: NeoForge added the tag manager per-reload via the game-bus
     * {@code AddServerReloadListenersEvent} and ordered it last by having each {@link DynamicRegistry} add a
     * dependency edge to {@link #ID}. Fabric has no such event, so the listener is registered once here. The
     * "runs last" ordering is still declared per-registry from {@link DynamicRegistry#registerToBus()} via
     * {@code ResourceLoader.addListenerOrdering(registryId, DynamicTagManager.ID)}; with zero registered
     * {@link DynamicRegistry registries} no edges exist and this manager loads an empty set, which is a clean no-op.
     */
    public static void bootstrap() {
        ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(ID, INSTANCE);
    }

    @Override
    protected Map<DynamicRegistry<?>, ScannedTags<?>> prepare(ResourceManager manager, ProfilerFiller profiler) {
        // Fabric divergence: NeoForge's ContextAwareReloadListener#makeConditionalOps captured the registry access from
        // the reload context and produced a ConditionalOps that both decoded tag files and evaluated conditions. Fabric
        // has neither hook nor ConditionalOps, so a plain RegistryOps (for decode) and a RegistryInfoLookup (for the
        // condition gate) are built from the running server's registry access, mirroring DynamicRegistry#apply.
        HolderLookup.Provider provider = registryLookup();
        RegistryOps<JsonElement> ops = provider.createSerializationContext(JsonOps.INSTANCE);
        RegistryOps.RegistryInfoLookup registryInfo = makeRegistryInfo(provider);
        Map<DynamicRegistry<?>, ScannedTags<?>> result = new IdentityHashMap<>();
        for (DynamicRegistry<?> registry : DynamicRegistry.allRegistries().values()) {
            result.put(registry, scanFor(registry, manager, ops, registryInfo));
        }
        return result;
    }

    private static <R> ScannedTags<R> scanFor(DynamicRegistry<R> registry, ResourceManager manager, RegistryOps<JsonElement> ops, RegistryOps.RegistryInfoLookup registryInfo) {
        TagLoader<R> loader = new TagLoader<>(registry, registry.getLogger());
        return new ScannedTags<>(loader, loader.scan(manager, ops, registryInfo));
    }

    @Override
    protected void apply(Map<DynamicRegistry<?>, ScannedTags<?>> data, ResourceManager manager, ProfilerFiller profiler) {
        for (Map.Entry<DynamicRegistry<?>, ScannedTags<?>> entry : data.entrySet()) {
            DynamicRegistry<?> registry = entry.getKey();
            Map<Identifier, List<Identifier>> resolved = entry.getValue().resolve();
            registry.bindTags(resolved);
            if (!resolved.isEmpty()) {
                registry.getLogger().info("Loaded {} tags for {}.", resolved.size(), registry.getId());
            }
        }
    }

    /**
     * @return The {@link HolderLookup.Provider} for the running server, or {@link RegistryAccess#EMPTY} if none is
     *         running. The server is present during a datapack reload, mirroring the NeoForge behavior (and
     *         {@code DynamicRegistry#registryLookup}).
     */
    private static HolderLookup.Provider registryLookup() {
        MinecraftServer server = PlaceboServer.getCurrentServer();
        return server != null ? server.registryAccess() : RegistryAccess.EMPTY;
    }

    /**
     * Derives the {@link RegistryOps.RegistryInfoLookup} consumed by the condition gate in {@link TagLoader#scan}. Built
     * directly from the {@link HolderLookup.Provider} (mirroring vanilla's {@code RegistryOps.HolderLookupAdapter} and
     * {@code DynamicRegistry#makeRegistryInfo}).
     */
    private static RegistryOps.RegistryInfoLookup makeRegistryInfo(HolderLookup.Provider provider) {
        return new RegistryOps.RegistryInfoLookup() {
            @Override
            public <T> Optional<RegistryOps.RegistryInfo<T>> lookup(ResourceKey<? extends Registry<? extends T>> registryKey) {
                return provider.lookup(registryKey).map(RegistryOps.RegistryInfo::fromRegistryLookup);
            }
        };
    }
}
