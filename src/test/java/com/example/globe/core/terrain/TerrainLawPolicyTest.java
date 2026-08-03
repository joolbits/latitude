package com.example.globe.core.terrain;

import com.example.globe.core.terrain.TerrainLawPolicy.Decision;
import com.example.globe.core.terrain.TerrainLawPolicy.JvmTerrainConfig;
import com.example.globe.core.terrain.TerrainLawPolicy.TerrainLaw;
import com.example.globe.core.terrain.TerrainLawPolicy.WarningKind;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P2-8 per-world terrain-law guard — the full policy table, row by row. The row numbers reference the
 * spec table in docs/binder/phase4-residual-closure-20260802.md (ship-on section).
 */
class TerrainLawPolicyTest {

    private static final TerrainLaw SHIPPED_ARMED =
            new TerrainLaw(TerrainLawPolicy.CURRENT_FORMULA_VERSION, true, 0.4, 1.0, 0.8);
    private static final TerrainLaw INERT_JVM =
            new TerrainLaw(TerrainLawPolicy.CURRENT_FORMULA_VERSION, false, 0.0, 1.0, 0.8);

    private static JvmTerrainConfig defaultsOnly(TerrainLaw law) {
        return new JvmTerrainConfig(law, false, false, false);
    }

    /** The full explicit set, strength included — the live-tuning shape (and gate-1's pins). */
    private static JvmTerrainConfig explicitFlags(TerrainLaw law) {
        return new JvmTerrainConfig(law, true, true, false);
    }

    /** A stale PARTIAL set: some -D present, but not the strength knob. */
    private static JvmTerrainConfig partialFlags(TerrainLaw law) {
        return new JvmTerrainConfig(law, true, false, false);
    }

    private static JvmTerrainConfig optIn(TerrainLaw law) {
        return new JvmTerrainConfig(law, true, true, true);
    }

    // ---- rows 1-4: unstamped worlds ------------------------------------------------------------

    @Test
    void newWorldStampsJvmLaw() {
        Decision d = TerrainLawPolicy.decide(Optional.empty(), false, defaultsOnly(SHIPPED_ARMED), true);
        assertEquals(SHIPPED_ARMED, d.effective());
        assertEquals(Optional.of(SHIPPED_ARMED), d.stampToWrite());
        assertFalse(d.stampAsInferred(), "a birth record is a fact, never an inference");
        assertEquals(WarningKind.STAMPED_NEW, d.warning());
    }

    @Test
    void newInertWorldStillStampsBirthRecord() {
        Decision d = TerrainLawPolicy.decide(Optional.empty(), false, defaultsOnly(INERT_JVM), true);
        assertEquals(Optional.of(INERT_JVM), d.stampToWrite(),
                "a world created OFF must be stamped OFF — a later armed open reads a fact, not a guess");
        assertFalse(d.stampAsInferred());
        assertEquals(WarningKind.STAMPED_NEW, d.warning());
    }

    @Test
    void legacyDefaultsKeepOffAndStampInferred() {
        // THE P2-8 case: pre-guard world, post-flip jar, bare launch. Terrain must stay OFF forever.
        Decision d = TerrainLawPolicy.decide(Optional.empty(), false, defaultsOnly(SHIPPED_ARMED), false);
        assertTrue(d.effective().isInert(), "a legacy world under defaults-only must NOT arm");
        assertEquals(Optional.of(TerrainLawPolicy.CANONICAL_OFF), d.stampToWrite());
        assertTrue(d.stampAsInferred(), "the legacy stamp is a reconstruction, and must say so");
        assertEquals(WarningKind.LEGACY_KEPT_OFF, d.warning());
    }

