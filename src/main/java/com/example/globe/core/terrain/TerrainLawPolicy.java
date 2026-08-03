package com.example.globe.core.terrain;

import java.util.Optional;

/**
 * P2-8 per-world TERRAIN LAW guard — the pure policy core (zero Minecraft imports, fully unit-tested).
 *
 * <p><b>The hazard this closes (audit P2-8, the synthesis critic's M1):</b> nothing used to persist the
 * Phase 4 terrain configuration per world. The same world reopened under a different strength — or under a
 * future change to the bias formula itself — would generate its NEW chunks under a DIFFERENT terrain law
 * than its old ones, leaving a permanent step at every chunk boundary between the two eras ("chunk shear").
 * Harmless while the feature was opt-in; load-bearing the moment the defaults flipped ON (2026-08-02, owner
 * decision): every pre-flip world was generated with terrain OFF, and the flipped defaults would silently
 * re-arm it at strength 0.4.
 *
 * <p><b>The design (ergonomics-first winner, 2026-08-02 judge panel):</b> every world carries a stamp of the
 * terrain law it was born under ({@link TerrainLaw}); on each load {@link #decide} chooses the EFFECTIVE law
 * from stamp + JVM config + provenance. The key idea is <i>provenance-sensitive precedence</i>:
 * <ul>
 *   <li><b>Baked-in defaults defer to the stamp.</b> A legacy (unstamped) world under defaults-only keeps
 *       terrain OFF forever — P2-8 closed by prevention, not by warning.</li>
 *   <li><b>Explicit {@code -D}s beat the stamp.</b> Every live-tuning workflow, and gate-1's build.gradle
 *       pins (which forward all five knobs as real {@code -D}s), keeps working unchanged.</li>
 *   <li><b>A formula-version boundary needs an explicit opt-in</b> ({@code latitude.terrainV2.adoptJvmArgs})
 *       — knob {@code -D}s alone cannot cross it, because a formula change shears even at equal knob
 *       values.</li>
 * </ul>
 *
 * <p><b>Run-shape outcomes:</b> the owner's pre-flip world on a post-flip jar, bare Modrinth launch →
 * terrain stays OFF, one plain-language chat line. A brand-new world → armed at the shipped defaults,
 * silent. Gate-1/biomePreview → pins are explicit, pinned OFF wins deterministically. Dial-turning with
 * {@code -D}s → flags win, world re-stamped, honest warning. A future default retune (say 0.4 → 0.5) →
 * every stamped world keeps its own 0.4 (STAMP_HELD, quiet).
 */
public final class TerrainLawPolicy {

    private TerrainLawPolicy() {
    }

    /**
     * Bump on ANY change to the SHAPE of the bias math (lift curve shape, carve/grip semantics, edge
     * geometry consumed) — not on mere knob-value changes. See the reciprocal comments atop
     * {@code GeoTerrainBiasFunction} and {@code GeoTerrainPrelimSurfaceFunction}: those are the classes
     * whose edits must come back here.
     */
    public static final int CURRENT_FORMULA_VERSION = 1;

    /** Numeric tolerance for knob comparison (doubles that survived an NBT round trip). */
    private static final double EPSILON = 1e-9;

    /**
     * Everything that changes generated shapes. {@code enabled} is the ARMED CONJUNCTION
     * ({@code terrainV2.enabled && geoV2.enabled}), not either raw flag — a law is "armed" only when the
     * wrapper both installs and has geography to read.
     */
    public record TerrainLaw(int formulaVersion, boolean enabled, double strength,
                             double oceanRatio, double gripWidth) {
        /** An inert law generates byte-identical-to-vanilla-pipeline terrain (wrapper no-op). */
        public boolean isInert() {
            return !enabled || strength == 0.0;
        }

        /** Human-readable one-liner for logs. */
        public String describe() {
            return isInert()
                    ? "OFF (v" + formulaVersion + ")"
                    : "ARMED v" + formulaVersion + " strength=" + strength
                            + " oceanRatio=" + oceanRatio + " gripWidth=" + gripWidth;
        }
    }

