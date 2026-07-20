package com.example.globe.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Phase 5 Crew 9 / S30 THE REAL SNOW COLLAPSE -- the pure decision math behind the owner's collapse EVENT
 * (Peetsa 2026-07-20 eve sketch, verbatim intent: "an unsuspecting player walks normal-looking snow; the snow
 * beneath GIVES WAY -- labeled TRAP ... SNOW FALLS: the roof tumbles as falling chunks WITH the player into the
 * void. And even sometimes you can drop down into a deep glacial cave (onto powder snow so you don't take fall
 * damage)"). S30 supersedes the S25b static powder LID (a sink-through with a visible powder texture): the trap
 * is now an EVENT -- crack, collapse, tumble, cushion -- driven by a GEN SANDWICH the player cannot read
 * (snow_block flush with the snowfield over a hidden powder_snow marker over the void).
 *
 * <p>This class holds ONLY the collapse decision math (mirroring {@link PowderRoofTrap}, {@link PolarBarrensBand},
 * {@link GlacialMarkScan}): the exact trigger signature, the span flood-fill, the deterministic distance-ordered
 * stagger, and the telegraph constant. ZERO Minecraft imports -- Core Logic layer, unit-testable in a plain JVM.
 * The MC-coupled runtime ({@code world.SnowCollapseRuntime}) supplies the block-probe that closes over a real
 * {@link net.minecraft.world.level.Level} and performs the falling-block spawns, particles, and audio.
 *
 * <p><b>No RNG anywhere.</b> The trigger is a pure block-shape predicate (no lottery), and the stagger is a pure
 * function of grid distance -- so a given collapse is byte-deterministic given the world state it reads. (The
 * gen-side {@code ~30%} deep-drop fraction is a SEEDED worldgen roll living in the feature, not here; the runtime
 * reacts only to what actually generated.)
 */
public final class SnowCollapseLaw {

    private SnowCollapseLaw() {
    }

    /**
     * The gen sandwich's void requirement: at least this many AIR blocks must open directly beneath the hidden
     * powder_snow marker for a column to be a genuine collapse trigger (never a false positive on solid ground or
     * a 1-block powder puddle). 6 clears a full player height plus headroom, so the collapse always drops the
     * victim rather than merely scuffing the surface. Matches the gen feature's sandwich = snow_block / powder /
     * open void.
     */
    public static final int TRIGGER_MIN_AIR_BELOW_MARKER = 6;

    /**
     * Hard cap on the number of columns a single collapse event converts (flood-fill bound). 24 is a wide slot
     * mouth or a small chamber roof -- enough that the collapse reads as "the ground opened", never a runaway
     * chain across a whole crevasse field (which would be a lag bomb and un-fun). The flood-fill stops the moment
     * this many contiguous sandwich columns are claimed.
     */
    public static final int MAX_SPAN_COLUMNS = 24;

    /**
     * Ticks of telegraph (cracking audio + snow sift) BEFORE the first block falls. ~0.5 s at 20 tps -- long
     * enough that a paying-attention player hears "something is wrong" yet short enough that it still reads as a
     * trap that GAVE WAY, not a scripted countdown. The runtime schedules every column's fall at {@code now +
     * TELEGRAPH_TICKS + }{@link #staggerTickOffset}.
     */
    public static final int TELEGRAPH_TICKS = 10;

    /**
     * Ceiling (ticks) on the outward stagger delay -- a far span column falls at most this many ticks after the
     * nearest one, so even a full {@link #MAX_SPAN_COLUMNS} span finishes tumbling within ~0.5 s of its start.
     * The stagger delay is {@code min(distanceFromTrigger, MAX_STAGGER_TICKS)}, so near columns fall first and the
     * collapse visibly ripples outward from under the player's feet.
     */
    public static final int MAX_STAGGER_TICKS = 10;

    /**
     * Manhattan radius (columns) of the ANTI-ENTOMBMENT carve-out around the trigger column. Columns within this
     * radius of the player's own column BREAK to particles only -- they never spawn a {@link
     * net.minecraft.world.entity.item.FallingBlockEntity} -- so tumbling snow can never land on and suffocate the
     * player as they fall down the shaft. Radius 1 (Manhattan) = the trigger column plus its four orthogonal
     * neighbours (5 columns): exactly "the 1-2 columns directly above the falling player" from the sketch, plus a
     * one-block safety collar. Everything beyond becomes real falling snow = the debris pile.
     */
    public static final int ANTI_ENTOMB_MANHATTAN_RADIUS = 1;

    /**
     * The EXACT trigger signature (no RNG, no false positives on solid ground): the player is standing on a
     * {@code snow_block} whose direct-below block is {@code powder_snow}, and at least {@link
     * #TRIGGER_MIN_AIR_BELOW_MARKER} air blocks open beneath that marker. All three must hold -- this is precisely
     * the gen sandwich and nothing else (ordinary snowfield is snow over snow/dirt with no powder marker; a real
     * exposed powder pocket has no snow_block cap; a shallow scoop fails the air count).
     *
     * @param standOnSnowBlock    is the block the player is standing on a {@code snow_block}?
     * @param belowIsPowderSnow   is the block directly below that snow_block a {@code powder_snow} marker?
     * @param airBelowMarker      count of contiguous air blocks directly below the powder marker
     */
    public static boolean matchesTriggerSignature(boolean standOnSnowBlock, boolean belowIsPowderSnow,
                                                  int airBelowMarker) {
        return standOnSnowBlock && belowIsPowderSnow && airBelowMarker >= TRIGGER_MIN_AIR_BELOW_MARKER;
    }

    /**
     * A column-shape probe the runtime supplies, closing over the real level + the trigger's flush roof-Y: it
     * answers "is column ({@code x},{@code z}) a collapse-sandwich column right now?" (snow_block over powder over
     * &ge;{@link #TRIGGER_MIN_AIR_BELOW_MARKER} air, at the shared flush roof height). Kept as a functional
     * interface so {@link #floodFillSpan} is pure and unit-testable against a synthetic boolean grid.
     */
    @FunctionalInterface
    public interface ColumnPredicate {
        boolean isSandwich(int x, int z);
    }

    /**
     * Flood-fill the contiguous collapse SPAN from the trigger column: 4-neighbour connectivity over columns the
     * {@code probe} reports as sandwich columns, breadth-first, capped at {@code cap} columns. Returns the claimed
     * columns as {@code {x,z}} pairs in BFS (deterministic) order, the trigger first; an empty list if the trigger
     * column itself is not a sandwich (defensive -- the caller has already matched the signature, but a race that
     * cleared it must degrade to "nothing to collapse", never throw).
     *
     * <p>Pure and allocation-only; no randomness. The {@code cap} is applied as a hard stop the instant the
     * claimed set reaches it, so the returned span is always {@code <= cap} and the fill is bounded regardless of
     * how large the real sandwich region is.
     *
     * @param probe     column sandwich test (world-closed in the runtime; a boolean grid in tests)
     * @param triggerX  the trigger column X (world or grid coordinate; the probe interprets it)
     * @param triggerZ  the trigger column Z
     * @param cap       maximum columns to claim (typically {@link #MAX_SPAN_COLUMNS})
     */
    public static List<int[]> floodFillSpan(ColumnPredicate probe, int triggerX, int triggerZ, int cap) {
        List<int[]> claimed = new ArrayList<>();
        if (probe == null || cap <= 0 || !probe.isSandwich(triggerX, triggerZ)) {
            return claimed;
        }
        Set<Long> seen = new HashSet<>();
        Deque<int[]> frontier = new ArrayDeque<>();
        seen.add(pack(triggerX, triggerZ));
        frontier.add(new int[]{triggerX, triggerZ});
        while (!frontier.isEmpty() && claimed.size() < cap) {
            int[] col = frontier.poll();
            claimed.add(col);
            // 4-neighbour expansion; enqueue only unseen sandwich columns.
            enqueueIfSandwich(probe, seen, frontier, col[0] + 1, col[1]);
            enqueueIfSandwich(probe, seen, frontier, col[0] - 1, col[1]);
            enqueueIfSandwich(probe, seen, frontier, col[0], col[1] + 1);
            enqueueIfSandwich(probe, seen, frontier, col[0], col[1] - 1);
        }
        return claimed;
    }

    private static void enqueueIfSandwich(ColumnPredicate probe, Set<Long> seen, Deque<int[]> frontier,
                                          int x, int z) {
        long key = pack(x, z);
        if (seen.contains(key)) {
            return;
        }
        if (probe.isSandwich(x, z)) {
            seen.add(key);
            frontier.add(new int[]{x, z});
        } else {
            // Mark non-sandwich cells seen too, so a column is probed at most once per fill (cheap + deterministic).
            seen.add(key);
        }
    }

    private static long pack(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xFFFFFFFFL);
    }

    /**
     * The deterministic stagger delay (ticks, relative to the end of the telegraph) for a span column: the
     * CHEBYSHEV (king-move) distance from the trigger column, clamped to {@link #MAX_STAGGER_TICKS}. Chebyshev is
     * chosen so the collapse ripples outward as concentric SQUARE rings (a diagonal neighbour falls on the same
     * beat as an orthogonal one), which reads as "the floor caved from under me outward" rather than a plus-shaped
     * cross. The trigger column itself returns 0 (it goes first). No RNG.
     */
    public static int staggerTickOffset(int colX, int colZ, int triggerX, int triggerZ) {
        int cheby = Math.max(Math.abs(colX - triggerX), Math.abs(colZ - triggerZ));
        return Math.min(cheby, MAX_STAGGER_TICKS);
    }

    /**
     * Absolute server tick at which a span column should convert: {@code now + }{@link #TELEGRAPH_TICKS}{@code +
     * }{@link #staggerTickOffset}. Convenience so the runtime and its tests agree on the schedule in one place.
     */
    public static long fireTickFor(long now, int colX, int colZ, int triggerX, int triggerZ) {
        return now + TELEGRAPH_TICKS + staggerTickOffset(colX, colZ, triggerX, triggerZ);
    }

    /**
     * Is this span column inside the anti-entombment carve-out (Manhattan distance from the trigger &le; {@link
     * #ANTI_ENTOMB_MANHATTAN_RADIUS})? Such columns break to particles only -- NO falling entity -- so tumbling
     * snow can never land on the player mid-shaft. Everything else becomes real falling snow (the debris pile).
     */
    public static boolean isAntiEntombColumn(int colX, int colZ, int triggerX, int triggerZ) {
        return Math.abs(colX - triggerX) + Math.abs(colZ - triggerZ) <= ANTI_ENTOMB_MANHATTAN_RADIUS;
    }
}
