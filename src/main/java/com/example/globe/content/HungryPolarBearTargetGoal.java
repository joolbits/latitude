package com.example.globe.content;

import com.example.globe.core.LatitudeV2Flags;
import com.example.globe.core.PolarHazardWindow;
import com.example.globe.util.LatitudeMath;
import com.example.globe.world.LatitudeBiomes;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.polarbear.PolarBear;
import net.minecraft.world.entity.player.Player;

/**
 * S25(B-behavior) HUNGRY BEARS (Peetsa 2026-07-20, TEST 117 round: in the food-scarce Barrens a polar bear
 * is not neutral -- it hunts). A {@link NearestAttackableTargetGoal} over players, added to the bear's
 * target selector at priority 5 by {@code PolarBearHungryAggroMixin} (a TAIL inject on
 * {@code PolarBear.registerGoals}, flag-gated so flag-off registers nothing -- vanilla bear byte-identical).
 * A plain class rather than an anonymous subclass inside the mixin: anonymous classes are NOT merged with a
 * mixin, and after merge their references to the mixin class would dangle -- this is the standard safe shape.
 *
 * <p><b>26.2 vanilla ground truth (javap-verified, 2026-07-20).</b> Vanilla polar bears target players only
 * via priority 2 {@code PolarBearAttackPlayersGoal} (cubs nearby) and priority 3
 * {@code NearestAttackableTargetGoal<Player>} gated on anger ({@code isAngryAt}); an un-angered, cub-less
 * bear never hunts. This goal joins at priority 5 -- BELOW hurt-by (1), cub defense (2), anger (3), and the
 * fox hunt (4) -- so every vanilla behavior wins ties and nothing vanilla is reordered. The constructor
 * mirrors the vanilla player-anger wiring {@code (mob, Player.class, 10, true, false, selector)}: same
 * checked-every-~10-ticks cadence, same must-see, no must-reach -- no RNG beyond vanilla's own goal
 * machinery.
 *
 * <p><b>The gates</b> (checked in {@link #canUse} so the goal is DORMANT -- one cheap flag/latitude read per
 * goal poll -- outside the hunt zone, and re-checked per candidate in the selector):
 * <ul>
 *   <li><b>Flag family</b>: {@code latitude.polarBarrens.enabled} ({@link LatitudeV2Flags#POLAR_BARRENS_ENABLED})
 *       -- registration is also flag-gated in the mixin, so this is belt-and-braces; plus the armed-radius
 *       read (0 on non-globe sessions: vanilla bears everywhere outside globe worlds).</li>
 *   <li><b>Latitude &gt;= 80</b>: the bear's own position against
 *       {@link PolarHazardWindow#AMBIENT_ONSET_DEG} (80) -- the S25 polar-country rung (ambient snowfall
 *       onset, villages end, strays-only, the S25(C) freeze onset), KEEP-SHARED so hungry country begins
 *       exactly where polar country does and moves with the rung. {@link LatitudeMath#absLatDegExact} =
 *       the established world-border latitude read (Classic + Mercator correct); NaN -&gt; not hungry.</li>
 *   <li><b>Difficulty</b>: no player-hunting on {@code PEACEFUL} -- checked EXPLICITLY because vanilla's
 *       targeting chain does not difficulty-gate neutral-mob player targeting (wolves can bite on Peaceful);
 *       the hungry hunt is an added hostility and must not leak into Peaceful.</li>
 *   <li><b>Adults only</b>: cubs don't hunt ({@code isBaby()} -- mirrors the vanilla cub-defense goal's
 *       adult gate).</li>
 *   <li><b>~16 blocks</b>: {@link #AGGRO_RANGE_BLOCKS} pinned in the selector via {@code distanceToSqr}
 *       (the bear's FOLLOW_RANGE attribute is 20, so the goal's default search box would otherwise reach
 *       20; the explicit check pins the design's 16). Creative/spectator players are excluded by the
 *       vanilla {@code TargetingConditions.forCombat()} chain this goal already rides.</li>
 * </ul>
 *
 * <p><b>The warning roar is preserved by construction:</b> the stand-up + {@code playWarningSound} live in
 * {@code PolarBear$PolarBearMeleeAttackGoal.checkAndPerformAttack} (javap: when the target is inside
 * {@code (width+3)^2} but not yet attackable, the bear stands and roars on the attack-cooldown beat) --
 * that goal consumes WHOEVER set the target, so a hungry-goal target gets the same warning display as a
 * cub-defense target before the first swipe.
 */
public final class HungryPolarBearTargetGoal extends NearestAttackableTargetGoal<Player> {

    /** The hunt radius (blocks) -- the owner-designed "~16": inside it a Barrens bear considers a visible
     *  player prey. Deliberately under the bear's 20-block FOLLOW_RANGE so the pin is the binding range. */
    public static final double AGGRO_RANGE_BLOCKS = 16.0;
    private static final double AGGRO_RANGE_SQR = AGGRO_RANGE_BLOCKS * AGGRO_RANGE_BLOCKS;

    private final PolarBear bear;

    public HungryPolarBearTargetGoal(PolarBear bear) {
        // Mirrors vanilla's own player-anger NATG wiring (10, mustSee, !mustReach); the selector re-checks
        // the hunt gates per candidate and pins the 16-block radius. (The lambda captures the constructor
        // PARAMETER -- legal in the super() argument's static-like context.)
        super(bear, Player.class, 10, true, false,
                (candidate, serverLevel) -> hungryHuntActive(bear) && bear.distanceToSqr(candidate) <= AGGRO_RANGE_SQR);
        this.bear = bear;
    }

    @Override
    public boolean canUse() {
        // Gate first: outside the hunt zone (or flag-off / Peaceful / cub) the goal is dormant before any
        // entity scan runs -- super.canUse() does the vanilla interval + search only for eligible bears.
        return hungryHuntActive(bear) && super.canUse();
    }

    /** The deterministic hunt gate -- see the class javadoc's bullet list. Pure reads, no state, no RNG. */
    private static boolean hungryHuntActive(PolarBear bear) {
        if (!LatitudeV2Flags.POLAR_BARRENS_ENABLED || bear.isBaby()) {
            return false;
        }
        if (LatitudeBiomes.getActiveRadiusBlocks() <= 0) {
            return false; // not an armed globe world: vanilla bear
        }
        if (bear.level().getDifficulty() == Difficulty.PEACEFUL) {
            return false; // added hostility never leaks into Peaceful
        }
        double absLatDeg = LatitudeMath.absLatDegExact(bear.level().getWorldBorder(), bear.getZ());
        return !Double.isNaN(absLatDeg) && absLatDeg >= PolarHazardWindow.AMBIENT_ONSET_DEG;
    }
}
