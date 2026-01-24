package com.example.tectonicterrain;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TectonicTerrainMod implements ModInitializer {
    public static final String MOD_ID = "tectonic_terrain_mod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("{} initialized. Tectonic terrain is controlled by Tectonic's world preset (e.g. level-type=tectonic:tectonic).", MOD_ID);
    }
}
