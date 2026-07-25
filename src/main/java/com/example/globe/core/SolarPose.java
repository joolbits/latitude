package com.example.globe.core;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

/**
 * Solar Tilt P2 — the pure CELESTIAL POSE composition (sweep amendment A6, option B: the per-body pose is
 * rebuilt from {@link SolarTilt}'s law and verified headlessly against {@link SolarTilt#solarDirection}).
 * JOML-only, ZERO Minecraft imports — JOML is a standalone math library (no MC bootstrap), so this stays in
 * the plain-JVM-testable Core Logic layer like the rest of {@code core} (the suite already runs JOML).
 *
 * <h2>The composition (numerically solved, not axis-guessed)</h2>
 * Vanilla 26.2 {@code SkyRenderer.renderSunMoonAndStars} composes {@code YP(-90°)} then, per body,
 * {@code XP(bodyAngle)} (bytecode receipt in the design §3a); each body rests at local {@code +Y}. The tilt
 * law says the world-frame direction of a body must be {@link SolarTilt#solarDirection}(φ, δ, H). A
 * brute-force solve over every axis/sign candidate against that vector (four (φ, δ, H) probes, error
 * &lt; 1e-5 — the {@code SolarProbe} experiment, re-proven by {@code SolarPoseTest} inside the suite) yields
 * exactly ONE composition:
 * <pre>
 *   YP(-90) · ZP(−φ) · XP(H_v) · ZP(+δ)  applied to ŷ   ==   solarDirection(φ, δ, −H_v)
 * </pre>
 * where {@code H_v} is VANILLA's body angle (its sign convention is the negative of {@link SolarTilt}'s hour
 * angle — vanilla's equatorial arc is {@code (−sin H_v, cos H_v, 0)} in world {east, up, south}, which is
 * {@code solarDirection(0, 0, −H_v)}; elevation is even in H, so the functional layer never felt the sign).
 *
 * <p>So the P2 mixin replaces each per-body {@code mulPose(XP(H_v))} argument with
 * {@link #tiltedBodyPose}{@code (q_vanilla, φ, δ)} = {@code ZP(−φ) · q_vanilla · ZP(+δ)} — the outer yaw is
 * untouched. Properties that make this the shipping choice over a {@code rotationTo} rebuild:
 * <ul>
 *   <li><b>Vanilla-identical at φ = 0, δ = 0 BY CONSTRUCTION</b> ({@code ZP(0)} is the identity quaternion, so
 *       the product returns {@code q_vanilla}'s rotation unchanged) — the §5 equator regression guard holds
 *       algebraically, pinned numerically by the identity test.</li>
 *   <li><b>Phase parity for free:</b> {@code q_vanilla} carries vanilla's exact per-frame angle INCLUDING its
 *       dawn/dusk easing curve — no re-derived clock, no partial-tick drift (design §4d's "reuse vanilla's
 *       sunAngle as H" option).</li>
 *   <li><b>No roll instability:</b> a shortest-arc {@code rotationTo} degenerates near the zenith (the sun
 *       quad would visibly spin at equator noon); a fixed composition has continuous roll everywhere, and one
 *       formula serves sun, moon AND the star sphere (a direction alone under-determines a sphere's
 *       orientation; a composition does not).</li>
 * </ul>
 *
 * <p><b>Stars</b> take the tilt only ({@code δ = 0} — design §5: the δ offset is meaningless for a
 * full-sphere field): {@link #tiltedStarPose}. The star wheel then circles a celestial pole at altitude φ —
 * the polar-night "stars wheel over a tilted pole" payoff.
 */
public final class SolarPose {

    private SolarPose() {
    }

