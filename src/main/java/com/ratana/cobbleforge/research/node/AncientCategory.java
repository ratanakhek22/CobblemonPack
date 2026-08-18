package com.ratana.cobbleforge.research.node;

/**
 * Mirrors the six "Ancient" minigame reward items from the design doc. Each legendary
 * node is tagged with exactly one of these, and an Ancient item of that category can
 * be spent (via Journal + Brush at the table) for a discount on whichever legendary of
 * that type it randomly lands on.
 */
public enum AncientCategory {
    GEODE_STEEL_ROCK_GROUND,
    PRISMARINE_WATER_ICE_ELECTRIC,
    SEED_GRASS_BUG_POISON,
    AMBER_DRAGON_FAIRY_FIRE,
    TOME_GHOST_DARK_PSYCHIC,
    TOTEM_FLYING_FIGHTING_NORMAL
}