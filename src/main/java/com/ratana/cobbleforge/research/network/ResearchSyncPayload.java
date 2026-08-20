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
import java.util.Optional;

public record ResearchSyncPayload(
        int points,
        List<ResourceLocation> orderedNodeIds,
        Map<ResourceLocation, NodeProgress> progress,
        Map<ResourceLocation, Integer> invested,
        Optional<RedeemResult> redeemResult   // NEW: empty for every ordinary sync; populated
        // only immediately after a redeem, read once by
        // the client and never re-shown on later syncs.
) implements CustomPacketPayload {

    /** amount is 0 when wasFallback is true (the flat NO_TARGET_FALLBACK_POINTS points went
     *  to the player's wallet, not to any specific node, so there's no node/amount pair to
     *  show — the client should render a different message for that case). nodeId is present
     *  either way isn't needed for the fallback case; encoded as the table's own placeholder
     *  ResourceLocation isn't ideal, so nodeId is itself an Optional too. */
    public record RedeemResult(Optional<ResourceLocation> nodeId, int amount, boolean wasFallback) {
        public static final StreamCodec<RegistryFriendlyByteBuf, RedeemResult> STREAM_CODEC =
                StreamCodec.composite(
                        ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs::optional), RedeemResult::nodeId,
                        ByteBufCodecs.VAR_INT, RedeemResult::amount,
                        ByteBufCodecs.BOOL, RedeemResult::wasFallback,
                        RedeemResult::new
                );
    }

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
                    RedeemResult.STREAM_CODEC.apply(ByteBufCodecs::optional), ResearchSyncPayload::redeemResult,
                    ResearchSyncPayload::new
            );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}