package com.ratana.cobbleforge.research.network;

import com.ratana.cobbleforge.research.player.NodeProgress;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Server -> Client: full snapshot of this player's research state. Sent once when the
 * table menu opens, and re-sent after every successful action. Full-snapshot rather
 * than incremental diffs is fine here -- ResearchPlayerData's own doc comment notes
 * recomputing slot order is "cheap at this scale (tens of nodes)".
 */
public record ResearchSyncPayload(
        int points,
        List<ResourceLocation> orderedNodeIds,   // index == slot position, server-computed
        Map<ResourceLocation, NodeProgress> progress,
        Map<ResourceLocation, Integer> invested
) implements CustomPacketPayload {

    public static final Type<ResearchSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("cobbleforge", "research_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ResearchSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ResearchSyncPayload::points,
                    ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()), ResearchSyncPayload::orderedNodeIds,
                    ByteBufCodecs.map(
                            HashMap::new,
                            ResourceLocation.STREAM_CODEC,
                            ByteBufCodecs.idMapper(i -> NodeProgress.values()[i], NodeProgress::ordinal)
                    ), ResearchSyncPayload::progress,
                    ByteBufCodecs.map(HashMap::new, ResourceLocation.STREAM_CODEC, ByteBufCodecs.VAR_INT),
                    ResearchSyncPayload::invested,
                    ResearchSyncPayload::new
            );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}