package com.ratana.cobbleforge;

import com.ratana.cobbleforge.research.item.AncientItem;
import com.ratana.cobbleforge.research.node.TypeGroup;
import com.ratana.cobbleforge.research.node.TypeGroupRegistry;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

import com.ratana.cobbleforge.research.block.ModBlocks;
import com.ratana.cobbleforge.research.block.entity.ModBlockEntities;
import com.ratana.cobbleforge.research.player.ModAttachments;
import com.ratana.cobbleforge.research.node.ModResearchNodes;
import com.ratana.cobbleforge.research.player.ResearchPointHooks;
import com.ratana.cobbleforge.research.item.ForgottenKnowledge;
import com.ratana.cobbleforge.research.item.ResearchJournal;
import com.ratana.cobbleforge.research.menu.ModMenuTypes;

@Mod(CobbleForgeMod.MOD_ID)
public class CobbleForgeMod {
    public static final String MOD_ID = "cobbleforge";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    public static final DeferredItem<Item> FORGOTTEN_KNOWLEDGE = ITEMS.register(
            "forgotten_knowledge",
            () -> new ForgottenKnowledge(new Item.Properties())
    );
    public static final DeferredItem<Item> RESEARCH_JOURNAL = ITEMS.register(
            "research_journal",
            () -> new ResearchJournal(new Item.Properties())
    );

    public static final DeferredItem<BlockItem> RUIN_BLOCK_ITEM = ITEMS.registerSimpleBlockItem(
            "ruin_block", ModBlocks.RUIN_BLOCK
    );
    public static final DeferredItem<BlockItem> RESEARCH_TABLE_ITEM = ITEMS.registerSimpleBlockItem(
            "research_table", ModBlocks.RESEARCH_TABLE
    );

    public static final DeferredItem<AncientItem> ANCIENT_GEODE = ITEMS.register(
            "ancient_geode",
            () -> new AncientItem(new Item.Properties().stacksTo(1), TypeGroup.STEEL_ROCK_GROUND)
    );
    public static final DeferredItem<AncientItem> ANCIENT_PRISMARINE = ITEMS.register(
            "ancient_prismarine",
            () -> new AncientItem(new Item.Properties().stacksTo(1), TypeGroup.WATER_ICE_ELECTRIC)
    );
    public static final DeferredItem<AncientItem> ANCIENT_SEED = ITEMS.register(
            "ancient_seed",
            () -> new AncientItem(new Item.Properties().stacksTo(1), TypeGroup.GRASS_BUG_POISON)
    );
    public static final DeferredItem<AncientItem> ANCIENT_AMBER = ITEMS.register(
            "ancient_amber",
            () -> new AncientItem(new Item.Properties().stacksTo(1), TypeGroup.DRAGON_FAIRY_FIRE)
    );
    public static final DeferredItem<AncientItem> ANCIENT_TOME = ITEMS.register(
            "ancient_tome",
            () -> new AncientItem(new Item.Properties().stacksTo(1), TypeGroup.GHOST_DARK_PSYCHIC)
    );
    public static final DeferredItem<AncientItem> ANCIENT_TOTEM = ITEMS.register(
            "ancient_totem",
            () -> new AncientItem(new Item.Properties().stacksTo(1), TypeGroup.FLYING_FIGHTING_NORMAL)
    );

    public CobbleForgeMod(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        ModAttachments.ATTACHMENT_TYPES.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModMenuTypes.MENU_TYPES.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
    }

    private void onServerStarting(net.neoforged.neoforge.event.server.ServerStartingEvent event) {
        TypeGroupRegistry.bootstrap(ModResearchNodes.all().values());
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        ModResearchNodes.bootstrap();
        ResearchPointHooks.register();
    }
}