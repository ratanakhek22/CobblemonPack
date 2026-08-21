package com.ratana.cobbleforge.research.client;

import com.ratana.cobbleforge.research.menu.ModMenuTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = "cobbleforge", value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.RESEARCH_TABLE.get(), ModResearchScreen::new);
        event.register(ModMenuTypes.RESEARCH_JOURNAL.get(), ModJournalScreen::new);
    }
}