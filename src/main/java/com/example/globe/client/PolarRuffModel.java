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
    // Sizing (revision 4, after the owner saw revision 3 in game: "it doesn't look like it really
    // connects to the hood -- it looks like a square mask around the face"). Revision 3 was a clean
    // rectangle floating at the front of the head, and a clean rectangle is exactly what a mask is.
    // Three answers, all geometry:
    //   1. the ring is DEEPER (3.75 not 3.0), so it roots 2px inside the hood instead of grazing it;
    //   2. FRINGE -- irregular tufts all round the outer edge, so the silhouette is shaggy rather
    //      than a drawn rectangle. This is what stops it reading as a mask;
    //   3. ROOT tufts lying ON the hood behind the ring, so the fur visibly continues onto the
    //      garment and dies out, instead of stopping dead at a seam.
    // The ring itself stays CLOSED all the way round -- that is what a parka ruff does, and an
    // open-bottomed one reads as a headband. It is the outline that needed breaking, not the loop.
    private static final float FRAME_Z = -6.75f;    // front of the fur (1.75px proud of the hood)
    private static final float FRAME_DEPTH = 3.75f; // z -6.75..-3.0: back edge buried in the hood
    private static final float FRAME_BAR = 1.75f;   // thickness of each fur bar
    private static final float RING_TOP = -9.75f;   // 0.75px over the hood crown
    private static final float RING_BOTTOM = 0.25f; // closes at the jaw, not below it
    private static final float RING_HALF_W = 5.75f; // 0.75px past the hood's cheeks
    private static final float OPENING_HALF_W = 4.0f;  // the face shows through at full width
    private static final float RING_HEIGHT = RING_BOTTOM - RING_TOP; // 10.0

    /**
     * The shaggy bits, as {@code {x, y, z, width, height, depth}}. UVs are allocated by index, since
     * the pelt is a uniform field and every tuft wants the same kind of fur.
     *
     * <p><b>Why they look like this (revision 5).</b> Revision 4's fringe was thirteen tufts of
     * near-identical size, evenly spaced, each standing clear of the ring with a visible gap. In
     * game that read as a COG -- machine-cut teeth, not fur. Fur is ragged because its edge is
     * continuous and its length varies: so these tufts are smaller, roughly twice as many, spaced
     * so each OVERLAPS its neighbour (no gaps to see through), and every one has a different
     * protrusion (0.3 to 0.9px) and a different front-to-back depth (1.4 to 2.6px) so the crest is
     * uneven in all three dimensions rather than a row of matching pegs.
     *
     * <p>Hand-placed rather than seeded on purpose: these numbers must be identical on every client
     * and stable across reloads, and a random seed would reshuffle the fur each time the model
     * baked. Each tuft overlaps the ring by ~0.5px so it grows out of it instead of floating.
     *
     * <p>The last five entries are ROOTS: they lie flat on the hood BEHIND the ring. The hood's real
     * surface is x +-5, y -9, so these sit just outside it and carry the fur back onto the garment,
     * which is what stops the ring reading as a mask stuck on the front of the face.
     */
    private static final float[][] TUFTS = {
            // ---- top crest: nine tufts, uneven height, overlapping ---------------------------
            {-5.70f, -10.25f, -6.55f, 1.45f, 1.10f, 1.60f},
            {-4.40f, -10.60f, -6.30f, 1.35f, 1.45f, 2.30f},
            {-3.15f, -10.10f, -6.65f, 1.50f, 0.95f, 1.40f},
            {-1.75f, -10.50f, -6.15f, 1.40f, 1.35f, 2.55f},
            {-0.45f, -10.15f, -6.60f, 1.30f, 1.00f, 1.70f},
            { 0.80f, -10.65f, -6.40f, 1.50f, 1.50f, 2.10f},
            { 2.20f, -10.20f, -6.70f, 1.35f, 1.05f, 1.50f},
            { 3.45f, -10.55f, -6.25f, 1.45f, 1.40f, 2.40f},
            { 4.70f, -10.30f, -6.55f, 1.30f, 1.15f, 1.60f},
            // ---- right cheek -----------------------------------------------------------------
            { 5.25f,  -9.40f, -6.45f, 1.35f, 1.35f, 1.80f},
            { 5.25f,  -8.15f, -6.60f, 0.90f, 1.45f, 1.45f},
            { 5.25f,  -6.80f, -6.20f, 1.25f, 1.30f, 2.35f},
            { 5.25f,  -5.55f, -6.65f, 0.85f, 1.50f, 1.55f},
            { 5.25f,  -4.10f, -6.35f, 1.40f, 1.35f, 2.05f},
            { 5.25f,  -2.80f, -6.60f, 0.95f, 1.45f, 1.60f},
            { 5.25f,  -1.40f, -6.25f, 1.20f, 1.30f, 2.20f},
            // ---- left cheek ------------------------------------------------------------------
            {-6.50f,  -9.45f, -6.50f, 1.25f, 1.40f, 1.70f},
            {-6.15f,  -8.10f, -6.65f, 0.90f, 1.35f, 1.40f},
            {-6.65f,  -6.70f, -6.25f, 1.40f, 1.45f, 2.30f},
            {-6.10f,  -5.40f, -6.60f, 0.85f, 1.30f, 1.50f},
            {-6.55f,  -4.05f, -6.30f, 1.30f, 1.45f, 2.15f},
            {-6.20f,  -2.75f, -6.65f, 0.95f, 1.35f, 1.55f},
            {-6.40f,  -1.35f, -6.35f, 1.15f, 1.30f, 2.00f},
            // ---- under the chin: shorter, so it does not read as a beard --------------------
            {-5.10f,  -0.25f, -6.55f, 1.35f, 0.85f, 1.55f},
            {-3.70f,  -0.25f, -6.30f, 1.45f, 1.05f, 2.10f},
            {-2.35f,  -0.25f, -6.65f, 1.30f, 0.80f, 1.45f},
            {-0.90f,  -0.25f, -6.40f, 1.40f, 1.00f, 1.95f},
            { 0.55f,  -0.25f, -6.60f, 1.35f, 0.80f, 1.50f},
            { 1.95f,  -0.25f, -6.35f, 1.45f, 1.05f, 2.05f},
            { 3.40f,  -0.25f, -6.55f, 1.30f, 0.85f, 1.60f},
            { 4.65f,  -0.25f, -6.45f, 1.40f, 0.95f, 1.80f},
            // ---- roots: fur spreading back onto the hood ------------------------------------
            { 4.90f,  -8.20f, -3.30f, 1.15f, 1.90f, 1.70f},
            { 4.90f,  -5.60f, -3.10f, 1.05f, 1.60f, 1.45f},
            {-6.05f,  -8.20f, -3.30f, 1.15f, 1.90f, 1.70f},
            {-6.05f,  -5.60f, -3.10f, 1.05f, 1.60f, 1.45f},
            {-2.20f,  -9.85f, -3.20f, 2.60f, 1.10f, 1.60f},
    };

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
        // UVs by index on a 3-wide grid in the sheet's unused right-hand band. Every slot is 10x5,
        // which comfortably holds the largest tuft's 8.1x4.05 footprint, and the pelt is a uniform
        // field so it does not matter which slot a given tuft lands in.
        CubeListBuilder fringe = CubeListBuilder.create();
        for (int i = 0; i < TUFTS.length; i++) {
            float[] t = TUFTS[i];
            fringe.texOffs(32 + (i % 3) * 10, (i / 3) * 5)
                    .addBox(t[0], t[1], t[2], t[3], t[4], t[5]);
        }
        root.addOrReplaceChild("fringe", fringe, PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(net.minecraft.client.renderer.entity.state.HumanoidRenderState state) {
        // Static geometry: the ruff rides the head, and translateToHead in the layer already applies
        // the head's rotation, so there is nothing to animate here.
    }
}