    @Test
    void legacyExplicitFlagsAdopt() {
        // The Phase-4 proof worlds were generated via -Ds: explicit flags describe how the world was
        // really made, so they win and re-stamp.
        Decision d = TerrainLawPolicy.decide(Optional.empty(), false, explicitFlags(SHIPPED_ARMED), false);
        assertEquals(SHIPPED_ARMED, d.effective());
        assertEquals(Optional.of(SHIPPED_ARMED), d.stampToWrite());
        assertFalse(d.stampAsInferred());
        assertEquals(WarningKind.LEGACY_ADOPTED_FLAGS, d.warning());
    }

    @Test
    void legacyOptInAdopts() {
        Decision d = TerrainLawPolicy.decide(Optional.empty(), false,
                new JvmTerrainConfig(SHIPPED_ARMED, false, false, true), false);
        assertEquals(SHIPPED_ARMED, d.effective());
        assertEquals(WarningKind.ADOPTED_BY_OPTIN, d.warning());
    }

    // ---- rows 5-8: stamped worlds, same formula ------------------------------------------------

    @Test
    void stampedMatchIsSilentNoRewrite() {
        Decision d = TerrainLawPolicy.decide(Optional.of(SHIPPED_ARMED), false, defaultsOnly(SHIPPED_ARMED), false);
        assertEquals(SHIPPED_ARMED, d.effective());
        assertEquals(Optional.empty(), d.stampToWrite(), "a matching stamp is never rewritten");
        assertEquals(WarningKind.NONE, d.warning());
    }

    @Test
    void bothInertMatchRegardlessOfKnobsAndVersion() {
        TerrainLaw oldInert = new TerrainLaw(0, false, 0.7, 0.3, 0.1); // ancient version, weird knobs, OFF
        Decision d = TerrainLawPolicy.decide(Optional.of(oldInert), false, defaultsOnly(INERT_JVM), false);
        assertEquals(WarningKind.NONE, d.warning(),
                "two inert laws are the same law: neither ever moved a block");
        assertEquals(Optional.empty(), d.stampToWrite());
    }

    @Test
    void mismatchOnDefaultsHoldsStamp() {
        // A future default retune (0.4 -> 0.5): every stamped world keeps its own law, quietly.
        TerrainLaw retuned = new TerrainLaw(TerrainLawPolicy.CURRENT_FORMULA_VERSION, true, 0.5, 1.0, 0.8);
        Decision d = TerrainLawPolicy.decide(Optional.of(SHIPPED_ARMED), false, defaultsOnly(retuned), false);
        assertEquals(SHIPPED_ARMED, d.effective(), "the world's own law wins over differing baked-in defaults");
        assertEquals(Optional.empty(), d.stampToWrite(), "never rewrite on hold");
        assertEquals(WarningKind.STAMP_HELD, d.warning());
    }

    @Test
    void armedStampWithDisarmedJvmPauses() {
        // Row 6b: stamp armed, wrapper not installed this run (defaults-only, disarmed defaults).
        Decision d = TerrainLawPolicy.decide(Optional.of(SHIPPED_ARMED), false, defaultsOnly(INERT_JVM), false);
        assertTrue(d.effective().isInert(), "an unhonorable armed stamp must pause, not pretend");
        assertEquals(0.0, d.effective().strength(), 0.0);
        assertEquals(Optional.empty(), d.stampToWrite(), "the stamp itself stays untouched");
        assertEquals(WarningKind.CANNOT_HONOR, d.warning());
    }

    @Test
    void mismatchWithExplicitFlagsAdoptsAndRestamps() {
        TerrainLaw dialed = new TerrainLaw(TerrainLawPolicy.CURRENT_FORMULA_VERSION, true, 0.6, 1.0, 0.8);
        Decision d = TerrainLawPolicy.decide(Optional.of(SHIPPED_ARMED), false, explicitFlags(dialed), false);
        assertEquals(dialed, d.effective(), "explicit -Ds are the live-tuning contract: flags win");
        assertEquals(Optional.of(dialed), d.stampToWrite());
        assertEquals(WarningKind.ADOPTED_BY_FLAGS, d.warning());
    }

