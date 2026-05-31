package dev.shadowsoffire.placebo;

import org.jspecify.annotations.Nullable;

import dev.shadowsoffire.placebo.events.PlaceboEvents;
import dev.shadowsoffire.placebo.network.PayloadHelper;
import dev.shadowsoffire.placebo.patreon.PatreonPreview;
import dev.shadowsoffire.placebo.patreon.TrailsManager;
import dev.shadowsoffire.placebo.patreon.WingsManager;
import dev.shadowsoffire.placebo.patreon.wings.Wing;
import dev.shadowsoffire.placebo.patreon.wings.WingLayer;
import dev.shadowsoffire.placebo.registry.DataMapManager;
import dev.shadowsoffire.placebo.util.SpecialTooltipItem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionBrewing;

/**
 * Client entrypoint for Placebo.
 * <p>
 * Fabric has no auto-scanned subscriber model, so the former NeoForge {@code @EventBusSubscriber(Dist.CLIENT)} handlers
 * become explicit static {@code bootstrapClient()} calls invoked here.
 */
public class PlaceboClient implements ClientModInitializer {

    /** Monotonic client tick counter, driving {@link dev.shadowsoffire.placebo.color.GradientColor} animation. */
    public static long ticks = 0;

    private static int scrollIdx = 0;
    private static ItemStack currentTooltipItem = ItemStack.EMPTY;
    private static long tooltipTick = 0;

    /**
     * Key-mapping category shared by the patreon cosmetic toggles ({@link WingsManager#TOGGLE}, {@link TrailsManager#TOGGLE}).
     * <p>
     * On 26.1 the {@link KeyMapping} constructor takes a {@link KeyMapping.Category} (no longer a plain String category id),
     * so the former NeoForge String category becomes a registered {@code KeyMapping.Category}. The id is kept as
     * {@code placebo:keys} to match upstream (its label resolves via {@code key.category.placebo.keys}).
     */
    public static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(Placebo.loc("keys"));

    @Override
    public void onInitializeClient() {
        PlaceboEvents.bootstrapClient();

        // Wires client receivers for staged payloads; must run after the common PayloadHelper.bootstrap() registers types.
        PayloadHelper.bootstrapClient();

        DataMapManager.bootstrapClient();

        // Replaces Neo's ClientTickEvent.Post handler that incremented PlaceboClient.ticks.
        ClientTickEvents.END_CLIENT_TICK.register(mc -> ticks++);

        // patreon cosmetics. Replaces NeoForge's @EventBusSubscriber(Dist.CLIENT) handlers + EntityRenderersEvent layer hooks.
        KeyMappingHelper.registerKeyMapping(WingsManager.TOGGLE);
        KeyMappingHelper.registerKeyMapping(TrailsManager.TOGGLE);

        // Replaces NeoForge EntityRenderersEvent.RegisterLayerDefinitions for the wing model.
        ModelLayerRegistry.registerModelLayer(WingsManager.WING_LOC, Wing::createLayer);

        // Replaces NeoForge EntityRenderersEvent.AddLayers: attach the WingLayer to the avatar/player renderer and bake the
        // shared Wing model instance from the now-available model set.
        LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, entityRenderer, registrationHelper, context) -> {
            if (entityRenderer instanceof AvatarRenderer renderer) {
                Wing.INSTANCE = new Wing(context.bakeLayer(WingsManager.WING_LOC));
                registrationHelper.register(new WingLayer(renderer));
            }
        });

        // Spawns the remote data-loader threads; each registers its END_CLIENT_TICK key-poll once data is present.
        WingsManager.init();
        TrailsManager.init();

        // Dormant preview cycler (both PARTICLES and WINGS flags are false); ported faithfully via the local-player tick.
        PatreonPreview.bootstrapClient();

        // Shift+scroll tooltip cycling for SpecialTooltipItem items. Replaces Neo's ItemTooltipEvent (which captured the
        // hovered item) plus the ScreenEvent.MouseScrolled.Pre / InputEvent.MouseScrollingEvent scroll handlers. Item
        // tooltips only render inside a Screen, so the per-screen ScreenMouseEvents path subsumes Neo's separate in-world
        // scroll handler (the tooltipTick == ticks guard already restricts firing to a tick where a tooltip rendered).
        ItemTooltipCallback.EVENT.register((stack, tooltipContext, tooltipFlag, lines) -> {
            currentTooltipItem = stack;
            tooltipTick = ticks;
        });
        ScreenEvents.AFTER_INIT.register((mc, screen, width, height) -> ScreenMouseEvents.allowMouseScroll(screen).register((scr, mouseX, mouseY, horizontalAmount, verticalAmount) -> {
            if (currentTooltipItem.getItem() instanceof SpecialTooltipItem && tooltipTick == ticks && Minecraft.getInstance().hasShiftDown()) {
                scrollIdx += verticalAmount < 0 ? 1 : -1;
                return false; // cancel the scroll while cycling the tooltip
            }
            return true;
        }));
    }

    /**
     * @return The animation cursor for gradient colors, advancing each client tick plus the in-frame partial tick.
     */
    public static float getColorTicks() {
        return (ticks + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false)) / 0.5F;
    }

    /**
     * @return The raw, unbounded tooltip scroll index, advanced by shift+scrolling over a {@link SpecialTooltipItem}.
     */
    public static int getTooltipScrollIndex() {
        return scrollIdx;
    }

    /**
     * @return The tooltip scroll index wrapped into {@code [0, size)} via {@link Math#floorMod(int, int)}.
     */
    public static int getTooltipScrollIndex(int size) {
        return Math.floorMod(scrollIdx, size);
    }

    /**
     * Resolves the client-side {@link PotionBrewing} instance from the current {@link ClientLevel}.
     * <p>
     * Nullable because no client level exists outside of an active world. Only called from {@code MixRegistry} under a
     * client-environment guard, so the client-only {@link Minecraft}/{@link ClientLevel} references here are never loaded
     * on a dedicated server.
     */
    @Nullable
    public static PotionBrewing getBrewingRegistry() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return null;
        }
        ClientLevel level = mc.level;
        return level == null ? null : level.potionBrewing();
    }

}
