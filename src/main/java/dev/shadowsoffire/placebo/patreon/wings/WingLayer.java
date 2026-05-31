package dev.shadowsoffire.placebo.patreon.wings;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.shadowsoffire.placebo.Placebo;
import dev.shadowsoffire.placebo.patreon.PatreonUtils.WingType;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;

public class WingLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

    // Fabric: NeoForge's ContextKey render-state slot becomes a RenderStateDataKey; the value is stored on the render
    // state via the FabricRenderState interface (auto-applied to all vanilla render states). The state is populated by
    // AvatarRendererMixin (replacing NeoForge's RegisterRenderStateModifiersEvent / AvatarRenderStateModifier).
    public static final RenderStateDataKey<WingRenderData> WING_DATA = RenderStateDataKey.create(() -> Placebo.loc("wings/render_data").toString());

    public WingLayer(RenderLayerParent<AvatarRenderState, PlayerModel> playerRenderer) {
        super(playerRenderer);
    }

    @Override
    public void submit(PoseStack stack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
        WingRenderData data = ((FabricRenderState) state).getData(WING_DATA);
        if (data == null) {
            return;
        }

        stack.pushPose();
        stack.translate(0, data.type().yOffset, 0);
        data.type().model.get().submit(stack, collector, lightCoords, state, data.texture());
        stack.popPose();
    }

    public record WingRenderData(WingType type, Identifier texture) {}

}
