package com.ratana.cobbleforge.research.node;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record ResearchNodeDefinition(
        ResourceLocation speciesId,
        List<NodeStage> stages,      // ordered; e.g. [SILHOUETTE, FULL_REVEAL] for bespoke
        List<Integer> stageWeights,  // same size as stages; relative share of totalCost
        int totalCost,               // constant across all legendaries per your design doc
        boolean bespoke
) {
    /** Points required to clear this specific stage's pay-wall, derived from the shared total. */
    public int costForStage(NodeStage stage) {
        int index = stages.indexOf(stage);
        if (index < 0) {
            throw new IllegalArgumentException("Stage " + stage + " not defined for " + speciesId);
        }
        int totalWeight = stageWeights.stream().mapToInt(Integer::intValue).sum();
        return Math.round(totalCost * (stageWeights.get(index) / (float) totalWeight));
    }

    public NodeStage firstStage() {
        return stages.getFirst();
    }

    /** Returns null if `current` is the last stage (path complete). */
    public NodeStage stageAfter(NodeStage current) {
        int index = stages.indexOf(current);
        return (index < 0 || index + 1 >= stages.size()) ? null : stages.get(index + 1);
    }
}
