package com.ratana.cobbleforge.research.altar;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LegendaryEncounterTracker {
    private LegendaryEncounterTracker() {}

    public static final String TAG_SPECIES = "cobbleforge_locked_species";
    public static final String TAG_SUMMONER = "cobbleforge_summoner";
    public static final String TAG_SPAWN_TICK = "cobbleforge_spawn_tick";

    private static final int MAX_DISTANCE_BLOCKS = 32;
    private static final long LIFESPAN_TICKS = 20L * 60 * 10;

    /** Entities marked here are permanently locked (caught) -- their removal from the world
     *  is expected and must NOT trigger an unlock. Small, append-only, trivial memory cost;
     *  unlike the old ACTIVE map this is never treated as a source of truth about existence. */
    private static final Set<UUID> CAPTURED = ConcurrentHashMap.newKeySet();

    public static void onCaptured(UUID entityId) {
        CAPTURED.add(entityId);
    }

    /** Called every ~20 ticks. Scans currently-loaded entities for our marker tag and
     *  evaluates removal conditions directly against them -- nothing to desync, since we
     *  never assert "this should still exist" independent of the world itself. */
    public static void tick(ServerLevel level) {
        long now = level.getGameTime();

        for (Entity e : level.getAllEntities()) {
            if (!(e instanceof PokemonEntity pokemonEntity)) continue;

            CompoundTag data = pokemonEntity.getPersistentData();
            if (!data.contains(TAG_SPECIES)) continue; // not one of ours

            UUID entityId = pokemonEntity.getUUID();
            if (CAPTURED.contains(entityId)) continue; // caught, permanently locked, not our concern anymore

            ResourceLocation species = ResourceLocation.parse(data.getString(TAG_SPECIES));
            UUID summoner = data.getUUID(TAG_SUMMONER);
            long spawnTick = data.getLong(TAG_SPAWN_TICK);

            ServerPlayer player = level.getServer().getPlayerList().getPlayer(summoner);
            boolean playerGone = player == null;
            boolean tooFar = !playerGone
                    && player.distanceToSqr(pokemonEntity) > (double) MAX_DISTANCE_BLOCKS * MAX_DISTANCE_BLOCKS;
            boolean expired = now - spawnTick > LIFESPAN_TICKS;

            if (playerGone || tooFar || expired) {
                pokemonEntity.discard();
                LockedLegendaryData.get(level).unlock(species);
            }
        }
    }

    /** Safety net for death by any cause not covered by discard() above (killed by a mob,
     *  fall damage, etc.) -- called from a LivingDeathEvent listener. */
    public static void onEntityDeath(PokemonEntity pokemonEntity, ServerLevel level) {
        CompoundTag data = pokemonEntity.getPersistentData();
        if (!data.contains(TAG_SPECIES)) return;
        if (CAPTURED.contains(pokemonEntity.getUUID())) return;

        ResourceLocation species = ResourceLocation.parse(data.getString(TAG_SPECIES));
        LockedLegendaryData.get(level).unlock(species);
    }
}