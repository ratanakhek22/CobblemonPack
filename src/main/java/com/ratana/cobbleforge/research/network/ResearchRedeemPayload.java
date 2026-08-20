package com.ratana.cobbleforge.research.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ResearchRedeemPayload() implements CustomPacketPayload {

    public static final Type<ResearchRedeemPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("cobbleforge", "research_redeem"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ResearchRedeemPayload> STREAM_CODEC =
            StreamCodec.unit(new ResearchRedeemPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}