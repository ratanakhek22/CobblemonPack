package com.ratana.cobbleforge.research.network;

import com.ratana.cobbleforge.research.block.entity.ResearchTableBlockEntity;
import com.ratana.cobbleforge.research.client.ClientResearchSync;
import com.ratana.cobbleforge.research.node.ModResearchNodes;
import com.ratana.cobbleforge.research.node.ResearchNodeDefinition;
import com.ratana.cobbleforge.research.node.TypeGroup;
import com.ratana.cobbleforge.research.player.ModAttachments;
import com.ratana.cobbleforge.research.player.NodeProgress;
import com.ratana.cobbleforge.research.player.ResearchPlayerData;
import static com.ratana.cobbleforge.research.ResearchConstants.INVESTMENT_INCREMENT;
import static com.ratana.cobbleforge.research.ResearchConstants.NO_TARGET_FALLBACK_POINTS;
import static com.ratana.cobbleforge.research.ResearchConstants.ANCIENT_ITEM_DISCOUNT;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

@EventBusSubscriber(modid = "cobbleforge")
public final class ResearchNetworking {
    private ResearchNetworking() {}

    private static Optional<ResourceLocation> pickEligibleTarget(
            com.ratana.cobbleforge.research.node.TypeGroup group, ResearchPlayerData data,
            Map<ResourceLocation, ResearchNodeDefinition> defs, java.util.Random random) {
        List<ResourceLocation> eligible = com.ratana.cobbleforge.research.node.TypeGroupRegistry.membersOf(group).stream()
                .filter(id -> {
                    ResearchNodeDefinition def = defs.get(id);
                    return def != null && data.getPointsInvested(id) < def.totalCost();
                })
                .toList();
        if (eligible.isEmpty()) return Optional.empty();
        return Optional.of(eligible.get(random.nextInt(eligible.size())));
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(ResearchActionPayload.TYPE, ResearchActionPayload.STREAM_CODEC,
                ResearchNetworking::handleAction);
        registrar.playToClient(ResearchSyncPayload.TYPE, ResearchSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientResearchSync.apply(payload)));
        registrar.playToServer(ResearchViewPayload.TYPE, ResearchViewPayload.STREAM_CODEC,
                ResearchNetworking::handleViewChange);
        registrar.playToServer(ResearchRedeemPayload.TYPE, ResearchRedeemPayload.STREAM_CODEC,
                ResearchNetworking::handleRedeem);
    }

    private static void handleRedeem(ResearchRedeemPayload payload,
                                     net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            if (!(sp.containerMenu instanceof com.ratana.cobbleforge.research.menu.ModResearchMenu menu)) return;

            ResearchTableBlockEntity be = menu.getBlockEntity();
            if (be == null || !be.hasJournal()) return;

            ItemStack ancientStack = be.getItem(ResearchTableBlockEntity.SLOT_ANCIENT_ITEM);
            ItemStack brushStack = be.getItem(ResearchTableBlockEntity.SLOT_BRUSH);
            if (ancientStack.isEmpty() || brushStack.isEmpty()) return;

            TypeGroup group = ResearchTableBlockEntity.ancientItemGroup(ancientStack);
            if (group == null) return;

            ResearchPlayerData data = getPlayerData(sp);
            Random random = new Random();

            Optional<ResourceLocation> target = pickEligibleTarget(group, data, getDefinitions(), random);

            be.removeItem(ResearchTableBlockEntity.SLOT_ANCIENT_ITEM, 1);
            brushStack.hurtAndBreak(1, sp, net.minecraft.world.entity.EquipmentSlot.MAINHAND);

            if (target.isPresent()) {
                ResourceLocation nodeId = target.get();
                ResearchNodeDefinition def = getDefinitions().get(nodeId);
                data.creditInvestedCapped(nodeId, def, ANCIENT_ITEM_DISCOUNT);
                sendSync(sp, data, Optional.of(new ResearchSyncPayload.RedeemResult(
                        Optional.of(nodeId), ANCIENT_ITEM_DISCOUNT, false)));
            } else {
                data.addPoints(NO_TARGET_FALLBACK_POINTS);
                sendSync(sp, data, Optional.of(new ResearchSyncPayload.RedeemResult(
                        Optional.empty(), NO_TARGET_FALLBACK_POINTS, true)));
            }
        });
    }

    private static void handleViewChange(ResearchViewPayload payload,
                                         net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            if (sp.containerMenu instanceof com.ratana.cobbleforge.research.menu.ModResearchMenu menu) {
                menu.setView(payload.redeemView()
                        ? com.ratana.cobbleforge.research.menu.ModResearchMenu.MenuView.REDEEM
                        : com.ratana.cobbleforge.research.menu.ModResearchMenu.MenuView.EXPLORE);
            }
        });
    }

    private static void handleForgottenKnowledge(ResearchPlayerData data, ResearchTableBlockEntity blockEntity,
                                                 ResourceLocation nodeId, ResearchNodeDefinition def) {
        if (blockEntity.forgottenKnowledgeCount() < 1) return;

        int currentInvested = data.getPointsInvested(nodeId);
        int remaining = def.remainingForNextStage(currentInvested);
        if (remaining <= 0) return; // already fully unlocked, nothing to skip into

        if (!data.creditInvestedFree(nodeId, def, remaining)) return;

        blockEntity.removeItem(ResearchTableBlockEntity.SLOT_FORGOTTEN_KNOWLEDGE, 1);
    }

    private static void handleAction(ResearchActionPayload payload,
                                     net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            if (!(sp.containerMenu instanceof com.ratana.cobbleforge.research.menu.ModResearchMenu menu)) return;

            ResearchTableBlockEntity blockEntity = menu.getBlockEntity();
            if (blockEntity == null || !blockEntity.hasJournal()) return; // Journal gates all functions

            ResearchNodeDefinition def = getDefinitions().get(payload.nodeId());
            if (def == null) return;

            ResearchPlayerData data = getPlayerData(sp);

            switch (payload.action()) {
                case SPEND_FORGOTTEN_KNOWLEDGE -> handleForgottenKnowledge(data, blockEntity, payload.nodeId(), def);
                case INVEST -> handleInvest(data, payload.nodeId(), def);
            }

            sendSync(sp, data);
        });
    }

    private static void handleInvest(ResearchPlayerData data, ResourceLocation nodeId, ResearchNodeDefinition def) {
        data.invest(nodeId, def, INVESTMENT_INCREMENT);
    }

    public static void sendSync(ServerPlayer player, ResearchPlayerData data) {
        sendSync(player, data, Optional.empty());
    }

    public static void sendSync(ServerPlayer player, ResearchPlayerData data,
                                Optional<ResearchSyncPayload.RedeemResult> redeemResult) {
        List<ResourceLocation> order = data.computeSlotOrder(getDefinitions().keySet(), new java.util.Random());
        Map<ResourceLocation, NodeProgress> progress = data.getAllProgress();
        Map<ResourceLocation, Integer> invested = data.getAllInvested();

        PacketDistributor.sendToPlayer(player,
                new ResearchSyncPayload(data.getPoints(), order, progress, invested, redeemResult));
    }

    private static ResearchPlayerData getPlayerData(ServerPlayer player) {
        return player.getData(ModAttachments.RESEARCH_DATA.get());
    }

    private static Map<ResourceLocation, ResearchNodeDefinition> getDefinitions() {
        return ModResearchNodes.all();
    }
}