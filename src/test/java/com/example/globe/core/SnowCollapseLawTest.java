package com.example.globe.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure tests for {@link SnowCollapseLaw} (S30 THE REAL SNOW COLLAPSE): the exact trigger signature (with every
 * near-miss), the span flood-fill (connectivity + cap), the deterministic distance-ordered stagger, and the
 * anti-entombment carve-out. No Minecraft on the classpath -- the block reads, falling-block spawns, and audio
 * live in {@code world.SnowCollapseRuntime} and are proven at live flight.
 */
class SnowCollapseLawTest {

    // --- constant pins (a future accidental change is caught here) ---------------------------------------

    @Test
    void constantsPinnedToDesign() {
        assertEquals(6, SnowCollapseLaw.TRIGGER_MIN_AIR_BELOW_MARKER, "need >= 6 air to drop the victim");
        assertEquals(24, SnowCollapseLaw.MAX_SPAN_COLUMNS, "flood-fill cap keeps a collapse bounded");
        assertEquals(10, SnowCollapseLaw.TELEGRAPH_TICKS, "~0.5s of cracking before the first fall");
        assertEquals(10, SnowCollapseLaw.MAX_STAGGER_TICKS, "outward ripple finishes within ~0.5s");
        assertEquals(1, SnowCollapseLaw.ANTI_ENTOMB_MANHATTAN_RADIUS, "trigger + orthogonal neighbours never fall");
        assertTrue(SnowCollapseLaw.TELEGRAPH_TICKS > 0, "telegraph must precede the collapse");
    }

    // --- matchesTriggerSignature: the exact sandwich, no false positives ---------------------------------

    @Test
    void fullSandwichTriggers() {
        assertTrue(SnowCollapseLaw.matchesTriggerSignature(true, true, 6), "snow/powder/6-air is the trigger");
        assertTrue(SnowCollapseLaw.matchesTriggerSignature(true, true, 40), "a deeper void still triggers");
    }

    @Test
    void everyNearMissFailsClosed() {
        assertFalse(SnowCollapseLaw.matchesTriggerSignature(false, true, 40),
                "not standing on snow_block (solid ground) never triggers");
        assertFalse(SnowCollapseLaw.matchesTriggerSignature(true, false, 40),
                "no powder marker below (ordinary snowfield) never triggers");
        assertFalse(SnowCollapseLaw.matchesTriggerSignature(true, true, 5),
                "only 5 air below the marker is too shallow (< 6)");
        assertFalse(SnowCollapseLaw.matchesTriggerSignature(true, true, 0),
                "no air at all (a buried powder seam) never triggers");
    }

    // --- floodFillSpan: 4-neighbour connectivity, cap, disconnected islands excluded ---------------------

    /** A boolean-grid sandwich predicate (out of bounds = not a sandwich). */
    private static SnowCollapseLaw.ColumnPredicate grid(boolean[][] g) {
        return (x, z) -> x >= 0 && x < g.length && z >= 0 && z < g[x].length && g[x][z];
    }

    @Test
    void floodFillClaimsTheConnectedSpanOnly() {
        // A plus-shaped connected region around (2,2), plus a DISCONNECTED island at (5,5) that must be excluded.
        boolean[][] g = new boolean[7][7];
        g[2][2] = true;
        g[1][2] = true;
        g[3][2] = true;
        g[2][1] = true;
        g[2][3] = true; // 5-cell plus
        g[5][5] = true; // island (diagonal-only would still be disconnected under 4-neighbour)
        List<int[]> span = SnowCollapseLaw.floodFillSpan(grid(g), 2, 2, SnowCollapseLaw.MAX_SPAN_COLUMNS);
        assertEquals(5, span.size(), "the connected plus is claimed; the island is not");
        assertEquals(2, span.get(0)[0], "BFS starts at the trigger column");
        assertEquals(2, span.get(0)[1]);
    }

