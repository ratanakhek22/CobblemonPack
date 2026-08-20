package com.ratana.cobbleforge.research.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> Server: "switch this menu's shared-slot visibility to the given view."
 *  No node is involved -- this is menu-wide state, not per-node, which is why it's a
 *  separate payload from ResearchActionPayload rather than a new Action entry there. */
public record ResearchViewPayload(boolean redeemView) implements CustomPacketPayload {

    public static final Type<ResearchViewPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("cobbleforge", "research_view"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ResearchViewPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, ResearchViewPayload::redeemView,
                    ResearchViewPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}