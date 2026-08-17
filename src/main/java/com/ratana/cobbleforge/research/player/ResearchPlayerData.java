package com.ratana.cobbleforge.research.player;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ResearchPlayerData {
    public static final Codec<ResearchPlayerData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("points").forGetter(ResearchPlayerData::getPoints),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.STRING)
                    .fieldOf("node_progress").forGetter(ResearchPlayerData::serializeProgress),
            ResourceLocation.CODEC.listOf().fieldOf("dex_awarded")
                    .forGetter(data -> new java.util.ArrayList<>(data.dexAwarded)),
            ResourceLocation.CODEC.listOf().fieldOf("capture_awarded")
                    .forGetter(data -> new java.util.ArrayList<>(data.captureAwarded))
    ).apply(instance, ResearchPlayerData::fromCodec));

    private int points;
    private final Map<ResourceLocation, NodeProgress> progress;
    private final Set<ResourceLocation> dexAwarded;
    private final Set<ResourceLocation> captureAwarded;

    public ResearchPlayerData() {
        this(0, new HashMap<>(), new HashSet<>(), new HashSet<>());
    }

    private ResearchPlayerData(int points, Map<ResourceLocation, NodeProgress> progress,
                               Set<ResourceLocation> dexAwarded, Set<ResourceLocation> captureAwarded) {
        this.points = points;
        this.progress = new HashMap<>(progress);
        this.dexAwarded = new HashSet<>(dexAwarded);
        this.captureAwarded = new HashSet<>(captureAwarded);
    }

    private static ResearchPlayerData fromCodec(int points, Map<ResourceLocation, String> rawProgress,
                                                java.util.List<ResourceLocation> dexAwardedList,
                                                java.util.List<ResourceLocation> captureAwardedList) {
        Map<ResourceLocation, NodeProgress> progress = new HashMap<>();
        rawProgress.forEach((id, name) -> progress.put(id, NodeProgress.valueOf(name)));
        return new ResearchPlayerData(points, progress, new HashSet<>(dexAwardedList), new HashSet<>(captureAwardedList));
    }

    private Map<ResourceLocation, String> serializeProgress() {
        Map<ResourceLocation, String> raw = new HashMap<>();
        progress.forEach((id, p) -> raw.put(id, p.name()));
        return raw;
    }

    public int getPoints() { return points; }

    public void addPoints(int amount) {
        if (amount > 0) points += amount;
    }

    /** Returns true only the first time this species' dex entry is awarded. */
    public boolean markDexAwarded(ResourceLocation speciesId) {
        return dexAwarded.add(speciesId);
    }

    /** Returns true only the first time this species' capture is awarded. */
    public boolean markCaptureAwarded(ResourceLocation speciesId) {
        return captureAwarded.add(speciesId);
    }

    public NodeProgress getProgress(ResourceLocation speciesId) {
        return progress.getOrDefault(speciesId, NodeProgress.LOCKED);
    }

    public boolean tryAdvance(ResourceLocation speciesId, NodeProgress target, int cost) {
        NodeProgress current = getProgress(speciesId);
        if (target.ordinal() != current.ordinal() + 1) return false;
        if (points < cost) return false;
        points -= cost;
        progress.put(speciesId, target);
        return true;
    }
}