    @Test
    void floodFillHonoursTheColumnCap() {
        boolean[][] g = new boolean[20][20];
        for (boolean[] row : g) {
            java.util.Arrays.fill(row, true); // a huge fully-sandwich region
        }
        List<int[]> span = SnowCollapseLaw.floodFillSpan(grid(g), 10, 10, SnowCollapseLaw.MAX_SPAN_COLUMNS);
        assertEquals(SnowCollapseLaw.MAX_SPAN_COLUMNS, span.size(), "the fill stops exactly at the cap");
    }

    @Test
    void floodFillOnANonSandwichTriggerIsEmpty() {
        boolean[][] g = new boolean[3][3]; // all false
        assertTrue(SnowCollapseLaw.floodFillSpan(grid(g), 1, 1, 24).isEmpty(),
                "if the trigger column is not a sandwich (a race cleared it) -> no collapse, never a throw");
    }

    @Test
    void floodFillRejectsNonPositiveCap() {
        boolean[][] g = {{true}};
        assertTrue(SnowCollapseLaw.floodFillSpan(grid(g), 0, 0, 0).isEmpty(), "cap 0 -> nothing");
    }

    // --- staggerTickOffset: Chebyshev, ordered, clamped, deterministic ----------------------------------

    @Test
    void staggerIsZeroAtTriggerAndGrowsWithChebyshevDistance() {
        assertEquals(0, SnowCollapseLaw.staggerTickOffset(5, 5, 5, 5), "the trigger column falls first");
        assertEquals(1, SnowCollapseLaw.staggerTickOffset(6, 5, 5, 5), "an orthogonal neighbour: distance 1");
        assertEquals(1, SnowCollapseLaw.staggerTickOffset(6, 6, 5, 5),
                "a DIAGONAL neighbour falls on the same beat (Chebyshev), so the ripple is a square ring");
        assertEquals(3, SnowCollapseLaw.staggerTickOffset(8, 6, 5, 5), "max(|dx|=3,|dz|=1)=3");
    }

    @Test
    void staggerClampsToTheCeiling() {
        assertEquals(SnowCollapseLaw.MAX_STAGGER_TICKS,
                SnowCollapseLaw.staggerTickOffset(1000, 1000, 0, 0), "a far column clamps to the stagger ceiling");
    }

    @Test
    void staggerIsDeterministicAndMonotone() {
        int near = SnowCollapseLaw.staggerTickOffset(6, 5, 5, 5);
        int far = SnowCollapseLaw.staggerTickOffset(9, 5, 5, 5);
        assertTrue(near <= far, "a nearer column never falls after a farther one");
        assertEquals(SnowCollapseLaw.staggerTickOffset(9, 5, 5, 5),
                SnowCollapseLaw.staggerTickOffset(9, 5, 5, 5), "pure function: identical inputs -> identical output");
    }

    @Test
    void fireTickComposesTelegraphPlusStagger() {
        long now = 1000L;
        assertEquals(now + SnowCollapseLaw.TELEGRAPH_TICKS, SnowCollapseLaw.fireTickFor(now, 5, 5, 5, 5),
                "the trigger column fires exactly at the end of the telegraph");
        assertEquals(now + SnowCollapseLaw.TELEGRAPH_TICKS + 2, SnowCollapseLaw.fireTickFor(now, 7, 5, 5, 5),
                "a distance-2 column fires two ticks into the outward ripple");
    }

    // --- isAntiEntombColumn: the suffocation carve-out ---------------------------------------------------

    @Test
    void antiEntombCoversTriggerAndOrthogonalNeighboursOnly() {
        assertTrue(SnowCollapseLaw.isAntiEntombColumn(5, 5, 5, 5), "the trigger column breaks to particles");
        assertTrue(SnowCollapseLaw.isAntiEntombColumn(6, 5, 5, 5), "an orthogonal neighbour breaks (Manhattan 1)");
        assertTrue(SnowCollapseLaw.isAntiEntombColumn(5, 4, 5, 5), "the other orthogonal neighbour too");
        assertFalse(SnowCollapseLaw.isAntiEntombColumn(6, 6, 5, 5),
                "a DIAGONAL neighbour (Manhattan 2) DOES tumble -- it cannot land on the shaft");
        assertFalse(SnowCollapseLaw.isAntiEntombColumn(7, 5, 5, 5), "a distance-2 column tumbles as debris");
    }
}