    @Test
    void mismatchWithOptInAdopts() {
        TerrainLaw dialed = new TerrainLaw(TerrainLawPolicy.CURRENT_FORMULA_VERSION, true, 0.6, 1.0, 0.8);
        Decision d = TerrainLawPolicy.decide(Optional.of(SHIPPED_ARMED), false, optIn(dialed), false);
        assertEquals(dialed, d.effective());
        assertEquals(WarningKind.ADOPTED_BY_OPTIN, d.warning());
    }

    // ---- rows 9-10: the formula boundary --------------------------------------------------------

    @Test
    void formulaMismatchPausesEvenWithExplicitKnobs() {
        // The line explicit knob flags must NOT cross: same knob values under a different formula still
        // shear, so only the adoptJvmArgs opt-in crosses it.
        TerrainLaw oldFormulaArmed = new TerrainLaw(TerrainLawPolicy.CURRENT_FORMULA_VERSION + 1, true, 0.4, 1.0, 0.8);
        Decision d = TerrainLawPolicy.decide(Optional.of(oldFormulaArmed), false, explicitFlags(SHIPPED_ARMED), false);
        assertTrue(d.effective().isInert(), "a formula boundary pauses terrain regardless of knob -Ds");
        assertEquals(Optional.empty(), d.stampToWrite());
        assertEquals(WarningKind.FORMULA_PAUSED, d.warning());
    }

    @Test
    void formulaMismatchWithOptInUpgradesToCurrentVersion() {
        TerrainLaw oldFormulaArmed = new TerrainLaw(TerrainLawPolicy.CURRENT_FORMULA_VERSION + 1, true, 0.4, 1.0, 0.8);
        Decision d = TerrainLawPolicy.decide(Optional.of(oldFormulaArmed), false, optIn(SHIPPED_ARMED), false);
        assertEquals(SHIPPED_ARMED, d.effective());
        assertEquals(Optional.of(SHIPPED_ARMED), d.stampToWrite());
        assertEquals(TerrainLawPolicy.CURRENT_FORMULA_VERSION, d.stampToWrite().orElseThrow().formulaVersion(),
                "every written stamp carries the CURRENT formula version");
        assertEquals(WarningKind.ADOPTED_BY_OPTIN, d.warning());
    }

    // ---- sweep fixes 2026-08-02 -----------------------------------------------------------------

    @Test
    void legacyPartialExplicitArmedStaysOff() {
        // THE stale-launcher-profile case (found by the adversarial sweep): a profile still carrying
        // -Dlatitude.terrainV2.enabled=true from the pre-flip era (when baked-in strength was 0.0, so
        // that arg meant "no-op") must NOT arm a legacy world — arming requires the strength knob.
        Decision d = TerrainLawPolicy.decide(Optional.empty(), false, partialFlags(SHIPPED_ARMED), false);
        assertTrue(d.effective().isInert(), "stale partial -Ds must not arm a legacy world");
        assertEquals(Optional.of(TerrainLawPolicy.CANONICAL_OFF), d.stampToWrite());
        assertEquals(WarningKind.LEGACY_KEPT_OFF, d.warning());
    }

    @Test
    void stampedInertPartialFlagsDoNotArm() {
        // Same rule one step later: a world already stamped OFF (the inferred legacy stamp) opened in a
        // profile with stale partial -Ds must stay OFF.
        Decision d = TerrainLawPolicy.decide(Optional.of(TerrainLawPolicy.CANONICAL_OFF), true,
                partialFlags(SHIPPED_ARMED), false);
        assertTrue(d.effective().isInert());
        assertEquals(Optional.empty(), d.stampToWrite());
        assertEquals(WarningKind.STAMP_HELD, d.warning());
    }

