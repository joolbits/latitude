package com.example.globe.core;

import org.joml.Quaternionf;
import org.joml.Quaternionfc;

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

    /** The tilted star-sphere pose: tilt only, no declination ({@code ZP(−φ) · vanillaBodyRotation}). */
    public static Quaternionf tiltedStarPose(Quaternionfc vanillaBodyRotation, double signedLatDeg) {
        return tiltedBodyPose(vanillaBodyRotation, signedLatDeg, 0.0);
    }
}
