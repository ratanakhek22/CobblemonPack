package com.ratana.cobbleforge.research.node;

import net.minecraft.resources.ResourceLocation;

import java.util.*;

public class ModResearchNodes {
    private static Map<ResourceLocation, ResearchNodeDefinition> NODES = Map.of();

    /** Called by ResearchNodeReloadListener every time datapacks (re)load -- including once
     *  at server start, and again on /reload. Replaces the entire map atomically. */
    public static void reload(Iterable<ResearchNodeReloadListener.Entry> entries) {
        Map<ResourceLocation, ResearchNodeDefinition> built = new HashMap<>();
        Map<Map<ResourceLocation, Integer>, List<ResourceLocation>> byRecipe = new HashMap<>();

        for (ResearchNodeReloadListener.Entry entry : entries) {
            var stages = entry.bespoke()
                    ? List.of(NodeStage.SILHOUETTE, NodeStage.FULL_REVEAL)
                    : List.of(NodeStage.SILHOUETTE, NodeStage.FULL_REVEAL, NodeStage.SACRIFICE_INFO);
            ResearchNodeDefinition def = new ResearchNodeDefinition(
                    entry.species(), stages, entry.totalCost(), entry.bespoke(), entry.requiredItems());
            built.put(entry.species(), def);

            if (!entry.bespoke()) {
                Map<ResourceLocation, Integer> normalized = new HashMap<>();
                for (var req : entry.requiredItems()) {
                    normalized.merge(req.item(), req.count(), Integer::sum);
                }
                byRecipe.computeIfAbsent(normalized, k -> new ArrayList<>()).add(entry.species());
            }
        }

        byRecipe.forEach((recipe, species) -> {
            if (species.size() > 1) {
                com.ratana.cobbleforge.CobbleForgeMod.LOGGER.error(
                        "Duplicate altar recipe {} shared by multiple legendaries: {}", recipe, species);
            }
        });

        NODES = Collections.unmodifiableMap(built);
    }

    public static ResearchNodeDefinition get(ResourceLocation speciesId) {
        return NODES.get(speciesId);
    }

    public static Map<ResourceLocation, ResearchNodeDefinition> all() {
        return NODES;
    }
}