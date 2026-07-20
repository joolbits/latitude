package com.example.globe.mixin.client;

import com.example.globe.client.PolarMusicClient;
import com.example.globe.core.LatitudeV2Flags;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Phase 5 Slice B-9/B-7 -- Peetsa stipulation S26 (2026-07-20): MUSIC FADES OUT AT THE DAMAGE LINE. The
 * client volume seam: multiplies the {@code SoundSource.MUSIC} category volume by the live
 * {@link PolarMusicClient#factor01() music factor} so the game's music eases to silence at the first
 * cold-damage latitude and resumes (eased) inside caves / shelter.
 *
 * <p><b>The seam (javap-verified against the 26.2 merged-deobf jar).</b>
 * {@code SoundEngine.calculateVolume(float, SoundSource)} is the single choke where a sound's raw volume is
 * multiplied by its category's user-slider volume + gain:
 * <pre>
 *   private float calculateVolume(float rawVolume, SoundSource source) {
 *       return clamp(rawVolume,0,1) * clamp(options.getFinalSoundSourceVolume(source),0,1)
 *              * gainBySource.getFloat(source);
 *   }
 * </pre>
 * A {@code @ModifyReturnValue} here (capturing the target's own args) multiplies the result by the music
 * factor for the {@code MUSIC} case ONLY -- every other category (crucially {@code WEATHER}, which carries
 * the {@link com.example.globe.client.PolarWindSoundInstance polar wind bed}) is returned untouched, so the
 * wind still howls while the music dies. The user's Music volume slider is NEVER written -- this only
 * multiplies the already-resolved value.
 *
 * <p><b>Flag gate.</b> When {@link LatitudeV2Flags#POLAR_BARRENS_ENABLED} is off the return is passed through
 * unchanged (the holder also pins the factor to 1.0), so the audio path is byte-identical.
 */
@Mixin(SoundEngine.class)
public class MusicFadeVolumeMixin {

    @ModifyReturnValue(
            method = "calculateVolume(FLnet/minecraft/sounds/SoundSource;)F",
            at = @At("RETURN"))
    private float globe$fadeMusicAtDamageLine(float original, float rawVolume, SoundSource source) {
        if (source != SoundSource.MUSIC || !LatitudeV2Flags.POLAR_BARRENS_ENABLED) {
            return original;
        }
        return original * PolarMusicClient.factor01();
    }
}
