package com.example.globe.core;

/**
 * Phase 5 Slice B-5 (Hemisphere Passage) -- the SINGLE SOURCE OF TRUTH for the E/W (antimeridian) edge
 * feature geometry. Pure math, zero Minecraft imports (Core Logic layer, unit-testable in a plain JVM).
 *
 * <h2>Why this class exists (the TEST 86 flight-recorder finding)</h2>
 * Every edge feature -- the approach fog, the crossing prompt, the arm re-arm line, the single white
 * advisory banner (TEST 89 retired the two-tier system and the EW dust particles), the mirror-teleport
 * arrival -- USED to derive its trigger distances from
 * the LIVE {@code WorldBorder} size ({@code getSize()*0.5}). In Peetsa's TEST-86 world the vanilla border was
 * mid-LERP at join (a stale {@code BorderLerp*} persisted in level.dat, growing halfSize over ~4 minutes), so
 * every one of those lines physically SLID ~100 blocks during his session -- a very plausible contributor to
 * the walked-repro failures. This class fixes that at the root: ALL edge geometry is derived from the mod's
 * OWN INTENDED X radius (the value {@code GlobeMod.setGlobeBorder} computes: {@code zRadius} in Classic,
 * {@code zRadius * MERCATOR_ASPECT} in Wide/Mercator), NOT the live border. The vanilla border stays the
 * physical wall; a lerping or {@code /worldborder}-vandalized border can no longer move our lines.
 *
 * <h2>Degrees, not blocks (Peetsa's directive)</h2>
 * The anchors are LONGITUDE DEGREES from the world center. The antimeridian edge is 180 deg; 1 deg of
 * longitude is {@code xRadiusIntended / 180} blocks. Peetsa asked the whole edge experience to be COMPACT --
 * fog/advisory within ~2.5 deg of the wall (TEST 92: "three degrees or two degrees even"), and a crossing to
 * land ~4 deg in ("not nine"). The anchors (edge = 180):
 * <ul>
 *   <li>{@link #RAMP_START_DEG} 177.5 -- fog begins, warning-banner (single white advisory) visibility cap.
 *       NOTHING edge-related is visible equatorward of here (TEST 92: tightened 176.5 -> 177.5 to 2.5 deg).</li>
 *   <li>{@link #REARM_DEG} 178.0 -- the passage arm re-arms once the player walks back out past this line
 *       (TEST 89: raised 177 -> 178 so a player who drifts only a couple of degrees off the wall gets the
 *       crossing prompt again -- 3 deg out was too far to re-prompt; now it sits just 1 deg beyond the
 *       179-deg prompt line, still comfortably past the DEAD_ZONE hysteresis floor).</li>
 *   <li>{@link #PROMPT_DEG} 179.0 -- the crossing prompt opens (closest to the edge).</li>
 *   <li>{@link #ARRIVAL_DEG} 176.0 -- the longitude the crossing DROPS the player at on the far side, 4 deg
 *       from the wall (TEST 92: pulled in from the old 175.5 deg + a rampStart-relative floor that ballooned
 *       to ~9 deg on tiny worlds). See the arrival note under the ordering invariant.</li>
 * </ul>
 *
 * <p>(TEST 89: the old {@code SEVERE_DEG} 178 anchor is GONE -- the warning banner's two-tier system was
 * retired for a single white advisory, so there is no longer a severe-tier boundary to anchor. 178 deg is now
 * simply where the passage arm re-arms.)
 *
 * <h2>The nested ordering invariant</h2>
 * In DEGREES the fog/re-arm/prompt anchors nest {@code prompt(179) > rearm(178) > rampStart(177.5)} -- prompt
 * closest to the edge. Translated to DISTANCE-FROM-EDGE (which shrinks toward the edge) that inverts to
 * {@code promptDist < rearmDist < rampStartDist}. {@link #resolve} preserves that strict ordering on EVERY
 * world size. ARRIVAL is NOT part of this nest: on properly-sized worlds it lands PAST the fog
 * ({@code arrivalDist > rampStartDist}, 4 deg > 2.5 deg), but on the tiny Itty-Bitty world the readability
 * floors push rampStart/rearm outward beyond 4 deg, so the 4-deg arrival lands INSIDE the re-arm band. That is
 * harmless and by design: the S2C arrival seeds the passage arm DISARMED and the sticky band holds it there,
 * so there is no self-reprompt regardless of where arrival lands (see {@link HemispherePassage}'s arrival
 * contract). Arrival never lands closer than {@link #ARRIVAL_MIN_PAST_PROMPT_BLOCKS} past the prompt line.
 *
 * <h2>Block floors for small worlds</h2>
 * On the smallest world (Itty-Bitty Classic, xRadius 3750) one degree is only ~20.8 blocks, so the pure
 * degree spacing would put the whole experience inside a handful of blocks. Three floors keep it readable and
 * keep the hysteresis honest, without ever breaking the ordering:
 * <ul>
 *   <li>{@link #PROMPT_MIN_DIST_BLOCKS} 40 -- the prompt never fires closer than 40 blocks from the wall.</li>
 *   <li>{@link #DEAD_ZONE_MIN_BLOCKS} 64 -- the re-arm line stays at least 64 blocks past the prompt line
 *       (the DEAD_ZONE anti-machine-gun discipline; {@code == HemisphereCrossing.DEAD_ZONE_BLOCKS}).</li>
 *   <li>{@link #FOG_BAND_MIN_BLOCKS} 60 -- the fog ramp (onset down to the full-fog climax) is at least
 *       60 blocks wide so the "weather front rolling in" reads instead of snapping on (TEST 92: lowered
 *       120 -> 60 so the compact 2.5-deg onset survives on small worlds instead of the old 120-block floor
 *       dominating and dragging the fog out to ~7 deg).</li>
 * </ul>
 * On Small/Regular Wide (xRadius 15000/20000) the pure degree values hold un-floored; the floors bind only on
 * the tiny worlds. When a floor engages the ordering is re-tightened with {@link #ORDER_MIN_STEP_BLOCKS} so
 * the invariant never inverts.
 *
 * <p><b>Small-world consequence (TEST 92, documented not "fixed").</b> With rearm floored to
 * {@code prompt + 64 = 104} blocks (~5 deg) on xRadius 3750, the re-arm line sits farther out than the 2.5-deg
 * fog-degree intent; the {@link #FOG_BAND_MIN_BLOCKS}/{@link #ORDER_MIN_STEP_BLOCKS} floors then push rampStart
 * to {@code rearm + 8 = 112} blocks so fog onset is ALWAYS strictly outside re-arm (fog is visible before the
 * re-arm line, never after). We deliberately KEEP the 64-block re-arm floor rather than shrink it: it is the
 * anti-machine-gun hysteresis (~30x per-tick jitter) shared with {@code HemisphereCrossing.DEAD_ZONE_BLOCKS},
 * and -- critically -- keeping it at 64 keeps the 4-deg arrival INSIDE the re-arm band on tiny worlds, where
 * the disarmed-arrival seed prevents an immediate re-prompt loop; an out-of-band arrival (from a shrunk floor)
 * would re-arm on landing and re-prompt the instant the player stepped back toward the wall.
 *
 * <h2>NOT in scope of this class</h2>
 * The polar N/S geometry, the polar warning-ladder degree constants (KEEP-SHARED with the EW axis via
 * {@code LatitudeMath.POLAR_STAGE_*}), and the polar fog are the OTHER axis -- untouched. The
 * {@code EdgeStructureVeto} band is a separate placement-determinism concern; it is ALSO degree-anchored now
 * (TEST 89: its own 173-deg anchor, poleward of this visual ramp plus a fan-out buffer), but it derives its
 * own width and does not read this record.
 */
