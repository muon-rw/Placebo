package dev.shadowsoffire.placebo;

import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.shadowsoffire.placebo.color.GradientColor;
import dev.shadowsoffire.placebo.commands.PlaceboCommand;
import dev.shadowsoffire.placebo.dynreg.DynRegPayloads;
import dev.shadowsoffire.placebo.dynreg.TagSyncPayload;
import dev.shadowsoffire.placebo.dynreg.tag.DynamicTagManager;
import dev.shadowsoffire.placebo.events.PlaceboEvents;
import dev.shadowsoffire.placebo.network.PayloadHelper;
import dev.shadowsoffire.placebo.payloads.ButtonClickPayload;
import dev.shadowsoffire.placebo.payloads.PatreonDisablePayload;
import dev.shadowsoffire.placebo.registry.DataMapManager;
import dev.shadowsoffire.placebo.registry.DeferredHelper;
import dev.shadowsoffire.placebo.registry.LootModifierManager;
import dev.shadowsoffire.placebo.systems.gear.GearSetRegistry;
import dev.shadowsoffire.placebo.systems.mixes.MixRegistry;
import dev.shadowsoffire.placebo.util.PlaceboServer;
import dev.shadowsoffire.placebo.util.PlaceboTaskQueue;
import dev.shadowsoffire.placebo.util.PlaceboUtil;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;

/**
 * Common entrypoint for Placebo.
 * <p>
 * Fabric has no two-bus model or constructor injection, so the former NeoForge {@code @Mod} constructor and
 * {@code @SubscribeEvent} handlers become explicit static {@code bootstrap()} calls, invoked here in dependency order.
 */
public class Placebo implements ModInitializer {

    public static final String MODID = "placebo";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    @Override
    public void onInitialize() {
        // Cached-MinecraftServer shim: install its lifecycle listeners first so later getCurrentServer() reads are valid.
        PlaceboServer.bootstrap();

        PlaceboConfig.load();

        // Mutable copy of the vanilla immutable map so PlaceboUtil.registerCustomColor(...) can add named colors at runtime.
        TextColor.NAMED_COLORS = new HashMap<>(TextColor.NAMED_COLORS);

        // Register the animated rainbow gradient as a named color. Neo did this in enqueueWork during common setup; here the
        // map is mutable by the line above, so it can run directly. GradientColor.getValue() is dist-gated, so storing the
        // RAINBOW instance is safe on a dedicated server (it returns the static base color there).
        PlaceboUtil.registerCustomColor(GradientColor.RAINBOW);

        PlaceboTaskQueue.bootstrap();
        PlaceboEvents.bootstrap();
        // Register the dynamic-registry tag manager (ordered last among dynreg listeners via per-registry edges added
        // in DynamicRegistry#registerToBus). No-ops cleanly until a concrete DynamicRegistry is constructed.
        DynamicTagManager.bootstrap();
        LootModifierManager.bootstrap();

        // systems/ dynamic registries. Mirrors Neo Placebo#setup(): GearSetRegistry then MixRegistry registerToBus().
        // registerToBus() installs each as a SERVER_DATA reload listener (ordered before DynamicTagManager).
        GearSetRegistry.INSTANCE.registerToBus();
        MixRegistry.INSTANCE.registerToBus();
        // Re-apply brewing mixes on server start. Replaces Neo's ServerAboutToStartEvent hook: the first dedicated-server
        // data reload runs before PotionBrewing exists, so the mixes must be (re-)added once the server is up. Registered
        // after PlaceboServer.bootstrap()'s SERVER_STARTING listener (above), which Fabric fires first (registration
        // order), guaranteeing PlaceboServer.getCurrentServer() is non-null when applyMixes() resolves brewing.
        ServerLifecycleEvents.SERVER_STARTING.register(server -> MixRegistry.applyMixes());

        PlaceboCommand.bootstrap();

        // Must run before PayloadHelper.bootstrap(): it stages the data-map sync payload, and bootstrap() locks the
        // provider map (any later registerPayload throws).
        DataMapManager.bootstrap();

        // Dynamic-registry client sync payloads. Staged here before bootstrap() locks the provider map; SyncManagement
        // dispatches them per player on SYNC_DATA_PACK_CONTENTS, in the order Start, Content(s), TagSync, End.
        PayloadHelper.registerPayload(new DynRegPayloads.Start.Provider());
        PayloadHelper.registerPayload(new DynRegPayloads.Content.Provider<>());
        PayloadHelper.registerPayload(new DynRegPayloads.End.Provider());
        PayloadHelper.registerPayload(new TagSyncPayload.Provider());

        // PLAY payloads: container button clicks and the patreon cosmetic toggle broadcast. Both staged before bootstrap().
        PayloadHelper.registerPayload(new ButtonClickPayload.Provider());
        PayloadHelper.registerPayload(new PatreonDisablePayload.Provider());

        // Locks the payload provider map: all registerPayload(...) calls must precede this.
        PayloadHelper.bootstrap();

        // Flush staged DeferredHelper objects into the still-open vanilla registries; must run during onInitialize.
        DeferredHelper.bootstrap();
    }

    public static Identifier loc(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }

}
