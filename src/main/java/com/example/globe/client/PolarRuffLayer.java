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
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
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
 *
 * <p><b>Crash fix (2026-08-03).</b> This layer used to be generic over {@code S extends
 * HumanoidRenderState}. That is a MODEL-side assumption (the registration in {@code
 * GlobeModClient} only checks {@code renderer.getModel() instanceof HeadedModel}), but a mob's
 * render-state class is a SEPARATE hierarchy from its model: Witch's model has a head bone (so the
 * registration attaches this layer) but {@code WitchRenderState extends HoldingEntityRenderState},
 * not {@code HumanoidRenderState} -- javap confirms the two hierarchies only reconverge at {@link
 * LivingEntityRenderState}. Because {@code helper.register(new PolarRuffLayer(renderer, baked))} at
 * the registration site necessarily constructs a raw type (the renderer's real {@code S} isn't
 * known there), the compiler had generated a synthetic bridge method for {@link RenderLayer#submit}
 * that cast its incoming state to {@code HumanoidRenderState} -- and that cast threw a
 * {@code ClassCastException} the instant a Witch (or any other non-Humanoid mob with a headed
 * model) rendered, crashing the client outright. The class is now generic over the loosest bound
 * every {@code LivingEntityRenderer} actually guarantees ({@link LivingEntityRenderState}, per
 * {@code LivingEntityRenderer<T, S extends LivingEntityRenderState, M>}), so that bridge cast can
 * never fail again for any mob; {@link #submit} does its own {@code instanceof} narrowing to
 * {@code HumanoidRenderState} before touching any Humanoid-specific field, and simply no-ops for
 * everyone else -- the same "provable no-op for every other entity" contract this layer already
 * documented, now actually enforced at the render-state level instead of only the model level.
 */
public class PolarRuffLayer<S extends LivingEntityRenderState, M extends EntityModel<S> & HeadedModel>
        extends RenderLayer<S, M> {

    private final PolarRuffModel ruff;

    public PolarRuffLayer(RenderLayerParent<S, M> parent, ModelPart bakedRuffRoot) {
        super(parent);
        this.ruff = new PolarRuffModel(bakedRuffRoot);
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int packedLight, S state,
            float yRot, float xRot) {
        if (!(state instanceof HumanoidRenderState humanoidState)) {
            return; // this mob's model has a head bone, but its render state carries no head slot.
        }
        ItemStack head = humanoidState.headEquipment;
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
                humanoidState, -1, 0);
        pose.popPose();
    }
}
