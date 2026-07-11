package com.example.globe.core.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-math gate for the HUD Studio sidebar's grouped-row layout walk (UI round 13 sweeper follow-up).
 * Pins the pre-885b3da4 "staircase" regression forever: same-baseY groups (undo/redo, a preset slot's
 * Load/Save/x) must advance the cumulative cursor ONCE per group, not once per widget.
 */
class GroupRowLayoutTest {

    private static final float TOL = 1e-4f;

    /** A Presets-tab-shaped baseY list -- two undo/redo buttons sharing a row, a lone row, and a
     *  Load/Save/x triple sharing a row -- with every reveal fully open (1.0) must reproduce the OLD
     *  fixed-baseY layout exactly: dispY[i] == baseY[i] for every tracked member, including every widget
     *  inside a shared-Y group. This is the staircase regression pinned forever. */
    @Test
    void allRevealsOneYieldsDispYEqualsBaseY() {
        int[] baseY = {0, 0, 24, 48, 48, 48};
        float[] reveal = {1f, 1f, 1f, 1f, 1f, 1f};
        int[] heights = {20, 20, 20, 20, 20, 20};

        GroupRowLayout.Result r = GroupRowLayout.compute(0f, baseY, reveal, heights);

        for (int i = 0; i < baseY.length; i++) {
            assertEquals((float) baseY[i], r.dispY[i], TOL, "dispY[" + i + "] should equal baseY at rest");
        }
    }

    /** Members that share a baseY (a logical row/group) must share the same dispY -- the group advances
     *  the cursor once, not once per member. */
    @Test
    void sameLineMembersShareDispY() {
        int[] baseY = {0, 0, 24, 48, 48, 48};
        float[] reveal = {1f, 1f, 1f, 1f, 1f, 1f};
        int[] heights = {20, 20, 20, 20, 20, 20};

        GroupRowLayout.Result r = GroupRowLayout.compute(0f, baseY, reveal, heights);

        assertEquals(r.dispY[0], r.dispY[1], TOL, "the undo/redo pair (baseY=0) should share dispY");
        assertEquals(r.dispY[3], r.dispY[4], TOL, "the Load/Save/x triple (baseY=48) should share dispY");
        assertEquals(r.dispY[4], r.dispY[5], TOL, "the Load/Save/x triple (baseY=48) should share dispY");
        // And their shared slot height should likewise be identical across the group.
        assertEquals(r.slotH[3], r.slotH[4], TOL);
        assertEquals(r.slotH[4], r.slotH[5], TOL);
    }

    /** A row that's mid-reveal (collapsed or partially open) shrinks only ITS OWN slot's contribution to
     *  the cursor -- rows below still ease along correctly, and a fully-collapsed row (reveal 0) makes
     *  every row below it sit strictly higher than it would at reveal 1. */
    @Test
    void midRevealCollapsesOnlyItsOwnSlot() {
        int[] baseY = {0, 24, 48};
        int[] heights = {20, 20, 20};

        // Baseline: everything settled open.
        GroupRowLayout.Result open = GroupRowLayout.compute(0f, baseY, new float[]{1f, 1f, 1f}, heights);
        // Row 1 (baseY=24) fully collapsed: row 2 (baseY=48) should rise to sit right under row 0's slot,
        // i.e. strictly less than its open-layout dispY, while row 0 itself is untouched.
        GroupRowLayout.Result collapsed = GroupRowLayout.compute(0f, baseY, new float[]{1f, 0f, 1f}, heights);
        assertEquals(open.dispY[0], collapsed.dispY[0], TOL, "row above an unrelated collapse is untouched");
        assertNotEquals(open.dispY[2], collapsed.dispY[2], "the row after a collapsed row should shift up");
        assertEquals(collapsed.dispY[1], collapsed.dispY[2], TOL,
                "with the middle row fully collapsed (reveal 0), it contributes no height, so the row below "
                        + "it sits exactly where the collapsed row itself sits");

        // A partial reveal (mid-roll) should land strictly between the collapsed and fully-open cursor
        // positions for the row below it.
        GroupRowLayout.Result midRoll = GroupRowLayout.compute(0f, baseY, new float[]{1f, 0.5f, 1f}, heights);
        assertTrue(midRoll.dispY[2] > collapsed.dispY[2] && midRoll.dispY[2] < open.dispY[2],
                "a half-open row should place the row below it strictly between the collapsed and fully-open positions");
    }
}
