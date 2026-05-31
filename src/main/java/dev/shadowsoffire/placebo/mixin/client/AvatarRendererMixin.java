package dev.shadowsoffire.placebo.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.shadowsoffire.placebo.patreon.PatreonUtils.WingType;
import dev.shadowsoffire.placebo.patreon.WingsManager;
import dev.shadowsoffire.placebo.patreon.wings.WingLayer;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;

/**
 * Populates the wing render data on the avatar render state.
 * <p>
 * Replaces NeoForge's {@code RegisterRenderStateModifiersEvent} / {@code AvatarRenderStateModifier}: Fabric has no
 * render-state-modifier callback (only {@code InvalidateRenderStateCallback}, which is for invalidation, not
 * extraction), and {@link AvatarRenderState} carries no player UUID/profile. So we inject at the tail of the renderer's
 * extraction method, read the UUID from the source entity, and stash the wing data on the state via
 * {@link FabricRenderState}. This is the faithful comparable of what the NeoForge modifier did internally.
 */
@Mixin(AvatarRenderer.class)
public class AvatarRendererMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
    private void placebo_extractWingData(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
        if (entity instanceof AbstractClientPlayer player) {
            if (WingsManager.DISABLED.contains(player.getUUID())) {
                return;
            }
            WingType type = WingsManager.getType(player.getUUID());
            if (type != null) {
                ((FabricRenderState) state).setData(WingLayer.WING_DATA, new WingLayer.WingRenderData(type, type.textureGetter.apply(player)));
            }
        }
    }

}
