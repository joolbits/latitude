package com.example.globe.world;

import com.example.globe.GlobeMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Registers worldgen-only blocks owned by the inner-cave trap feature. */
public final class CaveTrapBlocks {

    private CaveTrapBlocks() {
    }

    /**
     * False-floor powder used at trap entrances. It has no item and no loot table; its public singleton is
     * the stable worldgen placement seam.
     */
    public static CaveTrapPowderSnowBlock CAVE_TRAP_POWDER_SNOW;

    /** Registers {@code globe:cave_trap_powder_snow} during unconditional mod initialization. */
    public static void register() {
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id("cave_trap_powder_snow"));
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.ofLegacyCopy(Blocks.POWDER_SNOW)
                .noLootTable()
                .setId(blockKey);
        CAVE_TRAP_POWDER_SNOW = new CaveTrapPowderSnowBlock(properties);
        Registry.register(BuiltInRegistries.BLOCK, blockKey, CAVE_TRAP_POWDER_SNOW);

        GlobeMod.LOGGER.info("registered globe:cave_trap_powder_snow (worldgen-only mob-stumble floor)");
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(GlobeMod.MOD_ID, path);
    }
}
