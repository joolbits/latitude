package com.example.globe.mixin;

import com.example.globe.GlobeMod;
import com.example.globe.core.LatitudeV2Flags;
import com.example.globe.core.PolarSurfaceSpawns;
import com.example.globe.util.LatitudeMath;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * S13 (e) POLAR SURFACE ALLOWLIST (Peetsa, TEST-103 flight). Intercepts
 * {@code SpawnPlacements.checkSpawnRules(EntityType, ServerLevelAccessor, EntitySpawnReason, BlockPos,
 * RandomSource)} -- the TYPE-aware static gate every natural spawn attempt funnels through (called by
 * {@code NaturalSpawner} before the entity's own spawn predicate). Because it sees the entity TYPE it can do
 * what {@code MonsterSolarSpawnMixin} (on the type-blind {@code Monster.isDarkEnoughToSpawn}) cannot: gate
 * by species. In polar storm country the SKY-EXPOSED surface admits only {@code stray}; every other monster
 * becomes cave-only.
 *
 * <p><b>Gates (cheapest first; all short-circuit to vanilla):</b>
 * <ol>
 *   <li><b>flag</b> -- first line; flag-off returns before reading anything.</li>
 *   <li><b>reason == NATURAL</b> -- only the per-tick gameplay spawner (the polar-night surface siege). This
 *       is also the ONLY reason whose {@code RandomSource} is gameplay RNG, so deliberately skipping
 *       {@code CHUNK_GENERATION} keeps worldgen byte-identical, and {@code SPAWNER}/{@code SPAWN_EGG}/
 *       {@code COMMAND} (dungeon spawners, spawn eggs, admin tools) keep working at the poles. (Vanilla's
 *       chunk-generation pass spawns only the CREATURE category anyway; monsters are NATURAL-only.)</li>
 *   <li><b>category == MONSTER</b> -- passive polar fauna ({@code polar_bear}, {@code rabbit}) untouched.</li>
 *   <li><b>globe overworld</b> -- vanilla / non-globe worlds are 100% vanilla spawning.</li>
 *   <li><b>latitude band</b> -- {@link PolarSurfaceSpawns#inPolarBand(double)} (pure arithmetic on the synced
 *       border), equatorward of the 80-deg storm onset returns before the sky lookup.</li>
 *   <li><b>sky</b> -- {@code canSeeSky}: caves / roofed columns fall through, so the dungeon crawl under the
 *       ice is 100% vanilla (same "no-sky = untouched" law as the solar rule).</li>
 * </ol>
 * A sky-exposed non-stray monster in the band is vetoed. A sky-exposed stray is kept but thinned 1-in-
 * {@link PolarSurfaceSpawns#STRAY_SURFACE_KEEP_DENOM} using the spawn attempt's OWN {@code RandomSource}
 * (only under NATURAL, so no worldgen RNG is ever consumed). The decision math lives in the pure
 * {@link PolarSurfaceSpawns}; this shim only gathers inputs. Static handler because the target is static.
 */
@Mixin(SpawnPlacements.class)
public abstract class SpawnPlacementsPolarSurfaceMixin {

    @Inject(method = "checkSpawnRules(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/world/entity/EntitySpawnReason;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)Z",
            at = @At("HEAD"), cancellable = true)
    private static void globe$polarSurfaceAllowlist(EntityType<?> type, ServerLevelAccessor level,
                                                    EntitySpawnReason reason, BlockPos pos, RandomSource random,
                                                    CallbackInfoReturnable<Boolean> cir) {
        if (!LatitudeV2Flags.POLAR_SURFACE_SPAWNS_ENABLED) {
            return; // byte-identical flag-off
        }
        if (reason != EntitySpawnReason.NATURAL) {
            return; // gameplay spawns only -- never the worldgen (CHUNK_GENERATION) RNG stream, never spawners/eggs/commands
        }
        if (type.getCategory() != MobCategory.MONSTER) {
            return; // only the hostile menagerie is filtered; passive polar fauna untouched
        }
        ServerLevel serverLevel = level.getLevel();
        if (!GlobeMod.isGlobeOverworld(serverLevel)) {
            return; // vanilla / non-globe worlds: 100% vanilla spawning
        }
        double absLat = LatitudeMath.absLatDegExact(serverLevel.getWorldBorder(), pos.getZ());
        if (!PolarSurfaceSpawns.inPolarBand(absLat)) {
            return; // equatorward of polar storm country: untouched
        }
        if (!level.canSeeSky(pos)) {
            return; // caves / roofed columns: the dungeon crawl under the ice stays 100% vanilla
        }
        // Identify strays by registry id (this mapping has no EntityType.STRAY static field), mirroring the
        // village guard's registry.getKey idiom. A missing key degrades to "not a stray" -> veto (safe).
        Identifier typeId = EntityType.getKey(type);
        boolean isStray = typeId != null
                && "minecraft".equals(typeId.getNamespace()) && "stray".equals(typeId.getPath());
        if (!isStray) {
            cir.setReturnValue(false); // sky-exposed non-stray monster in the storm cap -> veto (cave-only)
            return;
        }
        // Sky-exposed stray in the storm cap: thin to 1-in-N so the non-barrens polar biomes (snowy_plains at
        // weight 80) lose the stray glut too. Spawn-attempt RNG only (NATURAL, gated above).
        if (PolarSurfaceSpawns.thinsStray(absLat, true, random.nextInt(PolarSurfaceSpawns.STRAY_SURFACE_KEEP_DENOM))) {
            cir.setReturnValue(false);
        }
    }
}
