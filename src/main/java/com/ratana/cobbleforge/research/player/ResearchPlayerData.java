package com.ratana.cobbleforge.research.player;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.ratana.cobbleforge.research.node.ResearchNodeDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class ResearchPlayerData {
    public static final Codec<ResearchPlayerData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("points").forGetter(ResearchPlayerData::getPoints),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.STRING)
                    .fieldOf("node_progress").forGetter(ResearchPlayerData::serializeProgress),
            ResourceLocation.CODEC.listOf().fieldOf("dex_awarded")
                    .forGetter(data -> new ArrayList<>(data.dexAwarded)),
            ResourceLocation.CODEC.listOf().fieldOf("capture_awarded")
                    .forGetter(data -> new ArrayList<>(data.captureAwarded)),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT)
                    .fieldOf("node_tiebreak").forGetter(data -> new HashMap<>(data.nodeTiebreak)),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT)
                    .fieldOf("points_invested").forGetter(data -> new HashMap<>(data.pointsInvested))
    ).apply(instance, ResearchPlayerData::fromCodec));

    private int points;
    private final Map<ResourceLocation, NodeProgress> progress;
    private final Set<ResourceLocation> dexAwarded;
    private final Set<ResourceLocation> captureAwarded;
    /**
     * Stable per-node random tiebreaker, assigned once on first encounter and never
     * changed after. Slot order is NOT stored directly — it's recomputed live by
     * sorting nodes on (progress descending, tiebreak ascending), so a node's ring
     * position is always a function of current progress, with the tiebreak only
     * separating nodes that are tied on progress (most commonly: everything still
     * untouched). This means a freshly added legendary — starting at zero progress,
     * same as any node this player just hasn't gotten to yet — is indistinguishable
     * from an old, ignored node. Nothing about position reveals *when* a node was
     * added, only how far the player has personally pushed it.
     */
    private final Map<ResourceLocation, Integer> nodeTiebreak;
    /** Cumulative points this player has spent unlocking each individual node so far. */
    private final Map<ResourceLocation, Integer> pointsInvested;

    public ResearchPlayerData() {
        this(0, new HashMap<>(), new HashSet<>(), new HashSet<>(), new HashMap<>(), new HashMap<>());
    }

    private ResearchPlayerData(int points, Map<ResourceLocation, NodeProgress> progress,
                               Set<ResourceLocation> dexAwarded, Set<ResourceLocation> captureAwarded,
                               Map<ResourceLocation, Integer> nodeTiebreak,
                               Map<ResourceLocation, Integer> pointsInvested) {
        this.points = points;
        this.progress = new HashMap<>(progress);
        this.dexAwarded = new HashSet<>(dexAwarded);
        this.captureAwarded = new HashSet<>(captureAwarded);
        this.nodeTiebreak = new HashMap<>(nodeTiebreak);
        this.pointsInvested = new HashMap<>(pointsInvested);
    }

    private static ResearchPlayerData fromCodec(int points, Map<ResourceLocation, String> rawProgress,
                                                List<ResourceLocation> dexAwardedList,
                                                List<ResourceLocation> captureAwardedList,
                                                Map<ResourceLocation, Integer> nodeTiebreakMap,
                                                Map<ResourceLocation, Integer> pointsInvestedMap) {
        Map<ResourceLocation, NodeProgress> progress = new HashMap<>();
        rawProgress.forEach((id, name) -> progress.put(id, NodeProgress.valueOf(name)));
        return new ResearchPlayerData(points, progress, new HashSet<>(dexAwardedList),
                new HashSet<>(captureAwardedList), nodeTiebreakMap, pointsInvestedMap);
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
        pointsInvested.merge(speciesId, cost, Integer::sum);
        return true;
    }

    /**
     * Adds a flat `amount` of investment into a node, if affordable, then re-derives that
     * node's NodeProgress from the new cumulative invested total via
     * def.progressForInvested(...) — see the required addition to ResearchNodeDefinition
     * below. Unlike tryAdvance, this doesn't target a specific stage; a single call may
     * cover only part of a stage's cost (progress stays put, invested still climbs), or in
     * principle cross more than one stage at once if amount is ever made larger than a
     * single stage's cost.
     *
     * Rejects outright (no deduction at all) rather than silently wasting points if:
     *  - amount isn't positive, or the player can't afford it, or
     *  - the node is already at its final stage (def.finalProgress()) — nothing left to buy,
     *    so there's no reason to let a stray/duplicate client packet drain points for nothing.
     *
     * Returns whether the investment was accepted, so the caller can decide whether a sync
     * is even necessary (a rejected invest changes nothing worth broadcasting).
     */
    public boolean invest(ResourceLocation speciesId, ResearchNodeDefinition def, int amount) {
        if (amount <= 0 || points < amount) return false;

        NodeProgress current = getProgress(speciesId);
        if (current == def.finalProgress()) return false; // fully unlocked already, nothing to buy

        points -= amount;
        int newInvested = pointsInvested.merge(speciesId, amount, Integer::sum);

        NodeProgress computed = def.progressForInvested(newInvested);
        if (computed.ordinal() > current.ordinal()) {
            progress.put(speciesId, computed);
        }
        return true;
    }

    /** Total points this player has sunk into a given node so far (0 if untouched). */
    public int getPointsInvested(ResourceLocation speciesId) {
        return pointsInvested.getOrDefault(speciesId, 0);
    }

    /**
     * Computes this player's current slot order across all registered nodes. A node's
     * position in the returned list IS its flat slot index (feed straight into
     * NodeSlotLayout.screenPosition). Recomputed on every call — cheap at this scale
     * (tens of nodes) — so progress changes are reflected immediately without needing
     * to persist or update a separate ordering. Any node in {@code allNodeIds} that
     * this player hasn't encountered before gets a random tiebreaker assigned and
     * persisted on the spot.
     */
    public List<ResourceLocation> computeSlotOrder(Iterable<ResourceLocation> allNodeIds, Random random) {
        List<ResourceLocation> ids = new ArrayList<>();
        allNodeIds.forEach(ids::add);

        for (ResourceLocation id : ids) {
            nodeTiebreak.computeIfAbsent(id, key -> random.nextInt());
        }

        ids.sort(Comparator
                .comparingInt((ResourceLocation id) -> getPointsInvested(id))
                .reversed()
                .thenComparingInt(nodeTiebreak::get));

        return ids;
    }

    public Map<ResourceLocation, NodeProgress> getAllProgress() {
        return new HashMap<>(progress);
    }

    public Map<ResourceLocation, Integer> getAllInvested() {
        return new HashMap<>(pointsInvested);
    }
}