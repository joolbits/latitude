package com.example.globe.client;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/**
 * B-10: the POLAR HOOD'S FUR RUFF -- real geometry, not a painted stripe.
 *
 * <p><b>Why this exists.</b> Peetsa asked for "a proper fur trim 'halo'" and sent three reference
 * parkas; every one of them has a thick ruff standing PROUD of the hood, framing the face like a
 * wreath. A texture can only ever paint fur flat onto the hood's own surface -- it cannot project
 * past the silhouette, so the earlier texture ruff always read as a stripe. A halo needs a model.
 * This is the mod's FIRST custom entity geometry.
 *
 * <p><b>Shape.</b> A closed rectangular ring of four slabs encircling the head at brow height,
 * standing ~3px proud of the 8x8x8 head box on every side. Deliberately chunky and rectangular:
 * that is Minecraft's own vocabulary (the turtle helmet, a wither's ribs), and a smoothly rounded
 * wreath would read as foreign here. The shagginess comes from the TEXTURE (irregular pelt strands
 * with a dark hide underline), the SILHOUETTE comes from this geometry -- the same division of
 * labour that made the icicles work.
 *
 * <p><b>Coordinates.</b> After {@code HeadedModel.translateToHead}, the origin sits at the neck
 * pivot with the head occupying y -8 (crown) to 0 (neck) and x/z -4..4. The ring is centred at
 * y = -5 (the brow line, where a hood opening actually sits) and is 5px tall, so it frames the
 * face rather than capping the skull.
 */
public class PolarRuffModel extends Model<net.minecraft.client.renderer.entity.state.HumanoidRenderState> {

    /** The model layer this geometry bakes into (registered client-side at init). */
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(com.example.globe.GlobeMod.MOD_ID, "polar_ruff"), "main");

    /** The pelt texture wrapped around the ring. */
    public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
            com.example.globe.GlobeMod.MOD_ID, "textures/entity/polar_ruff.png");

    // Ring geometry, in model pixels. The head is 8 wide; the ring stands OUT to +-7 so it reads
    // as a halo from every angle, including straight on (the angle her references are shot from).
    // Live-corrected 2026-07-27: at -7.5 the ring sat ON the crown and read as a flat hat BRIM,
    // not a hood opening. The face occupies roughly y -6..-1, so the ring now starts at -6 and
    // frames it -- which is what a ruff does and what her reference parkas all show.
    private static final float RING_TOP = -6.0f;
    private static final int RING_HEIGHT = 5;      // brow band, tall enough to frame the face
    private static final int RING_OUT = 7;         // outer half-extent (head is 4)
    private static final int SLAB_THICK = 3;       // how far the fur projects past the head

    public PolarRuffModel(ModelPart root) {
        super(root, RenderTypes::entityCutout);
    }

    /**
     * Four slabs forming a closed ring. Front/back span the full outer width so the corners are
     * closed by overlap -- no gaps at the diagonals, which is where a naive four-box ring falls
     * apart when the player turns.
     */
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        int fullWidth = RING_OUT * 2;                 // 14
        int sideDepth = (RING_OUT - SLAB_THICK) * 2;  // 8 -- meets the front/back slabs, no gap
        root.addOrReplaceChild("front", CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-RING_OUT, RING_TOP, -RING_OUT, fullWidth, RING_HEIGHT, SLAB_THICK),
                PartPose.ZERO);
        root.addOrReplaceChild("back", CubeListBuilder.create()
                .texOffs(0, 10)
                .addBox(-RING_OUT, RING_TOP, RING_OUT - SLAB_THICK, fullWidth, RING_HEIGHT, SLAB_THICK),
                PartPose.ZERO);
        root.addOrReplaceChild("left", CubeListBuilder.create()
                .texOffs(0, 20)
                .addBox(RING_OUT - SLAB_THICK, RING_TOP, -sideDepth / 2.0f,
                        SLAB_THICK, RING_HEIGHT, sideDepth),
                PartPose.ZERO);
        root.addOrReplaceChild("right", CubeListBuilder.create()
                .texOffs(0, 32)
                .addBox(-RING_OUT, RING_TOP, -sideDepth / 2.0f,
                        SLAB_THICK, RING_HEIGHT, sideDepth),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(net.minecraft.client.renderer.entity.state.HumanoidRenderState state) {
        // Static geometry: the ruff rides the head, and translateToHead in the layer already applies
        // the head's rotation, so there is nothing to animate here.
    }
}
