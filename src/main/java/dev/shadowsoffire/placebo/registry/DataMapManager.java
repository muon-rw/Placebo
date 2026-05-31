package dev.shadowsoffire.placebo.registry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import dev.shadowsoffire.placebo.Placebo;
import dev.shadowsoffire.placebo.json.JsonUtil;
import dev.shadowsoffire.placebo.network.PayloadHelper;
import dev.shadowsoffire.placebo.util.PlaceboServer;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Runtime loader, applier, and (optionally) synchronizer for {@link DataMapType data maps}, replacing NeoForge's
 * {@code net.neoforged.neoforge.registries.datamaps.DataMapLoader} and the data-map portion of its datapack-content sync
 * (which Fabric lacks entirely).
 * <p>
 * This manager:
 * <ol>
 * <li>is a {@link SimplePreparableReloadListener} registered on {@link PackType#SERVER_DATA} via the
 * {@code fabric-resource-loader-v1} {@link ResourceLoader}. For each {@link DataMapType} registered through
 * {@link DeferredHelper#dataMap}, it scans {@code data/<namespace>/data_maps/<registry-folder>/<datamap-id>.json}, using the
 * NeoForge folder convention ({@code <registry-folder>} = {@link net.minecraft.core.registries.Registries#elementsDirPath
 * elementsDirPath} of the target registry, e.g. {@code item}, {@code block}, {@code worldgen/biome}) so existing JSON stays
 * compatible. <b>Any</b> namespace may contribute a file whose path matches the data map id;</li>
 * <li>in {@link #prepare} parses each JSON off-thread, then in {@link #apply} (main thread) applies the
 * {@code fabric:load_conditions} gate via {@link JsonUtil#checkConditions}, decodes each value through
 * {@link DataMapType#codec()}, resolves the target registry entries (direct ids and {@code #tag} references), and publishes
 * the resulting per-entry value map through {@link DataMapRegistry#setValues}, so {@link DataMapRegistry#get} returns real
 * values server-side;</li>
 * <li>for each {@linkplain DataMapType#networkCodec() synced} data map, sends a {@link DataMapSyncPayload} to each player on
 * {@link ServerLifecycleEvents#SYNC_DATA_PACK_CONTENTS} (per player, never a null broadcast — the Fabric contract), which the
 * client decodes into its own {@link DataMapRegistry} table. The client table is cleared on disconnect by
 * {@link #bootstrapClient}.</li>
 * </ol>
 * <p>
 * <b>Divergences from NeoForge, documented for parity.</b>
 * <ul>
 * <li>{@link DataMapType} here is the non-advanced variant only: there is no remover codec and no {@code DataMapValueMerger}.
 * The supported JSON shape is therefore {@code {"replace": <bool>, "values": {"<id-or-#tag>": <value>}}}; the per-entry
 * {@code {"value": ...}} advanced wrapper and {@code "remove"} arrays are not parsed. {@code replace} is accepted but, because
 * Placebo replaces the whole table on every reload, it behaves as if always true (later files override earlier ones per key,
 * tag members before direct ids — matching NeoForge's default merge order).</li>
 * <li>{@code mandatorySync} cannot refuse a connection on Fabric (there is no required-channel handshake), so it is treated as
 * informational; the values are still always sent to every player for synced data maps.</li>
 * </ul>
 * <p>
 * Call {@link #bootstrap()} exactly once from the common {@code ModInitializer} (after the network framework bootstrap, since
 * {@link #bootstrap()} stages the sync payload through {@link PayloadHelper#registerPayload}) and {@link #bootstrapClient()}
 * once from the {@code ClientModInitializer}.
 */
public final class DataMapManager extends SimplePreparableReloadListener<Map<DataMapType<?, ?>, Map<Identifier, JsonElement>>> {

    private static final Logger LOGGER = LoggerFactory.getLogger("Placebo Data Maps");

    /**
     * Identifier of this reload listener, mirroring NeoForge's {@code neoforge:data_maps} loader id under Placebo's own
     * namespace.
     */
    public static final Identifier ID = Identifier.fromNamespaceAndPath(Placebo.MODID, "data_maps");

    /**
     * The single live instance, created by {@link #bootstrap()}.
     */
    private static final DataMapManager INSTANCE = new DataMapManager();

    private DataMapManager() {}

    /**
     * Registers the reload listener, the per-player sync hook, and the sync payload.
     * <p>
     * Must be called exactly once during common mod initialization, <b>after</b> the {@link PayloadHelper} payload-staging
     * window is open and <b>before</b> {@link PayloadHelper#bootstrap()} closes it, because {@link #bootstrap()} registers the
     * {@link DataMapSyncPayload} provider via {@link PayloadHelper#registerPayload}.
     */
    public static void bootstrap() {
        ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(ID, INSTANCE);
        PayloadHelper.registerPayload(new DataMapSyncPayloadProvider());
        ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register(INSTANCE::onSyncDataPackContents);
    }

    /**
     * Client bootstrap. Registers the disconnect hook that clears the client-side {@link DataMapRegistry} table, mirroring
     * how Placebo's dynamic-registry sync drops client data on disconnect. Call once from the {@code ClientModInitializer}.
     * <p>
     * This references the client-only {@code fabric-networking-api-v1} client package; it must only be invoked on the
     * physical client (it is, from {@link dev.shadowsoffire.placebo.PlaceboClient}).
     */
    public static void bootstrapClient() {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> DataMapRegistry.clearValues());
    }

    @Override
    protected Map<DataMapType<?, ?>, Map<Identifier, JsonElement>> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<DataMapType<?, ?>, Map<Identifier, JsonElement>> result = new LinkedHashMap<>();
        for (DataMapType<?, ?> type : DataMapRegistry.all()) {
            result.put(type, prepareType(type, manager));
        }
        return result;
    }

    /**
     * Scans {@code data/<ns>/data_maps/<registry-folder>/...} for files whose path matches {@code type}'s data map id, across
     * every namespace. The returned map is keyed by the contributing file's full id (namespace + data map path) so duplicate
     * paths from different packs/namespaces are all retained and merged deterministically in {@link #applyType}.
     */
    private static Map<Identifier, JsonElement> prepareType(DataMapType<?, ?> type, ResourceManager manager) {
        Map<Identifier, JsonElement> parsed = new LinkedHashMap<>();
        String folder = "data_maps/" + net.minecraft.core.registries.Registries.elementsDirPath(type.registryKey());
        FileToIdConverter lister = FileToIdConverter.json(folder);
        String wantedPath = type.id().getPath();
        for (Map.Entry<Identifier, Resource> entry : lister.listMatchingResources(manager).entrySet()) {
            Identifier location = entry.getKey();
            Identifier fileId = lister.fileToId(location);
            // The data map is identified by its path; any namespace may contribute a file at that path.
            if (!fileId.getPath().equals(wantedPath)) {
                continue;
            }
            try (var reader = entry.getValue().openAsReader()) {
                JsonElement json = JsonParser.parseReader(reader);
                parsed.put(fileId, json);
            }
            catch (JsonParseException | IOException e) {
                LOGGER.error("Couldn't parse data map '{}' file '{}': {}", type.id(), location, e);
            }
        }
        return parsed;
    }

    @Override
    protected void apply(Map<DataMapType<?, ?>, Map<Identifier, JsonElement>> objects, ResourceManager manager, ProfilerFiller profiler) {
        HolderLookup.Provider registries = lookupProvider();
        if (registries == null) {
            // No server/registry access available; clear stale values and bail. This matches the reload contract: a
            // reload with no populated registries cannot resolve any target entries.
            DataMapRegistry.clearValues();
            LOGGER.warn("Skipped data map loading: no registry access was available.");
            return;
        }
        RegistryOps.RegistryInfoLookup registryInfo = toRegistryInfo(registries);

        int total = 0;
        for (Map.Entry<DataMapType<?, ?>, Map<Identifier, JsonElement>> entry : objects.entrySet()) {
            total += applyType(entry.getKey(), entry.getValue(), registries, registryInfo);
        }
        LOGGER.info("Loaded {} data map values across {} data maps.", total, objects.size());
    }

    /**
     * Decodes, resolves, and stores the values for a single data map type. {@return the number of resolved entry values}.
     */
    private static <R, T> int applyType(DataMapType<R, T> type, Map<Identifier, JsonElement> files, HolderLookup.Provider registries, RegistryOps.RegistryInfoLookup registryInfo) {
        Optional<? extends HolderLookup.RegistryLookup<R>> lookupOpt = registries.lookup(type.registryKey());
        if (lookupOpt.isEmpty()) {
            LOGGER.warn("Skipping data map {}: target registry {} is not present.", type.id(), type.registryKey().identifier());
            DataMapRegistry.setValues(type, Map.of());
            return 0;
        }
        HolderLookup.RegistryLookup<R> lookup = lookupOpt.get();
        RegistryOps<JsonElement> ops = registries.createSerializationContext(JsonOps.INSTANCE);

        // Linked so that, within a file, tag-targeted entries apply before direct-id entries can override them (NeoForge's
        // default merge order). Across files, later files override earlier ones per key.
        Map<ResourceKey<R>, T> values = new LinkedHashMap<>();
        for (Map.Entry<Identifier, JsonElement> file : files.entrySet()) {
            Identifier fileId = file.getKey();
            JsonElement json = file.getValue();
            try {
                if (!JsonUtil.checkAndLogEmpty(json, fileId, type.id(), LOGGER)) {
                    continue;
                }
                if (!JsonUtil.checkConditions(json, fileId, type.id(), LOGGER, registryInfo)) {
                    continue;
                }
                if (!json.isJsonObject()) {
                    LOGGER.error("Data map {} file {} is not a JSON object, skipping.", type.id(), fileId);
                    continue;
                }
                JsonObject obj = json.getAsJsonObject();
                JsonElement valuesElement = obj.get("values");
                if (valuesElement == null || !valuesElement.isJsonObject()) {
                    LOGGER.error("Data map {} file {} is missing a 'values' object, skipping.", type.id(), fileId);
                    continue;
                }
                applyValues(type, valuesElement.getAsJsonObject(), lookup, ops, values, fileId);
            }
            catch (Exception e) {
                LOGGER.error("Failed parsing data map {} file {}.", type.id(), fileId, e);
            }
        }

        DataMapRegistry.setValues(type, values);
        return values.size();
    }

    /**
     * Decodes each value in a single file's {@code values} object and applies it to every resolved target entry. A key
     * beginning with {@code #} is a tag reference (applied to every member); otherwise it is a single entry id. Tag entries
     * are applied first within a file so a later direct-id entry can override the tag's value for a specific entry.
     */
    private static <R, T> void applyValues(DataMapType<R, T> type, JsonObject valuesObj, HolderLookup.RegistryLookup<R> lookup, RegistryOps<JsonElement> ops, Map<ResourceKey<R>, T> out, Identifier fileId) {
        // Two passes so tag-targeted values are written before direct-id values that may override them.
        List<Map.Entry<String, JsonElement>> tagEntries = new ArrayList<>();
        List<Map.Entry<String, JsonElement>> idEntries = new ArrayList<>();
        for (Map.Entry<String, JsonElement> e : valuesObj.entrySet()) {
            if (e.getKey().startsWith("#")) {
                tagEntries.add(e);
            }
            else {
                idEntries.add(e);
            }
        }

        for (Map.Entry<String, JsonElement> e : tagEntries) {
            T value = decodeValue(type, e.getValue(), ops, fileId, e.getKey());
            if (value == null) {
                continue;
            }
            Identifier tagId = Identifier.parse(e.getKey().substring(1));
            TagKey<R> tag = TagKey.create(type.registryKey(), tagId);
            Optional<HolderSet.Named<R>> named = lookup.get(tag);
            if (named.isEmpty()) {
                LOGGER.warn("Data map {} file {} references unknown tag {}.", type.id(), fileId, tagId);
                continue;
            }
            named.get().stream().forEach(holder -> holder.unwrapKey().ifPresent(key -> out.put(key, value)));
        }

        for (Map.Entry<String, JsonElement> e : idEntries) {
            T value = decodeValue(type, e.getValue(), ops, fileId, e.getKey());
            if (value == null) {
                continue;
            }
            Identifier entryId = Identifier.parse(e.getKey());
            ResourceKey<R> key = ResourceKey.create(type.registryKey(), entryId);
            if (lookup.get(key).isEmpty()) {
                LOGGER.warn("Data map {} file {} references unknown entry {}.", type.id(), fileId, entryId);
                continue;
            }
            out.put(key, value);
        }
    }

    /**
     * Decodes a single data map value via {@link DataMapType#codec()}. {@return the decoded value, or {@code null} if
     * decoding failed (the failure is logged)}.
     */
    private static <R, T> T decodeValue(DataMapType<R, T> type, JsonElement value, RegistryOps<JsonElement> ops, Identifier fileId, String key) {
        var result = type.codec().parse(ops, value);
        if (result.isError()) {
            LOGGER.error("Failed to decode data map {} value for {} in file {}: {}", type.id(), key, fileId, result.error().get().message());
            return null;
        }
        return result.getOrThrow();
    }

    // --- Sync ---------------------------------------------------------------------------------------------------------

    /**
     * {@link ServerLifecycleEvents.SyncDataPackContents} listener. Sends every synced data map's loaded values to the
     * joining/reloading player. Fabric fires this once per player (never with a {@code null} broadcast), so this always sends
     * to a single concrete player.
     */
    private void onSyncDataPackContents(ServerPlayer player, boolean joined) {
        RegistryAccess registryAccess = player.registryAccess();
        for (DataMapType<?, ?> type : DataMapRegistry.all()) {
            DataMapSyncPayload payload = buildSyncPayload(type, registryAccess);
            if (payload != null) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }

    /**
     * Encodes a synced data map's current values into a {@link DataMapSyncPayload}, or {@code null} if the data map is not
     * synced (has no {@link DataMapType#networkCodec() network codec}). Each value is encoded with the network codec into its
     * own buffer so the payload codec itself stays registry-free.
     */
    private static <R, T> DataMapSyncPayload buildSyncPayload(DataMapType<R, T> type, RegistryAccess registryAccess) {
        if (type.networkCodec() == null) {
            return null;
        }
        StreamCodec<RegistryFriendlyByteBuf, T> valueCodec = ByteBufCodecs.fromCodecWithRegistries(type.networkCodec());
        Map<Identifier, byte[]> encoded = new LinkedHashMap<>();
        for (ResourceKey<R> key : DataMapRegistry.keys(type)) {
            T value = DataMapRegistry.get(type, key);
            if (value == null) {
                continue;
            }
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registryAccess);
            try {
                valueCodec.encode(buf, value);
                byte[] bytes = new byte[buf.readableBytes()];
                buf.readBytes(bytes);
                encoded.put(key.identifier(), bytes);
            }
            finally {
                buf.release();
            }
        }
        return new DataMapSyncPayload(type.registryKey().identifier(), type.id(), encoded);
    }

    /**
     * Handles a received {@link DataMapSyncPayload} on the client: finds the matching synced {@link DataMapType}, decodes each
     * value with its network codec (using the client's registry access), and publishes them into the client-side
     * {@link DataMapRegistry} table.
     * <p>
     * Called only on the logical client (from {@link DataMapSyncPayloadProvider#handleClient}).
     */
    static void receiveSync(DataMapSyncPayload payload, RegistryAccess registryAccess) {
        DataMapType<?, ?> type = findType(payload.registryKey(), payload.dataMapId());
        if (type == null) {
            LOGGER.warn("Received sync for unknown data map {} (registry {}).", payload.dataMapId(), payload.registryKey());
            return;
        }
        applyReceived(type, payload, registryAccess);
    }

    private static <R, T> void applyReceived(DataMapType<R, T> type, DataMapSyncPayload payload, RegistryAccess registryAccess) {
        if (type.networkCodec() == null) {
            return;
        }
        StreamCodec<RegistryFriendlyByteBuf, T> valueCodec = ByteBufCodecs.fromCodecWithRegistries(type.networkCodec());
        Map<ResourceKey<R>, T> values = new HashMap<>();
        for (Map.Entry<Identifier, byte[]> entry : payload.encodedValues().entrySet()) {
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(entry.getValue()), registryAccess);
            try {
                T value = valueCodec.decode(buf);
                values.put(ResourceKey.create(type.registryKey(), entry.getKey()), value);
            }
            catch (Exception e) {
                LOGGER.error("Failed to decode synced data map {} value for {}.", type.id(), entry.getKey(), e);
            }
            finally {
                buf.release();
            }
        }
        DataMapRegistry.setValues(type, values);
    }

    /**
     * {@return the registered data map type matching the given registry key and data map id, or {@code null} if none}.
     */
    private static DataMapType<?, ?> findType(Identifier registryKey, Identifier dataMapId) {
        for (DataMapType<?, ?> type : DataMapRegistry.all()) {
            if (type.registryKey().identifier().equals(registryKey) && type.id().equals(dataMapId)) {
                return type;
            }
        }
        return null;
    }

    // --- Registry lookup helpers --------------------------------------------------------------------------------------

    /**
     * {@return the registry lookup provider, taken from the cached {@link MinecraftServer}, or {@code null} when none is
     * available}. {@link SimplePreparableReloadListener#reload} is {@code final} and does not expose the v1 {@code SharedState}
     * (and thus {@link ResourceLoader#REGISTRY_LOOKUP_KEY}) to {@link #apply}, so the lookup is taken from the cached server,
     * as documented in the integration map.
     */
    private static HolderLookup.Provider lookupProvider() {
        MinecraftServer server = PlaceboServer.getCurrentServer();
        return server == null ? null : server.registryAccess();
    }

    /**
     * Adapts a {@link HolderLookup.Provider} to the {@link RegistryOps.RegistryInfoLookup} that
     * {@link JsonUtil#checkConditions} requires for evaluating {@code fabric:load_conditions}.
     */
    private static RegistryOps.RegistryInfoLookup toRegistryInfo(HolderLookup.Provider provider) {
        return new RegistryOps.RegistryInfoLookup() {

            @Override
            public <T> Optional<RegistryOps.RegistryInfo<T>> lookup(ResourceKey<? extends Registry<? extends T>> registryKey) {
                return provider.lookup(registryKey).map(RegistryOps.RegistryInfo::fromRegistryLookup);
            }
        };
    }
}
