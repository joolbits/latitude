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
