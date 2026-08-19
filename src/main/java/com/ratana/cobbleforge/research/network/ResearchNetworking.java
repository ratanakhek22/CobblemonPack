package com.ratana.cobbleforge.research.network;

import com.ratana.cobbleforge.research.client.ClientResearchSync;
import com.ratana.cobbleforge.research.node.ModResearchNodes;
import com.ratana.cobbleforge.research.node.ResearchNodeDefinition;
import com.ratana.cobbleforge.research.player.ModAttachments;
import com.ratana.cobbleforge.research.player.NodeProgress;
import com.ratana.cobbleforge.research.player.ResearchPlayerData;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.List;
import java.util.Map;

@EventBusSubscriber(modid = "cobbleforge")
public final class ResearchNetworking {
    private ResearchNetworking() {}

    /**
     * Flat cost per "research deeper" click, everywhere, regardless of node or stage.
     * MUST match the client's ModResearchScreen.INVESTMENT_INCREMENT exactly, or the button's
     * displayed cost lies about what actually gets charged. Right now that's two independent
     * hardcoded 10s in two files — worth pulling into one shared constant (e.g. a static field
     * on ResearchActionPayload, since that's common-side and both client and server already
     * depend on it) once you're back in here, rather than trusting two copies to stay in sync.
     */
    private static final int INVESTMENT_INCREMENT = 10;

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(ResearchActionPayload.TYPE, ResearchActionPayload.STREAM_CODEC,
                ResearchNetworking::handleAction);
        registrar.playToClient(ResearchSyncPayload.TYPE, ResearchSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientResearchSync.apply(payload)));
    }

    private static void handleAction(ResearchActionPayload payload,
                                     net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;

            // getDefinitions() is keyed by speciesId -- that's the node's only identity now.
            ResearchNodeDefinition def = getDefinitions().get(payload.nodeId());
            if (def == null) return; // unknown node id, ignore silently

            ResearchPlayerData data = getPlayerData(sp);

            switch (payload.action()) {
                case SPEND_FORGOTTEN_KNOWLEDGE -> {
                    // TODO: consume 1 Forgotten Knowledge from the menu's SLOT_FORGOTTEN_KNOWLEDGE
                    // input slot (need the open ModResearchMenu instance for that, not just
                    // player data), then advance one stage for free regardless of point cost.
                }
                case INVEST -> handleInvest(data, payload.nodeId(), def);
            }

            sendSync(sp, data);
        });
    }

    /**
     * NOTE: data.invest(...) doesn't exist yet -- this is the shape ResearchPlayerData needs
     * to grow to make this compile and actually work. Written here as a spec rather than
     * guessed-at code, since I haven't seen ResearchPlayerData.java's real internals (points
     * storage, invested map, progress map) to implement it correctly myself.
     * Expected contract for ResearchPlayerData#invest(nodeId, def, amount):
     *  1. If getPoints() < amount, do nothing -- reject silently. The client already disables
     *     the button when it can't afford a click, but that's a courtesy, not a guarantee;
     *     the server is the only side allowed to actually decide this.
     *  2. Otherwise: subtract `amount` from points, add `amount` to invested[nodeId].
     *  3. Re-derive NodeProgress from the new cumulative invested[nodeId] against def's ordered
     *     stages() and their (now-flat, per your last refactor) per-stage cost -- e.g. walk the
     *     stage list, accumulating each stage's cost, and set progress to the furthest stage
     *     whose cumulative cost the invested total now covers. Loop rather than checking only
     *     "one stage forward," in case INVESTMENT_INCREMENT is ever changed to jump more than
     *     one stage's worth in a single click.
     *  4. Bespoke nodes stop at REVEALED -- def.stages() already excludes SACRIFICE_INFO for
     *     them (per the existing bespoke guard this method is replacing), so walking exactly
     *     def.stages() naturally respects that without a separate bespoke check here.
     *  5. Never let progress move backward, and never overshoot past def.stages()'s last entry
     *     even if invested somehow exceeds totalCost (shouldn't happen if the server rejects
     *     over-budget invests at step 1, but worth the same defensive floor/ceiling either way).
     */
    private static void handleInvest(ResearchPlayerData data, ResourceLocation nodeId, ResearchNodeDefinition def) {
        data.invest(nodeId, def, INVESTMENT_INCREMENT);
    }

    public static void sendSync(ServerPlayer player, ResearchPlayerData data) {
        List<ResourceLocation> order = data.computeSlotOrder(getDefinitions().keySet(), new java.util.Random());
        Map<ResourceLocation, NodeProgress> progress = data.getAllProgress();
        Map<ResourceLocation, Integer> invested = data.getAllInvested();

        PacketDistributor.sendToPlayer(player,
                new ResearchSyncPayload(data.getPoints(), order, progress, invested));
    }

    private static ResearchPlayerData getPlayerData(ServerPlayer player) {
        return player.getData(ModAttachments.RESEARCH_DATA.get());
    }

    private static Map<ResourceLocation, ResearchNodeDefinition> getDefinitions() {
        return ModResearchNodes.all();
    }
}