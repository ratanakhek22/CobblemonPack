package com.ratana.cobbleforge.research.node;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModResearchNodes {
    private static final Map<ResourceLocation, ResearchNodeDefinition> NODES = new HashMap<>();

    /** Call once during mod setup — populates the map. */
    public static void bootstrap() {
        // Shared-path legendaries: 3 stages, silhouette / reveal / sacrifice-info
        registerShared("cobblemon:moltres");
        registerShared("cobblemon:zapdos");
        registerShared("cobblemon:articuno");

        // Flagship legendaries: 2 stages only, hands off to a bespoke chain afterward
        registerBespoke("cobblemon:mewtwo");
    }

    private static final int TOTAL_COST = 60; // placeholder — same for every legendary per your design doc

    private static void registerShared(String speciesId) {
        register(speciesId,
                List.of(NodeStage.SILHOUETTE, NodeStage.FULL_REVEAL, NodeStage.SACRIFICE_INFO),
                List.of(20, 30, 50), // weights must match stage count and order
                false);
    }

    private static void registerBespoke(String speciesId) {
        register(speciesId,
                List.of(NodeStage.SILHOUETTE, NodeStage.FULL_REVEAL),
                List.of(40, 60),
                true);
    }

    private static void register(String speciesId, List<NodeStage> stages, List<Integer> weights, boolean bespoke) {
        ResourceLocation id = ResourceLocation.parse(speciesId);
        NODES.put(id, new ResearchNodeDefinition(id, stages, weights, TOTAL_COST, bespoke));
    }

    public static ResearchNodeDefinition get(ResourceLocation speciesId) {
        return NODES.get(speciesId);
    }

    public static Map<ResourceLocation, ResearchNodeDefinition> all() {
        return Collections.unmodifiableMap(NODES);
    }
}