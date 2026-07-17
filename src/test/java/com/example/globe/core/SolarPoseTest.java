package com.example.globe.core;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain-JVM tests for {@link SolarPose} — the headless half of the Solar Tilt P2 regression guard (sweep A6:
 * option B must be provable against {@link SolarTilt#solarDirection} without a client). JOML runs in a plain
 * JVM (no MC bootstrap), so the EXACT composed pose the mixin submits is reproduced here with real
 * quaternions:
 * <ul>
 *   <li><b>the vanilla-identity guard</b> — at φ = 0, δ = 0 the composed per-body pose IS vanilla's
 *       {@code XP(H)} and the full chain reproduces vanilla's sun direction (the §5 "equator must be
 *       vanilla" law; the live screenshot-diff is the P3 half),</li>
 *   <li><b>the law lock</b> — across a (φ, δ, H) grid the full chain {@code YP(-90)·tiltedBodyPose(XP(H))}
 *       lands the body exactly on {@code solarDirection(φ, δ, −H)} (vanilla's angle sign is the negative of
 *       the kernel's hour angle), so the render pose and the functional evaluator can never disagree,</li>
 *   <li><b>consequences</b> — midnight-sun/polar-night up-components, the stars-tilt-only pose, NaN safety.</li>
 * </ul>
 */
class SolarPoseTest {

    private static final float EPS = 1.0e-5f;

    /** The mixin's full composed chain: vanilla's untouched {@code YP(-90)} yaw, then the replacement
     *  per-body rotation — exactly what the PoseStack sees. Returns the world direction of local +Y. */
    private static Vector3f composedDir(double phiDeg, double deltaDeg, float vanillaAngleRad, boolean stars) {
        Quaternionf body = new Quaternionf().rotationX(vanillaAngleRad); // vanilla's Axis.XP.rotation(angle)
        Quaternionf replaced = stars
                ? SolarPose.tiltedStarPose(body, phiDeg)
                : SolarPose.tiltedBodyPose(body, phiDeg, deltaDeg);
        Quaternionf full = new Quaternionf().rotationY((float) Math.toRadians(-90)).mul(replaced);
        return full.transform(new Vector3f(0, 1, 0));
    }

    private static void assertDir(Vector3f actual, double[] want, String msg) {
        assertEquals(want[0], actual.x, EPS, msg + " (east)");
        assertEquals(want[1], actual.y, EPS, msg + " (up)");
        assertEquals(want[2], actual.z, EPS, msg + " (south)");
    }

    // ---- the identity guard (§5 regression law, headless half) ----------------------------------------

    @Test
    void equatorDeltaZeroPoseIsVanillaIdentity() {
        // At φ = 0, δ = 0 the replacement quaternion must BE vanilla's XP(H) (ZP(0) is the identity, so the
        // composition collapses by construction) — component-for-component.
        for (float h : new float[] {-2.8f, -1.2f, 0.0f, 0.4f, 1.57f, 3.0f}) {
            Quaternionf vanilla = new Quaternionf().rotationX(h);
            Quaternionf composed = SolarPose.tiltedBodyPose(new Quaternionf().rotationX(h), 0.0, 0.0);
            assertEquals(vanilla.x, composed.x, EPS, "qx @H=" + h);
            assertEquals(vanilla.y, composed.y, EPS, "qy @H=" + h);
            assertEquals(vanilla.z, composed.z, EPS, "qz @H=" + h);
            assertEquals(vanilla.w, composed.w, EPS, "qw @H=" + h);
        }
    }

    @Test
    void equatorDeltaZeroDirectionMatchesVanillaArc() {
        // Vanilla's world direction is (−sin H, cos H, 0) == solarDirection(0, 0, −H): the composed chain
        // must reproduce it — the sun rises east (+X) before noon (H < 0), zenith at H = 0, sets west.
        for (float h : new float[] {-1.5f, -0.5f, 0.0f, 0.5f, 1.5f}) {
            Vector3f d = composedDir(0.0, 0.0, h, false);
            assertDir(d, SolarTilt.solarDirection(0.0, 0.0, -h), "vanilla arc @H=" + h);
            assertEquals(-Math.sin(h), d.x, EPS, "east = -sin(H_vanilla) @H=" + h);
            assertEquals(Math.cos(h), d.y, EPS, "up = cos(H_vanilla) @H=" + h);
        }
    }

    // ---- the law lock: composed pose == solarDirection across the grid --------------------------------

    @Test
    void composedPoseLandsOnSolarDirectionAcrossTheGrid() {
        double[] phis = {-90, -75, -60, -35, 0, 20, 40, 60, 75, 90};
        double[] deltas = {-30, -15, 0, 15, 23.5, 30};
        float[] hours = {-3.0f, -1.7f, -0.6f, 0.0f, 0.9f, 2.2f};
        for (double phi : phis) {
            for (double delta : deltas) {
                for (float h : hours) {
                    Vector3f d = composedDir(phi, delta, h, false);
                    assertDir(d, SolarTilt.solarDirection(phi, delta, -h),
                            "phi=" + phi + " delta=" + delta + " H=" + h);
                }
            }
        }
    }

    @Test
    void composedUpComponentIsTheElevationLaw() {
        // asin(up) == solarElevationDeg: the render pose and the ONE evaluator (mob rules, §8) agree.
        double phi = 75.0, delta = 30.0;
        float h = (float) Math.PI; // vanilla midnight
        Vector3f d = composedDir(phi, delta, h, false);
        double elevationDeg = Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, d.y))));
        assertEquals(SolarTilt.solarElevationDeg(phi, delta, Math.PI), elevationDeg, 1e-3,
                "midnight sun at phi=75: the pose's up-component IS the evaluator's elevation");
        assertTrue(d.y > 0, "phi=75, delta=+30: the sun is ABOVE the horizon at midnight (midnight sun)");
        // Winter twin: polar night — below the horizon at NOON.
        Vector3f noonWinter = composedDir(-75.0, 30.0, 0.0f, false);
        assertTrue(noonWinter.y < 0, "phi=-75, delta=+30: the sun is BELOW the horizon at noon (polar night)");
    }

    // ---- stars + degeneracy ----------------------------------------------------------------------------

    @Test
    void starPoseIsTiltOnly() {
        // Stars take the tilt but no declination: identical to a body pose with δ = 0, for any δ the sun got.
        float h = 1.1f;
        Quaternionf star = SolarPose.tiltedStarPose(new Quaternionf().rotationX(h), 55.0);
        Quaternionf bodyNoDelta = SolarPose.tiltedBodyPose(new Quaternionf().rotationX(h), 55.0, 0.0);
        assertEquals(bodyNoDelta.x, star.x, EPS);
        assertEquals(bodyNoDelta.y, star.y, EPS);
        assertEquals(bodyNoDelta.z, star.z, EPS);
        assertEquals(bodyNoDelta.w, star.w, EPS);
        // And the wheel is HORIZONTAL at the pole (celestial pole at the zenith): a δ=0 body circles the
        // horizon at elevation 0 for EVERY hour angle — the circumpolar invariant (same law as the A1 pole
        // invariant: at |φ|=90 elevation is constant = sign(φ)·δ, here δ=0).
        for (float hh : new float[] {0.0f, 1.0f, 2.5f, (float) Math.PI}) {
            assertEquals(0.0, composedDir(90.0, 0.0, hh, true).y, EPS,
                    "north pole, delta 0: the wheel skims the horizon at H=" + hh);
        }
    }

    // ---- S11(d): the roll-free billboard pose (the TEST 101 diamond-sun fix) ---------------------------

    /** The mixin's roll-free chain: untouched yaw * rollFreeTiltedBodyPose. */
    private static Quaternionf rollFreeFull(double phiDeg, double deltaDeg, float vanillaAngleRad) {
        Quaternionf body = SolarPose.rollFreeTiltedBodyPose(
                new Quaternionf().rotationX(vanillaAngleRad), phiDeg, deltaDeg);
        return new Quaternionf().rotationY((float) Math.toRadians(-90)).mul(body);
    }

    @Test
    void bareCompositionReallyDidRollTheQuad() {
        // Document the S11d bug: the bare tilted composition carries in-plane roll — at (φ=80, δ=30) the
        // quad's X edge leaves the horizon by a large margin (the owner's 45° diamond).
        Quaternionf full = new Quaternionf().rotationY((float) Math.toRadians(-90))
                .mul(SolarPose.tiltedBodyPose(new Quaternionf().rotationX(0.9f), 80.0, 30.0));
        float rolledY = Math.abs(full.transform(new Vector3f(1, 0, 0)).y);
        assertTrue(rolledY > 0.1f, "the un-fixed composition must exhibit the roll the fix removes (was "
                + rolledY + ")");
    }

    @Test
    void rollFreePoseKeepsTheDirectionGridUnchanged() {
        // Direction and roll are orthogonal: the roll-free pose must land on the SAME solarDirection grid.
        double[] phis = {-90, -75, -60, -35, 0, 20, 40, 60, 80, 90};
        double[] deltas = {-30, -15, 0, 15, 23.5, 30};
        float[] hours = {-3.0f, -1.7f, -0.6f, 0.0f, 0.9f, 2.2f};
        for (double phi : phis) {
            for (double delta : deltas) {
                for (float h : hours) {
                    Vector3f d = rollFreeFull(phi, delta, h).transform(new Vector3f(0, 1, 0));
                    assertDir(d, SolarTilt.solarDirection(phi, delta, -h),
                            "roll-free dir phi=" + phi + " delta=" + delta + " H=" + h);
                }
            }
        }
    }

    @Test
    void rollFreePoseKeepsTheQuadEdgeOnTheHorizon() {
        // The S11d no-roll invariant: away from the zenith the quad's local X edge stays horizon-parallel
        // (zero world-Y) for the whole grid.
        double[] phis = {-90, -75, -60, -35, 0, 20, 40, 60, 80, 90};
        double[] deltas = {-30, 0, 30};
        float[] hours = {-3.0f, -1.7f, -0.6f, 0.0f, 0.9f, 2.2f};
        for (double phi : phis) {
            for (double delta : deltas) {
                for (float h : hours) {
                    Quaternionf full = rollFreeFull(phi, delta, h);
                    float up = full.transform(new Vector3f(0, 1, 0)).y;
                    if (Math.abs(up) >= 0.999f) {
                        continue; // zenith/nadir: horizontal edge undefined (and roll invisible)
                    }
                    float xWorldY = Math.abs(full.transform(new Vector3f(1, 0, 0)).y);
                    assertTrue(xWorldY < 1e-4f, "quad edge off the horizon by " + xWorldY
                            + " at phi=" + phi + " delta=" + delta + " H=" + h);
                }
            }
        }
    }

    @Test
    void rollFreeVanillaIdentityAtEquatorDeltaZero() {
        // At φ = 0, δ = 0 the roll-free pose must reproduce vanilla's FULL basis (not just the direction):
        // vanilla's quad X edge is horizon-parallel already, and the branch-follow keeps its exact side.
        for (float h : new float[] {-2.8f, -1.2f, -0.3f, 0.4f, 1.57f, 3.0f}) {
            Quaternionf vanilla = new Quaternionf().rotationX(h);
            Quaternionf rollFree = SolarPose.rollFreeTiltedBodyPose(new Quaternionf().rotationX(h), 0.0, 0.0);
            for (Vector3f axis : new Vector3f[] {new Vector3f(1, 0, 0), new Vector3f(0, 1, 0),
                    new Vector3f(0, 0, 1)}) {
                Vector3f want = vanilla.transform(new Vector3f(axis));
                Vector3f got = rollFree.transform(new Vector3f(axis));
                assertTrue(want.distance(got) < 1e-5f, "basis drift @H=" + h + " axis=" + axis);
            }
        }
    }

    @Test
    void rollFreeZenithFallbackIsFinite() {
        // Exactly at the zenith (φ=0, δ=0, H=0: s == ŷ) the horizontal edge is undefined; the fallback keeps
        // the (vanilla-identical) composition — finite, no NaN.
        Quaternionf q = SolarPose.rollFreeTiltedBodyPose(new Quaternionf().rotationX(0.0f), 0.0, 0.0);
        assertTrue(Float.isFinite(q.x) && Float.isFinite(q.y) && Float.isFinite(q.z) && Float.isFinite(q.w));
        Vector3f d = q.transform(new Vector3f(0, 1, 0));
        assertEquals(1.0f, d.y, EPS, "zenith direction preserved");
    }

    // ---- S11(e): the moon rides the MIRROR declination (the both-bodies-visible bug) --------------------

    @Test
    void mirrorDeclinationMoonIsTheExactSunAntipode() {
        // The bug: the moon's vanilla angle (H+π) survived the wrap, but the SUN's +δ hoisted the moon onto
        // the sun's never-setting small circle — both visible under the midnight sun. With the mirror −δ the
        // moon is the sun's EXACT antipode: midnight sun ⇒ moon always below the horizon.
        double phi = 80.0, delta = 30.0;
        for (float h : new float[] {-2.5f, -1.0f, 0.0f, 0.9f, 2.0f, 3.14f}) {
            Vector3f sun = rollFreeFull(phi, delta, h).transform(new Vector3f(0, 1, 0));
            Vector3f moon = rollFreeFull(phi, -delta, h + (float) Math.PI).transform(new Vector3f(0, 1, 0));
            assertTrue(sun.y > 0.0f, "midnight sun: the sun never sets (H=" + h + ")");
            assertTrue(moon.y < 0.0f, "midnight sun: the mirror-declination moon never rises (H=" + h + ")");
            // Antipode to float precision: the moon's angle is h + (float)π, so near the sin zero-crossing
            // the components carry ~1e-4 of float-π truncation — the SIGN law above is the invariant.
            assertEquals(-sun.x, moon.x, 2e-4f, "moon is the antipode (east) @H=" + h);
            assertEquals(-sun.y, moon.y, 2e-4f, "moon is the antipode (up) @H=" + h);
            assertEquals(-sun.z, moon.z, 2e-4f, "moon is the antipode (south) @H=" + h);
        }
        // And the OLD behaviour is pinned as wrong: with the sun's +δ the "moon" is up whenever the sun is.
        Vector3f wrongMoon = rollFreeFull(phi, delta, (float) Math.PI).transform(new Vector3f(0, 1, 0));
        assertTrue(wrongMoon.y > 0.0f, "same-declination moon rides the never-setting circle (the S11e bug)");
    }

    @Test
    void nanInputsDegradeToVanillaPose() {
        Quaternionf vanilla = new Quaternionf().rotationX(0.8f);
        Quaternionf composed = SolarPose.tiltedBodyPose(new Quaternionf().rotationX(0.8f), Double.NaN, 30.0);
        assertEquals(vanilla.x, composed.x, EPS);
        assertEquals(vanilla.w, composed.w, EPS);
        Quaternionf composed2 = SolarPose.tiltedBodyPose(new Quaternionf().rotationX(0.8f), 40.0, Double.NaN);
        assertEquals(vanilla.x, composed2.x, EPS);
        assertEquals(vanilla.w, composed2.w, EPS);
    }
}