    /** The canonical inert law legacy worlds are stamped with (knobs at their shipped neutral values). */
    public static final TerrainLaw CANONICAL_OFF =
            new TerrainLaw(CURRENT_FORMULA_VERSION, false, 0.0, 1.0, 0.8);

    /**
     * This run's JVM-side terrain configuration plus its PROVENANCE: {@code anyKnobExplicit} is true iff
     * any of the five terrain-law properties was passed as a real {@code -D} (vs baked-in defaults);
     * {@code adoptJvmArgs} is the per-run re-stamp opt-in.
     */
    public record JvmTerrainConfig(TerrainLaw law, boolean anyKnobExplicit, boolean adoptJvmArgs) {
    }

    /** What kind of operator-visible message the decision warrants. */
    public enum WarningKind {
        /** Silent match — log nothing. */
        NONE,
        /** Brand-new world stamped with this run's law. Log INFO only. */
        STAMPED_NEW,
        /** Legacy world kept OFF under defaults-only (THE P2-8 save). Chat + log WARN. */
        LEGACY_KEPT_OFF,
        /** Legacy world adopted explicit {@code -D}s (dev workflow: the flags describe how it was really
         *  made). Log INFO only. */
        LEGACY_ADOPTED_FLAGS,
        /** Stamped world kept its own law over differing baked-in defaults (quiet P2-8 save on a future
         *  default retune). Log INFO only. */
        STAMP_HELD,
        /** Explicit {@code -D}s re-stamped a world whose stamp disagreed. Chat + log WARN. */
        ADOPTED_BY_FLAGS,
        /** The adoptJvmArgs opt-in re-stamped the world. Chat + log WARN. */
        ADOPTED_BY_OPTIN,
        /** Stamp is ARMED but this run's config cannot honor it (wrapper disarmed). Chat + log WARN. */
        CANNOT_HONOR,
        /** Stamp is from a DIFFERENT formula version; terrain paused to prevent shear. Chat + log WARN. */
        FORMULA_PAUSED
    }

    /**
     * The decision: the law the run must generate under, the stamp to write (empty = leave the stored
     * stamp untouched), whether that stamp is INFERRED (legacy reconstruction, not a birth record), and
     * the warning class.
     */
    public record Decision(TerrainLaw effective, Optional<TerrainLaw> stampToWrite,
                           boolean stampAsInferred, WarningKind warning) {
    }