public final class EdgeGeometry {

    private EdgeGeometry() {
    }

    // ---- degree anchors (longitude degrees from world center; the antimeridian edge is 180) ----

    /** Fog / banner-visibility onset. TEST 92: 177.5 (2.5 deg) -- nothing edge-related shows equatorward of here. */
    public static final double RAMP_START_DEG = 177.5;
    /** The passage arm re-arms once the player is farther out than this. TEST 89: raised 177 -> 178 (just 1 deg
     *  beyond the 179-deg prompt line) so a small drift off the wall re-prompts the crossing. */
    public static final double REARM_DEG = 178.0;
    /** The crossing prompt opens at/inside this -- closest to the edge. */
    public static final double PROMPT_DEG = 179.0;
    /** The longitude the crossing drops the arriving player at on the far side: 4 deg from the wall (Peetsa
     *  TEST 92: "not nine"). On properly-sized worlds this is past the fog; on tiny worlds it lands in the
     *  re-arm band (harmless -- the arrival seeds the arm DISARMED). See the class-javadoc arrival note. */
    public static final double ARRIVAL_DEG = 176.0;

    // ---- block floors (small-world readability + hysteresis discipline) ----

    /** The prompt line never sits closer than this to the wall (readable approach even on Itty-Bitty). */
    public static final double PROMPT_MIN_DIST_BLOCKS = 40.0;
    /** The re-arm line stays at least this far past the prompt line (anti-machine-gun DEAD_ZONE discipline,
     *  == {@link HemisphereCrossing#DEAD_ZONE_BLOCKS}). */
    public static final double DEAD_ZONE_MIN_BLOCKS = 64.0;
    /** The fog ramp (onset -> full-fog climax) is at least this wide so the front reads as gathering
     *  (TEST 92: 120 -> 60 so the compact 2.5-deg onset survives on small worlds). */
    public static final double FOG_BAND_MIN_BLOCKS = 60.0;
    /** Strict-ordering epsilon: when a floor pushes a nearer line out, the next line out is kept at least this
     *  much farther so {@code prompt < rearm < rampStart} never inverts or ties. */
    public static final double ORDER_MIN_STEP_BLOCKS = 8.0;
    /** Defensive floor: the arrival never lands closer to the wall than this many blocks past the prompt line.
     *  Does NOT bind on any real world (xRadius >= 3750, where 4 deg is already ~83 blocks >> prompt + 16);
     *  it only guards degenerate tiny radii from dropping the player essentially at the prompt line. */
    public static final double ARRIVAL_MIN_PAST_PROMPT_BLOCKS = 16.0;

