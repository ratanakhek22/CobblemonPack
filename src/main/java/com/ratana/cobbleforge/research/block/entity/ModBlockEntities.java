package com.ratana.cobbleforge.research.block.entity;

import com.ratana.cobbleforge.CobbleForgeMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import com.ratana.cobbleforge.research.block.ModBlocks;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, CobbleForgeMod.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RuinBlockEntity>> RUIN_BLOCK =
            BLOCK_ENTITIES.register("ruin_block",
                    () -> BlockEntityType.Builder.of(RuinBlockEntity::new, ModBlocks.RUIN_BLOCK.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ResearchTableBlockEntity>> RESEARCH_TABLE =
            BLOCK_ENTITIES.register("research_table",
                    () -> BlockEntityType.Builder.of(ResearchTableBlockEntity::new, ModBlocks.RESEARCH_TABLE.get()).build(null));
}