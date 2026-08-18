package com.ratana.cobbleforge.research.node;

import net.minecraft.resources.ResourceLocation;
import org.joml.Vector2f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure math — no state, no lookups into player data. Takes the flat ordering
 * that ResearchPlayerData#computeSlotOrder already produces and converts each
 * index into a ring index + position-in-ring, then into screen coordinates.
 * Safe to call on both client and server since it does nothing but arithmetic
 * on values it's handed.
 */
public final class NodeSlotLayout {

    private static final int BASE_RING_CAPACITY = 6; // 6, 12, 24, 48...
    private static final float INNER_RADIUS = 20f;
    private static final float RING_SPACING = 16f;

    private NodeSlotLayout() {}

    public static int capacityForRing(int ringIndex) {
        return BASE_RING_CAPACITY << ringIndex;
    }

    public static float radiusForRing(int ringIndex) {
        return INNER_RADIUS + ringIndex * RING_SPACING;
    }

    /**
     * Given a flat slot index (position in the sorted list from
     * computeSlotOrder), returns which ring it falls in and its position
     * within that ring. Index 0 is the first slot of ring 0, index 6 is the
     * first slot of ring 1, index 18 (6+12) is the first slot of ring 2, etc.
     */
    public static SlotCoordinate ringForFlatIndex(int flatIndex) {
        int ring = 0;
        int remaining = flatIndex;
        while (remaining >= capacityForRing(ring)) {
            remaining -= capacityForRing(ring);
            ring++;
        }
        return new SlotCoordinate(ring, remaining);
    }

    public static Vector2f screenPosition(int ringIndex, int positionInRing, int centerX, int centerY) {
        int capacity = capacityForRing(ringIndex);
        double slotAngle = (2 * Math.PI * positionInRing) / capacity;
        // stagger alternating rings a half-slot so spokes don't line up radially
        double stagger = (ringIndex % 2 == 0) ? 0 : Math.PI / capacity;
        double angle = slotAngle + stagger;
        float radius = radiusForRing(ringIndex);
        return new Vector2f(
                centerX + (float) (radius * Math.cos(angle)),
                centerY + (float) (radius * Math.sin(angle))
        );
    }

    /**
     * Convenience: walks a full ordered node list (as returned by
     * computeSlotOrder) straight into a nodeId -> screen position map.
     * This is the function ResearchNetworking or ModResearchScreen should
     * actually call — the two methods above are the building blocks.
     */
    public static Map<ResourceLocation, Vector2f> layoutAll(List<ResourceLocation> orderedNodeIds,
                                                            int centerX, int centerY) {
        Map<ResourceLocation, Vector2f> result = new HashMap<>();
        for (int i = 0; i < orderedNodeIds.size(); i++) {
            SlotCoordinate slot = ringForFlatIndex(i);
            result.put(orderedNodeIds.get(i),
                    screenPosition(slot.ring(), slot.positionInRing(), centerX, centerY));
        }
        return result;
    }

    public record SlotCoordinate(int ring, int positionInRing) {}
}