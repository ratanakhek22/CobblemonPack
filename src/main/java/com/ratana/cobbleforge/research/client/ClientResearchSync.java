package com.ratana.cobbleforge.research.client;

import com.ratana.cobbleforge.research.menu.ModJournalMenu;
import com.ratana.cobbleforge.research.menu.ModResearchMenu;
import com.ratana.cobbleforge.research.network.ResearchSyncPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientResearchSync {
    private ClientResearchSync() {}

    public static void apply(ResearchSyncPayload payload) {
        if (Minecraft.getInstance().player == null) return;
        var menu = Minecraft.getInstance().player.containerMenu;

        if (menu instanceof ModResearchMenu m) {
            m.applySync(payload.points(), payload.orderedNodeIds(), payload.progress(), payload.invested());
            payload.redeemResult().ifPresent(m::setLastRedeemResult);
        } else if (menu instanceof ModJournalMenu jm) {
            jm.applySync(payload.points(), payload.orderedNodeIds(), payload.progress(), payload.invested());
        }
    }
}