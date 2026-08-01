package com.example.globe.client;

import com.example.globe.content.PolarOutfitting;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.item.ItemStack;

/**
 * B-10: draws {@link PolarRuffModel} -- the fur halo -- on anyone wearing the polar hood.
 *
 * <p>Gated on the HEAD equipment the render state already carries ({@code HumanoidRenderState
 * .headEquipment}, the same field vanilla's own {@code HumanoidArmorLayer} reads), so there is no
 * new netcode and no client/server disagreement: if the hood is rendering, the ruff is rendering.
 * Both hood variants qualify -- the plain hood and the goggled one -- because the ruff belongs to
 * the garment, not to the goggles.
 *
 * <p>{@code translateToHead} puts the pose stack on the head bone, so the ruff inherits head yaw
 * and pitch for free: look around and the halo turns with you, exactly like a real hood.
 *
 * <p>The pelt is drawn UNTINTED (colour {@code -1}, i.e. white multiplier) -- the suit's cloth is
 * dyeable but its fur is the constant identity across every dye, the rule the whole two-pass
 * texture model already follows.
 */
public class PolarRuffLayer<S extends HumanoidRenderState, M extends EntityModel<S> & HeadedModel>
        extends RenderLayer<S, M> {

    private final PolarRuffModel ruff;

    public PolarRuffLayer(RenderLayerParent<S, M> parent, ModelPart bakedRuffRoot) {
        super(parent);
        this.ruff = new PolarRuffModel(bakedRuffRoot);
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int packedLight, S state,
            float yRot, float xRot) {
        ItemStack head = state.headEquipment;
        if (head == null || head.isEmpty()) {
            return;
        }
        if (head.getItem() != PolarOutfitting.POLAR_HOOD
                && head.getItem() != PolarOutfitting.POLAR_HOOD_GOGGLED) {
            return; // not our garment: provable no-op for every other entity and every other hat.
        }
        pose.pushPose();
        getParentModel().translateToHead(pose);
        renderColoredCutoutModel(this.ruff, PolarRuffModel.TEXTURE, pose, collector, packedLight,
                state, -1, 0);
        pose.popPose();
    }
}
