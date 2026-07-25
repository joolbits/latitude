package com.example.globe.mixin.terrain;

import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor for {@link RandomState}'s otherwise-final {@code router} field, so the Phase 4 terrain-bias
 * hook can replace the world's {@link NoiseRouter} with one whose {@code finalDensity} (field #12) is
 * wrapped by {@link GeoTerrainBiasFunction}. Verified against the 26.2 deobf jar: {@code RandomState}
 * declares {@code private final NoiseRouter router;} with a public getter {@code router()} and NO setter,
 * so a {@code @Mutable @Accessor} is required to mutate it -- exactly the technique the reverted
 * {@code RandomStateOceanSinkMixin} precedent used.
 *
 * <p>Only the router field is exposed; every other {@code RandomState} field is left untouched. The
 * mutation site is gated by a real positive globe check (see {@link RandomStateRouterTerrainMixin}); on
 * non-globe worlds this accessor is never invoked.
 */
@Mixin(RandomState.class)
public interface RandomStateAccessor {

    @Accessor("router")
    NoiseRouter globe$getRouter();

    @Accessor("router")
    @Mutable
    void globe$setRouter(NoiseRouter router);
}
