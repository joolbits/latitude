package com.example.globe.world;

import com.example.globe.GlobeMod;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

import java.util.stream.Stream;

/**
 * Passes a placement position through only when its own vertical air/water column reaches a sturdy
 * ceiling within a gallery-scale bound. Unlike environment_scan this modifier never relocates the
 * candidate: configured features continue to own their original floor search and final placement.
 */
public final class RoofedCavernPlacement extends PlacementModifier {
    /** The ice-spire source range is Y0..100 while this world's noise height is 384; 128 preserves
     * tall noise galleries (well beyond the old 24/32-block scans) without an unbounded column walk. */
    public static final int MAX_ROOF_RISE = 128;
    static final String DEBUG_LOG_TEMPLATE = "[LAT][ROOFED_CAVERN] outcome={} originX={} originY={} "
            + "originZ={} biome={} skyVisible={} roofDistance={}";
    private static final boolean DEBUG_ROOFED_CAVERN = Boolean.getBoolean("latitude.debugRoofedCavern");

    public static final MapCodec<RoofedCavernPlacement> CODEC = MapCodec.unit(RoofedCavernPlacement::new);
    private static PlacementModifierType<RoofedCavernPlacement> type;

    /** Called during common initialization before worldgen registries freeze. */
    public static void register() {
        if (type == null) {
            type = Registry.register(
                    BuiltInRegistries.PLACEMENT_MODIFIER_TYPE,
                    Identifier.fromNamespaceAndPath(GlobeMod.MOD_ID, "roofed_cavern"),
                    () -> CODEC);
        }
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos origin) {
        int maximumRoofY = Math.min(origin.getY() + MAX_ROOF_RISE, context.getLevel().getMaxY());
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int result = RoofedCavernColumnGate.findRoof(
                origin.getY(), maximumRoofY, y -> {
            cursor.set(origin.getX(), y, origin.getZ());
            BlockState state = context.getBlockState(cursor);
            int flags = state.isAir() || state.getFluidState().getType().is(FluidTags.WATER)
                    ? RoofedCavernColumnGate.AIR_OR_WATER : 0;
            return state.isFaceSturdy(context.getLevel(), cursor, Direction.DOWN)
                    ? flags | RoofedCavernColumnGate.STURDY_UNDERSIDE : flags;
        });
        if (DEBUG_ROOFED_CAVERN) {
            logDebug(context, origin, result);
        }
        return RoofedCavernColumnGate.isRoofed(result) ? Stream.of(origin) : Stream.empty();
    }

    private static void logDebug(PlacementContext context, BlockPos origin, int result) {
        boolean skyVisible = context.getLevel().canSeeSky(origin);
        String biome = context.getLevel().getBiome(origin).unwrapKey()
                .map(key -> key.identifier().toString()).orElse("<unbound>");
        GlobeMod.LOGGER.info(DEBUG_LOG_TEMPLATE,
                RoofedCavernColumnGate.outcome(result), origin.getX(), origin.getY(), origin.getZ(), biome, skyVisible,
                RoofedCavernColumnGate.roofDistance(result));
    }

    @Override
    public PlacementModifierType<?> type() {
        if (type == null) {
            throw new IllegalStateException("roofed_cavern placement modifier was used before registration");
        }
        return type;
    }
}
