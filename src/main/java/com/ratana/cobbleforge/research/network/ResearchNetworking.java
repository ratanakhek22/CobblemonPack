package com.ratana.cobbleforge.research.network;

import com.ratana.cobbleforge.research.client.ClientResearchSync;
import com.ratana.cobbleforge.research.node.ModResearchNodes;
import com.ratana.cobbleforge.research.node.NodeStage;
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
import java.util.stream.Collectors;

@EventBusSubscriber(modid = "cobbleforge")
public final class ResearchNetworking {
    private ResearchNetworking() {}

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

            if (payload.action() == ResearchActionPayload.Action.SPEND_FORGOTTEN_KNOWLEDGE) {
                // TODO: consume 1 Forgotten Knowledge from the menu's SLOT_FORGOTTEN_KNOWLEDGE
                // input slot (need the open ModResearchMenu instance for that, not just
                // player data), then advance one stage for free regardless of point cost.
                sendSync(sp, data);
                return;
            }

            // Fixed identity mapping -- each NodeStage always means the same NodeProgress,
            // regardless of which node it belongs to. No positional bridging needed.
            NodeStage stage = switch (payload.action()) {
                case BUY_SILHOUETTE -> NodeStage.SILHOUETTE;
                case BUY_REVEAL -> NodeStage.FULL_REVEAL;
                case BUY_INGREDIENTS -> NodeStage.SACRIFICE_INFO;
                case SPEND_FORGOTTEN_KNOWLEDGE -> throw new IllegalStateException("handled above");
            };
            NodeProgress target = switch (stage) {
                case SILHOUETTE -> NodeProgress.SILHOUETTE;
                case FULL_REVEAL -> NodeProgress.REVEALED;
                case SACRIFICE_INFO -> NodeProgress.READY_FOR_SACRIFICE;
            };

            // Bespoke nodes don't have SACRIFICE_INFO -- def.stages() won't contain it,
            // and costForStage would throw, so guard instead of relying on the exception.
            if (def.stages().contains(stage)) {
                data.tryAdvance(payload.nodeId(), target, def.costForStage(stage));
            }

            sendSync(sp, data);
        });
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