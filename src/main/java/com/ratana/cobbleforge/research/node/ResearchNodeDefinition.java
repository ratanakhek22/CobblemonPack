package com.ratana.cobbleforge.research.node;

import com.ratana.cobbleforge.research.player.NodeProgress;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record ResearchNodeDefinition(
        ResourceLocation speciesId,
        List<NodeStage> stages,      // ordered; e.g. [SILHOUETTE, FULL_REVEAL] for bespoke
        int totalCost,               // constant across all legendaries per your design doc
        boolean bespoke
) {
    /** Points required to clear this specific stage's pay-wall, derived from the shared total. */
    public int costForStage(NodeStage stage) {
        int index = stages.indexOf(stage);
        if (index < 0) {
            throw new IllegalArgumentException("Stage " + stage + " not defined for " + speciesId);
        }
        int stepCount = stages.size();
        int base = totalCost / stepCount;
        int remainder = totalCost % stepCount;
        return (index == stepCount - 1) ? base + remainder : base;
    }

    public NodeStage firstStage() {
        return stages.getFirst();
    }

    /** Returns null if `current` is the last stage (path complete). */
    public NodeStage stageAfter(NodeStage current) {
        int index = stages.indexOf(current);
        return (index < 0 || index + 1 >= stages.size()) ? null : stages.get(index + 1);
    }

    /**
     * Given a cumulative invested amount, returns the furthest NodeProgress stage whose
     * cumulative cost is now covered. Walks stages in order rather than computing an index
     * directly, so a bespoke node's shorter list (no SACRIFICE_INFO entry) is respected
     * automatically instead of needing a separate branch here.
     */
    public NodeProgress progressForInvested(int invested) {
        NodeProgress result = NodeProgress.LOCKED;
        int cumulative = 0;
        for (NodeStage stage : stages) {
            cumulative += costForStage(stage);
            if (invested < cumulative) break;
            result = progressForStage(stage);
        }
        return result;
    }

    /** The stage this node stops at: READY_FOR_SACRIFICE for standard nodes, REVEALED for bespoke. */
    public NodeProgress finalProgress() {
        return stages.isEmpty() ? NodeProgress.LOCKED : progressForStage(stages.getLast());
    }

    private static NodeProgress progressForStage(NodeStage stage) {
        return switch (stage) {
            case SILHOUETTE -> NodeProgress.SILHOUETTE;
            case FULL_REVEAL -> NodeProgress.REVEALED;
            case SACRIFICE_INFO -> NodeProgress.READY_FOR_SACRIFICE;
        };
    }
}