    /**
     * The tilted per-body pose for the sun / moon: {@code ZP(−φ) · vanillaBodyRotation · ZP(+δ)}. With the
     * untouched outer {@code YP(-90)} this lands the body exactly on {@link SolarTilt#solarDirection}
     * (φ, δ, −H_v). Pure; a NaN latitude/declination degrades to the vanilla rotation (never a NaN pose —
     * a NaN quaternion would blank the whole sky).
     */
    public static Quaternionf tiltedBodyPose(Quaternionfc vanillaBodyRotation, double signedLatDeg,
                                             double deltaDeg) {
        if (Double.isNaN(signedLatDeg) || Double.isNaN(deltaDeg)) {
            return new Quaternionf(vanillaBodyRotation);
        }
        return new Quaternionf().rotationZ((float) -Math.toRadians(signedLatDeg))
                .mul(vanillaBodyRotation)
                .mul(new Quaternionf().rotationZ((float) Math.toRadians(deltaDeg)));
    }

    /** The tilted star-sphere pose: tilt only, no declination ({@code ZP(−φ) · vanillaBodyRotation}).
     *  Deliberately NOT roll-freed (S11d applies to the billboard QUADS only): for the star sphere the
     *  "twist" IS the wheel's spin — stripping it would freeze/misphase the star field. */
    public static Quaternionf tiltedStarPose(Quaternionfc vanillaBodyRotation, double signedLatDeg) {
        return tiltedBodyPose(vanillaBodyRotation, signedLatDeg, 0.0);
    }

    /**
     * S11(d) — the ROLL-FREE tilted billboard pose (the TEST 101 "diamond sun" fix). The bare composition
     * {@link #tiltedBodyPose} points the body correctly but carries a net IN-PLANE roll of the quad (the two
     * {@code ZP} rotations do not cancel in the quad plane), so away from the equator the square sun renders
     * as a ~45° diamond. This keeps the composed DIRECTION exactly and rebuilds the in-plane axes
     * horizon-locked:
     * <ul>
     *   <li>{@code s} = the composed pose's body direction (local frame; the outer {@code YP(-90)} yaw maps
     *       local Y to world Y unchanged, so local horizontality == world horizontality);</li>
     *   <li>the quad's local-X edge is re-anchored to {@code normalize(cross(ŷ, s))} — horizontal (zero
     *       world-Y) and perpendicular to the view direction — with the BRANCH chosen to follow the composed
     *       pose's own X ({@code dot > 0}), i.e. the minimal counter-roll, which at φ = 0, δ = 0 reproduces
     *       vanilla's basis exactly (probe + {@code SolarPoseTest}: basis error ~2e-7);</li>
     *   <li>{@code z = x × y} completes the right-handed frame; the quaternion is read off the matrix.</li>
     * </ul>
     * Probe-verified across the (φ, δ, H) grid: direction error ~7e-7, residual roll (world-Y of the X edge)
     * ~1e-7 away from the zenith. AT the zenith/nadir ({@code |up| ≥ ~0.9999}, where "horizontal edge" is
     * ill-defined and the roll of a square is invisible) it falls back to the bare composition (also NaN-safe:
     * a NaN input degrades through {@link #tiltedBodyPose} to the vanilla rotation). NOTE (a considered
     * trade): plain swing-twist stripping was probed first and REJECTED — it preserves direction but leaves a
     * residual apparent roll equal to the direction's south-component (worst ~1.0), failing the horizon
     * invariant.
     */
    public static Quaternionf rollFreeTiltedBodyPose(Quaternionfc vanillaBodyRotation, double signedLatDeg,
                                                     double deltaDeg) {
        Quaternionf composed = tiltedBodyPose(vanillaBodyRotation, signedLatDeg, deltaDeg);
        Vector3f s = composed.transform(new Vector3f(0, 1, 0)); // body direction in the pre-yaw local frame
        Vector3f x = new Vector3f(s.z, 0, -s.x);                // cross(ŷ, s): horizontal, perp to the view
        float n = x.length();
        if (n < 1e-4f) {
            return composed; // zenith/nadir: horizontal edge undefined, roll invisible — keep the composition
        }
        x.div(n);
        Vector3f xComposed = composed.transform(new Vector3f(1, 0, 0));
        if (x.dot(xComposed) < 0.0f) {
            x.negate(); // minimal counter-roll: stay on the composed pose's own side (vanilla-exact at φ=δ=0)
        }
        Vector3f z = new Vector3f(x).cross(s); // right-handed completion (z = x × y)
        return new Quaternionf().setFromNormalized(new Matrix3f(x, s, z));
    }
}
