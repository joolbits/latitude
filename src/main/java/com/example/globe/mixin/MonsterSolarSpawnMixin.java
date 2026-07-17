package com.example.globe.mixin;

import com.example.globe.GlobeMod;
import com.example.globe.core.LatitudeV2Flags;
import com.example.globe.core.SolarTilt;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Solar Tilt functional layer (P1) — surface-monster spawn rule in the polar bands (§8a/§8b of
 * {@code docs/binder/solar-tilt-design-20260716.md}).
 *
 * <p>Intercepts {@code Monster.isDarkEnoughToSpawn(ServerLevelAccessor, BlockPos, RandomSource)} — the STATIC
 * darkness gate every light-checked surface monster funnels through ({@code checkSurfaceMonstersSpawnRules} /
 * {@code checkMonsterSpawnRules} both call it; {@code checkAnyLight…} creepers deliberately do not and are
 * left alone). One interception therefore reaches all light-gated surface hostiles at once — the cleanest
 * single seam, exactly as the recon receipts recommend.
 *
 * <ul>
 *   <li><b>Winter-pole POLAR NIGHT</b> ({@link SolarTilt.FunctionalBand#POLAR_NIGHT}): the sky-exposed surface
 *       is treated as DARK ENOUGH to spawn even at global noon — so the polar night genuinely crawls around
 *       the clock. We bypass only the SKY-light term: the BLOCK-light gate is still honoured
 *       ({@code getBrightness(BLOCK) <= monsterSpawnBlockLightLimit}), so a torch-lit polar base stays safe,
 *       and roofed/cave columns (not sky-exposed) fall through to vanilla.</li>
 *   <li><b>Summer-pole MIDNIGHT SUN</b> ({@link SolarTilt.FunctionalBand#MIDNIGHT_SUN}): the sky-exposed
 *       surface is VETOED even at global midnight — the endless daylight keeps the open snow clear. Caves
 *       (not sky-exposed) are unaffected, so the dungeon crawl underneath is untouched.</li>
 * </ul>
 *
 * <p><b>Gate.</b> The band is {@link SolarTilt.FunctionalBand#NONE} (→ this mixin no-ops) unless the master
 * flag is on, the world is a globe overworld, AND {@code |φ| >= functionalMinDeg} (A2, default 74.5 — the
 * extreme cap only; livable mid-latitude country is never sieged). Byte-identical flag-off: the very first
 * line returns when {@link LatitudeV2Flags#SOLAR_TILT_V2_ENABLED} is false. Static handler because the target
 * is static. No persistent state (§8d) — stateless per spawn attempt (already rate-limited).
 *
 * <p><b>Honesty line (sweep FIX-2).</b> The injection target ({@code Monster.isDarkEnoughToSpawn(
 * ServerLevelAccessor, BlockPos, RandomSource)Z}, static) is signature-verified via javap receipts against
 * the 26.2 loom jar, but mixin APPLICATION is unproven until P2's first live run — §12 functional checklist
 * item 6 is the gate (repo precedent: {@code FogRendererPolarSetupMixin}'s no-live-launch note).
 *
 * <p><b>Known, LOW, vanilla-consistent:</b> phantoms use their own global-clock insomnia spawner and never
 * touch {@code isDarkEnoughToSpawn}, so they can still appear over a midnight-sun surface for insomniac
 * players (see the design's flight brief).
 */
@Mixin(Monster.class)
public abstract class MonsterSolarSpawnMixin {

    @Inject(method = "isDarkEnoughToSpawn(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)Z",
            at = @At("HEAD"), cancellable = true)
    private static void globe$solarPolarSpawnRule(ServerLevelAccessor level, BlockPos pos, RandomSource random,
                                                  CallbackInfoReturnable<Boolean> cir) {
        if (!LatitudeV2Flags.SOLAR_TILT_V2_ENABLED) {
            return; // byte-identical flag-off
        }
        ServerLevel serverLevel = level.getLevel();
        SolarTilt.FunctionalBand band = GlobeMod.solarFunctionalBand(serverLevel, pos.getZ());
        if (band == SolarTilt.FunctionalBand.NONE) {
            return; // sub-floor latitude, equinox, or not a globe overworld → vanilla light rules
        }
        if (!level.canSeeSky(pos)) {
            return; // caves / roofed columns are never sky-lit → both rules inert underground (vanilla)
        }
        if (band == SolarTilt.FunctionalBand.POLAR_NIGHT) {
            // Dark around the clock — but a torch (BLOCK light) still keeps the spot safe.
            int blockLimit = level.dimensionType().monsterSpawnBlockLightLimit();
            if (level.getBrightness(LightLayer.BLOCK, pos) <= blockLimit) {
                cir.setReturnValue(true);
            }
            // else: artificial light present → fall through to vanilla (which correctly refuses the spawn).
        } else { // MIDNIGHT_SUN
            // Endless daylight on the exposed surface — veto the spawn even at global midnight.
            cir.setReturnValue(false);
        }
    }
}
