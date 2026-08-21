package com.ratana.cobbleforge.research.node;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ResearchNodeReloadListener extends SimpleJsonResourceReloadListener {

    public ResearchNodeReloadListener() {
        super(new Gson(), "research_nodes");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> map, @NotNull ResourceManager resourceManager,
                         @NotNull ProfilerFiller profiler) {
        com.ratana.cobbleforge.CobbleForgeMod.LOGGER.info("ResearchNodeReloadListener.apply() called with {} raw entries", map.size());
        List<Entry> entries = new ArrayList<>();
        map.forEach((fileId, json) -> {
            com.ratana.cobbleforge.CobbleForgeMod.LOGGER.info("Found research node file: {}", fileId);
            Entry.CODEC.parse(JsonOps.INSTANCE, json).resultOrPartial(
                    err -> com.ratana.cobbleforge.CobbleForgeMod.LOGGER.error(
                            "Failed to parse research node {}: {}", fileId, err)
            ).ifPresent(entries::add);
        });
        com.ratana.cobbleforge.CobbleForgeMod.LOGGER.info("Parsed {} valid research node entries", entries.size());
        ModResearchNodes.reload(entries);
    }

    public record Entry(ResourceLocation species, boolean bespoke, int totalCost,
                        List<ResearchNodeDefinition.RequiredItem> requiredItems) {
        public static final com.mojang.serialization.Codec<Entry> CODEC =
                com.mojang.serialization.codecs.RecordCodecBuilder.create(instance -> instance.group(
                        ResourceLocation.CODEC.fieldOf("species").forGetter(Entry::species),
                        com.mojang.serialization.Codec.BOOL.optionalFieldOf("bespoke", false).forGetter(Entry::bespoke),
                        com.mojang.serialization.Codec.INT.fieldOf("total_cost").forGetter(Entry::totalCost),
                        ResearchNodeDefinition.RequiredItem.CODEC.listOf()
                                .optionalFieldOf("required_items", List.of()).forGetter(Entry::requiredItems)
                ).apply(instance, Entry::new));
    }
}