    @Test
    void partialFlagsMayStillDisarmAndRetune() {
        // The carve-out is only about ARMING: partial explicit flags may disarm an armed world...
        Decision disarm = TerrainLawPolicy.decide(Optional.of(SHIPPED_ARMED), false,
                partialFlags(INERT_JVM), false);
        assertEquals(INERT_JVM, disarm.effective(), "partial flags may disarm");
        assertEquals(WarningKind.ADOPTED_BY_FLAGS, disarm.warning());
        // ...but retuning an armed world's strength requires the strength knob (partial set holds).
        TerrainLaw retuned = new TerrainLaw(TerrainLawPolicy.CURRENT_FORMULA_VERSION, true, 0.6, 1.0, 0.8);
        Decision retune = TerrainLawPolicy.decide(Optional.of(SHIPPED_ARMED), false,
                explicitFlags(retuned), false);
        assertEquals(retuned, retune.effective(), "the full explicit set retunes as before");
    }

    @Test
    void inertStampIgnoresFormulaVersion() {
        // An inert stamp never shaped a block: its formula version is meaningless and must not trigger
        // the perpetual false "different version" pause (sweep finding).
        TerrainLaw ancientInert = new TerrainLaw(TerrainLawPolicy.CURRENT_FORMULA_VERSION + 5, false, 0.0, 1.0, 0.8);
        Decision d = TerrainLawPolicy.decide(Optional.of(ancientInert), true, defaultsOnly(SHIPPED_ARMED), false);
        assertEquals(WarningKind.STAMP_HELD, d.warning(),
                "an inert stamp under differing defaults holds quietly — never FORMULA_PAUSED");
        assertTrue(d.effective().isInert());
    }

    @Test
    void formulaPausedLogOmitsFlagRemedy() {
        TerrainLaw oldArmed = new TerrainLaw(TerrainLawPolicy.CURRENT_FORMULA_VERSION + 1, true, 0.4, 1.0, 0.8);
        String log = TerrainLawPolicy.logText(WarningKind.FORMULA_PAUSED, oldArmed, false,
                new TerrainLaw(oldArmed.formulaVersion(), false, 0.0, 1.0, 0.8), SHIPPED_ARMED);
        assertTrue(log.contains("adoptJvmArgs"), "the one honest remedy must be present: " + log);
        assertFalse(log.contains("This world's own law as flags"),
                "the policy refuses knob -Ds across a formula boundary, so the log must not offer them: " + log);
    }

    // ---- laws, remedies, texts ------------------------------------------------------------------

    @Test
    void lawsMatchEpsilon() {
        TerrainLaw a = new TerrainLaw(1, true, 0.4, 1.0, 0.8);
        TerrainLaw closeEnough = new TerrainLaw(1, true, 0.4 + 1e-12, 1.0, 0.8);
        TerrainLaw different = new TerrainLaw(1, true, 0.41, 1.0, 0.8);
        assertTrue(TerrainLawPolicy.lawsMatch(a, closeEnough), "NBT round-trip jitter must not read as a retune");
        assertFalse(TerrainLawPolicy.lawsMatch(a, different));
    }

    @Test
    void everyWrittenStampCarriesCurrentFormulaVersion() {
        // Sweep every decide() path that writes a stamp; each written stamp must be v-current.
        JvmTerrainConfig[] configs = {
                defaultsOnly(SHIPPED_ARMED), explicitFlags(SHIPPED_ARMED), optIn(SHIPPED_ARMED),
                defaultsOnly(INERT_JVM), explicitFlags(INERT_JVM), optIn(INERT_JVM),
        };
        Optional<TerrainLaw>[] stamps = new Optional[] {
                Optional.<TerrainLaw>empty(),
                Optional.of(new TerrainLaw(TerrainLawPolicy.CURRENT_FORMULA_VERSION, true, 0.9, 1.0, 0.8)),
                Optional.of(new TerrainLaw(TerrainLawPolicy.CURRENT_FORMULA_VERSION + 3, true, 0.4, 1.0, 0.8)),
        };
        for (JvmTerrainConfig cfg : configs) {
            for (Optional<TerrainLaw> stamp : stamps) {
                for (boolean isNew : new boolean[] {true, false}) {
                    Decision d = TerrainLawPolicy.decide(stamp, false, cfg, isNew);
                    d.stampToWrite().ifPresent(written -> assertEquals(
                            TerrainLawPolicy.CURRENT_FORMULA_VERSION, written.formulaVersion(),
                            "written stamp must always be v-current (cfg=" + cfg + " stamp=" + stamp + ")"));
                }
            }
        }
    }