    /** Blocks per longitude degree for a given intended X radius: {@code xRadius / 180}. */
    public static double blocksPerDegree(double xRadiusIntended) {
        return Math.max(1.0, xRadiusIntended) / 180.0;
    }

    /** Distance-from-edge (blocks) for a longitude-degree anchor at a given intended X radius:
     *  {@code (180 - deg) * blocksPerDegree}. */
    public static double distForDeg(double deg, double xRadiusIntended) {
        return (180.0 - deg) * blocksPerDegree(xRadiusIntended);
    }

    /**
     * Distance (blocks, {@code >= 0}) from world-X {@code x} to the nearest E/W edge, measured against the
     * INTENDED X radius (NOT any live border size): {@code max(0, xRadiusIntended - |x - centerX|)}. This is
     * the ONE quantity every edge feature reads, on both client and server, so a lerping/vandalized border
     * can never make the client and server disagree about where the edge is.
     */
    public static double distanceToEdge(double xRadiusIntended, double centerX, double x) {
        return Math.max(0.0, xRadiusIntended - Math.abs(x - centerX));
    }

    /**
     * The resolved, per-world block geometry: every distance is measured FROM the E/W edge (shrinks toward
     * the wall) and derived purely from the intended X radius, with the small-world floors applied and the
     * strict ordering {@code promptDist < rearmDist < rampStartDist} guaranteed.
     */
    public record Resolved(double xRadiusIntended,
                           double promptDist,
                           double rearmDist,
                           double rampStartDist,
                           double fogClimaxDist,
                           double arrivalDist) {

        /** {@code |x - centerX|} at which the crossing drops the arriving player (the ARRIVAL_DEG column,
         *  4 deg from the wall). Clamped non-negative for degenerate tiny radii. */
        public double arrivalAbsX() {
            return Math.max(0.0, xRadiusIntended - arrivalDist);
        }
    }

    /**
     * Resolve the block geometry for a world of the given intended X radius. Pure function of {@code xRadius}
     * ONLY -- this is exactly what makes the whole edge experience immune to a lerping/vandalized live border
     * (the old code fed it {@code border.getSize()*0.5}).
     */
    public static Resolved resolve(double xRadiusIntended) {
        // Nearest-to-edge first, flooring and then re-tightening the ordering outward so no floor can invert it.
        double prompt = Math.max(distForDeg(PROMPT_DEG, xRadiusIntended), PROMPT_MIN_DIST_BLOCKS);
        // Re-arm sits at 178 deg but never closer than DEAD_ZONE_MIN (64) past the prompt line -- the
        // anti-machine-gun hysteresis floor still wins on tiny worlds (Itty: distForDeg(178)=41.7 < 104).
        double rearm = Math.max(distForDeg(REARM_DEG, xRadiusIntended), prompt + DEAD_ZONE_MIN_BLOCKS);
        double climax = prompt; // fog reaches full opacity at the prompt line and holds to the wall
        double rampStart = Math.max(
                Math.max(distForDeg(RAMP_START_DEG, xRadiusIntended), climax + FOG_BAND_MIN_BLOCKS),
                rearm + ORDER_MIN_STEP_BLOCKS);

        // Arrival: the crossing drops the player at ARRIVAL_DEG (176 deg, 4 deg from the wall -- Peetsa "not
        // nine"), resolved from the intended radius like every other line, with only a defensive floor a few
        // blocks past the prompt line for degenerate tiny radii. On Small/Regular Wide this is exactly 176 deg
        // and lands PAST the fog (arrival > rampStart); on Itty-Bitty the readability floors push rampStart/
        // rearm past 4 deg, so the arrival lands INSIDE the re-arm band -- harmless, because the S2C arrival
        // seeds the arm DISARMED and the sticky band holds it (no self-reprompt). NOTE this is deliberately
        // NOT floored to sit past rampStart/rearm; arrival is its own axis, decoupled from the fog nest.
        double arrival = Math.max(distForDeg(ARRIVAL_DEG, xRadiusIntended),
                prompt + ARRIVAL_MIN_PAST_PROMPT_BLOCKS);

        return new Resolved(xRadiusIntended, prompt, rearm, rampStart, climax, arrival);
    }
}
