package com.ratana.cobbleforge.research.block;

import com.ratana.cobbleforge.CobbleForgeMod;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(CobbleForgeMod.MOD_ID);

    public static final DeferredBlock<RuinBlock> RUIN_BLOCK =
            BLOCKS.register("ruin_block",
                    () -> new RuinBlock(Block.Properties.of()
                            .strength(-1.0F, 3600000.0F)
                            .noLootTable()));
}