    /**
     * The whole policy. See the class javadoc for the intent; the row numbers here match the spec table
     * in {@code docs/binder/phase4-residual-closure-20260802.md}'s ship-on section.
     */
    public static Decision decide(Optional<TerrainLaw> stampOpt, boolean stampWasInferred,
                                  JvmTerrainConfig jvm, boolean worldIsNew) {
        TerrainLaw jvmLaw = jvm.law();

        if (stampOpt.isEmpty()) {
            if (worldIsNew) {
                // Row 1 — birth record, EVEN WHEN INERT: a later armed open of a created-OFF world should
                // read "created OFF" as a known fact, not a legacy guess.
                return new Decision(jvmLaw, Optional.of(jvmLaw), false, WarningKind.STAMPED_NEW);
            }
            if (jvm.adoptJvmArgs()) {
                // Row 4.
                return new Decision(jvmLaw, Optional.of(jvmLaw), false, WarningKind.ADOPTED_BY_OPTIN);
            }
            if (jvm.anyKnobExplicit()) {
                // Row 3 — explicit flags on a legacy world describe how it was really made (the Phase-4
                // proof worlds were all generated via -Ds).
                return new Decision(jvmLaw, Optional.of(jvmLaw), false, WarningKind.LEGACY_ADOPTED_FLAGS);
            }
            // Row 2 — THE P2-8 case: pre-guard world under baked-in defaults. Terrain stays OFF forever.
            return new Decision(CANONICAL_OFF, Optional.of(CANONICAL_OFF), true, WarningKind.LEGACY_KEPT_OFF);
        }

        TerrainLaw stamp = stampOpt.get();

        // Row 5 short-circuit — two inert laws are the SAME law regardless of knob values or formula
        // version: neither ever moved a block.
        if (stamp.isInert() && jvmLaw.isInert()) {
            return new Decision(stamp, Optional.empty(), stampWasInferred, WarningKind.NONE);
        }

        // Rows 9/10 — the formula boundary. Explicit knob -Ds do NOT cross it (equal knob values under a
        // different formula still shear); only the adoptJvmArgs opt-in does.
        if (stamp.formulaVersion() != CURRENT_FORMULA_VERSION) {
            if (jvm.adoptJvmArgs()) {
                return new Decision(jvmLaw, Optional.of(jvmLaw), false, WarningKind.ADOPTED_BY_OPTIN);
            }
            return new Decision(paused(stamp), Optional.empty(), stampWasInferred, WarningKind.FORMULA_PAUSED);
        }

        // Row 5 — full match: silent, no rewrite.
        if (lawsMatch(stamp, jvmLaw)) {
            return new Decision(stamp, Optional.empty(), stampWasInferred, WarningKind.NONE);
        }

        // Values differ, same formula. Provenance decides.
        if (jvm.adoptJvmArgs()) {
            // Row 8.
            return new Decision(jvmLaw, Optional.of(jvmLaw), false, WarningKind.ADOPTED_BY_OPTIN);
        }
        if (jvm.anyKnobExplicit()) {
            // Row 7 — dial-turning; flags always win, world re-stamped, honest warning.
            return new Decision(jvmLaw, Optional.of(jvmLaw), false, WarningKind.ADOPTED_BY_FLAGS);
        }
        if (!stamp.isInert() && !jvmLaw.enabled()) {
            // Row 6b — armed stamp, wrapper not armed this run (defaults-only). The wrapper was never
            // installed, so the stamp CANNOT be honored; the effective law must not claim biasing (labels,
            // mirror veto, spawn clamp all read it).
            return new Decision(paused(stamp), Optional.empty(), stampWasInferred, WarningKind.CANNOT_HONOR);
        }
        // Row 6 — differing baked-in defaults (e.g. a future retune): the world's own law wins, quietly.
        return new Decision(stamp, Optional.empty(), stampWasInferred, WarningKind.STAMP_HELD);
    }

    /** The "cannot honor / paused" effective law: disarmed, but carrying the stamp's identity knobs. */
    private static TerrainLaw paused(TerrainLaw stamp) {
        return new TerrainLaw(stamp.formulaVersion(), false, 0.0, stamp.oceanRatio(), stamp.gripWidth());
    }

    /**
     * True iff the two laws generate identical terrain: both inert (regardless of knobs/version), OR same
     * formula version, same armed state, and every knob within {@link #EPSILON}.
     */
    public static boolean lawsMatch(TerrainLaw a, TerrainLaw b) {
        if (a.isInert() && b.isInert()) {
            return true;
        }
        return a.formulaVersion() == b.formulaVersion()
                && a.enabled() == b.enabled()
                && Math.abs(a.strength() - b.strength()) <= EPSILON
                && Math.abs(a.oceanRatio() - b.oceanRatio()) <= EPSILON
                && Math.abs(a.gripWidth() - b.gripWidth()) <= EPSILON;
    }

    /**
     * The minimal {@code -D} set reproducing the stamped law, for copy-paste from the log:
     * {@code -Dlatitude.terrainV2.enabled=false} for an inert stamp; the enabled/geoV2/strength lines
     * (plus any non-default ratio/grip) for an armed one.
     */
    public static String remedyFlags(TerrainLaw stamped) {
        if (stamped.isInert()) {
            return "-Dlatitude.terrainV2.enabled=false";
        }
        StringBuilder sb = new StringBuilder("-Dlatitude.geoV2.enabled=true -Dlatitude.terrainV2.enabled=true"
                + " -Dlatitude.terrainV2.strength=" + stamped.strength());
        if (Math.abs(stamped.oceanRatio() - 1.0) > EPSILON) {
            sb.append(" -Dlatitude.terrainV2.oceanStrengthRatio=").append(stamped.oceanRatio());
        }
        if (Math.abs(stamped.gripWidth() - 0.8) > EPSILON) {
            sb.append(" -Dlatitude.terrainV2.gripWidth=").append(stamped.gripWidth());
        }
        return sb.toString();
    }

