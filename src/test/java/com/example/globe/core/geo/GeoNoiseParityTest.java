package com.example.globe.core.geo;

import com.example.globe.util.LatitudeMath;
import com.example.globe.util.ValueNoise2D;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Guards the deliberate verbatim copies in {@link GeoNoise} against drift from the util originals.
 * GeoAuthority's calibration (land fractions, component counts) was measured against the util
 * formulas; if these ever diverge the geography silently changes, so this test fails loudly instead.
 * (The test CAN import util — only the {@code core} layer itself must stay Minecraft-free.)
 */
class GeoNoiseParityTest {

    @Test
    void valueNoiseMatchesUtilExactly() {
        long[] seeds = {0L, 1L, -1L, 2591890304012655616L, 0x67656F5F636F6E31L};
        int[] scales = {1, 7, 800, 4200, 6000};
        for (long seed : seeds) {
            for (int scale : scales) {
                for (int x = -5000; x <= 5000; x += 733) {
                    for (int z = -3000; z <= 3000; z += 611) {
                        assertEquals(ValueNoise2D.sampleBlocks(seed, x, z, scale),
                                GeoNoise.valueNoise(seed, x, z, scale), 0.0,
                                "valueNoise drift at seed=" + seed + " x=" + x + " z=" + z + " scale=" + scale);
                    }
                }
            }
        }
    }

    @Test
    void hash01MatchesUtilExactly() {
        long[] seeds = {0L, 1L, -1L, 2591890304012655616L};
        int[] salts = {0, 1, 0x504A58, 0x504A5A, 0x50434E};
        for (long seed : seeds) {
            for (int salt : salts) {
                for (int x = -400; x <= 400; x += 37) {
                    for (int z = -400; z <= 400; z += 41) {
                        assertEquals(LatitudeMath.hash01(seed, x, z, salt),
                                GeoNoise.hash01(seed, x, z, salt), 0.0,
                                "hash01 drift at seed=" + seed + " x=" + x + " z=" + z + " salt=" + salt);
                    }
                }
            }
        }
    }
}
