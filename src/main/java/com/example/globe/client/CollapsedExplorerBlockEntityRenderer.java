package com.example.globe.client;

import com.example.globe.world.CollapsedExplorerBlock;
import com.example.globe.world.CollapsedExplorerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.skeleton.SkeletonModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.state.SkeletonRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;

/**
 * Draws one frozen vanilla skeleton model across the paired collapsed-explorer blocks.
 *
 * <p>This is model rendering only: no entity is created, so there is no AI, physics, equipment, damage,
 * despawn, loot, or difficulty-dependent lifetime. The pose is submitted only from the HEAD anchor.
 */
public final class CollapsedExplorerBlockEntityRenderer
        implements BlockEntityRenderer<
                CollapsedExplorerBlockEntity,
                CollapsedExplorerBlockEntityRenderer.RenderState> {

    private static final Identifier SKELETON_TEXTURE =
            Identifier.withDefaultNamespace("textures/entity/skeleton/skeleton.png");
    private static final float BODY_BASE_HEIGHT = 0.26F;

    private final SkeletonModel<SkeletonRenderState> model;

    public CollapsedExplorerBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        model = new SkeletonModel<>(context.bakeLayer(ModelLayers.SKELETON));
    }

    @Override
    public RenderState createRenderState() {
        return new RenderState();
    }

    @Override
    public void extractRenderState(
            CollapsedExplorerBlockEntity blockEntity,
            RenderState renderState,
            float partialTick,
            Vec3 cameraPosition,
            CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(
                blockEntity, renderState, partialTick, cameraPosition, breakProgress);
        BlockState blockState = blockEntity.getBlockState();
        renderState.facing = blockState.getValue(CollapsedExplorerBlock.FACING);
        renderState.part = blockState.getValue(CollapsedExplorerBlock.PART);
    }

    @Override
    public void submit(
            RenderState renderState,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            CameraRenderState cameraState) {
        if (renderState.part != BedPart.HEAD) {
            return;
        }

        Direction facing = renderState.facing;
        poseStack.pushPose();
        // HEAD owns the renderer; translate one cell back to the feet, then lay the upright vanilla
        // model along FACING so its head returns into this canonical anchor cell.
        poseStack.translate(
                0.5D - facing.getStepX(),
                BODY_BASE_HEIGHT,
                0.5D - facing.getStepZ());
        poseStack.mulPose(Axis.YP.rotationDegrees(yawFor(facing)));
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        // Match LivingEntityRenderer's model-space conversion without creating a LivingEntity.
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.501F, 0.0F);
        collector.submitModel(
                model,
                renderState.skeleton,
                poseStack,
                model.renderType(SKELETON_TEXTURE),
                renderState.lightCoords,
                OverlayTexture.NO_OVERLAY,
                0xFFFFFFFF,
                renderState.breakProgress);
        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen() {
        // The single HEAD anchor intentionally draws back through the neighbouring FOOT cell.
        return true;
    }

    private static float yawFor(Direction facing) {
        return switch (facing) {
            case EAST -> 90.0F;
            case NORTH -> 180.0F;
            case WEST -> 270.0F;
            default -> 0.0F;
        };
    }

    public static final class RenderState extends BlockEntityRenderState {
        private final SkeletonRenderState skeleton = new SkeletonRenderState();
        private Direction facing = Direction.NORTH;
        private BedPart part = BedPart.FOOT;
    }
}
