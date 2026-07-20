package com.example.globe.content;

import com.example.globe.GlobeMod;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/**
 * Phase 5 S27 (Peetsa, TEST 118 flight, 2026-07-20) — the mod's FIRST custom PARTICLE type,
 * {@code globe:frost_glint}. Owner on the snow-glint sparkle: "go back to the amethyst sparkle, but instead
 * desaturate it so it's not purple." The amethyst look is the vanilla {@code GlowParticle} 4-point glow star
 * (WAX_OFF), but its lilac cast is HARDCODED in the provider ({@code setColor(1.0, 0.9, 1.0)} + the emissive
 * glow — bytecode-verified against the 26.2 {@code GlowParticle$WaxOffProvider}). A vanilla particle can't be
 * re-tinted, so this registers our own type whose CLIENT factory ({@code client.FrostGlintParticle}) rebuilds
 * the exact glow-star behaviour with a DESATURATED near-white ice tint, reusing the vanilla {@code minecraft:glow}
 * sprite (no custom art — see {@code assets/globe/particles/frost_glint.json}).
 *
 * <p><b>Registration is UNCONDITIONAL</b> ({@link #register()} is called from {@code GlobeMod.onInitialize}
 * before registry freeze, mirroring {@link PolarOutfitting}'s precedent): the {@link SimpleParticleType} must be
 * a consistent registry entry across every session regardless of flags. The particle only ever SPAWNS from the
 * flag-gated snow-sparkle path (see {@code GlobeModClient.spawnSnowSparkle}); this class just creates + names
 * the type. The CLIENT-side rendering factory is registered separately in {@code GlobeModClient} (a dedicated
 * server never loads the client factory).
 */
public final class GlobeParticles {

    private GlobeParticles() {
    }

    /**
     * {@code globe:frost_glint} — a {@link SimpleParticleType} (no extra data, respects the vanilla particle
     * limiter: {@code FabricParticleTypes.simple()} defaults {@code overrideLimiter = false}, matching the
     * vanilla WAX_OFF/GLOW type this replaces on the snowfields). Created at class-load; registered by
     * {@link #register()}; consumed as {@code GlobeModClient.SPARKLE_PARTICLE}.
     */
    public static final SimpleParticleType FROST_GLINT = FabricParticleTypes.simple();

    /** Register {@link #FROST_GLINT} into {@link BuiltInRegistries#PARTICLE_TYPE}. Called UNCONDITIONALLY from
     *  {@code GlobeMod.onInitialize} during the mod-init window, before registry freeze. */
    public static void register() {
        Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                Identifier.fromNamespaceAndPath(GlobeMod.MOD_ID, "frost_glint"), FROST_GLINT);
        GlobeMod.LOGGER.info("[S27] registered globe:frost_glint (first custom particle; desaturated glow-star sparkle)");
    }
}
