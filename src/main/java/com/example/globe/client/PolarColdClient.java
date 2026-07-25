package com.example.globe.client;

import com.example.globe.core.ColdProtection;
import com.example.globe.core.ColdShelter;
import com.example.globe.core.PolarHazardWindow;
import com.example.globe.core.PolarWarmth;
import com.example.globe.core.PolarWounds;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Phase 5 Slice B-7 (Pole Passage) -- P2 CLIENT shims for the polar-cold presentation cues. These read the OWN
 * player's client-visible state exactly the way the P1 server shims in {@code GlobeMod} read the authoritative
 * state, feeding the SAME pure cores ({@link ColdProtection}, {@link ColdShelter}, {@link PolarWarmth},
 * {@link PolarWounds}) so the client presentation and the server mechanic can never disagree:
 * <ul>
 *   <li>{@link #coldProtectionPieces} -- freeze-immune armor count (server twin {@code coldProtectionPieceCount}).</li>
 *   <li>{@link #isSheltered} -- raw SKY light at the eye (server twin {@code isColdSheltered}); trap-proof.</li>
 *   <li>{@link #isNearWarmth} -- the cached 9x5x9 warmth box scan (server twin {@code isNearWarmthCached}).</li>
 *   <li>{@link #isHealLocked} / {@link #isInColdZone} -- the composed S6 predicate for the frozen-wounds cue.</li>
 * </ul>
 * MC-coupled glue (no unit test -- the DECISIONS are proven in the pure cores; this is a thin read layer).
 */
public final class PolarColdClient {

    private PolarColdClient() {
    }

    /** The four armor slots consulted for cold protection (matches the server {@code COLD_ARMOR_SLOTS}). */
    private static final EquipmentSlot[] COLD_ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    // Warmth box scan geometry -- identical to the server (9x5x9 around the player).
    private static final int WARMTH_SCAN_RADIUS_XZ = 4;
    private static final int WARMTH_SCAN_RADIUS_Y = 2;
    /** Cache the (cheap-but-not-free) 405-block scan for ~1 s, matching the server's WARMTH_RESCAN_TICKS. */
    private static final long WARMTH_RESCAN_TICKS = 20L;

    private static long warmthCacheTick = Long.MIN_VALUE;
    private static boolean warmthCacheValue;

    /** World-switch hygiene: drop the warmth cache so it can't leak across worlds. */
    public static void reset() {
        warmthCacheTick = Long.MIN_VALUE;
        warmthCacheValue = false;
    }

    /** Count of freeze-immune ({@code freeze_immune_wearables}) pieces the OWN player wears (0..4); the same
     *  read the server does, so {@code ColdProtection} sees the same count on both sides. */
    public static int coldProtectionPieces(Minecraft mc) {
        if (mc == null || mc.player == null) {
            return 0;
        }
        int count = 0;
        for (EquipmentSlot slot : COLD_ARMOR_SLOTS) {
            ItemStack stack = mc.player.getItemBySlot(slot);
            if (!stack.isEmpty() && stack.is(holder -> holder.is(ItemTags.FREEZE_IMMUNE_WEARABLES))) {
                count++;
            }
        }
        return count;
    }

    /** True iff the OWN player wears a FULL freeze-immune set (freeze damage negated) -- the one evaluator the
     *  hypothermia-rung suppression, the LETHAL text swap and the removal whisper all key on. */
    public static boolean protectionFull(Minecraft mc) {
        return ColdProtection.negatesFreezeDamage(coldProtectionPieces(mc));
    }

    /** True iff the OWN player is in the polar cold zone ({@code |lat| >=} the frostbite onset, 85 deg). */
    public static boolean isInColdZone(Minecraft mc) {
        if (mc == null || mc.player == null || mc.level == null) {
            return false;
        }
        double latDeg = com.example.globe.util.LatitudeMath.absLatDegExact(
                mc.level.getWorldBorder(), mc.player.getZ());
        return latDeg >= PolarHazardWindow.FROSTBITE_ONSET_DEG;
    }

    /** True iff the OWN player is genuinely sheltered (raw SKY light at the eye {@code <= 3}); the graded,
     *  trap-proof S4 read (a single overhead block reads ~11-13 side-lit = exposed). */
    public static boolean isSheltered(Minecraft mc) {
        if (mc == null || mc.player == null || mc.level == null) {
            return false;
        }
        BlockPos eye = BlockPos.containing(mc.player.getX(), mc.player.getEyeY(), mc.player.getZ());
        int rawSkyLight = mc.level.getLightEngine().getLayerListener(LightLayer.SKY).getLightValue(eye);
        return ColdShelter.isSheltered(rawSkyLight);
    }

    /** True iff a {@link PolarWarmth} source sits within the 9x5x9 box around the OWN player -- cached ~1 s
     *  like the server scan. Callers gate this behind sheltered+in-zone so the scan is a campfire-hut nicety. */
    public static boolean isNearWarmth(Minecraft mc) {
        if (mc == null || mc.player == null || mc.level == null) {
            return false;
        }
        long now = mc.level.getGameTime();
        if (warmthCacheTick != Long.MIN_VALUE && now >= warmthCacheTick
                && (now - warmthCacheTick) < WARMTH_RESCAN_TICKS) {
            return warmthCacheValue;
        }
        boolean warm = scanForWarmth(mc, mc.player.blockPosition());
        warmthCacheTick = now;
        warmthCacheValue = warm;
        return warm;
    }

    /** The composed S6 heal-lock predicate for the OWN player: in the cold zone, genuinely sheltered, and NOT
     *  near warmth. Short-circuits zone -&gt; shelter -&gt; warmth-scan, exactly like the server. */
    public static boolean isHealLocked(Minecraft mc) {
        if (!isInColdZone(mc)) {
            return false;
        }
        if (!isSheltered(mc)) {
            return false;
        }
        boolean nearWarmth = isNearWarmth(mc);
        return PolarWounds.healLocked(true, true, nearWarmth);
    }

    private static boolean scanForWarmth(Minecraft mc, BlockPos center) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dy = -WARMTH_SCAN_RADIUS_Y; dy <= WARMTH_SCAN_RADIUS_Y; dy++) {
            for (int dx = -WARMTH_SCAN_RADIUS_XZ; dx <= WARMTH_SCAN_RADIUS_XZ; dx++) {
                for (int dz = -WARMTH_SCAN_RADIUS_XZ; dz <= WARMTH_SCAN_RADIUS_XZ; dz++) {
                    m.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    BlockState state = mc.level.getBlockState(m);
                    if (state.isAir()) {
                        continue;
                    }
                    Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                    boolean lit = !state.hasProperty(BlockStateProperties.LIT)
                            || state.getValue(BlockStateProperties.LIT);
                    if (PolarWarmth.isWarmBlock(id.getNamespace(), id.getPath(), lit)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