    @Test
    void currentFormulaVersionIsOne() {
        // Bump tripwire: if you changed the bias math's SHAPE, bump the version AND this test together —
        // that is the moment to think about every stamped world in existence.
        assertEquals(1, TerrainLawPolicy.CURRENT_FORMULA_VERSION);
    }

    @Test
    void remedyFlagsForInertStamp() {
        assertEquals("-Dlatitude.terrainV2.enabled=false",
                TerrainLawPolicy.remedyFlags(TerrainLawPolicy.CANONICAL_OFF));
    }

    @Test
    void remedyFlagsForArmedStamp() {
        assertEquals("-Dlatitude.geoV2.enabled=true -Dlatitude.terrainV2.enabled=true"
                        + " -Dlatitude.terrainV2.strength=0.4",
                TerrainLawPolicy.remedyFlags(SHIPPED_ARMED),
                "default-valued ratio/grip must not clutter the remedy line");
        assertEquals("-Dlatitude.geoV2.enabled=true -Dlatitude.terrainV2.enabled=true"
                        + " -Dlatitude.terrainV2.strength=0.4 -Dlatitude.terrainV2.oceanStrengthRatio=0.5",
                TerrainLawPolicy.remedyFlags(
                        new TerrainLaw(TerrainLawPolicy.CURRENT_FORMULA_VERSION, true, 0.4, 0.5, 0.8)));
    }

    @Test
    void chatTextsContainNoJargon() {
        for (WarningKind kind : WarningKind.values()) {
            String chat = TerrainLawPolicy.chatText(kind);
            for (String jargon : new String[] {"geoV2", "NBT", "SavedData", "formulaVersion", "-D"}) {
                assertFalse(chat.contains(jargon),
                        kind + " chat line leaks jargon '" + jargon + "': " + chat);
            }
        }
    }

    @Test
    void chatOnlyForTheDecisionsThatNeedIt() {
        assertFalse(TerrainLawPolicy.chatText(WarningKind.LEGACY_KEPT_OFF).isEmpty());
        assertFalse(TerrainLawPolicy.chatText(WarningKind.CANNOT_HONOR).isEmpty());
        assertFalse(TerrainLawPolicy.chatText(WarningKind.FORMULA_PAUSED).isEmpty());
        assertFalse(TerrainLawPolicy.chatText(WarningKind.ADOPTED_BY_FLAGS).isEmpty());
        assertFalse(TerrainLawPolicy.chatText(WarningKind.ADOPTED_BY_OPTIN).isEmpty());
        assertTrue(TerrainLawPolicy.chatText(WarningKind.NONE).isEmpty());
        assertTrue(TerrainLawPolicy.chatText(WarningKind.STAMPED_NEW).isEmpty());
        assertTrue(TerrainLawPolicy.chatText(WarningKind.LEGACY_ADOPTED_FLAGS).isEmpty());
        assertTrue(TerrainLawPolicy.chatText(WarningKind.STAMP_HELD).isEmpty());
    }

    @Test
    void inferredWordingSaysPredates() {
        String log = TerrainLawPolicy.logText(WarningKind.LEGACY_KEPT_OFF,
                TerrainLawPolicy.CANONICAL_OFF, true, TerrainLawPolicy.CANONICAL_OFF, SHIPPED_ARMED);
        assertTrue(log.contains("before terrain shaping existed"),
                "an inferred stamp must be worded as a reconstruction, not a birth record: " + log);
        assertTrue(log.contains("adoptJvmArgs"), "the remedy must be one copy-paste away: " + log);
    }
}
