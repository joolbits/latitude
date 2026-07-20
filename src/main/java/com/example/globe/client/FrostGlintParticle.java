package com.example.globe.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;

/**
 * Phase 5 S27 (Peetsa, TEST 118 flight, 2026-07-20) — the CLIENT render factory for {@code globe:frost_glint}
 * (type registered in {@code content.GlobeParticles}). Owner: "go back to the amethyst sparkle, but instead
 * desaturate it so it's not purple." The amethyst sparkle IS the vanilla {@code GlowParticle} glow-star; its
 * {@code GlowParticle} constructor is {@code private} and its WAX_OFF provider bakes a LILAC
 * {@code setColor(1.0f, 0.9f, 1.0f)}, so it can be neither subclassed nor re-tinted. This class re-creates the
 * glow-star VERBATIM from the 26.2 {@code GlowParticle} + {@code GlowParticle$WaxOffProvider} bytecode
 * (disassembled with {@code javap -c} before writing) and swaps only the colour to a desaturated ice tint.
 *
 * <p><b>Behaviour parity (from {@code GlowParticle.<init>}):</b> {@code friction = 0.96f};
 * {@code speedUpWhenYMotionIsBlocked = true}; {@code quadSize *= 0.75f}; {@code hasPhysics = false};
 * {@code setSpriteFromAge(sprites)} at construction and every {@link #tick()}; {@link #getLayer()} =
 * {@code SingleQuadParticle.Layer.OPAQUE}; and the EMISSIVE {@link #getLightCoords(float)} override
 * (LightCoordsUtil.addSmoothBlockEmission over {@code (age+partialTick)/lifetime}) — that self-lit glow is the
 * "sparkle" quality the owner wants back.
 *
 * <p><b>Provider parity (from {@code WaxOffProvider.createParticle}):</b> spawn with zero incoming velocity,
 * {@link #setColor} to the ice tint, {@code setParticleSpeed(vx·0.01/2, vy·0.01, vz·0.01/2)}, and
 * {@code setLifetime(random.nextInt(30) + 10)} (10–39 ticks). The caller ({@code GlobeModClient.spawnSnowSparkle})
 * already spawns each glint with ZERO velocity hugging the snow, so the speed scaling is inert here (kept for
 * fidelity), and the short life + emissive flicker carry the in-place twinkle.
 */
public class FrostGlintParticle extends SingleQuadParticle {

    /**
     * The DESATURATED ICE tint (owner S27): near-white with the faintest cool cast — a whisper of blue, NOT
     * purple, NOT saturated. Replaces WAX_OFF's baked lilac {@code (1.0, 0.9, 1.0)}; the emissive glow behaviour
     * is unchanged, so it reads as the same amethyst-style sparkle in white instead of lavender. One-line P4 dial.
     */
    private static final float FROST_R = 0.94f;
    private static final float FROST_G = 0.97f;
    private static final float FROST_B = 1.00f;

    private final SpriteSet sprites;

    FrostGlintParticle(ClientLevel level, double x, double y, double z,
                       double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, vx, vy, vz, sprites.first());
        this.friction = 0.96f;
        this.speedUpWhenYMotionIsBlocked = true;
        this.sprites = sprites;
        this.quadSize *= 0.75f;
        this.hasPhysics = false;
        setSpriteFromAge(sprites);
    }

    @Override
    public SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.OPAQUE;
    }

    /** The emissive glow (verbatim GlowParticle): the base light plus a smooth block-emission that fades over
     *  the particle's life — the self-lit "sparkle" the owner is after. */
    @Override
    public int getLightCoords(float partialTick) {
        return LightCoordsUtil.addSmoothBlockEmission(
                super.getLightCoords(partialTick), (this.age + partialTick) / (float) this.lifetime);
    }

    @Override
    public void tick() {
        super.tick();
        setSpriteFromAge(this.sprites);
    }

    /**
     * The client factory, registered in {@code GlobeModClient} via Fabric's {@code ParticleProviderRegistry}
     * with the vanilla glow sprite set (the JSON asset lists {@code minecraft:glow}). Mirrors
     * {@code GlowParticle$WaxOffProvider.createParticle} with the ice tint substituted for the lilac.
     */
    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double vx, double vy, double vz, RandomSource random) {
            FrostGlintParticle particle = new FrostGlintParticle(level, x, y, z, 0.0, 0.0, 0.0, this.sprites);
            particle.setColor(FROST_R, FROST_G, FROST_B);
            particle.setParticleSpeed(vx * 0.01 / 2.0, vy * 0.01, vz * 0.01 / 2.0);
            particle.setLifetime(random.nextInt(30) + 10);
            return particle;
        }
    }
}
