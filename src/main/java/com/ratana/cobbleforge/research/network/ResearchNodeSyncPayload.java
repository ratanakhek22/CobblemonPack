package com.ratana.cobbleforge.research.network;

import com.ratana.cobbleforge.research.node.NodeStage;
import com.ratana.cobbleforge.research.node.ResearchNodeDefinition;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record ResearchNodeSyncPayload(List<ResearchNodeDefinition> nodes) implements CustomPacketPayload {

    public static final Type<ResearchNodeSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("cobbleforge", "research_node_sync"));

    private static final StreamCodec<RegistryFriendlyByteBuf, ResearchNodeDefinition> DEFINITION_CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC, ResearchNodeDefinition::speciesId,
                    ByteBufCodecs.idMapper(i -> NodeStage.values()[i], NodeStage::ordinal)
                            .apply(ByteBufCodecs.list()), ResearchNodeDefinition::stages,
                    ByteBufCodecs.VAR_INT, ResearchNodeDefinition::totalCost,
                    ByteBufCodecs.BOOL, ResearchNodeDefinition::bespoke,
                    ResearchNodeDefinition.RequiredItem.STREAM_CODEC.apply(ByteBufCodecs.list()),
                    ResearchNodeDefinition::requiredItems,
                    ResearchNodeDefinition::new
            );

    public static final StreamCodec<RegistryFriendlyByteBuf, ResearchNodeSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    DEFINITION_CODEC.apply(ByteBufCodecs.list()), ResearchNodeSyncPayload::nodes,
                    ResearchNodeSyncPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}