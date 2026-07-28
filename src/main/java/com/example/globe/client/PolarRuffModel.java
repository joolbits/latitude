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
 * <p><b>Shape -- a MANE, not a collar.</b> Owner correction 2026-07-27: the first ring went
 * "around the head circumferentially, not as a 'mane' framing the face". She is right, and every
 * reference parka shows why: a hood ruff does NOT encircle the skull. It borders the FACE OPENING
 * -- arcing over the brow, down both temples, and under the chin -- with plain hood fabric behind
 * the head. So this is a vertical picture-frame standing in front of the face, open in the middle
 * where the face shows, projecting forward past the face plane. Deliberately chunky and
 * rectangular: Minecraft's own vocabulary. Silhouette from geometry, shagginess from the pelt
 * texture (guard-hair streaking + a dark hide underline) -- the division of labour that made the
 * icicles work.
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

    // Frame geometry, in model pixels. After translateToHead the bare head is x -4..4, y -8 (crown)
    // to 0 (neck), z -4..4, and the FACE is the -Z side. The HOOD is an outer-armour layer, which
    // vanilla inflates by 1.0 on every side, so the garment's real surface is x +-5, y -9..1,
    // z +-5 -- the ruff has to clear THAT, not the bare head, or it z-fights the hood it trims.
    //
    // Sizing (revision 3, after the owner saw revision 2 in game). Revision 2 was 4px deep with 2px
    // bars, and the chin bar hung below the jaw: it read as a deep picture frame bolted to the face
    // rather than fur. This one is a RING that hugs the hood -- thin bars, shallow depth, closing at
    // the jaw line, standing just 0.75px proud of the hood all the way round.
    private static final float FRAME_Z = -6.5f;     // front of the fur (1.5px proud of the hood)
    private static final int FRAME_DEPTH = 3;       // z -6.5..-3.5: shallow, so it reads as trim
    private static final float FRAME_BAR = 1.75f;   // thickness of each fur bar
    private static final float RING_TOP = -9.75f;   // 0.75px over the hood crown
    private static final float RING_BOTTOM = 0.25f; // closes at the jaw, not below it
    private static final float RING_HALF_W = 5.75f; // 0.75px past the hood's cheeks
    private static final float OPENING_HALF_W = 4.0f;  // the face shows through at full width
    private static final float RING_HEIGHT = RING_BOTTOM - RING_TOP; // 10.0

    public PolarRuffModel(ModelPart root) {
        super(root, RenderTypes::entityCutout);
    }

    /**
     * Four bars forming a ring around the face opening: brow, two temples, chin. The TEMPLES run the
     * full height and own the corners; the brow and chin bars span only the gap between them. Butted
     * rather than overlapped, deliberately -- two boxes sharing a face z-fight, and a flickering
     * corner on a head that turns constantly would be the first thing anyone noticed.
     */
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        float openingWidth = OPENING_HALF_W * 2.0f;
        float templeX = RING_HALF_W - FRAME_BAR;
        root.addOrReplaceChild("brow", CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-OPENING_HALF_W, RING_TOP, FRAME_Z, openingWidth, FRAME_BAR, FRAME_DEPTH),
                PartPose.ZERO);
        root.addOrReplaceChild("chin", CubeListBuilder.create()
                .texOffs(0, 8)
                .addBox(-OPENING_HALF_W, RING_BOTTOM - FRAME_BAR, FRAME_Z, openingWidth, FRAME_BAR,
                        FRAME_DEPTH),
                PartPose.ZERO);
        root.addOrReplaceChild("temple_left", CubeListBuilder.create()
                .texOffs(0, 16)
                .addBox(templeX, RING_TOP, FRAME_Z, FRAME_BAR, RING_HEIGHT, FRAME_DEPTH),
                PartPose.ZERO);
        root.addOrReplaceChild("temple_right", CubeListBuilder.create()
                .texOffs(20, 16)
                .addBox(-RING_HALF_W, RING_TOP, FRAME_Z, FRAME_BAR, RING_HEIGHT, FRAME_DEPTH),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(net.minecraft.client.renderer.entity.state.HumanoidRenderState state) {
        // Static geometry: the ruff rides the head, and translateToHead in the layer already applies
        // the head's rotation, so there is nothing to animate here.
    }
}
