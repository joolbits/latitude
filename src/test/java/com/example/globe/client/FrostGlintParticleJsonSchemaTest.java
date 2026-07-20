package com.example.globe.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * S27 JSON schema tripwire for the {@code globe:frost_glint} particle definition asset. The client particle
 * factory ({@link FrostGlintParticle}) is registered against the sprite set the ParticleEngine loads from
 * {@code assets/globe/particles/frost_glint.json}; if that file is missing or lists a bad sprite, the glint
 * renders as the missing-texture checkerboard (or the factory registration throws) at first client load — a
 * failure a launch-free build never sees. This pins the intended contract: the asset exists, is well-formed,
 * and its {@code textures} list is EXACTLY the vanilla {@code minecraft:glow} sprite (the texture-less reuse
 * that gives the glow-star its shape with no custom art — mirroring vanilla {@code glow.json}/{@code wax_off.json}).
 * Mirrors {@code world.PowderCrevasseRoofJsonSchemaTest}'s style; full sprite resolution happens at boot.
 */
class FrostGlintParticleJsonSchemaTest {

    private static JsonObject load(String resourcePath) {
        InputStream stream = FrostGlintParticleJsonSchemaTest.class.getResourceAsStream(resourcePath);
        assertNotNull(stream, "must be on the classpath (main resources): " + resourcePath);
        return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void frostGlintReusesTheVanillaGlowSpriteOnly() {
        JsonObject def = load("/assets/globe/particles/frost_glint.json");
        JsonArray textures = def.getAsJsonArray("textures");
        assertNotNull(textures, "the particle definition must list a sprite set under \"textures\"");
        List<String> sprites = new ArrayList<>();
        textures.forEach(e -> sprites.add(e.getAsString()));
        assertEquals(List.of("minecraft:glow"), sprites,
                "frost_glint reuses ONLY the vanilla glow sprite (no custom art) — the desaturation is code-side "
                        + "in FrostGlintParticle.Provider (setColor 0.94, 0.97, 1.0), never a new texture");
    }
}
