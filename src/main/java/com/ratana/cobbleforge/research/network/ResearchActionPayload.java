package com.ratana.cobbleforge.research.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> Server: "I want to spend points advancing this node to the given stage."
 * Server re-validates cost server-side via ResearchPlayerData#tryAdvance -- this
 * payload only expresses intent, it never carries a trusted cost.
 */
public record ResearchActionPayload(ResourceLocation nodeId, Action action) implements CustomPacketPayload {

    public enum Action { INVEST, BUY_SILHOUETTE, BUY_REVEAL, BUY_INGREDIENTS, SPEND_FORGOTTEN_KNOWLEDGE }

    public static final Type<ResearchActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("cobbleforge", "research_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ResearchActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC, ResearchActionPayload::nodeId,
                    ByteBufCodecs.idMapper(i -> Action.values()[i], Action::ordinal), ResearchActionPayload::action,
                    ResearchActionPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}