    private static final String ADOPT_REMEDY =
            "To adopt this run's terrain settings permanently, launch with"
                    + " -Dlatitude.terrainV2.adoptJvmArgs=true — expect a visible step where old land meets new.";

    /** The full operator log line for a decision (always names both laws; always ends with the remedy). */
    public static String logText(WarningKind kind, TerrainLaw stamped, boolean inferred,
                                 TerrainLaw effective, TerrainLaw jvmLaw) {
        String stampSide = stamped == null ? "no stamp"
                : (inferred
                        ? "appears to have been created before terrain shaping existed (treated as OFF)"
                        : "stamped " + stamped.describe());
        String base = "Latitude terrain law [" + kind + "]: world " + stampSide
                + "; this run's config " + jvmLaw.describe()
                + "; effective " + effective.describe() + ".";
        return switch (kind) {
            case NONE -> "";
            case STAMPED_NEW -> base + " New world stamped with this run's terrain law.";
            case LEGACY_ADOPTED_FLAGS -> base
                    + " Legacy world adopted this run's explicit -D flags (they describe how it was made).";
            case LEGACY_KEPT_OFF, FORMULA_PAUSED -> base
                    + " " + ADOPT_REMEDY
                    + (stamped != null ? " This world's own law as flags: " + remedyFlags(stamped) : "");
            case STAMP_HELD -> "Latitude terrain law: keeping this world's own settings ("
                    + stamped.describe() + ") over this build's defaults (" + jvmLaw.describe()
                    + "). Pass the -D flags explicitly, or -Dlatitude.terrainV2.adoptJvmArgs=true, to change them.";
            case CANNOT_HONOR, ADOPTED_BY_FLAGS, ADOPTED_BY_OPTIN -> base
                    + (stamped != null ? " This world's stamped law as flags: " + remedyFlags(stamped) : "");
        };
    }

    /**
     * The player-facing chat line, plain language only (no flag names, no NBT/geoV2 jargon — the owner is
     * a non-programmer). Empty string = no chat for this kind.
     */
    public static String chatText(WarningKind kind) {
        return switch (kind) {
            case LEGACY_KEPT_OFF -> "Latitude: this world was made before the new terrain shaping existed, "
                    + "so shaping stays OFF here — new land will keep matching what you've already explored. "
                    + "Fresh worlds get the new terrain automatically. (The game log shows how to opt this "
                    + "world in.)";
            case CANNOT_HONOR -> "Latitude: this world was made with terrain shaping ON, but shaping is "
                    + "turned off in this game session, so it is paused here. New land may not line up with "
                    + "older land until it is turned back on.";
            case FORMULA_PAUSED -> "Latitude: this world's terrain was shaped by a different version of "
                    + "Latitude, so terrain shaping is paused here to keep new land lining up with the old. "
                    + "See the game log for how to switch this world to the current terrain.";
            case ADOPTED_BY_FLAGS -> "Latitude: your launch options changed this world's terrain settings. "
                    + "Land generated from now on uses the new settings; places you've already explored keep "
                    + "their shape, and you may see a step where old and new areas meet.";
            case ADOPTED_BY_OPTIN -> "Latitude: this world now uses the current terrain settings (you opted "
                    + "in). Existing land keeps its shape; brand-new areas may show a step where old meets new.";
            case NONE, STAMPED_NEW, LEGACY_ADOPTED_FLAGS, STAMP_HELD -> "";
        };
    }
}
