package com.ratana.cobbleforge.research.node;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Species;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

public final class TypeGroupRegistry {
    private TypeGroupRegistry() {}

    /** type name -> group. ASSUMPTION: ElementalType.getName() returns lowercase type
     *  identifiers like "fire", "water", etc. -- verify against actual Cobblemon output
     *  before trusting this map; if names differ (e.g. capitalized, or a ResourceLocation
     *  instead of String), only this map's keys need adjusting. */
    private static final Map<String, TypeGroup> TYPE_TO_GROUP = Map.ofEntries(
            Map.entry("steel", TypeGroup.STEEL_ROCK_GROUND),
            Map.entry("rock", TypeGroup.STEEL_ROCK_GROUND),
            Map.entry("ground", TypeGroup.STEEL_ROCK_GROUND),
            Map.entry("water", TypeGroup.WATER_ICE_ELECTRIC),
            Map.entry("ice", TypeGroup.WATER_ICE_ELECTRIC),
            Map.entry("electric", TypeGroup.WATER_ICE_ELECTRIC),
            Map.entry("grass", TypeGroup.GRASS_BUG_POISON),
            Map.entry("bug", TypeGroup.GRASS_BUG_POISON),
            Map.entry("poison", TypeGroup.GRASS_BUG_POISON),
            Map.entry("dragon", TypeGroup.DRAGON_FAIRY_FIRE),
            Map.entry("fairy", TypeGroup.DRAGON_FAIRY_FIRE),
            Map.entry("fire", TypeGroup.DRAGON_FAIRY_FIRE),
            Map.entry("ghost", TypeGroup.GHOST_DARK_PSYCHIC),
            Map.entry("dark", TypeGroup.GHOST_DARK_PSYCHIC),
            Map.entry("psychic", TypeGroup.GHOST_DARK_PSYCHIC),
            Map.entry("flying", TypeGroup.FLYING_FIGHTING_NORMAL),
            Map.entry("fighting", TypeGroup.FLYING_FIGHTING_NORMAL),
            Map.entry("normal", TypeGroup.FLYING_FIGHTING_NORMAL)
    );

    private static final Map<TypeGroup, List<ResourceLocation>> GROUP_MEMBERS = new EnumMap<>(TypeGroup.class);

    public static void bootstrap(Collection<ResearchNodeDefinition> allDefinitions) {
        for (TypeGroup g : TypeGroup.values()) {
            GROUP_MEMBERS.put(g, new ArrayList<>());
        }

        for (ResearchNodeDefinition def : allDefinitions) {
            for (TypeGroup group : groupsFor(def.speciesId())) {
                GROUP_MEMBERS.get(group).add(def.speciesId());
            }
        }
    }

    /** A dual-typed species can belong to two DIFFERENT groups if its two types map to
     *  different groups (per design: "in both pools if they don't overlap into one item").
     *  A species whose two types map to the SAME group is naturally deduped via the Set. */
    private static Set<TypeGroup> groupsFor(ResourceLocation speciesId) {
        Species species = PokemonSpecies.getByIdentifier(speciesId);
        if (species == null) return Set.of();

        FormData form = species.getStandardForm();
        Set<TypeGroup> groups = new HashSet<>();

        TypeGroup primary = TYPE_TO_GROUP.get(form.getPrimaryType().getName().toLowerCase(Locale.ROOT));
        if (primary != null) groups.add(primary);

        ElementalType secondary = form.getSecondaryType();
        if (secondary != null) {
            TypeGroup secondaryGroup = TYPE_TO_GROUP.get(secondary.getName().toLowerCase(Locale.ROOT));
            if (secondaryGroup != null) groups.add(secondaryGroup);
        }

        return groups;
    }

    public static List<ResourceLocation> membersOf(TypeGroup group) {
        return GROUP_MEMBERS.getOrDefault(group, List.of());
    }
}